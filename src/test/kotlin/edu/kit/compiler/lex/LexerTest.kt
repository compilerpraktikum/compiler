package edu.kit.compiler.lex

import edu.kit.compiler.Token
import kotlinx.coroutines.flow.toCollection
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import kotlin.test.Test
import kotlin.test.junit5.JUnit5Asserter

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
    fun testSimpleInput() {
        FixedInputProvider.input = "class ++HelloWorld{1234"
        
        val tokens = runBlocking {
            lexer.tokenStream().toCollection(mutableListOf())
        }
        
        JUnit5Asserter.assertEquals(null, 7, tokens.size)
        JUnit5Asserter.assertTrue(null, tokens[0] is Token.Identifier)
        JUnit5Asserter.assertEquals(null, Token.Whitespace(), tokens[1])
        JUnit5Asserter.assertEquals(null, Token.Operator(Token.Op.PlusPlus), tokens[2])
        JUnit5Asserter.assertTrue(null, tokens[3] is Token.Identifier)
        JUnit5Asserter.assertEquals(null, Token.Operator(Token.Op.LeftBrace), tokens[4])
        JUnit5Asserter.assertTrue(null, tokens[5] is Token.Literal && (tokens[5] as Token.Literal).value == 1234)
        JUnit5Asserter.assertTrue(null, tokens[6] is Token.Eof)
    }
    
    @Test
    fun testIllegalComment() {
        FixedInputProvider.input = "/*/***/*/"
        
        val tokens = runBlocking {
            lexer.tokenStream().toCollection(mutableListOf())
        }
        
        JUnit5Asserter.assertEquals(null, 4, tokens.size)
        JUnit5Asserter.assertTrue(null, tokens[0] is Token.Comment)
        JUnit5Asserter.assertTrue(null, (tokens[1] as? Token.Operator)?.op == Token.Op.Mul)
        JUnit5Asserter.assertTrue(null, (tokens[2] as? Token.Operator)?.op == Token.Op.Div)
        JUnit5Asserter.assertTrue(null, tokens[3] is Token.Eof)
    }
    
    @Test
    fun testIllegalCharacters() {
        FixedInputProvider.input = "\u0000\u0001ä"
    
        val tokens = runBlocking {
            lexer.tokenStream().toCollection(mutableListOf())
        }
    
        JUnit5Asserter.assertEquals(null, 4, tokens.size)
        JUnit5Asserter.assertTrue(null, tokens[0] is Token.ErrorToken)
        JUnit5Asserter.assertTrue(null, tokens[1] is Token.ErrorToken)
        JUnit5Asserter.assertTrue(null, tokens[2] is Token.ErrorToken)
        JUnit5Asserter.assertTrue(null, tokens[3] is Token.Eof)
    }
    
    @Test
    fun testEvilInput() {
        FixedInputProvider.input =
                "class class classname throws >>>>\u0000||||/*class/**/>>>==01234.1_012protected\u0004"
    
        val tokens = runBlocking {
            lexer.tokenStream().toCollection(mutableListOf())
        }
    
        val expectedTokens =
                listOf(Token.Identifier("class"),
                        Token.Whitespace(),
                        Token.Identifier("class"),
                        Token.Whitespace(),
                        Token.Identifier("classname"),
                        Token.Whitespace(),
                        Token.Identifier("throws"),
                        Token.Whitespace(),
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
                        Token.Eof())
    
        JUnit5Asserter.assertEquals(null, expectedTokens.size, tokens.size)
    
        tokens.forEachIndexed { index, token ->
            val expectedToken = expectedTokens[index]
            if (expectedToken is Token.ErrorToken) {
                JUnit5Asserter.assertTrue(null, token is Token.ErrorToken)
            } else {
                JUnit5Asserter.assertEquals(null, expectedToken, token)
            }
        }
    }
}