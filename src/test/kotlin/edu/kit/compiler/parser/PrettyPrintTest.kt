package edu.kit.compiler.parser

import edu.kit.compiler.normalizeLineEndings
import edu.kit.compiler.utils.assertIdempotence
import edu.kit.compiler.utils.createSemanticAST
import edu.kit.compiler.utils.prettyPrint
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

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
        runTestEqualsAndIdempotence(
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

        runTestEqualsAndIdempotence(
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
        runTestEqualsAndIdempotence(
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
        runTestEqualsAndIdempotence(
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
        runTestEqualsAndIdempotence(
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
        runTestEqualsAndIdempotence(
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
        runTestEqualsAndIdempotence(
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
    fun testOfficialExampleInput() {
        runTestEqualsAndIdempotence(
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

    @Test
    fun testUnaryMinus() {
        runTestEqualsAndIdempotence(
            """
                class Test {
                    public void test() {
                        -5.test();
                    }
                }
            """.trimIndent(),
            """
                class Test {
                    public void test() {
                        -(5.test());
                    }
                }

            """.trimIndent()
        )
        runTestEqualsAndIdempotence(
            """
                class Test {
                    public void test() {
                        (-5).test();
                    }
                }
            """.trimIndent(),
            """
                class Test {
                    public void test() {
                        (-5).test();
                    }
                }

            """.trimIndent()
        )
    }

    private fun runTestEqualsAndIdempotence(input: String, expected: String) {
        assertIdempotence(input)
        assertPrettyPrintEqualsDesired(input, expected)
    }

    private fun assertPrettyPrintEqualsDesired(input: String, expected: String) {
        val (ast) = createSemanticAST(input)
        val pretty = prettyPrint(ast)

        assertEquals(expected, pretty.normalizeIndent().normalizeLineEndings())
    }
}
