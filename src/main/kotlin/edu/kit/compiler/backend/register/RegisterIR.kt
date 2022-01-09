package edu.kit.compiler.backend.register

// NOTE: This file is a copy-paste result of MolkIR.kt, and thus not complete

/**
 * Intermediate representation after register allocation.
 */
interface RegisterIR {
    fun toAssembler(): String
}

/**
 * Platform registers that can be allocated to IR instructions
 */
sealed class PlatformTarget : RegisterIR {
    /**
     * An x86-64 general purpose register
     */
    sealed class GeneralPurposeRegister(private val registerName: String) : PlatformTarget() {
        object RAX : GeneralPurposeRegister("rax")
        object RBX : GeneralPurposeRegister("rbx")
        object RCX : GeneralPurposeRegister("rcx")
        object RDX : GeneralPurposeRegister("rdx")
        object RSI : GeneralPurposeRegister("rsi")
        object RDI : GeneralPurposeRegister("rdi")
        object RSP : GeneralPurposeRegister("rsp")
        object RBP : GeneralPurposeRegister("rbp")
        object R8 : GeneralPurposeRegister("r8")
        object R9 : GeneralPurposeRegister("r9")
        object R10 : GeneralPurposeRegister("r10")
        object R11 : GeneralPurposeRegister("r11")
        object R12 : GeneralPurposeRegister("r12")
        object R13 : GeneralPurposeRegister("r13")
        object R14 : GeneralPurposeRegister("r14")
        object R15 : GeneralPurposeRegister("r15")

        companion object {
            val registers = arrayOf(RAX, RBX, RCX, RDX, RSI, RDI, RSP, RBP, R8, R9, R10, R11, R12, R13, R14, R15)
        }

        override fun toAssembler(): String {
            return "%$registerName"
        }
    }

    /**
     * A constant loaded by an address mode of the respective instruction
     */
    class Constant(val value: String) : PlatformTarget() {
        override fun toAssembler(): String {
            return "\$$value"
        }
    }
}

sealed class Instruction : RegisterIR {
    class Label(val name: String) : Instruction() {
        override fun toAssembler(): String = "$name:"
    }

    class Call(val name: String, val arguments: List<PlatformTarget>, val result: PlatformTarget?) : Instruction() {
        override fun toAssembler(): String {
            val args = arguments.joinToString(" | ") { it.toAssembler() }
            val resultStr = result?.let { " -> ${it.toAssembler()}" } ?: ""
            return "call $name [ $arguments ]$resultStr"
        }
    }

    class Jump(val name: String, val label: String) : Instruction() {
        override fun toAssembler(): String = "$name $label"
    }

    class BinaryOperation(val name: String, val left: PlatformTarget, val right: PlatformTarget) : Instruction() {
        override fun toAssembler(): String = "$name ${left.toAssembler()}, ${right.toAssembler()}"
    }

    class UnaryOperation(val name: String, val operand: PlatformTarget) : Instruction() {
        override fun toAssembler(): String = "$name ${operand.toAssembler()}"
    }

    companion object {
        fun movl(from: PlatformTarget, to: PlatformTarget) =
            BinaryOperation("movl", from, to)

        fun movq(from: PlatformTarget, to: PlatformTarget) =
            BinaryOperation("movq", from, to)

        fun cmpl(left: PlatformTarget, right: PlatformTarget) =
            BinaryOperation("cmpl", left, right)

        fun cmpq(left: PlatformTarget, right: PlatformTarget) =
            BinaryOperation("cmpq", left, right)

        fun push(operand: PlatformTarget) =
            UnaryOperation("push", operand)

        fun pop(operand: PlatformTarget) =
            UnaryOperation("pop", operand)

        /****************************************
         * Jumps
         ****************************************/
        private fun jump(name: String, label: String) =
            Jump(name, label)

        /* ktlint-disable no-multi-spaces */
        fun jmp(label: String) =
            jump("jmp", label)

        fun jl(label: String) =
            jump("jl", label)

        fun jle(label: String) =
            jump("jle", label)

        fun je(label: String) =
            jump("je", label)

        fun jne(label: String) =
            jump("jne", label)

        fun jg(label: String) =
            jump("jg", label)

        fun jge(label: String) =
            jump("jge", label)

        /* ktlint-enable no-multi-spaces */

        /****************************************
         * Binary operations with result
         ****************************************/
        fun idivq(
            left: PlatformTarget,
            right: PlatformTarget,
            resultDiv: PlatformTarget,
            resultMod: PlatformTarget
        ) =
            BinaryOperationWithTwoPartResult("idivq", left, right, resultDiv, resultMod)

        private fun binOp(
            name: String,
            left: PlatformTarget,
            right: PlatformTarget,
            result: PlatformTarget
        ) =
            BinaryOperationWithResult(name, left, right, result)

        /* ktlint-disable no-multi-spaces */
        // basic arithmetic
        fun addl(left: PlatformTarget, right: PlatformTarget, result: PlatformTarget) =
            binOp("addl", left, right, result)

        fun addq(left: PlatformTarget, right: PlatformTarget, result: PlatformTarget) =
            binOp("addq", left, right, result)

        fun subl(left: PlatformTarget, right: PlatformTarget, result: PlatformTarget) =
            binOp("subl", left, right, result)

        fun subq(left: PlatformTarget, right: PlatformTarget, result: PlatformTarget) =
            binOp("subq", left, right, result)

        fun imull(left: PlatformTarget, right: PlatformTarget, result: PlatformTarget) =
            binOp("imull", left, right, result)

        fun imulq(left: PlatformTarget, right: PlatformTarget, result: PlatformTarget) =
            binOp("imulq", left, right, result)

        // increment / decrement
        fun incl(left: PlatformTarget, right: PlatformTarget, result: PlatformTarget) =
            binOp("incl", left, right, result)

        fun incq(left: PlatformTarget, right: PlatformTarget, result: PlatformTarget) =
            binOp("incq", left, right, result)

        fun decl(left: PlatformTarget, right: PlatformTarget, result: PlatformTarget) =
            binOp("decl", left, right, result)

        fun decq(left: PlatformTarget, right: PlatformTarget, result: PlatformTarget) =
            binOp("decq", left, right, result)

        // negate (2 complement)
        fun negl(left: PlatformTarget, right: PlatformTarget, result: PlatformTarget) =
            binOp("negl", left, right, result)

        fun negq(left: PlatformTarget, right: PlatformTarget, result: PlatformTarget) =
            binOp("negq", left, right, result)

        fun notl(left: PlatformTarget, right: PlatformTarget, result: PlatformTarget) =
            binOp("notl", left, right, result)

        fun notq(left: PlatformTarget, right: PlatformTarget, result: PlatformTarget) =
            binOp("notq", left, right, result)

        fun andl(left: PlatformTarget, right: PlatformTarget, result: PlatformTarget) =
            binOp("andl", left, right, result)

        fun andq(left: PlatformTarget, right: PlatformTarget, result: PlatformTarget) =
            binOp("andq", left, right, result)

        fun orl(left: PlatformTarget, right: PlatformTarget, result: PlatformTarget) =
            binOp("orl", left, right, result)

        fun orq(left: PlatformTarget, right: PlatformTarget, result: PlatformTarget) =
            binOp("orq", left, right, result)

        fun xorl(left: PlatformTarget, right: PlatformTarget, result: PlatformTarget) =
            binOp("xorl", left, right, result)

        fun xorq(left: PlatformTarget, right: PlatformTarget, result: PlatformTarget) =
            binOp("xorq", left, right, result)

        // bitwise shift (arithmetic = preserve sign)
        fun sall(left: PlatformTarget, right: PlatformTarget, result: PlatformTarget) =
            binOp("sall", left, right, result)

        fun salq(left: PlatformTarget, right: PlatformTarget, result: PlatformTarget) =
            binOp("salq", left, right, result)

        fun sarl(left: PlatformTarget, right: PlatformTarget, result: PlatformTarget) =
            binOp("sarl", left, right, result)

        fun sarq(left: PlatformTarget, right: PlatformTarget, result: PlatformTarget) =
            binOp("sarq", left, right, result)

        // bitwise shift (logical)
        fun shll(left: PlatformTarget, right: PlatformTarget, result: PlatformTarget) =
            binOp("shll", left, right, result)

        fun shlq(left: PlatformTarget, right: PlatformTarget, result: PlatformTarget) =
            binOp("shlq", left, right, result)

        fun shrl(left: PlatformTarget, right: PlatformTarget, result: PlatformTarget) =
            binOp("shrl", left, right, result)

        fun shrq(left: PlatformTarget, right: PlatformTarget, result: PlatformTarget) =
            binOp("shrq", left, right, result)

        /* ktlint-enable no-multi-spaces */
    }

    class BinaryOperationWithResult(
        val name: String,
        val left: PlatformTarget,
        val right: PlatformTarget,
        val result: PlatformTarget
    ) : Instruction() {
        override fun toAssembler(): String =
            "$name [ ${left.toAssembler()} | ${right.toAssembler()} ] -> ${result.toAssembler()}"
    }

    class BinaryOperationWithTwoPartResult(
        val name: String,
        val left: PlatformTarget,
        val right: PlatformTarget,
        val resultLeft: PlatformTarget,
        val resultRight: PlatformTarget
    ) : Instruction() {
        override fun toAssembler(): String =
            "$name [ ${left.toAssembler()} | ${right.toAssembler()} ] -> [ ${resultLeft.toAssembler()} | ${resultRight.toAssembler()} ]"
    }
}
