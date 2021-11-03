package edu.kit.compiler.lex

/**
 * Provides character input for the lexer asynchronously. The input source is implementation-dependant.
 */
interface InputProvider {

    companion object {
        const val END_OF_FILE: Char = 0x20E0.toChar()
    }

    /**
     * Provide the next input character. [InputProvider.END_OF_FILE] is return if the end of input has been reached.
     */
    suspend fun next(): Char

    /**
     * Lookahead in the buffer by a given [offset]. By default, peeks ahead 0 characters and thus returns the
     * character that would be returned by the next [next()][next] call.
     * Returns [InputProvider.END_OF_FILE], if the end of input has been reached.
     */
    suspend fun peek(offset: Int = 0): Char
}
