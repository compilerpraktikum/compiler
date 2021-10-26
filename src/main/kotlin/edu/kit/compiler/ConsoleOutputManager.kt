package edu.kit.compiler

import java.io.PrintStream

/**
 * Handle user-directed output.
 */
class ConsoleOutputManager(
    private val phase: String
) {

    companion object {
        private val errorColor: String = "\u001b[31m"
        private val warnColor: String = "\u001b[33m"
        private val resetColor = "\u001b[0m"
    }

    public fun warn(message: String) {
        formatAndPrint(System.out, warnColor, message, "Warning")
    }

    /**
     * Log messages and exist if specified.
     */
    public fun error(message: String) {
        formatAndPrint(System.err, errorColor, message, "Error")
    }

    public fun info(message: String) {
        formatAndPrint(System.out, resetColor, message, "Info")
    }

    private fun formatAndPrint(printStream: PrintStream, color: String, message: String, messagePrefix: String) {
        printStream.println("[$phase] $color$messagePrefix: $message$resetColor")
    }
}