package edu.kit.compiler.parser

import edu.kit.compiler.lex.AbstractLexer
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.produceIn

class Parser(private val lexer: AbstractLexer, private val estimatedLookAhead: Int = 2) {
    
    private lateinit var lookaheadBuffer: LookaheadBuffer
    
    suspend fun parse(): ASTNode = coroutineScope {
        lookaheadBuffer = LookaheadBuffer(lexer.tokens().buffer().produceIn(this@coroutineScope), estimatedLookAhead)
        
        TODO()
    }
}