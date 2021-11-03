package edu.kit.compiler.parser

import edu.kit.compiler.TestUtils
import edu.kit.compiler.initializeKeywords
import edu.kit.compiler.lex.BufferedInputProvider
import edu.kit.compiler.lex.Lexer
import edu.kit.compiler.lex.StringTable
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.io.FileInputStream
import java.util.stream.Stream
import kotlin.io.path.absolutePathString

internal class ParserMjTestSuite {
    companion object {
        /** this is used to run multiple instances of the test:
         * see: https://blog.oio.de/2018/11/13/how-to-use-junit-5-methodsource-parameterized-tests-with-kotlin/
         *
         * @return Stream of **relative** Paths (e.g. LongestPattern.mj) The name displayed in the test results and
         *         shouldn't be verbose
         */
        @JvmStatic
        fun provideTests(): Stream<TestUtils.TestFileArgument> = TestUtils.getTestSuiteFilesFor("syntax")
    }

    @ParameterizedTest
    @MethodSource("provideTests")
    fun test_parser(testConfig: TestUtils.TestFileArgument) {
        val inputFile = testConfig.path

        println("Running parser on $inputFile")

        val input = BufferedInputProvider(FileInputStream(inputFile.toFile()))

        val stringTable = StringTable().apply {
            initializeKeywords()
        }
        val lexer = Lexer(inputFile.absolutePathString(), input, stringTable)

        val parser = Parser(lexer.tokens())

        val ast: ASTNode = runBlocking {
            parser.parse()
        }

        // TODO: proper error detection by inspecting the AST for Error
        if (testConfig.name.endsWith("invalid.mj")) {
            kotlin.test.assertTrue("Expected an invalid token, but ended successfully") { false }
        } else {
            kotlin.test.assertTrue("succeeded") { true }
        }
    }
}
