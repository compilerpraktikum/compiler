package edu.kit.compiler.lex

import edu.kit.compiler.Token
import edu.kit.compiler.debugRepr
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.toCollection
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.io.FileInputStream
import java.nio.file.Path
import java.nio.file.Paths
import java.util.stream.Stream
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
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
        fun provideValidTests(): Stream<Path> {
            val path = Paths.get("test-cases", "lexer")
            return path.listDirectoryEntries("*.mj").map { path.relativize(it).also { println(it) } }.stream()
        }
    
    }
    
    @ParameterizedTest
    @MethodSource("provideValidTests")
    fun test_lexer(path: Path) {
        val absolutePath = Paths.get("test-cases", "lexer").toAbsolutePath()
        val inputFile = absolutePath.resolve(path)
        val outputFile = absolutePath.resolve(path.name + ".out")
    
        println("For input $inputFile expect $outputFile")
    
        val input = RingBuffer(FileInputStream(inputFile.toFile()).channel)
    
        val lexer = Lexer(input, StringTable())
    
        val tokens: List<Token> = runBlocking {
            lexer.tokenStream().toCollection(mutableListOf())
        }
    
        if (path.name.endsWith("invalid.mj")) {
            assertTrue("Expected an invalid token") { tokens.any { it is Token.ErrorToken } }
        } else {
            val expected = outputFile.readLines()
        
            assertEquals(expected, tokens.debugRepr)
        }
    
    
    }
}