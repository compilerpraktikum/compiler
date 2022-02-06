package edu.kit.compiler.backend

/**
 * Strategy pattern for code generation.
 */
interface Backend {
    /**
     * Generate executable.
     */
    fun generate()
}
