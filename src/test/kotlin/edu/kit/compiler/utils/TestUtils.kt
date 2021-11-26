package edu.kit.compiler.utils

import edu.kit.compiler.ast.AST
import edu.kit.compiler.ast.WipeSourceRangeVisitor
import edu.kit.compiler.lex.LexerMjTestSuite
import edu.kit.compiler.parser.Parser
import edu.kit.compiler.semantic.AstNode
import edu.kit.compiler.semantic.visitor.PrettyPrintVisitor
import edu.kit.compiler.semantic.visitor.accept
import edu.kit.compiler.wrapper.wrappers.Parsed
import edu.kit.compiler.wrapper.wrappers.validate
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
    fun <T> expectNode(input: String, expectedNode: T, runParser: Parser.(WipeSourceRangeVisitor) -> T) {
        val (lexer, sourceFile) = createLexer(input)
        val wipeSourceRangeVisitor = WipeSourceRangeVisitor()
        val res: T = Parser(sourceFile, lexer.tokens()).runParser(wipeSourceRangeVisitor)
        Assertions.assertEquals(expectedNode, res)
    }

    @OptIn(ExperimentalStdlibApi::class)
    fun createAST(input: String): Parsed<AST.Program> {
        val (lexer, sourceFile) = createLexer(input)

        val ast = Parser(sourceFile, lexer.tokens()).parse()
        sourceFile.printAnnotations()
        return ast
    }

    fun assertIdemPotence(input: String) {
        println("====[ input ]====")
        println(input)

        val ast1 = createAST(input)
        val pretty1 = prettyPrint(ast1.validate()!!)
        println("====[ pretty1 ]====")
        println(pretty1)

        val ast2 = createAST(pretty1)
        val pretty2 = prettyPrint(ast2.validate()!!)
        println("====[ pretty2 ]====")
        println(pretty2)

        assertEquals(pretty1, pretty2)
    }

    fun prettyPrint(astRoot: AstNode.Program): String {
        val byteArrayOutputStream = ByteArrayOutputStream()
        val utf8: String = StandardCharsets.UTF_8.name()
        val printStream = PrintStream(byteArrayOutputStream, true, utf8)

        astRoot.accept(PrettyPrintVisitor(printStream))

        return byteArrayOutputStream.toString(utf8)
    }
}
