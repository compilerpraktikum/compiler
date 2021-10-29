package edu.kit.compiler.lex

/**
 * Provides character input for the lexer asynchronously. The input source is implementation-dependant.
 */
interface InputProvider {
    
    /**
     * Lookahead in the buffer by a given [offset]. By default, peeks ahead 0 characters and thus returns the
     * character that would be returned by the next [nextChar] call.
     * Returns `null`, if the end of input has been reached.
     */
    suspend fun peek(offset: Int = 0): Char?
    
    /**
     * Provide the next input character or `null`, if the end of input has been reached.
     */
    suspend fun nextChar(): Char?
}