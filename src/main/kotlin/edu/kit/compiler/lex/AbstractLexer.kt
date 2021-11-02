package edu.kit.compiler.lex

import edu.kit.compiler.Token
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.flow

/**
 * Generic base class for lexers.
 */
abstract class AbstractLexer(
    fileName: String,
    private val input: InputProvider,
    protected val stringTable: StringTable,
) {
    var position = SourcePosition(fileName, 1, 0)
        private set

    /**
     * Get the next character and update the internal position.
     *
     * @return next character from the input
     */
    protected suspend fun next(): Char {
        val c = input.nextChar()

        position = when (c) {
            '\n' -> position.nextLine()
            '\r' -> position // invisible characters
            else -> position.nextColumn()
        }

        return c
    }

    /**
     * Lookahead in the input by a given [offset].
     *
     * @param offset amount of bytes to peek after the current position. By default, peeks ahead 0 characters and thus
     * returns the character that would be returned by the next [next]-call.
     *
     * @return the character [offset] bytes behind the current position in the input.
     */
    protected suspend fun peek(offset: Int = 0): Char = input.peek(offset)

    /**
     * Lexes the given input.
     * @return [Flow] of [Tokens][Token]
     */
    fun tokens(): Flow<Token> = flow {
        var c = peek()

        while (c != BufferedInputProvider.END_OF_FILE) {
            val token = scanToken()
            token.position = position
            emit(token)
            c = peek()
        }

        emit(Token.Eof)
    }.buffer()

    /**
     * Lex the next token.
     *
     * @return the next [token][Token]
     */
    protected abstract suspend fun scanToken(): Token
}
