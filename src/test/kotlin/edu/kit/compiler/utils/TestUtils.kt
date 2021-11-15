package edu.kit.compiler.utils

import edu.kit.compiler.ast.AST
import edu.kit.compiler.ast.Identity
import edu.kit.compiler.ast.Lenient
import edu.kit.compiler.ast.Of
import edu.kit.compiler.ast.PrettyPrintVisitor
import edu.kit.compiler.ast.accept
import edu.kit.compiler.ast.toValidAst
import edu.kit.compiler.lex.LexerMjTestSuite
import edu.kit.compiler.parser.Parser
import org.junit.jupiter.api.Assertions
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.nio.file.Paths
import java.util.stream.Stream
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.test.assertEquals

object TestUtils {

    class TestFileArgument(val name: String, val path: Path) {
        // This is used for naming in the junit output
        override fun toString(): String = name
    }

    /** this is used to run multiple instances of the test:
     * see: https://blog.oio.de/2018/11/13/how-to-use-junit-5-methodsource-parameterized-tests-with-kotlin/
     *
     * @param subdirectory Name of the subdirectory in `test-cases`, from where this function should get the test files
     *
     * @return Stream of **relative** Paths (e.g. LongestPattern.mj) The name displayed in the test results and
     *         shouldn't be verbose
     */
    fun getTestSuiteFilesFor(subdirectory: String): Stream<TestFileArgument> {
        // https://stackoverflow.com/questions/320542/how-to-get-the-path-of-a-running-jar-file
        val testFolderAbsolutePath =
            File(LexerMjTestSuite::class.java.protectionDomain.codeSource.location.toURI()).getPath()
        val projectRootDirectory = Paths.get(testFolderAbsolutePath).parent.parent.parent.parent
        val path = projectRootDirectory.resolve("test-cases").resolve(subdirectory)
        return path.listDirectoryEntries("*.mj").map { TestFileArgument(path.relativize(it).name, it) }.stream()
    }

    @OptIn(ExperimentalStdlibApi::class)
    fun <T> expectNode(input: String, expectedNode: T, runParser: Parser.() -> T) {
        val (lexer, sourceFile) = createLexer(input)
        val res = Parser(sourceFile, lexer.tokens()).runParser()
        Assertions.assertEquals(expectedNode, res)
    }

    @OptIn(ExperimentalStdlibApi::class)
    fun createAST(input: String): AST.Program<Lenient<Of>, Lenient<Of>, Lenient<Of>, Lenient<Of>> {
        val (lexer, sourceFile) = createLexer(input)
        return Parser(sourceFile, lexer.tokens()).parse()
    }

    fun assertIdemPotence(input: String) {
        println("====[ input ]====")
        println(input)

        val ast1 = createAST(input)
        val pretty1 = prettyPrint(toValidAst(ast1)!!)
        println("====[ pretty1 ]====")
        println(pretty1)

        val ast2 = createAST(pretty1)
        val pretty2 = prettyPrint(toValidAst(ast2)!!)
        println("====[ pretty2 ]====")
        println(pretty2)

        assertEquals(pretty1, pretty2)
    }

    fun prettyPrint(astRoot: AST.Program<Identity<Of>, Identity<Of>, Identity<Of>, Identity<Of>>): String {
        val byteArrayOutputStream: ByteArrayOutputStream = ByteArrayOutputStream()
        val utf8: String = StandardCharsets.UTF_8.name()
        val printStream = PrintStream(byteArrayOutputStream, true, utf8)

        astRoot.accept(PrettyPrintVisitor(printStream))

        return byteArrayOutputStream.toString(utf8)
    }
}
