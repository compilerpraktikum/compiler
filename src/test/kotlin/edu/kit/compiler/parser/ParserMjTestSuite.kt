package edu.kit.compiler.parser

import edu.kit.compiler.ast.AST
import edu.kit.compiler.initializeKeywords
import edu.kit.compiler.lex.BufferedInputProvider
import edu.kit.compiler.lex.Lexer
import edu.kit.compiler.lex.StringTable
import edu.kit.compiler.utils.TestUtils
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.io.FileInputStream
import java.util.concurrent.TimeUnit
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

    @OptIn(ExperimentalStdlibApi::class)
    @ParameterizedTest
    @Timeout(3, unit = TimeUnit.SECONDS)
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

        var exception: Throwable? = null
        try {
            val ast: AST.Program = runBlocking {
                parser.parse()
            }
        } catch (ex: Throwable) {
            exception = ex
        }
        if (testConfig.name.endsWith("invalid.mj")) {
            assert(exception != null) {
                "expected failure, but got success"
            }
        } else {
            assert(exception == null) {
                val stack = exception!!.stackTraceToString()
                "expected success, but got failure: $stack"
            }
        }
    }
}
