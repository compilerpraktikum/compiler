package edu.kit.compiler.backend.register

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
     */
    sealed class GeneralPurposeRegister(private val registerName: String) : PlatformTarget() {

        private var width = Width.WIDTH_64

        class RAX : GeneralPurposeRegister("rax")
        class RBX : GeneralPurposeRegister("rbx")
        class RCX : GeneralPurposeRegister("rcx")
        class RDX : GeneralPurposeRegister("rdx")
        class RSI : GeneralPurposeRegister("rsi")
        class RDI : GeneralPurposeRegister("rdi")
        class RSP : GeneralPurposeRegister("rsp")
        class RBP : GeneralPurposeRegister("rbp")
        class R8 : GeneralPurposeRegister("r8")
        class R9 : GeneralPurposeRegister("r9")
        class R10 : GeneralPurposeRegister("r10")
        class R11 : GeneralPurposeRegister("r11")
        class R12 : GeneralPurposeRegister("r12")
        class R13 : GeneralPurposeRegister("r13")
        class R14 : GeneralPurposeRegister("r14")
        class R15 : GeneralPurposeRegister("r15")

        fun halfWordWidth(): PlatformTarget.GeneralPurposeRegister {
            throw NotImplementedError("8 bit registers have not been implemented")
        }

        fun wordWidth(): PlatformTarget.GeneralPurposeRegister {
            throw NotImplementedError("16 bit registers have not been implemented")
        }

        /**
         * Use a 32 bit register
         */
        fun doubleWidth(): PlatformTarget.GeneralPurposeRegister {
            this.width = Width.WIDTH_32
            return this
        }

        /**
         * Use a 64 bit register
         */
        fun quadWidth(): PlatformTarget.GeneralPurposeRegister {
            this.width = Width.WIDTH_64
            return this
        }

        override fun toAssembler(): String {
            return when (width) {
                Width.WIDTH_64 -> "%$registerName"
                Width.WIDTH_32 -> {
                    require(!this.registerName.last().isDigit()) { "there are no 32bit equivalents to %$registerName" }
                    "%${registerName.replace('r', 'e')}"
                }
            }
        }

        /**
         * Register bit width
         */
        private enum class Width {
            WIDTH_32, WIDTH_64
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
        val const: Int?,
        val base: PlatformTarget?,
        val index: PlatformTarget?,
        val scale: Int?,
    ) : PlatformTarget() {
        init {
            if (scale != null) {
                check(scale in listOf(1, 2, 4, 8)) { "scale must be 1, 2, 4 or 8" }
                check(index != null) { "cannot provide scale without index" }
            }
        }

        constructor(
            const: Int,
            base: PlatformTarget,
            index: PlatformTarget? = null,
            scale: Int? = null
        ) : this(const as Int?, base, index, scale)

        constructor(
            base: PlatformTarget,
            index: PlatformTarget? = null,
            scale: Int? = null
        ) : this(
            null,
            base,
            index,
            scale
        )

        constructor(const: Int, index: PlatformTarget? = null, scale: Int? = null) : this(
            const,
            null,
            index,
            scale
        )

        override fun toAssembler(): String {
            val constStr = const?.toString() ?: ""
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

sealed class Instruction : PlatformIR {
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
        fun movb(from: PlatformTarget, to: PlatformTarget) =
            BinaryOperation("movb", from, to)

        fun movl(from: PlatformTarget, to: PlatformTarget) =
            BinaryOperation("movl", from, to)

        fun movq(from: PlatformTarget, to: PlatformTarget) =
            BinaryOperation("movq", from, to)

        fun subq(from: PlatformTarget, value: PlatformTarget) =
            BinaryOperation("subq", from, value)

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
            right: PlatformTarget
        ) = BinaryOperation(name, left, right)
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
