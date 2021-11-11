package edu.kit.compiler.parser

import edu.kit.compiler.Token
import edu.kit.compiler.ast.AST

/**
 * Abstract base class for a parser that consumes a token sequence and generates an abstract syntax tree from it.
 *
 * @param[tokens] [sequence][Sequence] of [tokens][edu.kit.compiler.Token]
 */
abstract class AbstractParser(tokens: Sequence<Token>) {

    /**
     * The lookahead buffer that provides the token stream and buffers tokens when a lookahead is required
     */
    private val buffer = LookaheadBuffer(tokens)

    /**
     * Retrieve the next token.
     */
    protected fun next(): Token = buffer.get()

    /**
     * Peek into the token sequence and return the token at the given offset (starting at 0 for the token
     * that a call to [next] would return next).
     */
    protected fun peek(offset: Int = 0) = buffer.peek(offset)

    /**
     * Construct the AST from the token sequence
     */
    abstract fun parse(): AST.Program

    /**
     * Expect and return a token of type [T].
     */
    protected inline fun <reified T : Token> expect(anc: AnchorSet): T {
        if (peek() is T)
            return next() as T

        println("expected ${T::class.simpleName}, but got ${peek()}")
        panicMode(anc)
        TODO("not implemented: lenient token")
    }

    /**
     * Read a token from the token stream and expect it to be an [Token.Operator] of type [type].
     * If this is not the case, enter [panicMode] and read from the stream until a token from [anc] is upfront.
     */
    protected fun expectOperator(
        type: Token.Operator.Type,
        anc: AnchorSet = FirstFollowUtils.emptyFirstSet
    ): Token.Operator {
        val peek = peek()
        if (peek !is Token.Operator) {
            println("expected operator ($type), but got $peek")
            panicMode(anc)
            TODO("not implemented: lenient token")
        }

        if (peek.type == type)
            return next() as Token.Operator
        else {
            println("expected operator ($type), but got $peek")
            panicMode(anc)
            TODO("not implemented: lenient token")
        }
    }

    /**
     * Read a token and expect it to be an identifier. If it is not an identifier, enter [panicMode] and read from the
     * token stream until a token within [anc] is upfront.
     */
    protected fun expectIdentifier(anc: AnchorSet): Token.Identifier = expect(anc)

    /**
     * Read a token from the token stream and expect it to be a [Token.Keyword] of type [type].
     * If this is not the case, enter [panicMode] and read from the stream until a token from [anc] is upfront.
     */
    protected fun expectKeyword(type: Token.Keyword.Type, anc: AnchorSet): Token.Keyword {
        val peek = peek()
        if (peek !is Token.Keyword) {
            println("expected keyword ($type), but got $peek")
            panicMode(anc)
            TODO("not implemented: lenient token")
        }

        if (peek.type == type)
            return next() as Token.Keyword
        else {
            println("expected keyword ($type), but got $peek")
            panicMode(anc)
            TODO("not implemented: lenient token")
        }
    }

    /**
     * Read from the token stream until a token is at the front of the stream that is within the given [AnchorSet]
     *
     * @param anchorSet [AnchorSet] containing all tokens that are accepted as the next token in the stream.
     */
    protected fun panicMode(anchorSet: AnchorSet) {
        while (peek() !in anchorSet) next()
    }
}
