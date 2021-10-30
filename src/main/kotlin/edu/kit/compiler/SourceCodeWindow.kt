package edu.kit.compiler

import edu.kit.compiler.lex.SourcePosition
import edu.kit.compiler.parser.LookaheadBuffer
import edu.kit.compiler.parser.peekFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.takeWhile

class SourceCodeWindow(
    private val lookaheadBuffer: LookaheadBuffer<Token>
) {
    
    companion object {
        private val DUMMY_WHITESPACE = Token.Whitespace("").apply {
            position = SourcePosition("", 0, 0)
        }
    }
    
    private val history = ArrayList<Token>(32).apply {
        add(DUMMY_WHITESPACE) // invariant: history always contains a whitespace / comment token at the beginning
    }
    
    fun notice(token: Token) {
        if (token.containsNewLine()) {
            history.clear()
        }
        
        history.add(token)
    }
    
    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun buildLine(): String = buildString {
        append(history.first().getContentOrNull()!!.substringAfterLast('\n'))
        history.asSequence().drop(1).forEach {
            append(it.sourceCode)
        }
        
        lookaheadBuffer.peekFlow().takeWhile {
            val content = it.getContentOrNull()
            return@takeWhile if (content == null) {
                append(it.sourceCode)
                true
            } else {
                if (!content.containsNewLine()) {
                    append(content)
                    true
                } else {
                    append(content.substringBefore('\n'))
                    false
                }
            }
        }.collect()
    }
    
}

// get content that can possibly contain newlines (only whitespace and comments)
private fun Token.getContentOrNull(): String? = when (this) {
    is Token.Whitespace -> content
    is Token.Comment -> content
    else -> null
}

private fun String.containsNewLine(): Boolean = contains('\n')
private fun Token.containsNewLine(): Boolean = getContentOrNull()?.containsNewLine() ?: false
