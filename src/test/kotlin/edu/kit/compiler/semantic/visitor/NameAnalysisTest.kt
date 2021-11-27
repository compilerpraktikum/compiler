package edu.kit.compiler.semantic.visitor

import edu.kit.compiler.parser.Parser
import edu.kit.compiler.semantic.doNameAnalysis
import edu.kit.compiler.utils.createLexer
import edu.kit.compiler.wrapper.wrappers.validate
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

internal class NameAnalysisTest {

    private fun checkNames(shouldSucceed: Boolean, input: () -> String) {
        val (lexer, sourceFile, stringTable) = createLexer(input())
        val parser = Parser(sourceFile, lexer.tokens())
        val ast = parser.parse().validate()
        sourceFile.printAnnotations()
        ast!!
        doNameAnalysis(ast, sourceFile, stringTable)

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

    @Test
    fun testValidStringUsage() {
        checkNames(true) {
            """
                class Test {
                    public static void main(String[] args) {}
                    public String test(String i, String j) {
                        String k;
                    }
                }
            """.trimIndent()
        }
    }

    @Test
    fun testInvalidStringUsage() {
        checkNames(false) {
            """
                class Test {
                    public void main(String arg) {
                        int l = arg.length;
                    }
                }
            """.trimIndent()
        }
    }

    @Test
    fun testStringInstantiation() {
        checkNames(false) {
            """
                class Test {
                    public void main(String arg) {
                        String s = new String();
                    }
                }
            """.trimIndent()
        }
    }

    @Test
    fun testValidSystemCalls() {
        // note: types are not checked here, so these calls are all valid
        checkNames(true) {
            """
                class Test {
                    public void main(String arg) {
                        System.in.read();
                        System.out.println();
                        System.out.write();
                        System.out.flush();
                    }
                }
            """.trimIndent()
        }
    }

    @Test
    fun testInvalidSystemField() {
        checkNames(false) {
            """
                class Test {
                    public void main(String arg) {
                        System.test.println();
                    }
                }
            """.trimIndent()
        }
    }

    @Test
    fun testInvalidSystemCall() {
        checkNames(false) {
            """
                class Test {
                    public void main(String arg) {
                        System.out.print();
                    }
                }
            """.trimIndent()
        }
    }

    @Test
    fun testInvalidSystemUsage() {
        checkNames(false) {
            """
                class Test {
                    public void main(String arg) {
                        System;
                    }
                }
            """.trimIndent()
        }
        checkNames(false) {
            """
                class Test {
                    public void main(String arg) {
                        System.in;
                    }
                }
            """.trimIndent()
        }
        checkNames(false) {
            """
                class Test {
                    public void main(String arg) {
                        System.out;
                    }
                }
            """.trimIndent()
        }
    }

    @Test
    fun testShadowSystem() {
        checkNames(false) {
            """
                class System {}

                class Test {
                    public void main(String arg) {
                        System.out.println();
                    }
                }
            """.trimIndent()
        }
        checkNames(false) {
            """
                class Test {
                    public int System;
                    public void test() {
                        System.out.println();
                    }
                }
            """.trimIndent()
        }
        checkNames(false) {
            """
                class Test {
                    public void test() {
                        int System;
                        System.out.println();
                    }
                }
            """.trimIndent()
        }
        checkNames(false) {
            """
                class Test {
                    public void test() {
                        int System;
                        System.out.println();
                    }
                }
            """.trimIndent()
        }
        checkNames(true) {
            """
                class Test {
                    public void test() {
                        System.out.println();
                        int System;
                    }
                }
            """.trimIndent()
        }
    }

    @Test
    fun testAccessFromStaticMethod() {
        checkNames(false) {
            """
                class Test {
                    public int i;
                    public static void main(String arg) {
                        i;
                    }
                }
            """.trimIndent()
        }
        checkNames(false) {
            """
                class Test {
                    public int test() {}
                    public static void main(String arg) {
                        test();
                    }
                }
            """.trimIndent()
        }
    }

    @Test
    fun testCallingMain() {
        checkNames(true) {
            """
                class Test {
                    public void main(String[] arg) {}
                    public void test() {
                        main();
                    }
                }
            """.trimIndent()
        }
        checkNames(false) {
            """
                class Test {
                    public static void main(String[] arg) {}
                    public void test() {
                        main();
                    }
                }
            """.trimIndent()
        }
    }
}
