package edu.kit.compiler.utils

import edu.kit.compiler.Token
import edu.kit.compiler.initializeKeywords
import edu.kit.compiler.lex.Lexer
import edu.kit.compiler.lex.SourceFile
import edu.kit.compiler.lex.StringTable
import edu.kit.compiler.lex.Symbol

fun createLexer(input: String, fileName: String = "/path/to/file"): Triple<Lexer, SourceFile, StringTable> {
    val stringTable = StringTable(StringTable::initializeKeywords)
    val sourceFile = SourceFile.from(fileName, input)
    return Triple(Lexer(sourceFile, stringTable), sourceFile, stringTable)
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

fun String.toSymbol() = Symbol(this, isKeyword = false)
