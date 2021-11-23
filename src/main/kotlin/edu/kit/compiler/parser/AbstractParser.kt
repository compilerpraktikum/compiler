package edu.kit.compiler.parser

import edu.kit.compiler.Token
import edu.kit.compiler.ast.AST
import edu.kit.compiler.lex.AnnotatableFile
import edu.kit.compiler.lex.AnnotationType
import edu.kit.compiler.lex.SourceRange
import edu.kit.compiler.wrapper.wrappers.Lenient
import java.util.Optional

/**
 * Abstract base class for a parser that consumes a token sequence and generates an abstract syntax tree from it.
 *
 * @param tokens [sequence][Sequence] of [tokens][Token]
 * @param sourceFile input wrapper that handles error reporting
 */
abstract class AbstractParser(tokens: Sequence<Token>, protected val sourceFile: AnnotatableFile) {

    /**
     * While in panic mode, suppress errors
     */
    protected var panicMode = false

    /**
     * The lookahead buffer that provides the token stream and buffers tokens when a lookahead is required
     */
    private val buffer = LookaheadBuffer(tokens)

    /**
     * Retrieve the next token.
     *
     * @param recovering if recovering, [panicMode] will not be disabled by reading the token
     */
    protected fun next(recovering: Boolean = false): Token {
        if (!recovering)
            panicMode = false

        return buffer.get()
    }

    /**
     * Peek into the token sequence and return the token at the given offset (starting at 0 for the token
     * that a call to [next] would return next).
     */
    protected fun peek(offset: Int = 0) = buffer.peek(offset)

    /**
     * Construct the AST from the token sequence
     */
    abstract fun parse(): Lenient<AST.Program>

    /**
     * Expect and return a token of type [T].
     */
    protected inline fun <reified T : Token> expect(anc: AnchorUnion, errorMsg: () -> String): Optional<T> {
        val p = peek()
        if (p is T)
            return Optional.of(next() as T)

        reportError(p, errorMsg())
        recover(anc)
        return Optional.empty()
    }

    /**
     * Read a token from the token stream and expect it to be an [Token.Operator] of type [type].
     * If this is not the case, enter [recover] and read from the stream until a token from [anc] is upfront.
     */
    protected fun expectOperator(
        type: Token.Operator.Type,
        anc: AnchorUnion,
        errorMsg: () -> String
    ): Optional<Token.Operator> {
        val peek = peek()
        if (peek !is Token.Operator) {
            reportError(peek, errorMsg())
            recover(anc)
            return Optional.empty()
        }

        return if (peek.type == type)
            Optional.of(next() as Token.Operator)
        else {
            reportError(peek, errorMsg())
            recover(anc)
            Optional.empty()
        }
    }

    /**
     * Read a token and expect it to be an identifier. If it is not an identifier, enter [recover] and read from the
     * token stream until a token within [anc] is upfront.
     */
    protected fun expectIdentifier(anc: AnchorUnion, errorMsg: () -> String): Optional<Token.Identifier> =
        expect(anc, errorMsg)

    /**
     * Read a token from the token stream and expect it to be a [Token.Keyword] of type [type].
     * If this is not the case, enter [recover] and read from the stream until a token from [anc] is upfront.
     *
     * @param type keyword [Token.Keyword.Type] that is expected
     * @param anc [AnchorUnion] for error recovery
     * @param errorMsg lazy error message generator
     */
    protected fun expectKeyword(
        type: Token.Keyword.Type,
        anc: AnchorUnion,
        errorMsg: () -> String
    ): Optional<Token.Keyword> {
        val peek = peek()
        if (peek !is Token.Keyword) {
            reportError(peek, errorMsg())
            recover(anc)
            return Optional.empty()
        }

        return if (peek.type == type)
            Optional.of(next() as Token.Keyword)
        else {
            reportError(peek, errorMsg())
            recover(anc)
            Optional.empty()
        }
    }

    /**
     * Read from the token stream until a token is at the front of the stream that is within the given [AnchorSet]
     *
     * @param anchorSet [AnchorSet] containing all tokens that are accepted as the next token in the stream.
     */
    protected fun recover(anchorSet: AnchorUnion) {
        val anc = anchorSet.provide()
        while (peek() !in anc && peek() !is Token.Eof) next(recovering = true)
    }

    /**
     * Annotate the source input with an error message
     */
    protected fun reportError(range: SourceRange, message: String) {
        if (!panicMode)
            sourceFile.annotate(AnnotationType.ERROR, range, message)

        panicMode = true
    }

    protected fun reportError(token: Token, message: String) {
        reportError(token.range, message)
    }
}
