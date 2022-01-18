package edu.kit.compiler.backend.molkir

import java.io.PrintStream

interface MolkIR {
    fun toMolki(): String
}

sealed interface Target : MolkIR {
    sealed interface Input : Target
    sealed interface Output : Target
    sealed interface InputOutput : Input, Output
}

class Constant(val value: Int) : Target.Input {
    override fun toMolki(): String = "$$value"
}

@JvmInline
value class RegisterId(val value: Int) : MolkIR {
    override fun toMolki(): String = value.toString()
}

enum class Width(val inBytes: Int, val suffix: String) {
    BYTE(1, "l"),
    WORD(2, "w"),
    DOUBLE(4, "d"),
    QUAD(8, "");

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

class Register(val id: RegisterId, val width: Width) : Target.InputOutput {
    override fun toMolki(): String = "%@" + id.toMolki() + width.suffix

    companion object {
        fun byte(id: RegisterId) = Register(id, Width.BYTE)
        fun word(id: RegisterId) = Register(id, Width.WORD)
        fun double(id: RegisterId) = Register(id, Width.DOUBLE)
        fun quad(id: RegisterId) = Register(id, Width.QUAD)
    }
}

class ReturnRegister(val width: Width) : Target.Output {
    override fun toMolki(): String = "%@r0" + width.suffix

    companion object {
        fun byte() = ReturnRegister(Width.BYTE)
        fun word() = ReturnRegister(Width.WORD)
        fun double() = ReturnRegister(Width.DOUBLE)
        fun quad() = ReturnRegister(Width.QUAD)
    }
}

class Memory
private constructor(
    val const: Int?,
    val base: Register?,
    val index: Register?,
    val scale: Int?,
) : Target.InputOutput {
    init {
        if (scale != null) {
            check(scale in listOf(1, 2, 4, 8)) { "scale must be 1, 2, 4 or 8" }
            check(index != null) { "cannot provide scale without index" }
        }
    }

    constructor(
        const: Int,
        base: Register,
        index: Register? = null,
        scale: Int? = null
    ) : this(const as Int?, base, index, scale)

    constructor(
        base: Register,
        index: Register? = null,
        scale: Int? = null
    ) : this(null, base, index, scale)

    constructor(
        const: Int,
        index: Register? = null,
        scale: Int? = null
    ) : this(const, null, index, scale)

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

    class Call(
        val name: String,
        val arguments: List<Target.Input>,
        val result: Target.Output?
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

    class BinaryOperationWithTwoPartResult(
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

        fun label(name: String) = Instruction.Label(name)

        fun call(name: String, arguments: List<Target.Input>, result: Target.Output?) =
            Call(name, arguments, result)

        /* @formatter:off */
        fun movl(from: Target.Input, to: Target.Output) = UnaryOperationWithResult("movl", from, to)
        fun movq(from: Target.Input, to: Target.Output) = UnaryOperationWithResult("movq", from, to)

        fun cmpl(left: Target.Input, right: Target.Input) = BinaryOperation("cmpl", left, right)
        fun cmpq(left: Target.Input, right: Target.Input) = BinaryOperation("cmpq", left, right)
        /* @formatter:on */

        /****************************************
         * Jumps
         ****************************************/
        /* ktlint-disable no-multi-spaces */
        /* @formatter:off */
        fun jmp(label: String) = Jump("jmp", label)
        fun jl(label: String)  = Jump("jl",  label)
        fun jle(label: String) = Jump("jle", label)
        fun je(label: String)  = Jump("je",  label)
        fun jne(label: String) = Jump("jne", label)
        fun jg(label: String)  = Jump("jg",  label)
        fun jge(label: String) = Jump("jge", label)
        /* @formatter:on */
        /* ktlint-enable no-multi-spaces */

        /****************************************
         * Binary operations with result
         ****************************************/
        fun idivq(left: Target.Input, right: Target.Input, resultDiv: Target.Output, resultMod: Target.Output) =
            BinaryOperationWithTwoPartResult("idivq", left, right, resultDiv, resultMod)

        /* ktlint-disable no-multi-spaces */
        /* @formatter:off */
        // basic arithmetic
        fun addl(left: Target.Input, right: Target.Input, result: Target.Output)  = BinaryOperationWithResult("addl",  left, right, result)
        fun addq(left: Target.Input, right: Target.Input, result: Target.Output)  = BinaryOperationWithResult("addq",  left, right, result)
        fun subl(left: Target.Input, right: Target.Input, result: Target.Output)  = BinaryOperationWithResult("subl",  left, right, result)
        fun subq(left: Target.Input, right: Target.Input, result: Target.Output)  = BinaryOperationWithResult("subq",  left, right, result)
        fun imull(left: Target.Input, right: Target.Input, result: Target.Output) = BinaryOperationWithResult("imull", left, right, result)
        fun imulq(left: Target.Input, right: Target.Input, result: Target.Output) = BinaryOperationWithResult("imulq", left, right, result)

        // increment / decrement
        fun incl(left: Target.Input, right: Target.Input, result: Target.Output)  = BinaryOperationWithResult("incl", left, right, result)
        fun incq(left: Target.Input, right: Target.Input, result: Target.Output)  = BinaryOperationWithResult("incq", left, right, result)
        fun decl(left: Target.Input, right: Target.Input, result: Target.Output)  = BinaryOperationWithResult("decl", left, right, result)
        fun decq(left: Target.Input, right: Target.Input, result: Target.Output)  = BinaryOperationWithResult("decq", left, right, result)

        // negate (2 complement)
        fun negl(left: Target.Input, right: Target.Input, result: Target.Output)  = BinaryOperationWithResult("negl", left, right, result)
        fun negq(left: Target.Input, right: Target.Input, result: Target.Output)  = BinaryOperationWithResult("negq", left, right, result)
        fun notl(left: Target.Input, right: Target.Input, result: Target.Output)  = BinaryOperationWithResult("notl", left, right, result)
        fun notq(left: Target.Input, right: Target.Input, result: Target.Output)  = BinaryOperationWithResult("notq", left, right, result)
        fun andl(left: Target.Input, right: Target.Input, result: Target.Output)  = BinaryOperationWithResult("andl", left, right, result)
        fun andq(left: Target.Input, right: Target.Input, result: Target.Output)  = BinaryOperationWithResult("andq", left, right, result)
        fun orl(left: Target.Input, right: Target.Input, result: Target.Output)   = BinaryOperationWithResult("orl",  left, right, result)
        fun orq(left: Target.Input, right: Target.Input, result: Target.Output)   = BinaryOperationWithResult("orq",  left, right, result)
        fun xorl(left: Target.Input, right: Target.Input, result: Target.Output)  = BinaryOperationWithResult("xorl", left, right, result)
        fun xorq(left: Target.Input, right: Target.Input, result: Target.Output)  = BinaryOperationWithResult("xorq", left, right, result)

        // bitwise shift (arithmetic = preserve sign)
        fun sall(left: Target.Input, right: Target.Input, result: Target.Output)  = BinaryOperationWithResult("sall", left, right, result)
        fun salq(left: Target.Input, right: Target.Input, result: Target.Output)  = BinaryOperationWithResult("salq", left, right, result)
        fun sarl(left: Target.Input, right: Target.Input, result: Target.Output)  = BinaryOperationWithResult("sarl", left, right, result)
        fun sarq(left: Target.Input, right: Target.Input, result: Target.Output)  = BinaryOperationWithResult("sarq", left, right, result)

        // bitwise shift (logical)
        fun shll(left: Target.Input, right: Target.Input, result: Target.Output)  = BinaryOperationWithResult("shll", left, right, result)
        fun shlq(left: Target.Input, right: Target.Input, result: Target.Output)  = BinaryOperationWithResult("shlq", left, right, result)
        fun shrl(left: Target.Input, right: Target.Input, result: Target.Output)  = BinaryOperationWithResult("shrl", left, right, result)
        fun shrq(left: Target.Input, right: Target.Input, result: Target.Output)  = BinaryOperationWithResult("shrq", left, right, result)
        /* @formatter:on */
        /* ktlint-enable no-multi-spaces */
    }
}

/**
 * Builder to construct the assembler code for a function.
 */
class FunctionBuilder(val name: String, val numArgs: Int, val numResults: Int) {
    init {
        check(numArgs >= 0)
        check(numResults in 0..1) { "multi return not supported" }
    }

    private val instructions = mutableListOf<Instruction>()

    fun build(out: PrintStream) {
        out.appendLine(".function $name $numArgs $numResults")
        instructions.forEach {
            val indent = if (it.hasIndent) 4 else 0
            out.append(" ".repeat(indent))
            out.appendLine(it.toMolki())
        }
        out.appendLine(".endfunction")
    }

    private fun add(instruction: Instruction) {
        instructions.add(instruction)
    }

    operator fun Instruction.unaryPlus() = add(this)
}
