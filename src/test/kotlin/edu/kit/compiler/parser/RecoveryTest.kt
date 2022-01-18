package edu.kit.compiler.parser

import edu.kit.compiler.source.SourceFile
import edu.kit.compiler.utils.createLexer
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * Test cases for correct error recovery. Probably not exhaustive. Test cases are strings were the parser-defined error
 * position is marked with '#'. The test routine will determine the positions of '#' characters, remove those characters
 * and then assert that the errors are found there.
 */

class RecoveryTest {

    @Test
    fun testParameterParsing() {
        testParse("class a { public void foo(#final int a, a#, a#) { } }")
    }

    @Test
    fun testExpressionParsing() {
        testParse("class a { public void foo() { a + b + #+ c; #--d; int c = d[#]#d; (a = b) = c; } }")
    }

    @Test
    fun testClasses() {
        testParse("class #class class class class")
    }

    @OptIn(ExperimentalStdlibApi::class)
    private fun testParse(input: String) {
        // determine error positions
        val errorPositions = mutableListOf<Int>()
        var curr = -1
        do {
            curr = input.indexOf('#', curr + 1)

            if (curr > -1)
                errorPositions.add(curr - errorPositions.size)
        } while (curr > -1)

        val (lexer, sourceFile) = createLexer(input.replace("#", ""))
        val parser = Parser(sourceFile, lexer.tokens())
        parser.parse()

        sourceFile.printAnnotations()

        val annotations = sourceFile.getAnnotations().toList()
        assertEquals(
            errorPositions.size,
            annotations.size,
            "found ${if (errorPositions.size > annotations.size) "less" else "more"} errors than expected"
        )

        for ((index, a) in annotations.map(SourceFile.Annotation::range).withIndex()) {
            assertEquals(errorPositions[index], a.start.offset)
        }
    }
}
