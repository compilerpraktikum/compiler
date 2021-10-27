package edu.kit.compiler.error

import edu.kit.compiler.ConsoleOutputManager
import java.io.OutputStreamWriter
import java.io.PrintStream

/**
 * A more specific implementation of the std `Result` type that supports nice error reporting and does not work with
 * actual `Throwable`s, because we don't need to process expected exceptions
 */
sealed class CompilerResult<R> {
    class Success<R>(val result: R) : CompilerResult<R>()

    class Failure<R>(private val message: String, private val compilerPhase: String = "COMPILER") : CompilerResult<R>() {
        /**
         * Report the error of a failed result to an [OutputStreamWriter].
         */
        fun reportError(outputStream: PrintStream) {
            ConsoleOutputManager(compilerPhase, outputStream, outputStream).error(this.message)
        }
    }

    companion object {
        /**
         * Constructs a valid result with the specified value
         */
        fun <R> success(result: R): CompilerResult<R> {
            return Success<R>(result)
        }

        /**
         * Constructs a result indicating an erroneous state and providing a strategy to report the error to the user.
         */
        fun <R> failure(message: String, compilerPhase: String = "COMPILER"): CompilerResult<R> {
            return Failure<R>(message, compilerPhase)
        }
    }

    inline fun unwrap(onError: Failure<R>.() -> Nothing): R =
        when(this) {
            is Success -> this.result
            is Failure -> onError.invoke(this)
        }
}