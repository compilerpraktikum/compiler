package edu.kit.compiler.lexer

import edu.kit.compiler.utils.MjTestSuite
import kotlin.io.path.readLines
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class LexerMjTestSuite : MjTestSuite("lexer") {

    override fun TestContext.execute() {
        val stringTable = StringTable(StringTable::initializeKeywords)
        val lexer = Lexer(source, stringTable)

        val tokens: List<Token> = lexer.tokens().toList()
        checkResult(successful = !source.hasError)

        if (testCase.shouldSucceed) {
            val expected = testCase.path.parent.resolve(testCase.path.fileName.toString() + ".out").readLines()
            assertEquals(expected, tokens.lexTestRepr)
        } else {
            assertTrue("Expected an invalid token") { tokens.any { it is Token.ErrorToken } }
        }
    }
}
