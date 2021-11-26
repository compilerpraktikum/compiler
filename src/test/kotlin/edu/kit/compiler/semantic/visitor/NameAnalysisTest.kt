package edu.kit.compiler.semantic.visitor

import edu.kit.compiler.parser.Parser
import edu.kit.compiler.semantic.doNameAnalysis
import edu.kit.compiler.utils.createLexer
import edu.kit.compiler.wrapper.wrappers.validate
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

internal class NameAnalysisTest {

    private fun checkNames(shouldSucceed: Boolean, input: () -> String) {
        val (lexer, sourceFile) = createLexer(input())
        val parser = Parser(sourceFile, lexer.tokens())
        val ast = parser.parse().validate()
        sourceFile.printAnnotations()
        ast!!
        doNameAnalysis(ast, sourceFile)

        sourceFile.printAnnotations()

        assertEquals(shouldSucceed, !sourceFile.hasError)
    }

    @Test
    fun testValidCallOtherMethod() {
        checkNames(true) {
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
        checkNames(false) {
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
        checkNames(false) {
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

    @Test
    fun testKnownMethodOtherClass() {
        checkNames(true) {
            """
                class Foo {
                    public int bar() {}
                }

                class Test {
                    public Foo foo;
                    public void main(int i) {
                        foo.bar();
                    }
                }
            """.trimIndent()
        }
    }

    @Test
    fun testUnknownClassAsField() {
        checkNames(false) {
            """
                class Test {
                    public Foo foo;
                    public void main(int i) {
                        foo.bar();
                    }
                }
            """.trimIndent()
        }
    }

    @Test
    fun testUnknownClassAsParameter() {
        checkNames(false) {
            """
                class Test {
                    public void main(Foo foo) {
                        foo.bar();
                    }
                }
            """.trimIndent()
        }
    }

    @Test
    fun testUnknownClassAsVariable() {
        checkNames(false) {
            """
                class Test {
                    public Foo foo;
                    public void main(int i) {
                        Foo foo;
                        foo.bar();
                    }
                }
            """.trimIndent()
        }
    }

    @Test
    fun testShadowValid() {
        checkNames(true) {
            """
                class Test {
                    public int i;
                    public void main(int i) {
                    }
                }

                class Sup {
                    public int i;
                    public void main() {
                        int i;
                    }
                }
            """.trimIndent()
        }
    }

    @Test
    fun testShadowInvalidParameter() {
        checkNames(false) {
            """
                class Test {
                    public int i;
                    public void main(int i) {
                        int i;
                    }
                }
            """.trimIndent()
        }
    }

    @Test
    fun testShadowInvalidBlock() {
        checkNames(false) {
            """
                class Test {
                    public void main() {
                        int i;
                        int j;
                        int i;
                    }
                }
            """.trimIndent()
        }
    }

    @Test
    fun testShadowInvalidBlockNested() {
        checkNames(false) {
            """
                class Test {
                    public void main() {
                        int i;
                        {
                            int i;
                        }
                    }
                }
            """.trimIndent()
        }
    }

    @Test
    fun testShadowValidBlockNested() {
        checkNames(true) {
            """
                class Test {
                    public void main() {
                        int j;
                        {
                            int i;
                        }
                        {
                            int i;
                        }
                    }
                }
            """.trimIndent()
        }
    }

    @Test
    fun testParameterAccess() {
        checkNames(true) {
            """
                class Test {
                    public void main(int i) {
                        i + i;
                    }
                }
            """.trimIndent()
        }
    }
}
