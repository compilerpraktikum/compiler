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
    sealed class Register(private val registerName: String) : PlatformTarget() {

        private var width = Width.WIDTH_64

        class RAX : Register("rax")
        class RBX : Register("rbx")
        class RCX : Register("rcx")
        class RDX : Register("rdx")
        class RSI : Register("rsi")
        class RDI : Register("rdi")
        class RSP : Register("rsp")
        class RBP : Register("rbp")
        class R8 : Register("r8")
        class R9 : Register("r9")
        class R10 : Register("r10")
        class R11 : Register("r11")
        class R12 : Register("r12")
        class R13 : Register("r13")
        class R14 : Register("r14")
        class R15 : Register("r15")

        fun halfWordWidth(): Register {
            throw NotImplementedError("8 bit registers have not been implemented")
        }

        fun wordWidth(): Register {
            throw NotImplementedError("16 bit registers have not been implemented")
        }

        /**
         * Use a 32 bit register
         */
        fun doubleWidth(): Register {
            this.width = Width.WIDTH_32
            return this
        }

        /**
         * Use a 64 bit register
         */
        fun quadWidth(): Register {
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

    class Call(val name: String, val arguments: List<PlatformTarget>, val result: PlatformTarget?) :
        PlatformInstruction() {
        override fun toAssembler(): String {
            val args = arguments.joinToString(" | ") { it.toAssembler() }
            val resultStr = result?.let { " -> ${it.toAssembler()}" } ?: ""
            return "call $name [ $arguments ]$resultStr"
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

        fun subq(from: PlatformTarget, value: PlatformTarget) =
            BinaryOperation("subq", from, value)

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
