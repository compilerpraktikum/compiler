package edu.kit.compiler.utils

import edu.kit.compiler.Token
import edu.kit.compiler.initializeKeywords
import edu.kit.compiler.lex.Lexer
import edu.kit.compiler.lex.SourceFile
import edu.kit.compiler.lex.StringTable

fun createLexer(input: String, fileName: String = "/path/to/file"): Lexer {
    val stringTable = StringTable(StringTable::initializeKeywords)
    return Lexer(SourceFile.from(fileName, input), stringTable)
}

fun <T> Sequence<T>.takeWhileInclusive(predicate: (T) -> Boolean): Sequence<T> {
    var shouldContinue = true
    return this.takeWhile {
        val result = shouldContinue
        shouldContinue = predicate(it)
        result
    }
}

fun Sequence<Token>.toList() = takeWhileInclusive { it !is Token.Eof }.toCollection(mutableListOf())
