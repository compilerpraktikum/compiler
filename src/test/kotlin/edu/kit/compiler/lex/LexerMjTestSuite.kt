package edu.kit.compiler.lex

import edu.kit.compiler.utils.TestUtils
import edu.kit.compiler.Token
import edu.kit.compiler.initializeKeywords
import edu.kit.compiler.lexTestRepr
import kotlinx.coroutines.flow.toCollection
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.io.FileInputStream
import java.util.stream.Stream
import kotlin.io.path.absolutePathString
import kotlin.io.path.readLines
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class LexerMjTestSuite {

    companion object {
        /** this is used to run multiple instances of the test:
         * see: https://blog.oio.de/2018/11/13/how-to-use-junit-5-methodsource-parameterized-tests-with-kotlin/
         *
         * @return Stream of **relative** Paths (e.g. LongestPattern.mj) The name displayed in the test results and
         *         shouldn't be verbose
         */
        @JvmStatic
        fun provideValidTests(): Stream<TestUtils.TestFileArgument> = TestUtils.getTestSuiteFilesFor("lexer")
    }

    @ParameterizedTest
    @MethodSource("provideValidTests")
    fun test_lexer(testConfig: TestUtils.TestFileArgument) {
        val inputFile = testConfig.path
        val outputFile = testConfig.path.parent.resolve(testConfig.name + ".out")

        println("For input $inputFile expect $outputFile")

        val input = BufferedInputProvider(FileInputStream(inputFile.toFile()))

        val stringTable = StringTable().apply {
            initializeKeywords()
        }
        val lexer = Lexer(inputFile.absolutePathString(), input, stringTable)

        val tokens: List<Token> = runBlocking {
            lexer.tokens().toCollection(mutableListOf())
        }

        if (testConfig.name.endsWith("invalid.mj")) {
            assertTrue("Expected an invalid token") { tokens.any { it is Token.ErrorToken } }
        } else {
            val expected = outputFile.readLines()

            assertEquals(expected, tokens.lexTestRepr)
        }
    }
}
