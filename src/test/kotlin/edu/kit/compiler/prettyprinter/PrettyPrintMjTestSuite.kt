package edu.kit.compiler.parser

import edu.kit.compiler.ast.AST
import edu.kit.compiler.ast.Identity
import edu.kit.compiler.ast.Of
import edu.kit.compiler.ast.PrettyPrintVisitor
import edu.kit.compiler.ast.accept
import edu.kit.compiler.ast.toValidAst
import edu.kit.compiler.initializeKeywords
import edu.kit.compiler.lex.Lexer
import edu.kit.compiler.lex.SourceFile
import edu.kit.compiler.lex.StringTable
import edu.kit.compiler.utils.TestUtils
import edu.kit.compiler.utils.createLexer
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.charset.MalformedInputException
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import java.util.stream.Stream
import kotlin.test.assertEquals

internal class PrettyPrintMjTestSuite {
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
    fun test_idem(testConfig: TestUtils.TestFileArgument) {
        val inputFile = testConfig.path

        println("Running parser on $inputFile")

        val input = try {
            SourceFile.from(inputFile)
        } catch (e: MalformedInputException) {
            assert(testConfig.name.endsWith("invalid.mj")) { "supposedly valid test contained invalid ASCII characters" }
            return
        }

        val stringTable = StringTable {
            initializeKeywords()
        }

        var exception: Throwable? = null
        try {
            println("====[ input ]====")
            println(input)
            val lexer1 = Lexer(input, stringTable)
            val pretty1 = prettyPrint(toValidAst(Parser(lexer1.tokens()).parse())!!)
            println("====[ pretty1 ]====")
            println(pretty1)
            val ast2 = createAST(pretty1)
            val pretty2 = prettyPrint(ast2)
            println("====[ pretty2 ]====")
            println(pretty2)

            assertEquals(pretty1, pretty2)
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
    fun prettyPrint(astRoot: AST.Program<Identity<Of>, Identity<Of>, Identity<Of>, Identity<Of>>): String {
        val byteArrayOutputStream: ByteArrayOutputStream = ByteArrayOutputStream()
        val utf8: String = StandardCharsets.UTF_8.name()
        val printStream = PrintStream(byteArrayOutputStream, true, utf8)

        astRoot.accept(PrettyPrintVisitor(printStream))

        return byteArrayOutputStream.toString(utf8)
    }

    @OptIn(ExperimentalStdlibApi::class)
    fun createAST(input: String): AST.Program<Identity<Of>, Identity<Of>, Identity<Of>, Identity<Of>> {
        val lexer = createLexer(input)
        return toValidAst(Parser(lexer.tokens()).parse())!!
    }
}
