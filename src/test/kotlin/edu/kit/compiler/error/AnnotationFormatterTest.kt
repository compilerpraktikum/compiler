package edu.kit.compiler.error

import edu.kit.compiler.lex.AnnotationType
import edu.kit.compiler.lex.InputProvider
import edu.kit.compiler.lex.SourceFile
import edu.kit.compiler.lex.SourcePosition
import edu.kit.compiler.lex.SourceRange
import kotlin.test.Test

internal class AnnotationFormatterTest {

    private fun SourceFile.range(start: Int, length: Int) = SourceRange(SourcePosition(this, start), length)

    fun annotateSource(input: String, block: SourceFile.() -> Unit) {
        val source = SourceFile.from("/path/to/file", input)
        while (source.next() != InputProvider.END_OF_FILE) {}
        source.block()
        source.printAnnotations()
    }

    @Test
    fun testSingleLineAnnotation() {
        println("--------------------------------------")
        annotateSource(
            """





            class Test {
                public int test() {
                    boolean b = true;
                    int a = 7 + 5 ==
                         3 * 7 + 42;
                }
            }
            """.trimIndent()
        ) {
            annotate(
                AnnotationType.ERROR, range(80, 36), "invalid assignment: types do not match",
                listOf(
                    range(80, 1) to "type is int",
                    range(84, 32) to "type is boolean",
                )
            )
        }
        println("--------------------------------------")
    }
}
