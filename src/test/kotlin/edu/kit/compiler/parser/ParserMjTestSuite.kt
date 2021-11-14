package edu.kit.compiler.parser

import edu.kit.compiler.initializeKeywords
import edu.kit.compiler.lex.Lexer
import edu.kit.compiler.lex.SourceFile
import edu.kit.compiler.lex.StringTable
import edu.kit.compiler.utils.TestUtils
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.nio.charset.MalformedInputException
import java.util.concurrent.TimeUnit
import java.util.stream.Stream

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

        println("Testing parser on $inputFile")

        val input = try {
            SourceFile.from(inputFile)
        } catch (e: MalformedInputException) {
            assert(testConfig.name.endsWith("invalid.mj")) { "supposedly valid test contained invalid ASCII characters" }
            return
        }

        val stringTable = StringTable(StringTable::initializeKeywords)
        val lexer = Lexer(input, stringTable)

        val parser = Parser(lexer.tokens())

        var exception: Throwable? = null
        try {
            parser.parse()
        } catch (ex: Throwable) {
            exception = ex
        }
        if (testConfig.name.endsWith("invalid.mj")) {
            assert(exception != null) {
                "expected failure, but got success for file ${testConfig.name}"
            }
        } else {
            assert(exception == null) {
                val stack = exception!!.stackTraceToString()
                "expected success, but got failure for file ${testConfig.name}: $stack"
            }
        }
    }
}
