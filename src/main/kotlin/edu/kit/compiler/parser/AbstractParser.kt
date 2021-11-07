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
    protected inline fun <reified T : Token> expect(): T {
        val token = next()

        if (token is T)
            return token
        else
            enterPanicMode()
    }

    // TODO: this should probably not return `Nothing`, but this way the type system just eats it at the moment
    protected fun enterPanicMode(): Nothing {
        // very black magic
        // such panic
        // much confusing
        // wow
        throw IllegalArgumentException("in panic mode: *explosion sounds*")
    }
}
