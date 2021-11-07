package edu.kit.compiler.lex

import edu.kit.compiler.Token
import edu.kit.compiler.error.AnnotationFormatter
import edu.kit.compiler.initializeKeywords
import edu.kit.compiler.lexTestRepr
import edu.kit.compiler.utils.TestUtils
import edu.kit.compiler.utils.toList
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.nio.charset.MalformedInputException
import java.util.stream.Stream
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

        println("Testing $inputFile")

        val input = try {
            SourceFile.from(inputFile)
        } catch (e: MalformedInputException) {
            assert(testConfig.name.endsWith("invalid.mj")) { "supposedly valid test contained invalid ASCII characters" }
            return
        }

        val stringTable = StringTable().apply {
            initializeKeywords()
        }
        val lexer = Lexer(input, stringTable)

        val tokens: List<Token> = lexer.tokens().toList()

        input.printAnnotations(AnnotationFormatter.DEFAULT)

        if (testConfig.name.endsWith("invalid.mj")) {
            assertTrue("Expected an invalid token") { tokens.any { it is Token.ErrorToken } }
        } else {
            val expected = outputFile.readLines()

            assertEquals(expected, tokens.lexTestRepr)
        }
    }
}
