package edu.kit.compiler.backend.register

import edu.kit.compiler.backend.molkir.Width

// NOTE: This file is a copy-paste result of MolkIR.kt, and thus not complete

/**
 * Intermediate representation after register allocation.
 */
interface PlatformIR {
    fun toAssembler(): String
}

/**
 * Platform registers that can be allocated to IR instructions
 */
sealed class PlatformTarget : PlatformIR {
    /**
     * An x86-64 general purpose register
     *
     * @param register the [EnumRegister] that is referenced by this handle
     */
    sealed class Register(val register: EnumRegister) : PlatformTarget() {

        private var width = Width.QUAD

        class RAX : Register(EnumRegister.RAX)
        class RBX : Register(EnumRegister.RBX)
        class RCX : Register(EnumRegister.RCX)
        class RDX : Register(EnumRegister.RDX)
        class RSI : Register(EnumRegister.RSI)
        class RDI : Register(EnumRegister.RDI)
        class RSP : Register(EnumRegister.RSP)
        class RBP : Register(EnumRegister.RBP)
        class R8 : Register(EnumRegister.R8)
        class R9 : Register(EnumRegister.R9)
        class R10 : Register(EnumRegister.R10)
        class R11 : Register(EnumRegister.R11)
        class R12 : Register(EnumRegister.R12)
        class R13 : Register(EnumRegister.R13)
        class R14 : Register(EnumRegister.R14)
        class R15 : Register(EnumRegister.R15)

        fun halfWordWidth(): Register {
            this.width = Width.BYTE
            return this
        }

        fun wordWidth(): Register {
            this.width = Width.WORD
            return this
        }

        /**
         * Use a 32 bit register
         */
        fun doubleWidth(): Register {
            this.width = Width.DOUBLE
            return this
        }

        /**
         * Use a 64 bit register
         */
        fun quadWidth(): Register {
            this.width = Width.QUAD
            return this
        }

        override fun toAssembler(): String {
            return "%${register.getFullRegisterName(this.width)}"
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

    class Memory private constructor(
        val const: String?,
        val base: Register?,
        val index: Register?,
        val scale: Int?,
    ) : PlatformTarget() {
        companion object {
            fun of(
                const: String,
                base: Register,
                index: Register? = null,
                scale: Int? = null
            ) = Memory(const, base, index, scale)

            fun of(
                base: Register,
                index: Register? = null,
                scale: Int? = null
            ) = Memory(null, base, index, scale)

            fun of(
                const: String,
                index: Register? = null,
                scale: Int? = null
            ) = Memory(const, null, index, scale)
        }

        init {
            if (scale != null) {
                check(scale in listOf(1, 2, 4, 8)) { "scale must be 1, 2, 4 or 8" }
                check(index != null) { "cannot provide scale without index" }
            }
        }

        override fun toAssembler(): String {
            val constStr = const ?: ""
            val baseStr = base?.toAssembler() ?: ""
            val extStr = if (index != null) {
                val indexStr = index.toAssembler()
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
}

sealed class PlatformInstruction : PlatformIR {
    class Label(val name: String) : PlatformInstruction() {
        override fun toAssembler(): String = "$name:"
    }

    class Call(val name: String) : PlatformInstruction() {
        override fun toAssembler(): String {
            return "call $name"
        }
    }

    class Jump(val name: String, val label: String) : PlatformInstruction() {
        override fun toAssembler(): String = "$name $label"
    }

    /**
     * Operation without any operands
     */
    class Operation(val name: String) : PlatformInstruction() {
        override fun toAssembler(): String {
            return name
        }
    }

    /**
     * Operation with one operand
     */
    class UnaryOperation(val name: String, val operand: PlatformTarget) : PlatformInstruction() {
        override fun toAssembler(): String = "$name ${operand.toAssembler()}"
    }

    /**
     * Operation with two operands, the second usually being the result target (if any).
     */
    class BinaryOperation(val name: String, val left: PlatformTarget, val right: PlatformTarget) :
        PlatformInstruction() {
        override fun toAssembler(): String = "$name ${left.toAssembler()}, ${right.toAssembler()}"
    }

    companion object {
        fun movb(from: PlatformTarget, to: PlatformTarget) =
            BinaryOperation("movb", from, to)

        fun movl(from: PlatformTarget, to: PlatformTarget) =
            BinaryOperation("movl", from, to)

        fun movq(from: PlatformTarget, to: PlatformTarget) =
            BinaryOperation("movq", from, to)

        fun addq(from: PlatformTarget, value: PlatformTarget) =
            BinaryOperation("addq", from, value)

        fun subq(from: PlatformTarget, value: PlatformTarget) =
            BinaryOperation("subq", from, value)

        fun pushb(operand: PlatformTarget) =
            UnaryOperation("pushb", operand)

        fun pushl(operand: PlatformTarget) =
            UnaryOperation("pushl", operand)

        fun pushq(operand: PlatformTarget) =
            UnaryOperation("pushq", operand)

        /**
         * Generate an operand-less operation
         */
        fun op(name: String) =
            Operation(name)

        /**
         * Generate a unary operation
         */
        fun unOp(name: String, operand: PlatformTarget) =
            UnaryOperation(name, operand)

        /**
         * Generate a binary operation
         */
        fun binOp(
            name: String,
            left: PlatformTarget,
            right: PlatformTarget
        ) = BinaryOperation(name, left, right)
    }

    class BinaryOperationWithTwoPartResult(
        val name: String,
        val left: PlatformTarget,
        val right: PlatformTarget,
        val resultLeft: PlatformTarget,
        val resultRight: PlatformTarget
    ) : PlatformInstruction() {
        override fun toAssembler(): String =
            "$name [ ${left.toAssembler()} | ${right.toAssembler()} ] -> [ ${resultLeft.toAssembler()} | ${resultRight.toAssembler()} ]"
    }
}
