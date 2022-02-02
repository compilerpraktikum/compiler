package edu.kit.compiler.backend.molkir

import edu.kit.compiler.backend.register.getAtntSuffix

interface MolkIR {
    fun toMolki(): String
}

sealed interface Target : MolkIR {
    sealed interface Input : Target
    sealed interface Output : Target
    sealed interface InputOutput : Input, Output

    val width: Width
}

class Constant(val value: String, override val width: Width) : Target.Input {
    override fun toMolki(): String = "$$value"
}

@JvmInline
value class RegisterId(val value: Int) : MolkIR {
    override fun toMolki(): String = value.toString()
    override fun toString(): String = value.toString()
}

enum class Width(val inBytes: Int, val registerSuffix: String, val instructionSuffix: String) {
    BYTE(1, "b", "b"),
    WORD(2, "w", "w"),
    DOUBLE(4, "d", "l"),
    QUAD(8, "", "q");

    companion object {
        fun fromByteSize(size: Int): Width? = when (size) {
            1 -> BYTE
            2 -> WORD
            4 -> DOUBLE
            8 -> QUAD
            else -> null
        }
    }
}

data class Register(val id: RegisterId, override val width: Width) : Target.InputOutput {
    override fun toMolki(): String = "%@" + id.toMolki() + width.registerSuffix

    companion object {
        fun byte(id: RegisterId) = Register(id, Width.BYTE)
        fun word(id: RegisterId) = Register(id, Width.WORD)
        fun double(id: RegisterId) = Register(id, Width.DOUBLE)
        fun quad(id: RegisterId) = Register(id, Width.QUAD)
    }
}

class ReturnRegister(override val width: Width) : Target.Output {
    override fun toMolki(): String = "%@r0" + width.registerSuffix

    companion object {
        fun byte() = ReturnRegister(Width.BYTE)
        fun word() = ReturnRegister(Width.WORD)
        fun double() = ReturnRegister(Width.DOUBLE)
        fun quad() = ReturnRegister(Width.QUAD)
    }
}

class Memory
private constructor(
    val const: String?,
    val base: Register?,
    val index: Register?,
    val scale: Int?,
    override val width: Width,
) : Target.InputOutput {
    companion object {
        fun of(
            const: String,
            base: Register,
            index: Register? = null,
            scale: Int? = null,
            width: Width
        ) = Memory(const, base, index, scale, width)

        fun of(
            base: Register,
            index: Register? = null,
            scale: Int? = null,
            width: Width
        ) = Memory(null, base, index, scale, width)

        fun of(
            const: String,
            index: Register? = null,
            scale: Int? = null,
            width: Width
        ) = Memory(const, null, index, scale, width)
    }

    init {
        if (scale != null) {
            check(scale in listOf(1, 2, 4, 8)) { "scale must be 1, 2, 4 or 8" }
            check(index != null) { "cannot provide scale without index" }
        }
    }

    fun width(newWidth: Width) = Memory(const, base, index, scale, newWidth)

    override fun toMolki(): String {
        val constStr = const?.toString() ?: ""
        val baseStr = base?.toMolki() ?: ""
        val extStr = if (index != null) {
            val indexStr = index.toMolki()
            if (scale != null) {
                ",$indexStr,$scale"
            } else {
                ",$indexStr"
            }
        } else {
            ""
        }
        return "$constStr($baseStr$extStr)"
    }
}

sealed class Instruction : MolkIR {
    open val hasIndent: Boolean = true

    class Label(val name: String) : Instruction() {
        override val hasIndent: Boolean = false

        override fun toMolki(): String = "$name:"
    }

    class Comment(val content: String) : Instruction() {
        override fun toMolki(): String = "; $content"
    }

    /**
     * @param name function name
     * @param arguments [Target.Input]s for arguments
     * @param result where to store the function's result value
     * @param external whether this is an external function linked at a later point
     */
    class Call(
        val name: String,
        val arguments: List<Target.Input>,
        val result: Target.Output?,
        val external: Boolean
    ) : Instruction() {
        override fun toMolki(): String {
            val args = arguments.joinToString(" | ") { it.toMolki() }
            val resultStr = result?.let { " -> ${it.toMolki()}" } ?: ""
            return "call $name [ $args ]$resultStr"
        }
    }

    class Jump(val name: String, val label: String) : Instruction() {
        override fun toMolki(): String = "$name $label"
    }

    class UnaryOperationWithResult(
        val name: String,
        val operand: Target.Input,
        val result: Target.Output,
    ) : Instruction() {
        override fun toMolki(): String = "$name ${operand.toMolki()}, ${result.toMolki()}"
    }

    class BinaryOperation(
        val name: String,
        val left: Target.Input,
        val right: Target.Input,
    ) : Instruction() {
        override fun toMolki(): String = "$name ${left.toMolki()}, ${right.toMolki()}"
    }

    class BinaryOperationWithResult(
        val name: String,
        val left: Target.Input,
        val right: Target.Input,
        val result: Target.Output,
    ) : Instruction() {
        override fun toMolki(): String = "$name [ ${left.toMolki()} | ${right.toMolki()} ] -> ${result.toMolki()}"
    }

    class SubtractionOperationWithResult(
        val name: String,
        val left: Target.Input,
        val right: Target.Input,
        val result: Target.Output,
    ) : Instruction() {
        override fun toMolki(): String = "$name [ ${left.toMolki()} | ${right.toMolki()} ] -> ${result.toMolki()}"
    }

    class DivisionOperation(
        val name: String,
        val left: Target.Input,
        val right: Target.Input,
        val resultLeft: Target.Output,
        val resultRight: Target.Output,
    ) : Instruction() {
        override fun toMolki(): String =
            "$name [ ${left.toMolki()} | ${right.toMolki()} ] -> [ ${resultLeft.toMolki()} | ${resultRight.toMolki()} ]"
    }

    /**
     * Instruction suffixes:
     *   - b - byte (1 byte)
     *   - w - word (2 bytes)
     *   - l - double (4 bytes)
     *   - q - quad (8 bytes)
     */
    companion object {

        fun comment(content: String) = Comment(content)

        fun label(name: String) = Label(name)

        fun call(name: String, arguments: List<Target.Input>, result: Target.Output?, external: Boolean) =
            Call(name, arguments, result, external)

        /* ktlint-disable no-multi-spaces */
        /* @formatter:off */

        fun cmpl(left: Target.Input, right: Target.Input) = BinaryOperation("cmpl", left, right)
        fun cmpq(left: Target.Input, right: Target.Input) = BinaryOperation("cmpq", left, right)

        /****************************************
         * Jumps
         ****************************************/
        fun jmp(label: String) = Jump("jmp", label)
        fun jl(label: String)  = Jump("jl",  label)
        fun jle(label: String) = Jump("jle", label)
        fun je(label: String)  = Jump("je",  label)
        fun jne(label: String) = Jump("jne", label)
        fun jg(label: String)  = Jump("jg",  label)
        fun jge(label: String) = Jump("jge", label)

        /****************************************
         * Unary operations with result
         ****************************************/
        // move
        fun mov(from: Target.Input, to: Target.Output): UnaryOperationWithResult {
            check(from.width.inBytes == to.width.inBytes) { "widths of from and to mismatch: mov from ${from.width} to ${to.width}" }
            return UnaryOperationWithResult("mov${from.width.getAtntSuffix()}", from, to)
        }
        fun movl(from: Target.Input, to: Target.Output) = UnaryOperationWithResult("movl", from, to)
        fun movq(from: Target.Input, to: Target.Output) = UnaryOperationWithResult("movq", from, to)
        fun movsxlq(from: Target.Input, to: Target.Output) = UnaryOperationWithResult("movsxlq", from, to)

        // increment / decrement
        fun incl(operand: Target.Input, result: Target.Output) = UnaryOperationWithResult("incl", operand, result)
        fun incq(operand: Target.Input, result: Target.Output) = UnaryOperationWithResult("incq", operand, result)
        fun decl(operand: Target.Input, result: Target.Output) = UnaryOperationWithResult("decl", operand, result)
        fun decq(operand: Target.Input, result: Target.Output) = UnaryOperationWithResult("decq", operand, result)

        // negate (2 complement)
        fun negl(operand: Target.Input, result: Target.Output) = UnaryOperationWithResult("negl", operand, result)
        fun negq(operand: Target.Input, result: Target.Output) = UnaryOperationWithResult("negq", operand, result)
        fun neg(operand: Target.Input, result: Target.Output) =
            UnaryOperationWithResult("neg" + operand.width.getAtntSuffix(), operand, result)

        // logical not
        fun notl(operand: Target.Input, result: Target.Output) = UnaryOperationWithResult("notl", operand, result)
        fun notq(operand: Target.Input, result: Target.Output) = UnaryOperationWithResult("notq", operand, result)
        fun not(operand: Target.Input, result: Target.Output) =
            UnaryOperationWithResult("not" + operand.width.getAtntSuffix(), operand, result)

        /****************************************
         * Binary operations with result
         ****************************************/
        fun idivq(left: Target.Input, right: Target.Input, resultDiv: Target.Output, resultMod: Target.Output) =
            DivisionOperation("idivq", left, right, resultDiv, resultMod)

        // basic arithmetic
        fun add(left: Target.Input, right: Target.Input, result: Target.Output) =
            BinaryOperationWithResult("add" + left.width.getAtntSuffix(), left, right, result)

        fun addl(left: Target.Input, right: Target.Input, result: Target.Output)  = BinaryOperationWithResult("addl",  left, right, result)
        fun addq(left: Target.Input, right: Target.Input, result: Target.Output)  = BinaryOperationWithResult("addq",  left, right, result)

        fun sub(left: Target.Input, right: Target.Input, result: Target.Output) =
            SubtractionOperationWithResult("sub" + left.width.getAtntSuffix(), left, right, result)

        fun subl(left: Target.Input, right: Target.Input, result: Target.Output)  = SubtractionOperationWithResult("subl",  left, right, result)
        fun subq(left: Target.Input, right: Target.Input, result: Target.Output)  = SubtractionOperationWithResult("subq",  left, right, result)

        fun imul(left: Target.Input, right: Target.Input, result: Target.Output) =
            BinaryOperationWithResult("imul" + left.width.getAtntSuffix(), left, right, result)
        fun imull(left: Target.Input, right: Target.Input, result: Target.Output) = BinaryOperationWithResult("imull", left, right, result)
        fun imulq(left: Target.Input, right: Target.Input, result: Target.Output) = BinaryOperationWithResult("imulq", left, right, result)

        // basic logic
        fun and(left: Target.Input, right: Target.Input, result: Target.Output) =
            BinaryOperationWithResult("and" + left.width.getAtntSuffix(), left, right, result)
        fun andl(left: Target.Input, right: Target.Input, result: Target.Output)  = BinaryOperationWithResult("andl", left, right, result)
        fun andq(left: Target.Input, right: Target.Input, result: Target.Output)  = BinaryOperationWithResult("andq", left, right, result)
        fun or(left: Target.Input, right: Target.Input, result: Target.Output) =
            BinaryOperationWithResult("or" + left.width.getAtntSuffix(), left, right, result)
        fun orl(left: Target.Input, right: Target.Input, result: Target.Output)   = BinaryOperationWithResult("orl",  left, right, result)
        fun orq(left: Target.Input, right: Target.Input, result: Target.Output)   = BinaryOperationWithResult("orq",  left, right, result)
        fun xor(left: Target.Input, right: Target.Input, result: Target.Output) =
            BinaryOperationWithResult("xor" + left.width.getAtntSuffix(), left, right, result)
        fun xorl(left: Target.Input, right: Target.Input, result: Target.Output)  = BinaryOperationWithResult("xorl", left, right, result)
        fun xorq(left: Target.Input, right: Target.Input, result: Target.Output)  = BinaryOperationWithResult("xorq", left, right, result)

        // bitwise shift (arithmetic = preserve sign)
        fun sar(left: Target.Input, right: Target.Input, result: Target.Output) =
            BinaryOperationWithResult("sar" + left.width.getAtntSuffix(), left, right, result)
        fun sall(left: Target.Input, right: Target.Input, result: Target.Output)  = BinaryOperationWithResult("sall", left, right, result)
        fun salq(left: Target.Input, right: Target.Input, result: Target.Output)  = BinaryOperationWithResult("salq", left, right, result)
        fun sarl(left: Target.Input, right: Target.Input, result: Target.Output)  = BinaryOperationWithResult("sarl", left, right, result)
        fun sarq(left: Target.Input, right: Target.Input, result: Target.Output)  = BinaryOperationWithResult("sarq", left, right, result)

        // bitwise shift (logical)
        fun shl(left: Target.Input, right: Target.Input, result: Target.Output) =
            BinaryOperationWithResult("shl" + left.width.getAtntSuffix(), left, right, result)
        fun shll(left: Target.Input, right: Target.Input, result: Target.Output)  = BinaryOperationWithResult("shll", left, right, result)
        fun shlq(left: Target.Input, right: Target.Input, result: Target.Output)  = BinaryOperationWithResult("shlq", left, right, result)
        fun shr(left: Target.Input, right: Target.Input, result: Target.Output) =
            BinaryOperationWithResult("shr" + left.width.getAtntSuffix(), left, right, result)
        fun shrl(left: Target.Input, right: Target.Input, result: Target.Output)  = BinaryOperationWithResult("shrl", left, right, result)
        fun shrq(left: Target.Input, right: Target.Input, result: Target.Output)  = BinaryOperationWithResult("shrq", left, right, result)

        /* @formatter:on */
        /* ktlint-enable no-multi-spaces */
    }
}
