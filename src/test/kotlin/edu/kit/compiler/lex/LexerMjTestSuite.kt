package edu.kit.compiler.lex

import edu.kit.compiler.Token
import edu.kit.compiler.initializeKeywords
import edu.kit.compiler.lexTestRepr
import kotlinx.coroutines.flow.toCollection
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.io.File
import java.io.FileInputStream
import java.nio.file.Path
import java.nio.file.Paths
import java.util.stream.Stream
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readLines
import kotlin.test.assertEquals
import kotlin.test.assertTrue


internal class LexerMjTestSuite {
    
    class TestFileArgument(val name: String, val path: Path) {
        // This is used for naming in the junit output
        override fun toString(): String = name
        
    }
    
    companion object {
        /** this is used to run multiple instances of the test:
         * see: https://blog.oio.de/2018/11/13/how-to-use-junit-5-methodsource-parameterized-tests-with-kotlin/
         *
         * @return Stream of **relative** Paths (e.g. LongestPattern.mj) The name displayed in the test results and
         *         shouldn't be verbose
         */
        @JvmStatic
        fun provideValidTests(): Stream<TestFileArgument> {
            // https://stackoverflow.com/questions/320542/how-to-get-the-path-of-a-running-jar-file
            val testFolderAbsolutePath =
                    File(LexerMjTestSuite::class.java.protectionDomain.codeSource.location.toURI()).getPath()
            val projectRootDirectory = Paths.get(testFolderAbsolutePath).parent.parent.parent.parent
            val path = projectRootDirectory.resolve("test-cases").resolve("lexer")
            return path.listDirectoryEntries("*.mj").map { TestFileArgument(path.relativize(it).name, it) }.stream()
        }
    
    }
    
    @ParameterizedTest
    @MethodSource("provideValidTests")
    fun test_lexer(testConfig: TestFileArgument) {
        val inputFile = testConfig.path
        val outputFile = testConfig.path.parent.resolve(testConfig.name + ".out")
        
        println("For input $inputFile expect $outputFile")
        
        val input = BufferedInputProvider(FileInputStream(inputFile.toFile()))
        
        
        val stringTable = StringTable().apply {
            initializeKeywords()
        }
        val lexer = Lexer(input, stringTable)
        
        val tokens: List<Token> = runBlocking {
            lexer.tokenStream().toCollection(mutableListOf())
        }
        
        if (testConfig.name.endsWith("invalid.mj")) {
            assertTrue("Expected an invalid token") { tokens.any { it is Token.ErrorToken } }
        } else {
            val expected = outputFile.readLines()
            
            assertEquals(expected, tokens.lexTestRepr)
        }
        
        
    }
}