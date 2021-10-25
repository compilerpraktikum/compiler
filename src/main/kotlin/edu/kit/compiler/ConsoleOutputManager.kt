package edu.kit.compiler

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.system.exitProcess

/**
 * Handle errors, warnings etc..
 *
 */
class ConsoleOutputManager {
    private val logger: Logger

    constructor(clazz: Class<*>) {
        this.logger = LoggerFactory.getLogger(clazz)
    }

    constructor(name: String) {
        this.logger = LoggerFactory.getLogger(name)
    }

    public fun warn(message: String) {
        logger.warn(message)
    }

    /**
     * Log messages and exists.
     */
    public fun error(message: String) {
        logger.error(message)
        exitProcess(1)
    }

    public fun error(message: String, cause: Throwable) {
        logger.error(message, cause)
        exitProcess(1)
    }

    public fun info(message: String) {
        logger.info(message)
    }

    public fun debug(message: String) {
        logger.debug(message)
    }

    /**
     * Wrap printing to console.
     */
    public fun println(line: String) {
        kotlin.io.println(line)
    }

}