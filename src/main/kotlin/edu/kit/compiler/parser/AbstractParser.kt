package edu.kit.compiler.parser

import edu.kit.compiler.SourceCodeWindow
import edu.kit.compiler.Token
import edu.kit.compiler.ast.AST
import edu.kit.compiler.lex.AbstractLexer
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.flow.produceIn
import kotlinx.coroutines.flow.take

private val Token.isRelevantForSyntax
    get() = !(this is Token.Whitespace || this is Token.Comment)

/**
 * Asynchronous parser that consumes a lexer flow generated by [AbstractLexer.tokens] and generates an [ASTNode] from
 * it.
 *
 * @param lexer [AbstractLexer] implementation providing a flow of [edu.kit.compiler.Token]
 */
abstract class AbstractParser(private val tokens: Flow<Token>) {

    /**
     * The lookahead buffer that provides the token stream and preloads tokens, when a lookahead is required
     */
    private lateinit var lookaheadBuffer: LookaheadBuffer<Token>

    /**
     * The source code window saves a line of tokens (terminated by whitespace containing any form of newline), to
     * provide reasonable error messages.
     */
    private lateinit var sourceCodeWindow: SourceCodeWindow

    /**
     * Reconstruct the line of source code that is currently being parsed.
     *
     * @return a string representation of the current line of code
     */
    protected suspend fun getCurrentSourceLine() = sourceCodeWindow.buildLine()

    /**
     * Take the foremost token within the token stream (that isn't whitespace or a comment) and advance the token stream
     * behind it.
     */
    protected suspend fun next(): Token {
        var token: Token
        do {
            token = lookaheadBuffer.get()
            sourceCodeWindow.notice(token)
        } while (!token.isRelevantForSyntax)
        return token
    }

    /**
     * Peek into the parser-relevant token stream and return the token at the given offset (starting at 0 for the token
     * that a call to [next] would return next). Does not mutate the token stream.
     */
    protected suspend fun peek(offset: Int = 0) = lookaheadBuffer.peekFlow()
        .filter { it.isRelevantForSyntax }
        .take(offset + 1).last()

    suspend fun initialize() = coroutineScope {
        lookaheadBuffer = LookaheadBuffer(tokens.buffer(32768).produceIn(this@coroutineScope))
        sourceCodeWindow = SourceCodeWindow(lookaheadBuffer)
    }
    /**
     * Parse the lexer stream into an AST. Suspends when the lexer isn't fast enough.
     */
    @OptIn(FlowPreview::class)
    suspend fun parse(): AST.Program = coroutineScope {
        initialize()

        parseAST()
    }

    /**
     * Construct the AST from the token stream
     */
    abstract suspend fun parseAST(): AST.Program

    /**
     * Expect and return a token of type [T].
     */
    protected suspend inline fun <reified T : Token> expect(): T {
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
        TODO("*explosion sounds*")
    }
}
