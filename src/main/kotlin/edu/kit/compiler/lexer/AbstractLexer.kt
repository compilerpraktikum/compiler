package edu.kit.compiler.lexer

import edu.kit.compiler.source.AnnotationType
import edu.kit.compiler.source.InputProvider
import edu.kit.compiler.source.SourceFile

/**
 * Generic base class for lexers.
 */
abstract class AbstractLexer(
    protected val sourceFile: SourceFile,
    protected val stringTable: StringTable,
) {
    /**
     * Get the next character.
     *
     * @return next character from the input
     */
    protected fun next(): Char = sourceFile.next()

    /**
     * Lookahead in the input by a given [offset].
     *
     * @param offset amount of bytes to peek after the current position. By default, peeks ahead 0 characters and thus
     * returns the character that would be returned by the next [next]-call.
     *
     * @return the character [offset] bytes behind the current position in the input.
     */
    protected fun peek(offset: Int = 0): Char = sourceFile.peek(offset)

    /**
     * Lexes the given input.
     * @return [Sequence] of [Tokens][Token]
     */
    fun tokens(): Sequence<Token> = sequence {
        // need to use next() instead of peek() because otherwise the source position is not correct
        var c = next()
        while (c != InputProvider.END_OF_FILE) {
            val position = sourceFile.currentPosition
            val token = scanToken(c)
            token.position = position

            if (token is Token.ErrorToken) {
                if (token.error != null) {
                    sourceFile.annotate(AnnotationType.ERROR, token.range, token.error)
                }
                if (token.errorReporter != null) {
                    token.errorReporter.invoke(token)
                }
            }

            yield(token)
            c = next()
        }

        val eof = Token.Eof()
        eof.position = sourceFile.currentPosition
        yield(eof)
    }

    /**
     * Lex the next token.
     *
     * @param[firstChar] first char of the token
     * @return the next [token][Token]
     */
    protected abstract fun scanToken(firstChar: Char): Token
}
