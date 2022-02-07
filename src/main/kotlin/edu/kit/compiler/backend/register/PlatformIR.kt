package edu.kit.compiler.backend.register

import edu.kit.compiler.backend.molkir.Width

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
    data class Register(val register: EnumRegister, val width: Width = Width.QUAD) : PlatformTarget() {
        fun width(width: Width): Register {
            return copy(width = width)
        }

        fun halfWordWidth() = width(Width.BYTE)
        fun wordWidth() = width(Width.WORD)
        fun doubleWidth() = width(Width.DOUBLE)
        fun quadWidth() = width(Width.QUAD)

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
        val scale: String?,
    ) : PlatformTarget() {
        companion object {
            fun of(
                const: String,
                base: Register,
                index: Register? = null,
                scale: String? = null
            ) = Memory(const, base, index, scale)

            fun of(
                base: Register,
                index: Register? = null,
                scale: String? = null
            ) = Memory(null, base, index, scale)

            fun of(
                const: String,
                index: Register? = null,
                scale: String? = null
            ) = Memory(const, null, index, scale)
        }

        init {
            if (scale != null) {
                check(scale in listOf("1", "2", "4", "8")) { "scale must be 1, 2, 4 or 8" }
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
        override fun toAssembler(): String = "L$name:"
    }

    class Call(val name: String) : PlatformInstruction() {
        override fun toAssembler(): String {
            return "call $name"
        }
    }

    class Jump(val name: String, private val label: String) : PlatformInstruction() {
        override fun toAssembler(): String = "$name L$label"
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
        fun mov(from: PlatformTarget, to: PlatformTarget, width: Width) =
            binOp("mov${width.instructionSuffix}", from, to)

        fun movzx(
            from: PlatformTarget,
            to: PlatformTarget,
            sourceWidth: Width,
            targetWidth: Width
        ): PlatformInstruction {
            require(sourceWidth.inBytes < 4) { "cannot zero-extend double-word or quad-word operands" }
            return binOp("movz${sourceWidth.instructionSuffix}${targetWidth.instructionSuffix}", from, to)
        }

        fun add(from: PlatformTarget, value: PlatformTarget, width: Width) =
            binOp("add${width.instructionSuffix}", from, value)

        fun sub(from: PlatformTarget, value: PlatformTarget, width: Width) =
            binOp("sub${width.instructionSuffix}", from, value)

        fun xor(left: PlatformTarget, right: PlatformTarget, width: Width) =
            binOp("xor${width.instructionSuffix}", left, right)

        fun push(operand: PlatformTarget) =
            unOp("push", operand)

        fun pop(operand: PlatformTarget, width: Width) =
            unOp("pop${width.instructionSuffix}", operand)

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

        /**
         * Generate a comment
         */
        fun comment(text: String) = op("/* $text */")
    }
}
