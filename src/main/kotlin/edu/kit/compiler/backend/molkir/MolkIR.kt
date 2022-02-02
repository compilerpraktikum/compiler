package edu.kit.compiler.backend.molkir

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

private fun inferSuffix(operand: Target, vararg others: Target): String {
    others.forEachIndexed { i, other ->
        check(operand.width == other.width) { "incompatible types ($i): ${operand.width} <-> ${other.width}" }
    }
    return operand.width.instructionSuffix
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
        name: String,
        val operand: Target.Input,
        val result: Target.Output,
    ) : Instruction() {
        val name = name + inferSuffix(operand, result)

        override fun toMolki(): String = "$name ${operand.toMolki()}, ${result.toMolki()}"
    }

    class BinaryOperation(
        name: String,
        val left: Target.Input,
        val right: Target.Input,
    ) : Instruction() {
        val name = name + inferSuffix(left, right)

        override fun toMolki(): String = "$name ${left.toMolki()}, ${right.toMolki()}"
    }

    class BinaryOperationWithResult(
        name: String,
        val left: Target.Input,
        val right: Target.Input,
        val result: Target.Output,
    ) : Instruction() {
        val name = name + inferSuffix(left, right, result)

        override fun toMolki(): String = "$name [ ${left.toMolki()} | ${right.toMolki()} ] -> ${result.toMolki()}"
    }

    class SubtractionOperationWithResult(
        name: String,
        val left: Target.Input,
        val right: Target.Input,
        val result: Target.Output,
    ) : Instruction() {
        val name = name + inferSuffix(left, right, result)

        override fun toMolki(): String = "$name [ ${left.toMolki()} | ${right.toMolki()} ] -> ${result.toMolki()}"
    }

    class DivisionOperation(
        name: String,
        val left: Target.Input,
        val right: Target.Input,
        val resultLeft: Target.Output,
        val resultRight: Target.Output,
    ) : Instruction() {
        val name = name + inferSuffix(left, right, resultLeft, resultRight)

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

        fun cmp(left: Target.Input, right: Target.Input) = BinaryOperation("cmp", left, right)

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
        fun mov(from: Target.Input, to: Target.Output): UnaryOperationWithResult = UnaryOperationWithResult("mov", from, to)
        fun inc(operand: Target.Input, result: Target.Output) = UnaryOperationWithResult("inc", operand, result)
        fun dec(operand: Target.Input, result: Target.Output) = UnaryOperationWithResult("dec", operand, result)
        fun neg(operand: Target.Input, result: Target.Output) = UnaryOperationWithResult("neg", operand, result)
        fun not(operand: Target.Input, result: Target.Output) = UnaryOperationWithResult("not", operand, result)

        /****************************************
         * Binary operations with result
         ****************************************/
        fun idiv(left: Target.Input, right: Target.Input, resultDiv: Target.Output, resultMod: Target.Output) =
            DivisionOperation("idiv", left, right, resultDiv, resultMod)

        // basic arithmetic
        fun add(left: Target.Input, right: Target.Input, result: Target.Output) = BinaryOperationWithResult("add", left, right, result)
        fun sub(left: Target.Input, right: Target.Input, result: Target.Output) = SubtractionOperationWithResult("sub", left, right, result)
        fun imul(left: Target.Input, right: Target.Input, result: Target.Output) = BinaryOperationWithResult("imul", left, right, result)

        // basic logic
        fun and(left: Target.Input, right: Target.Input, result: Target.Output) = BinaryOperationWithResult("and", left, right, result)
        fun or(left: Target.Input, right: Target.Input, result: Target.Output) = BinaryOperationWithResult("or", left, right, result)
        fun xor(left: Target.Input, right: Target.Input, result: Target.Output) = BinaryOperationWithResult("xor", left, right, result)

        // bitwise shift (arithmetic = preserve sign)
        fun sal(left: Target.Input, right: Target.Input, result: Target.Output)  = BinaryOperationWithResult("sal", left, right, result)
        fun sar(left: Target.Input, right: Target.Input, result: Target.Output)  = BinaryOperationWithResult("sar", left, right, result)

        // bitwise shift (logical)
        fun shl(left: Target.Input, right: Target.Input, result: Target.Output)  = BinaryOperationWithResult("shl", left, right, result)
        fun shr(left: Target.Input, right: Target.Input, result: Target.Output)  = BinaryOperationWithResult("shr", left, right, result)

        /* @formatter:on */
        /* ktlint-enable no-multi-spaces */
    }
}
