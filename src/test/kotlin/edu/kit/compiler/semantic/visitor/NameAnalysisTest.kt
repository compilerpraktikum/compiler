package edu.kit.compiler.semantic.visitor

import edu.kit.compiler.parser.Parser
import edu.kit.compiler.semantic.doNameAnalysis
import edu.kit.compiler.utils.createLexer
import edu.kit.compiler.wrapper.wrappers.validate
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

internal class NameAnalysisTest {

    private fun check(shouldSucceed: Boolean, input: () -> String) {
        val (lexer, sourceFile) = createLexer(input())
        val parser = Parser(sourceFile, lexer.tokens())
        val ast = parser.parse().validate()
        sourceFile.printAnnotations()
        ast!!
        try {
            doNameAnalysis(ast, sourceFile)
        } catch (e: NotImplementedError) {
            kotlin.check(sourceFile.hasError) { "" }
            sourceFile.printAnnotations()
            assertFalse(shouldSucceed)
        }

        sourceFile.printAnnotations()

        assertEquals(shouldSucceed, !sourceFile.hasError)
    }

    @Test
    fun testValidCallOtherMethod() {
        check(true) {
            """
                class Test {
                    public int f;
                    public void a(int f) {
                        b(f);
                    }

                    public void b(int f) {}
                }
            """.trimIndent()
        }
    }

    @Test
    fun testUnknownMethod() {
        check(false) {
            """
                class Test {
                    public void main(int i) {
                        test();
                    }
                }
            """.trimIndent()
        }
    }

    @Test
    fun testUnknownMethodOtherClass() {
        check(false) {
            """
                class Foo {
                    public int bar() {}
                }

                class Test {
                    public Foo foo;
                    public void main(int i) {
                        foo.test();
                    }
                }
            """.trimIndent()
        }
    }
}
