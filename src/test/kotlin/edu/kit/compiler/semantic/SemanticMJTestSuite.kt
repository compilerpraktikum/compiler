package edu.kit.compiler.parser

import edu.kit.compiler.initializeKeywords
import edu.kit.compiler.lex.Lexer
import edu.kit.compiler.lex.SourceFile
import edu.kit.compiler.lex.StringTable
import edu.kit.compiler.semantic.doSemanticAnalysis
import edu.kit.compiler.utils.TestUtils
import edu.kit.compiler.wrapper.wrappers.validate
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.nio.charset.MalformedInputException
import java.util.concurrent.TimeUnit
import java.util.stream.Stream
import kotlin.test.assertFalse

internal class SemanticMJTestSuite {
    companion object {
        /** this is used to run multiple instances of the test:
         * see: https://blog.oio.de/2018/11/13/how-to-use-junit-5-methodsource-parameterized-tests-with-kotlin/
         *
         * @return Stream of **relative** Paths (e.g. LongestPattern.mj) The name displayed in the test results and
         *         shouldn't be verbose
         */
        @JvmStatic
        fun provideTests(): Stream<TestUtils.TestFileArgument> = TestUtils.getTestSuiteFilesFor("semantic")
    }

    @OptIn(ExperimentalStdlibApi::class)
    @ParameterizedTest
    @Timeout(3, unit = TimeUnit.SECONDS)
    @MethodSource("provideTests")
    fun test_semantic_analysis(testConfig: TestUtils.TestFileArgument) {
        val inputFile = testConfig.path

        println("Testing Analysis on $inputFile")

        val input = try {
            SourceFile.from(inputFile)
        } catch (e: MalformedInputException) {
            assert(testConfig.name.endsWith("invalid.mj")) { "supposedly valid test contained invalid ASCII characters" }
            return
        }

        val stringTable = StringTable(StringTable::initializeKeywords)
        val lexer = Lexer(input, stringTable)

        val parser = Parser(input, lexer.tokens())

        val ast = parser.parse().validate()

        input.printAnnotations()
        ast!!
        try {
            doSemanticAnalysis(ast, input, stringTable)
        } catch (e: NotImplementedError) {
            kotlin.check(input.hasError) { "" }
            input.printAnnotations()
            assertFalse(false)
        }

        input.printAnnotations()
        println(input.hasError)
        if (testConfig.name.endsWith("invalid.mj")) {
            assert(input.hasError) {
                "expected failure, but got success for file ${testConfig.name}"
            }
        } else {
            assert(!input.hasError) {
                "expected success, but got failure for file ${testConfig.name}"
            }
        }
    }
}
