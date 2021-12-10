package edu.kit.compiler.lex

import edu.kit.compiler.Token
import edu.kit.compiler.utils.createLexer
import edu.kit.compiler.utils.toSymbol
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class LexerTest {

    @Test
    fun testNoInput() {
        expectTokenSequence("", listOf(Token.Eof()))
    }

    @Test
    fun testSimpleInput() {
        expectTokenSequence(
            "class ++HelloWorld{1234",
            listOf(
                Token.Keyword(Token.Keyword.Type.Class),
                Token.Whitespace(" "),
                Token.Operator(Token.Operator.Type.PlusPlus),
                Token.Identifier("HelloWorld".toSymbol()),
                Token.Operator(Token.Operator.Type.LeftBrace),
                Token.Literal("1234"),
                Token.Eof()
            )
        )
    }

    @Test
    fun testWhitespace() {
        expectTokenSequence(
            " \n\r\t\b\u000C",
            listOf(
                Token.Whitespace(" \n\n\t"),
                Token.ErrorToken("", ""),
                Token.ErrorToken("", ""),
                Token.Eof()
            )
        )
    }

    @Test
    fun testKeywordAggregation() {
        expectTokenSequence(
            "classthrowsabstract",
            listOf(
                Token.Identifier("classthrowsabstract".toSymbol()),
                Token.Eof()
            )
        )
    }

    @Test
    fun testLongestToken() {
        expectTokenSequence(
            "<<<<<<<<<<>>>====::",
            listOf(
                Token.Operator(Token.Operator.Type.LeftShift),
                Token.Operator(Token.Operator.Type.LeftShift),
                Token.Operator(Token.Operator.Type.LeftShift),
                Token.Operator(Token.Operator.Type.LeftShift),
                Token.Operator(Token.Operator.Type.LeftShift),
                Token.Operator(Token.Operator.Type.RightShiftAssign),
                Token.Operator(Token.Operator.Type.Eq),
                Token.Operator(Token.Operator.Type.Assign),
                Token.Operator(Token.Operator.Type.Colon),
                Token.Operator(Token.Operator.Type.Colon),
                Token.Eof()
            )
        )
    }

    @Test
    fun testBrokenComment() {
        expectTokenSequence(
            "/*/",
            listOf(
                Token.ErrorToken("", ""),
                Token.Eof()
            )
        )
    }

    @Test
    fun testIllegalComment() {
        expectTokenSequence(
            "/*/***/*/",
            listOf(
                Token.Comment("/*/***/"),
                Token.Operator(Token.Operator.Type.Mul),
                Token.Operator(Token.Operator.Type.Div),
                Token.Eof()
            )
        )
    }

    @Test
    fun testIllegalCharacters() {
        expectTokenSequence(
            "\u0000\u0001ä",
            listOf(
                Token.ErrorToken("", ""),
                Token.ErrorToken("", ""),
                Token.ErrorToken("", ""),
                Token.Eof()
            )
        )
    }

    @Test
    fun testEvilInput() {
        expectTokenSequence(
            "class class classname throws >>>>\u0000||||/*class/**/>>>==01234.1_012protected\u0004",
            listOf(
                Token.Keyword(Token.Keyword.Type.Class),
                Token.Whitespace(" "),
                Token.Keyword(Token.Keyword.Type.Class),
                Token.Whitespace(" "),
                Token.Identifier("classname".toSymbol()),
                Token.Whitespace(" "),
                Token.Keyword(Token.Keyword.Type.Throws),
                Token.Whitespace(" "),
                Token.Operator(Token.Operator.Type.RightShift),
                Token.Operator(Token.Operator.Type.Gt),
                Token.ErrorToken("", "ignore error message for test case"),
                Token.Operator(Token.Operator.Type.Or),
                Token.Operator(Token.Operator.Type.Or),
                Token.Comment("/*class/**/"),
                Token.Operator(Token.Operator.Type.RightShiftAssign),
                Token.Operator(Token.Operator.Type.Assign),
                Token.Literal("0"),
                Token.Literal("1234"),
                Token.Operator(Token.Operator.Type.Dot),
                Token.Literal("1"),
                Token.Identifier("_012protected".toSymbol()),
                Token.ErrorToken("", "ignore error message for test case"),
                Token.Eof()
            )
        )
    }

    @Test
    @Ignore
    fun testVeryLongIdentifiers() {
        val NUM_IDENTIFIERS = 1000
        val IDENTIFIER_LENGTH = 100000
        val input = buildString {
            repeat(NUM_IDENTIFIERS) {
                append("a".repeat(IDENTIFIER_LENGTH))
                append(" ")
            }
        }
        val IDENTIFIER = Token.Identifier("a".repeat(IDENTIFIER_LENGTH).toSymbol())
        val expectedTokens = mutableListOf<Token>().apply {
            repeat(NUM_IDENTIFIERS) {
                add(IDENTIFIER)
                add(Token.Whitespace(" "))
            }
            add(Token.Eof())
        }

        expectTokenSequence(input, expectedTokens, measureDuration = true)
    }

    @Test
    @Ignore
    fun testVeryLongIdentifiersMulti() {
        timings.clear()
        repeat(21) {
            testVeryLongIdentifiers()
            print(".")
            System.out.flush()
        }
        println()
        timings.removeFirst() // warmup
        println("Average: ${timings.average()}ms")
        println("Difference (max - min): ${timings.maxOrNull()!! - timings.minOrNull()!!}ms")
    }

    /**
     * Parse an input string and assert the exact resulting token sequence matches a given sequence
     */
    private fun expectTokenSequence(input: String, expectedTokens: List<Token>, measureDuration: Boolean = false) {
        val start = System.currentTimeMillis()
        val (lexer, _) = createLexer(input)
        val tokens = lexer.tokens().toList()
        val duration = System.currentTimeMillis() - start

        assertEquals(expectedTokens.size, tokens.size)

        expectedTokens.zip(tokens).forEach { (expected, lexed) ->
            if (expected is Token.ErrorToken) {
                assertTrue(lexed is Token.ErrorToken)
            } else {
                assertEquals(expected, lexed)
            }
        }

        if (measureDuration)
            timings.add(duration)
    }

    private val timings = mutableListOf<Long>()
}
