package edu.kit.compiler.backend.molkir

import java.io.PrintStream

interface MolkIR {
    fun toMolki(): String
}

abstract class Target : MolkIR {
    override fun toString(): String = toMolki()
}

abstract class OutputTarget : Target()
abstract class InputOutputTarget : OutputTarget()

class Constant(val value: Int) : InputOutputTarget() {
    override fun toMolki(): String = "$$value"
}

enum class Width(val inBytes: Int, val suffix: String) {
    BYTE(1, "l"),
    WORD(2, "w"),
    DOUBLE(4, "d"),
    QUAD(8, ""),
}

class Register(val id: Int, val width: Width) : InputOutputTarget() {
    override fun toMolki(): String = "%@" + id + width.suffix

    companion object {
        fun byte(id: Int) = Register(id, Width.BYTE)
        fun word(id: Int) = Register(id, Width.WORD)
        fun double(id: Int) = Register(id, Width.DOUBLE)
        fun quad(id: Int) = Register(id, Width.QUAD)
    }
}

class ReturnRegister(val width: Width) : OutputTarget() {
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
    val base: InputOutputTarget?,
    val index: InputOutputTarget?,
    val scale: Int?,
) : InputOutputTarget() {
    init {
        if (scale != null) {
            check(scale in listOf(1, 2, 4, 8)) { "scale must be 1, 2, 4 or 8" }
            check(index != null) { "cannot provide scale without index" }
        }
    }

    constructor(
        const: Int,
        base: InputOutputTarget,
        index: InputOutputTarget? = null,
        scale: Int? = null
    ) : this(const as Int?, base, index, scale)

    constructor(base: InputOutputTarget, index: InputOutputTarget? = null, scale: Int? = null) : this(
        null,
        base,
        index,
        scale
    )

    constructor(const: Int, index: InputOutputTarget? = null, scale: Int? = null) : this(const, null, index, scale)

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

abstract class Instruction : MolkIR {
    class Label(val name: String) : Instruction() {
        override fun toMolki(): String = "$name:"
    }

    class Call(val name: String, val arguments: List<InputOutputTarget>, val result: Target?) : Instruction() {
        override fun toMolki(): String {
            val args = arguments.joinToString(" | ") { it.toMolki() }
            val resultStr = result?.let { " -> ${it.toMolki()}" } ?: ""
            return "call $name [ $arguments ]$resultStr"
        }
    }

    class Jump(val name: String, val label: String) : Instruction() {
        override fun toMolki(): String = "$name $label"
    }

    class BinaryOperation(val name: String, val left: Target, val right: Target) : Instruction() {
        override fun toMolki(): String = "$name ${left.toMolki()}, ${right.toMolki()}"
    }

    class BinaryOperationWithResult(
        val name: String,
        val left: InputOutputTarget,
        val right: InputOutputTarget,
        val result: Target
    ) : Instruction() {
        override fun toMolki(): String = "$name [ ${left.toMolki()} | ${right.toMolki()} ] -> ${result.toMolki()}"
    }

    class BinaryOperationWithTwoPartResult(
        val name: String,
        val left: InputOutputTarget,
        val right: InputOutputTarget,
        val resultLeft: Target,
        val resultRight: Target
    ) : Instruction() {
        override fun toMolki(): String =
            "$name [ ${left.toMolki()} | ${right.toMolki()} ] -> [ ${resultLeft.toMolki()} | ${resultRight.toMolki()} ]"
    }
}

/**
 * Builder to construct the assembler code for a function.
 *
 * Instruction suffixes:
 *   - b - byte (1 byte)
 *   - w - word (2 bytes)
 *   - l - double (4 bytes)
 *   - q - quad (8 bytes)
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
            val indent = if (it is Instruction.Label) 0 else 4
            out.append(" ".repeat(indent))
            out.appendLine(it.toMolki())
        }
        out.appendLine(".endfunction")
    }

    private fun add(instruction: Instruction) {
        instructions.add(instruction)
    }

    fun label(name: String) {
        add(Instruction.Label(name))
    }

    fun call(name: String, arguments: List<InputOutputTarget>, result: Target?) {
        add(Instruction.Call(name, arguments, result))
    }

    /* @formatter:off */
    fun movl(from: InputOutputTarget, to: Target) { add(Instruction.BinaryOperation("movl", from, to)) }
    fun movq(from: InputOutputTarget, to: Target) { add(Instruction.BinaryOperation("movq", from, to)) }

    fun cmpl(left: InputOutputTarget, right: InputOutputTarget) { add(Instruction.BinaryOperation("cmpl", left, right)) }
    fun cmpq(left: InputOutputTarget, right: InputOutputTarget) { add(Instruction.BinaryOperation("cmpq", left, right)) }
    /* @formatter:on */

    /****************************************
     * Jumps
     ****************************************/
    private fun jump(name: String, label: String) {
        add(Instruction.Jump(name, label))
    }

    /* ktlint-disable no-multi-spaces */
    /* @formatter:off */
    fun jmp(label: String) { jump("jmp", label) }
    fun jl(label: String)  { jump("jl",  label) }
    fun jle(label: String) { jump("jle", label) }
    fun je(label: String)  { jump("je",  label) }
    fun jne(label: String) { jump("jne", label) }
    fun jg(label: String)  { jump("jg",  label) }
    fun jge(label: String) { jump("jge", label) }
    /* @formatter:on */
    /* ktlint-enable no-multi-spaces */

    /****************************************
     * Binary operations with result
     ****************************************/
    fun idivq(left: InputOutputTarget, right: InputOutputTarget, resultDiv: Target, resultMod: Target) {
        add(Instruction.BinaryOperationWithTwoPartResult("idivq", left, right, resultDiv, resultMod))
    }

    private fun binOp(name: String, left: InputOutputTarget, right: InputOutputTarget, result: Target) {
        add(Instruction.BinaryOperationWithResult(name, left, right, result))
    }

    /* ktlint-disable no-multi-spaces */
    /* @formatter:off */
    // basic arithmetic
    fun addl(left: InputOutputTarget, right: InputOutputTarget, result: Target)  { binOp("addl",  left, right, result) }
    fun addq(left: InputOutputTarget, right: InputOutputTarget, result: Target)  { binOp("addq",  left, right, result) }
    fun subl(left: InputOutputTarget, right: InputOutputTarget, result: Target)  { binOp("subl",  left, right, result) }
    fun subq(left: InputOutputTarget, right: InputOutputTarget, result: Target)  { binOp("subq",  left, right, result) }
    fun imull(left: InputOutputTarget, right: InputOutputTarget, result: Target) { binOp("imull", left, right, result) }
    fun imulq(left: InputOutputTarget, right: InputOutputTarget, result: Target) { binOp("imulq", left, right, result) }

    // increment / decrement
    fun incl(left: InputOutputTarget, right: InputOutputTarget, result: Target)  { binOp("incl", left, right, result) }
    fun incq(left: InputOutputTarget, right: InputOutputTarget, result: Target)  { binOp("incq", left, right, result) }
    fun decl(left: InputOutputTarget, right: InputOutputTarget, result: Target)  { binOp("decl", left, right, result) }
    fun decq(left: InputOutputTarget, right: InputOutputTarget, result: Target)  { binOp("decq", left, right, result) }

    // negate (2 complement)
    fun negl(left: InputOutputTarget, right: InputOutputTarget, result: Target)  { binOp("negl", left, right, result) }
    fun negq(left: InputOutputTarget, right: InputOutputTarget, result: Target)  { binOp("negq", left, right, result) }
    fun notl(left: InputOutputTarget, right: InputOutputTarget, result: Target)  { binOp("notl", left, right, result) }
    fun notq(left: InputOutputTarget, right: InputOutputTarget, result: Target)  { binOp("notq", left, right, result) }
    fun andl(left: InputOutputTarget, right: InputOutputTarget, result: Target)  { binOp("andl", left, right, result) }
    fun andq(left: InputOutputTarget, right: InputOutputTarget, result: Target)  { binOp("andq", left, right, result) }
    fun orl(left: InputOutputTarget, right: InputOutputTarget, result: Target)   { binOp("orl",  left, right, result) }
    fun orq(left: InputOutputTarget, right: InputOutputTarget, result: Target)   { binOp("orq",  left, right, result) }
    fun xorl(left: InputOutputTarget, right: InputOutputTarget, result: Target)  { binOp("xorl", left, right, result) }
    fun xorq(left: InputOutputTarget, right: InputOutputTarget, result: Target)  { binOp("xorq", left, right, result) }

    // bitwise shift (arithmetic = preserve sign)
    fun sall(left: InputOutputTarget, right: InputOutputTarget, result: Target)  { binOp("sall", left, right, result) }
    fun salq(left: InputOutputTarget, right: InputOutputTarget, result: Target)  { binOp("salq", left, right, result) }
    fun sarl(left: InputOutputTarget, right: InputOutputTarget, result: Target)  { binOp("sarl", left, right, result) }
    fun sarq(left: InputOutputTarget, right: InputOutputTarget, result: Target)  { binOp("sarq", left, right, result) }

    // bitwise shift (logical)
    fun shll(left: InputOutputTarget, right: InputOutputTarget, result: Target)  { binOp("shll", left, right, result) }
    fun shlq(left: InputOutputTarget, right: InputOutputTarget, result: Target)  { binOp("shlq", left, right, result) }
    fun shrl(left: InputOutputTarget, right: InputOutputTarget, result: Target)  { binOp("shrl", left, right, result) }
    fun shrq(left: InputOutputTarget, right: InputOutputTarget, result: Target)  { binOp("shrq", left, right, result) }
    /* @formatter:on */
    /* ktlint-enable no-multi-spaces */
}
