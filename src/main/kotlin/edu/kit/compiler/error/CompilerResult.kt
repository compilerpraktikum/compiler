package edu.kit.compiler.error

import java.io.OutputStreamWriter
import java.io.PrintStream

/**
 * A more specific implementation of the std `Result` type that supports nice error reporting and does not work with
 * actual `Throwable`s, because we don't need to process expected exceptions
 */
class CompilerResult<R> private constructor(
    private val internalValue: Any?,
    private val errorMessage: String? = null,
    private val errorReporter: ((PrintStream) -> Unit)? = null
) {

    companion object {
        /**
         * Denotes that the result is in a Fail-State
         */
        private object FAILURE

        /**
         * Constructs a valid result with the specified value
         */
        fun <R> success(result: R): CompilerResult<R> {
            return CompilerResult(internalValue = result)
        }

        /**
         * Constructs a result indicating an erroneous state and providing a strategy to report the error to the user.
         */
        fun <R> failure(message: String, report: ((PrintStream) -> Unit)? = null): CompilerResult<R> {
            return CompilerResult(internalValue = FAILURE, errorMessage = message, errorReporter = report)
        }
    }

    /**
     * Whether the result indicates an erroneous state
     */
    val isFailure
        get() = this.internalValue == FAILURE

    /**
     * Whether the result indicates a successful operation and a value is present
     */
    val isSuccess
        get() = this.internalValue != FAILURE

    fun get(): R {
        if (this.isFailure)
            error("cannot get a failed result's value")

        // since we cannot store the result as a value of `R`, as we need to indicate a FAIL-state, we need to cast
        // it according to the class contract. Let's hope the caller isn't doing illegal casts itself
        @Suppress("UNCHECKED_CAST")
        return this.internalValue as R
    }

    /**
     * Report the error of a failed result to an [OutputStreamWriter].
     */
    fun reportError(outputStream: PrintStream) {
        if (this.isSuccess)
            error("cannot report an error of a successful result")

        outputStream.print(this.errorMessage)
        if (this.errorReporter != null) {
            outputStream.println(":")
            this.errorReporter.invoke(outputStream)
        }

        outputStream.println()
    }

    inline fun unwrap(onError: CompilerResult<R>.() -> Nothing): R {
        return if (this.isFailure) {
            onError.invoke(this)
        } else {
            this.get()
        }
    }
}