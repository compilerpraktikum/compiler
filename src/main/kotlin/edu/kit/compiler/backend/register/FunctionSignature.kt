package edu.kit.compiler.backend.register

import edu.kit.compiler.backend.molkir.Width

/**
 * A function signature is a wrapper around an array of parameter [widths][Width].
 */
class FunctionSignature(vararg val parameters: Width) {

    /**
     * The number of parameters this function has
     */
    val parameterCount = parameters.size

    /**
     * Generate an offset from RBP to the n'th parameter
     *
     * @param parameter the index of the parameter that is being referenced
     */
    fun generateStackOffset(parameter: Int): PlatformTarget.Memory {
        var offset = 2 * Width.QUAD.inBytes // pushed RBP and return address

        for (i in (0 until parameter)) {
            offset += parameters[i].inBytes
        }

        return PlatformTarget.Memory.of(const = offset.toString(), base = PlatformTarget.Register(EnumRegister.RBP))
    }
}
