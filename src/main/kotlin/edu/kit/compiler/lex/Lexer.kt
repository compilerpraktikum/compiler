package edu.kit.compiler.lex

import edu.kit.compiler.Token
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.flow

/**
 * lexicographic analysis and tokenization of an input stream.
 *
 * @param input input abstracted in a ring buffer
 * @param stringTable string table of current compilation run
 */
class Lexer(private val input: InputProvider, private val stringTable: StringTable) {
    
    fun tokenStream() = flow {
        val c = input.nextChar()
        
        while (c != null) {
            when (input.nextChar()) {
                
                else -> emit(Token.ErrorToken("unexpected character: $c"))
            }
        }
        
        emit(Token.Eof())
    }.buffer()
    
}