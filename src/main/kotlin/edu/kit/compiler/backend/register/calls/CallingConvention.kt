package edu.kit.compiler.backend.register.calls

import edu.kit.compiler.backend.molkir.Width
import edu.kit.compiler.backend.register.PlatformInstruction
import edu.kit.compiler.backend.register.PlatformTarget
import edu.kit.compiler.backend.register.RegisterAllocator

/**
 * Implement the necessary behavior for different calling conventions (like mapping arguments to [PlatformTarget]s,
 * saving and restoring registers, etc.)
 */
interface CallingConvention {

    /**
     * Generate the function prologue for a function called by this convention+
     *
     * @param reservedSpace how much space the function should reserve in the stack frame statically
     */
    fun generateFunctionPrologue(reservedSpace: Int): List<PlatformInstruction>

    /**
     * Generate the function epilogue for a function called by this convention
     *
     * @param returnValue location of the return value, if any. `null` otherwise
     * @param returnWidth width of the return value. `null` iff [returnValue] is `null` too.
     */
    fun generateFunctionEpilogue(returnValue: PlatformTarget?, returnWidth: Width?): List<PlatformInstruction>

    /**
     * Get the [PlatformTarget] where the function's return value will be located at
     *
     * @param width bit-width of return value
     */
    fun getReturnValueTarget(width: Width): PlatformTarget.Register

    /**
     * Get the [PlatformTarget] where a parameter is located
     *
     * @param virtualRegisterId virtual register id of the parameter (i.e. 0 for the first parameter)
     */
    fun getParameterLocation(virtualRegisterId: Int): PlatformTarget

    /**
     * Mark a register as used by the function, so it can be saved if required by the calling convention
     *
     * @param register that is being used by the function
     */
    fun taintRegister(register: PlatformTarget.Register)

    /**
     * Prepare a function call by creating a [FunctionCallBuilder] and adding all parameters to the call, preparing
     * the stack, generating the call, and generating the call epilgoue.
     *
     * @param allocator the [RegisterAllocator] utilized by the [edu.kit.compiler.backend.register.FunctionTransformer]
     * @param arguments how many argument the called function takes
     * @param instructionAppender a callback that allows appending instructions to the generated code
     * @param init client function that is called to generate the function call prologue. The caller can provide the
     * function call arguments here.
     */
    fun generateFunctionCall(
        allocator: RegisterAllocator,
        arguments: Int,
        instructionAppender: (PlatformInstruction) -> Unit,
        init: FunctionCallBuilder.() -> Unit
    )

    /**
     * A receiver class for function call DSL that is used for stateful construction of function calls (specifically
     * argument organization). Arguments are passed to [prepareArgument] in reversed order. Afterwards, a call to
     * [generateCall] will append the call instruction. Lastly, [cleanupStack] restores the stack frame to the correct
     * state after the generated function call.
     *
     * @param allocator the register allocator used during transformation
     * @param arguments the number of arguments the call takes
     * @param instructionAppenderCallback a callback that allows appending instructions to the generated code
     */
    abstract class FunctionCallBuilder protected constructor(
        val allocator: RegisterAllocator,
        val arguments: Int,
        val instructionAppenderCallback: (PlatformInstruction) -> Unit
    ) {

        /**
         * Prepare an argument according to the respective calling convention. The arguments must be passed to this
         * function in reverse order
         *
         * @param source where to find the argument value
         * @param width the argument bit width
         */
        abstract fun prepareArgument(
            source: PlatformTarget,
            width: Width
        )

        /**
         * Generate the actual function call
         *
         * @param name function name
         */
        abstract fun generateCall(name: String)

        /**
         * Cleanup stack frame after the call
         */
        abstract fun cleanupStack()
    }
}
