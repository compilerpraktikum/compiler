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
    val scale: String?,
    override val width: Width,
) : Target.InputOutput {
    companion object {
        fun of(
            const: String,
            base: Register,
            index: Register? = null,
            scale: String? = null,
            width: Width
        ) = Memory(const, base, index, scale, width)

        fun of(
            base: Register,
            index: Register? = null,
            scale: String? = null,
            width: Width
        ) = Memory(null, base, index, scale, width)

        fun of(
            const: String,
            index: Register? = null,
            scale: String? = null,
            width: Width
        ) = Memory(const, null, index, scale, width)
    }

    init {
        if (scale != null) {
            check(scale in listOf("1", "2", "4", "8")) { "scale must be 1, 2, 4 or 8" }
            check(index != null) { "cannot provide scale without index" }
        }
    }

    fun width(newWidth: Width) = Memory(const, base, index, scale, newWidth)

    override fun toMolki(): String {
        val constStr = const ?: ""
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

private fun checkWidth(operand: Target, vararg others: Target) {
    others.forEachIndexed { i, other ->
        check(operand.width == other.width) { "incompatible types ($i): ${operand.width} <-> ${other.width}" }
    }
}

sealed class Instruction : MolkIR {
    open val hasIndent: Boolean = true

    class Label(val name: String) : Instruction() {
        override val hasIndent: Boolean = false

        override fun toMolki(): String = "$name:"
    }

    class Comment(val content: String) : Instruction() {
        override fun toMolki(): String = "/* $content */"
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
        val type: Type,
        val operand: Target.Input,
        val result: Target.Output,
        private val overwriteName: String? = null,
    ) : Instruction() {
        init {
            if (overwriteName == null) {
                checkWidth(operand, result)
            }
        }

        val name
            get() = overwriteName ?: (type.instruction + operand.width.instructionSuffix)

        override fun toMolki(): String = "$name ${operand.toMolki()}, ${result.toMolki()}"

        enum class Type(val instruction: String) {
            MOV("mov"),
            INC("inc"),
            DEC("dec"),
            NEG("neg"),
            NOT("not");
        }
    }

    class BinaryOperation(
        val type: Type,
        val left: Target.Input,
        val right: Target.Input,
    ) : Instruction() {
        init {
            checkWidth(left, right)
        }

        val name
            get() = type.instruction + left.width.instructionSuffix

        override fun toMolki(): String = "$name ${left.toMolki()}, ${right.toMolki()}"

        enum class Type(val instruction: String) {
            CMP("cmp");
        }
    }

    class BinaryOperationWithResult(
        val type: Type,
        val left: Target.Input,
        val right: Target.Input,
        val result: Target.Output,
    ) : Instruction() {
        init {
            checkWidth(left, right, result)
        }

        val name
            get() = type.instruction + left.width.instructionSuffix

        override fun toMolki(): String = "$name [ ${left.toMolki()} | ${right.toMolki()} ] -> ${result.toMolki()}"

        enum class Type(val instruction: String) {
            ADD("add"),
            SUB("sub"),
            IMUL("imul"),
            AND("and"),
            OR("or"),
            XOR("xor"),
            SAR("sar"),
            SHL("shl"),
            SHR("shr");
        }
    }

    class DivisionOperation(
        val left: Target.Input,
        val right: Target.Input,
        val resultLeft: Target.Output,
        val resultRight: Target.Output,
    ) : Instruction() {
        init {
            checkWidth(left, right, resultLeft, resultRight)
        }

        val name
            get() = "idiv" + left.width.instructionSuffix

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

        fun cmp(left: Target.Input, right: Target.Input) = BinaryOperation(BinaryOperation.Type.CMP, right, left) // x86 / molki syntax for cmp is: `cmp right, left`

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
        fun mov(from: Target.Input, to: Target.Output): UnaryOperationWithResult = UnaryOperationWithResult(UnaryOperationWithResult.Type.MOV, from, to)
        fun movs(from: Target.Input, to: Target.Output): UnaryOperationWithResult =
            UnaryOperationWithResult(UnaryOperationWithResult.Type.MOV, from, to, "movs" + from.width.instructionSuffix + to.width.instructionSuffix)
        fun inc(operand: Target.Input, result: Target.Output) = UnaryOperationWithResult(UnaryOperationWithResult.Type.INC, operand, result)
        fun dec(operand: Target.Input, result: Target.Output) = UnaryOperationWithResult(UnaryOperationWithResult.Type.DEC, operand, result)
        fun neg(operand: Target.Input, result: Target.Output) = UnaryOperationWithResult(UnaryOperationWithResult.Type.NEG, operand, result)
        fun not(operand: Target.Input, result: Target.Output) = UnaryOperationWithResult(UnaryOperationWithResult.Type.NOT, operand, result)

        /****************************************
         * Binary operations with result
         ****************************************/
        fun idiv(left: Target.Input, right: Target.Input, resultDiv: Target.Output, resultMod: Target.Output) =
            DivisionOperation(left, right, resultDiv, resultMod)

        // basic arithmetic
        fun add(left: Target.Input, right: Target.Input, result: Target.Output) = BinaryOperationWithResult(BinaryOperationWithResult.Type.ADD, left, right, result)
        fun sub(left: Target.Input, right: Target.Input, result: Target.Output) = BinaryOperationWithResult(BinaryOperationWithResult.Type.SUB, left, right, result)
        fun imul(left: Target.Input, right: Target.Input, result: Target.Output) = BinaryOperationWithResult(BinaryOperationWithResult.Type.IMUL, left, right, result)

        // basic logic
        fun and(left: Target.Input, right: Target.Input, result: Target.Output) = BinaryOperationWithResult(BinaryOperationWithResult.Type.AND, left, right, result)
        fun or(left: Target.Input, right: Target.Input, result: Target.Output) = BinaryOperationWithResult(BinaryOperationWithResult.Type.OR, left, right, result)
        fun xor(left: Target.Input, right: Target.Input, result: Target.Output) = BinaryOperationWithResult(BinaryOperationWithResult.Type.XOR, left, right, result)

        // bitwise shift (arithmetic = preserve sign)
        fun sar(left: Target.Input, right: Target.Input, result: Target.Output)  = BinaryOperationWithResult(BinaryOperationWithResult.Type.SAR, left, right, result)

        // bitwise shift (logical)
        fun shl(left: Target.Input, right: Target.Input, result: Target.Output)  = BinaryOperationWithResult(BinaryOperationWithResult.Type.SHL, left, right, result)
        fun shr(left: Target.Input, right: Target.Input, result: Target.Output)  = BinaryOperationWithResult(BinaryOperationWithResult.Type.SHR, left, right, result)

        /* @formatter:on */
        /* ktlint-enable no-multi-spaces */
    }
}
