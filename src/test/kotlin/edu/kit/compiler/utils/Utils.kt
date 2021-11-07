package edu.kit.compiler.utils

import edu.kit.compiler.initializeKeywords
import edu.kit.compiler.lex.InputProvider
import edu.kit.compiler.lex.Lexer
import edu.kit.compiler.lex.StringTable
import edu.kit.compiler.parser.Parser
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions

/**
 * An input provider sourcing its input from a fixed string
 */
class InlineInputProvider(private val input: String) : InputProvider {
    private var cursor: Int = 0

    override suspend fun next(): Char {
        return if (cursor < input.length) input[cursor++]
        else InputProvider.END_OF_FILE
    }

    override suspend fun peek(offset: Int): Char {
        return if (cursor + offset < input.length) input[cursor + offset]
        else InputProvider.END_OF_FILE
    }
}

fun createLexer(input: String, fileName: String = "/path/to/file"): Lexer {
    val stringTable = StringTable().apply {
        initializeKeywords()
    }
    return Lexer(fileName, InlineInputProvider(input), stringTable)
}

