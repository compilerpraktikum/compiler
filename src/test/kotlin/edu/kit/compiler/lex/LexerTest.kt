package edu.kit.compiler.lex

import edu.kit.compiler.Token
import kotlinx.coroutines.flow.toCollection
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class LexerTest {
    
    /**
     * An input provider sourcing its input from a fixed string
     */
    private object FixedInputProvider : InputProvider {
        var input: String = ""
            set(value) {
                cursor = 0
                field = value
            }
        
        private var cursor: Int = 0
        
        override suspend fun peek(offset: Int): Char? {
            return if ((cursor + offset) < input.length) input[cursor + offset]
            else null
        }
        
        override suspend fun nextChar(): Char? {
            return if (cursor < input.length) input[cursor++]
            else null
        }
    }
    
    private lateinit var lexer: Lexer
    
    @BeforeEach
    internal fun setUp() {
        lexer = Lexer(FixedInputProvider, StringTable())
    }
    
    @Test
    fun testNoInput() {
        expectTokenSequence("", listOf(Token.Eof))
    }
    
    @Test
    fun testSimpleInput() {
        expectTokenSequence("class ++HelloWorld{1234", listOf(
                Token.Keyword(Token.Key.Class),
                Token.Whitespace(" "),
                Token.Operator(Token.Op.PlusPlus),
                Token.Identifier("HelloWorld"),
                Token.Operator(Token.Op.LeftBrace),
                Token.Literal(1234),
                Token.Eof
        ))
    }
    
    @Test
    fun testWhitespace() {
        expectTokenSequence(" \n\r\t\b\u000C", listOf(
                Token.Whitespace(" \n\r\t"),
                Token.ErrorToken(""),
                Token.ErrorToken(""),
                Token.Eof
        ))
    }
    
    @Test
    fun testKeywordAggregation() {
        expectTokenSequence("classthrowsabstract", listOf(
                Token.Identifier("classthrowsabstract"),
                Token.Eof
        ))
    }
    
    @Test
    fun testLongestToken() {
        expectTokenSequence("<<<<<<<<<<>>>====::", listOf(
                Token.Operator(Token.Op.LeftShift),
                Token.Operator(Token.Op.LeftShift),
                Token.Operator(Token.Op.LeftShift),
                Token.Operator(Token.Op.LeftShift),
                Token.Operator(Token.Op.LeftShift),
                Token.Operator(Token.Op.RightShiftAssign),
                Token.Operator(Token.Op.Eq),
                Token.Operator(Token.Op.Assign),
                Token.Operator(Token.Op.Colon),
                Token.Operator(Token.Op.Colon),
                Token.Eof
        ))
    }
    
    @Test
    fun testBrokenComment() {
        expectTokenSequence("/*/", listOf(
                Token.ErrorToken(""),
                Token.Eof
        ))
    }
    
    @Test
    fun testIllegalComment() {
        expectTokenSequence("/*/***/*/", listOf(
                Token.Comment("/*/***/"),
                Token.Operator(Token.Op.Mul),
                Token.Operator(Token.Op.Div),
                Token.Eof
        ))
    }
    
    @Test
    fun testIllegalCharacters() {
        expectTokenSequence("\u0000\u0001ä", listOf(
                Token.ErrorToken(""),
                Token.ErrorToken(""),
                Token.ErrorToken(""),
                Token.Eof
        ))
    }
    
    @Test
    fun testEvilInput() {
        expectTokenSequence("class class classname throws >>>>\u0000||||/*class/**/>>>==01234.1_012protected\u0004",
                listOf(Token.Keyword(Token.Key.Class),
                        Token.Whitespace(" "),
                        Token.Keyword(Token.Key.Class),
                        Token.Whitespace(" "),
                        Token.Identifier("classname"),
                        Token.Whitespace(" "),
                        Token.Keyword(Token.Key.Throws),
                        Token.Whitespace(" "),
                        Token.Operator(Token.Op.RightShift),
                        Token.Operator(Token.Op.Gt),
                        Token.ErrorToken("ignore error message for test case"),
                        Token.Operator(Token.Op.Or),
                        Token.Operator(Token.Op.Or),
                        Token.Comment("/*class/**/"),
                        Token.Operator(Token.Op.RightShiftAssign),
                        Token.Operator(Token.Op.Assign),
                        Token.Literal(0),
                        Token.Literal(1234),
                        Token.Operator(Token.Op.Dot),
                        Token.Literal(1),
                        Token.Identifier("_012protected"),
                        Token.ErrorToken("ignore error message for test case"),
                        Token.Eof))
    }
    
    /**
     * Parse an input string and assert the exact resulting token sequence matches a given sequence
     */
    private fun expectTokenSequence(input: String, expectedTokens: List<Token>) {
        FixedInputProvider.input = input
        
        val tokens = runBlocking {
            lexer.tokenStream().toCollection(mutableListOf())
        }
        
        assertEquals(expectedTokens.size, tokens.size);
        
        expectedTokens.zip(tokens).forEach { (expected, lexed) ->
            if (expected is Token.ErrorToken) {
                assertTrue(lexed is Token.ErrorToken)
            } else {
                assertEquals(expected, lexed)
            }
        }
    }
}