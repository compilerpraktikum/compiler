package edu.kit.compiler.parser

import edu.kit.compiler.utils.TestUtils.assertIdemPotence
import edu.kit.compiler.utils.TestUtils.createAST
import edu.kit.compiler.utils.TestUtils.prettyPrint
import edu.kit.compiler.wrapper.wrappers.validate
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

private fun String.normalizeLineEndings() = replace("\r\n", "\n").replace("\r", "\n")

private fun String.normalizeIndent() = replace("\t", " ".repeat(4))

/**
 * Idempotenz- und Gleichheitstests
 * Beware!
 * Beim Schreiben von expected-Prints die getabbten Codefragmente einfügen, sonst macht IntelliJ Spaces daraus!!!
 */
class PrettyPrintTest {

    @Test
    fun testOneClassEmptyMain() {
        // not right, need to compare f(x) and f(f(x)) !
        runTestEqualsAndIdemPotence(
            """
            /* OK , unary minus after binop */

            class Main {
                public static void main(String[] args) {}
            }""",
            """
            class Main {
                public static void main(String[] args) { }
            }

            """.trimIndent()
        )
    }

    @Test
    fun testTwoClassesFieldsMethodsMainMethod() {

        runTestEqualsAndIdemPotence(
            """
            class test {
                public int y;
                public int a;
                public static void main(String[] args) {}
            }
            class test2 {public int testMethod(int a, int b, int c){} public int testMethod()throws Boom{}}
            """.trimIndent(),
            """
            class test {
                public static void main(String[] args) { }
                public int a;
                public int y;
            }
            class test2 {
                public int testMethod(int a, int b, int c) { }
                public int testMethod() throws Boom { }
            }

            """.trimIndent()
        )
    }

    @Test
    fun testEmptyBlockIndentation() {
        runTestEqualsAndIdemPotence(
            """
            class test {
                public int y;
                public int a;
                public static void main(String[] args) {
                    { }
                }

                public static void main(Striiing[] args) { }
            }
            """.trimIndent(),
            """
            class test {
                public static void main(String[] args) { }
                public static void main(Striiing[] args) { }
                public int a;
                public int y;
            }

            """.trimIndent()
        )
    }
    /*
    """class Test {
            public void m() {
                a[2 * (-i + 1)][2];
            }
        }"""
     */

    @Test
    fun testArrayAccessExpression() {
        runTestEqualsAndIdemPotence(
            """
            class Test {
                public void m() {
                    a[2 * (-i + 1)][2];
                    a.b[42];
                    new int[5][4];
                }
            }
            """.trimIndent(),
            """class Test {
    public void m() {
        (a[2 * ((-i) + 1)])[2];
        (a.b)[42];
        (new int[5])[4];
    }
}

            """.trimIndent()
        )
    }

    @Test
    fun testWhile() {
        runTestEqualsAndIdemPotence(
            """
                class C {
                    public static void main(String[] args) {
                        while (true){
                            thi.s.test(a);
                        }
                        if (false)
                            thi.s.test(a);
                        else
                            noticeableEctstasies = null;


                        if (true) {
                            thi.s.test(a);
                            thi.s.test(a);
                        } else {
                            noticeableEctstasies = null;
                            noticeableEctstasies = null;
                        }
                    }
                }

            """.trimIndent(),
            """
                class C {
                    public static void main(String[] args) {
                        while (true) {
                            (thi.s).test(a);
                        }
                        if (false)
                            (thi.s).test(a);
                        else
                            noticeableEctstasies = null;
                        if (true) {
                            (thi.s).test(a);
                            (thi.s).test(a);
                        } else {
                            noticeableEctstasies = null;
                            noticeableEctstasies = null;
                        }
                    }
                }

            """.trimIndent()
        )
    }

    /**
     * Test for bug regression where pretty printer would turn `-(2147483648)` into `-2147483648` which changes semantics.
     */
    @Test
    fun regressionBoundaries() {
        runTestEqualsAndIdemPotence(
            """
            class HelloWorld { public static void main(String[] args) { int x = -(2147483648); } }
            """.trimIndent(),
            """
            class HelloWorld {
                public static void main(String[] args) {
                    int x = -(2147483648);
                }
            }

            """.trimIndent()
        )
    }

    /**
     * Inverse test to check bug fix for [regressionBoundaries] did not break anything.
     */
    @Test
    fun regressionNoBoundaries() {
        runTestEqualsAndIdemPotence(
            """
            class HelloWorld { public static void main(String[] args) { int x = -2147483648; } }
            """.trimIndent(),
            """
            class HelloWorld {
                public static void main(String[] args) {
                    int x = -2147483648;
                }
            }

            """.trimIndent()
        )
    }

    @Test
    fun testOfficialBeispieleingabe() {
        runTestEqualsAndIdemPotence(
            """
            class HelloWorld
            {
                public int c;
                public boolean[] array;
                public static /* blabla */ void main(String[] args)
                { System.out.println( (43110 + 0) );
                boolean b = true && (!false);
                if (23+19 == (42+0)*1)
                    b = (0 <1);
                    else if (!array[2+2]) {
                        int x = 0;;
                        x = x+1;
                    } else {
                        new HelloWorld().bar(42+0*1, -1);
                    }
                }
                public int bar(int a, int b) { return c = (a+b); }
            }
            """.trimIndent(),
            """
            class HelloWorld {
                public int bar(int a, int b) {
                    return c = (a + b);
                }
                public static void main(String[] args) {
                    (System.out).println(43110 + 0);
                    boolean b = true && (!false);
                    if ((23 + 19) == ((42 + 0) * 1))
                        b = (0 < 1);
                    else if (!(array[2 + 2])) {
                        int x = 0;
                        x = (x + 1);
                    } else {
                        (new HelloWorld()).bar(42 + (0 * 1), -1);
                    }
                }
                public boolean[] array;
                public int c;
            }

            """.trimIndent()
        )
    }

    private fun runTestEqualsAndIdemPotence(actual: String, expected: String) {
        assertIdemPotence(actual)
        assertPrettyPrintEqualsDesired(actual, expected)
    }

    private fun assertPrettyPrintEqualsDesired(actual: String, expected: String) {
        val ast = createAST(actual)
        val pretty = prettyPrint(ast.validate()!!)

        assertEquals(expected, pretty.normalizeIndent().normalizeLineEndings())
    }
}
