package edu.kit.compiler.backend.register

import edu.kit.compiler.backend.molkir.Width

/**
 * @param registerBaseName base name of the register in its default width (16 bit for x86 registers,
 * 64 bit for x86_64 extension)
 * @param extension whether the register is part of the 64 bit extended register set
 */
enum class EnumRegister(private val registerBaseName: String, private val extension: Boolean = false) {
    RAX("ax"),
    RBX("bx"),
    RCX("cx"),
    RDX("dx"),
    RSI("si"),
    RDI("di"),
    RSP("sp"),
    RBP("bp"),
    R8("r8", true),
    R9("r9", true),
    R10("r10", true),
    R11("r11", true),
    R12("r12", true),
    R13("r13", true),
    R14("r14", true),
    R15("r15", true);

    /**
     * Get the full name of the register given the [width]. Does not include assembler-specific prefixes like '%'.
     */
    fun getFullRegisterName(width: Width): String {
        val prefix =
            if (!extension) {
                when (width) {
                    Width.BYTE, Width.WORD -> ""
                    Width.DOUBLE -> "e"
                    Width.QUAD -> "r"
                }
            } else {
                ""
            }

        val suffix =
            if (extension) {
                when (width) {
                    Width.BYTE -> "b"
                    Width.WORD -> "w"
                    Width.DOUBLE -> "d"
                    Width.QUAD -> ""
                }
            } else {
                if (width == Width.BYTE) "l"
                else ""
            }

        return prefix + registerBaseName + suffix
    }
}
