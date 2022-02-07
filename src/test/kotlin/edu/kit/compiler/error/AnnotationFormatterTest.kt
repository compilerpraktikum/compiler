package edu.kit.compiler.error

import edu.kit.compiler.source.AnnotationType
import edu.kit.compiler.source.InputProvider
import edu.kit.compiler.source.SourceFile
import edu.kit.compiler.source.SourceNote
import edu.kit.compiler.source.SourcePosition
import edu.kit.compiler.source.SourceRange
import kotlin.test.Test

internal class AnnotationFormatterTest {

    private fun SourceFile.range(start: Int, length: Int) = SourceRange(SourcePosition(this, start), length)

    private fun annotateSource(input: String, block: SourceFile.() -> Unit) {
        val source = SourceFile.from("/path/to/file", input)

        @Suppress("ControlFlowWithEmptyBody")
        while (source.next() != InputProvider.END_OF_FILE) {
        }

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
                    SourceNote(range(80, 1), "type is int"),
                    SourceNote(range(84, 32), "type is boolean"),
                )
            )
        }
        println("--------------------------------------")
    }
}
