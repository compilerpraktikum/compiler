package edu.kit.compiler.semantic.visitor

import edu.kit.compiler.parser.Parser
import edu.kit.compiler.semantic.doSemanticAnalysis
import edu.kit.compiler.utils.createLexer
import edu.kit.compiler.wrapper.wrappers.validate
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class TypeAnalysisTest {

    private fun check(shouldSucceed: Boolean, input: () -> String) {
        val (lexer, sourceFile, stringTable) = createLexer(input())
        val parser = Parser(sourceFile, lexer.tokens())
        val ast = parser.parse().validate()
        sourceFile.printAnnotations()
        ast!!
        try {
//            doNameAnalysis(ast, sourceFile, stringTable)
//            doTypeAnalysis(ast, sourceFile)
            doSemanticAnalysis(ast, sourceFile, stringTable)
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
                    public static void main(String[] args) {}

                    public void b(int f) {}
                }
            """.trimIndent()
        }
    }

    @Test
    fun testBasicFieldAccess() {
        check(true) {
            """
                class Test {
                    public int f;
                    public void a() {
                        Test test = new Test();
                        test.f = 44;
                    }
                    public static void main(String[] args) {}
                }

            """.trimIndent()
        }
    }

    @Test
    fun testStrange() {
        check(true) {
            """
                class Test {
                    public static void main(String[] args) {}
                    public void a(int someVal) {
                        int idx = 5;
                        int[] arr = new int[5];
                        if (arr[idx] == 46) {
                            System.out.write(arr[idx]);
                        } else {
                            this.a(arr[idx]);
                        }
                    }
                }

            """.trimIndent()
        }
    }

    @Test
    fun debugFib() {
        check(true) {
            """
            class Fibonacci {
                public int fib(int n) {
                    int a = 0;
                    int b = 1;
                    while (n > 0) {
                        int c = a + b;
                        a = b;
                        b = c;
                        n = n - 1;
                    }
                    return a;
                }
                public static void main(String[] args) {
                    int n = 4;
                    int x = new Fibonacci().fib(n);
                    Fibonacci a = new Fibonacci();
                    a.fib(new Fibonacci().fib(n));
                    a.fib(n);
                    System.out.println(a.fib(n));
                }
            }

            """.trimIndent()
        }
    }

//    @Test
//    fun testUnknownMethod() {
//        check(false) {
//            """
//                class Test {
//                    public void main(int i) {
//                        test();
//                    }
//                }
//            """.trimIndent()
//        }
//    }
//
//    @Test
//    fun testUnknownMethodOtherClass() {
//        check(false) {
//            """
//                class Foo {
//                    public int bar() {}
//                }
//
//                class Test {
//                    public Foo foo;
//                    public void main(int i) {
//                        foo.test();
//                    }
//                }
//            """.trimIndent()
//        }
//    }
}
