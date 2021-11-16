package edu.kit.compiler.parser

import edu.kit.compiler.error.AnnotationFormatter
import edu.kit.compiler.lex.SourceFile
import edu.kit.compiler.lex.SourcePosition
import edu.kit.compiler.utils.createLexer
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * Test cases for correct error recovery. Probably not exhaustive
 */
class RecoveryTest {
    @Test
    fun testMultiArrayAccess() {
        testParse("class a { public void foo(int a int a) {} }")
    }

    @OptIn(ExperimentalStdlibApi::class)
    private fun testParse(input: String, vararg expectedErrorPositions: SourcePosition) {
        val (lexer, sourceFile) = createLexer(input)
        val parser = Parser(sourceFile, lexer.tokens())
        parser.parse()

        sourceFile.printAnnotations(AnnotationFormatter.DEFAULT)

        val annotations = sourceFile.getAnnotations().toList()
        assertEquals(expectedErrorPositions.size, annotations.size)

        for ((index, a) in annotations.map(SourceFile.Annotation::position).withIndex()) {
            // todo check that expected source positions of annotations match results
        }
    }
}
