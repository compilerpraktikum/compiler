package edu.kit.compiler.utils

import edu.kit.compiler.lex.LexerMjTestSuite
import edu.kit.compiler.parser.Parser
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import java.util.stream.Stream
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

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
    fun <T> expectNode(input: String, expectedNode: T, runParser: suspend Parser.() -> T) {
        val lexer = createLexer(input)
        val res = runBlocking {
            Parser(lexer.tokens()).also { it.initialize() }.runParser()
        }
        Assertions.assertEquals(expectedNode, res)
    }
}