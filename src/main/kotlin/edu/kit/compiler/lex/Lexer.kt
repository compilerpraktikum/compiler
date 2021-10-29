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
class Lexer(private val input: BufferedInputProvider, private val stringTable: StringTable) {
    
    fun tokenStream() = flow {
        val c = input.next()
        
        while (c != null) {
            when (input.next()) {
                
                else -> emit(Token.ErrorToken("unexpected character: $c"))
            }
        }
        
        emit(Token.Eof())
    }.buffer()
    
}