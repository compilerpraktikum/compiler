@file:Suppress("MemberVisibilityCanBePrivate")

package edu.kit.compiler.utils

object Logger {
    enum class Level(val prefix: String, val severity: Int) {
        OFF("", 0),
        FATAL("FATAL", 10),
        ERROR("ERROR", 20),
        WARNING("WARNING", 30),
        INFO("INFO", 40),
        DEBUG("DEBUG", 50),
        TRACE("TRACE", 60),
        ALL("", Int.MAX_VALUE)
    }

    var level: Level = Level.INFO

    private fun isEnabledFor(level: Level): Boolean {
        return level.severity <= this.level.severity
    }

    fun log(level: Level, message: () -> String) {
        if (isEnabledFor(level)) {
            println("[${level.prefix}] ${message()}")
        }
    }

    fun fatal(message: () -> String) = log(Level.FATAL, message)
    fun error(message: () -> String) = log(Level.ERROR, message)
    fun warning(message: () -> String) = log(Level.WARNING, message)
    fun info(message: () -> String) = log(Level.INFO, message)
    fun debug(message: () -> String) = log(Level.DEBUG, message)
    fun trace(message: () -> String) = log(Level.TRACE, message)
}
