package edu.kit.compiler

import kotlin.system.exitProcess

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
        formatAndPrint(warnColor, message, "Warning")
    }

    /**
     * Log messages and exist if specified.
     */
    public fun error(message: String, exit:Boolean) {
        formatAndPrint(errorColor, message, "Error")
        if (exit) exitProcess(1)
    }

    public fun info(message: String) {
        formatAndPrint(resetColor, message, "Info")
    }

    private fun formatAndPrint(color: String, message: String, messagePrefix: String) {
        println("[$phase] $color$messagePrefix: $message$resetColor")
    }

    /**
     * Wrap printing to console.
     */
    public fun println(line: String) {
        kotlin.io.println(line)
    }

}