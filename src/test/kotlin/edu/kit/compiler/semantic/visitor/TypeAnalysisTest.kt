package edu.kit.compiler.semantic.visitor

import edu.kit.compiler.ast.validate
import edu.kit.compiler.parser.Parser
import edu.kit.compiler.semantic.doNameAnalysis
import edu.kit.compiler.utils.createLexer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

class TypeAnalysisTest {

    private fun check(shouldSucceed: Boolean, annotations: List<String>? = null, input: () -> String) {
        val (lexer, sourceFile, stringTable) = createLexer(input())
        val parser = Parser(sourceFile, lexer.tokens())
        val ast = parser.parse().validate()
        if (ast == null) {
            sourceFile.printAnnotations()
            fail("failed to parse program")
        }

        doNameAnalysis(ast, sourceFile, stringTable)
        ast.accept(TypeAnalysisVisitor(sourceFile))

        sourceFile.printAnnotations()

        assertEquals(shouldSucceed, !sourceFile.hasError)
        if (annotations != null) {
            assertEquals(annotations.size, sourceFile.getAnnotations().count())
            annotations.asSequence()
                .zip(sourceFile.getAnnotations())
                .forEachIndexed { index, (expected, actual) ->
                    assertEquals(expected, actual.message, "annotation #$index differs")
                }
        }
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
    fun testCallSystemOutInvalidArgument() =
        check(false, listOf("incompatible types: expected `int`, but got `A`")) {
            """
            class A {
                public static void main(String[] args) {
                    System.out.println(new A());
                }
            }
        """
        }

    @Test
    fun testReturnMethodReturnValue() =
        check(true) {
            """
            class A {
                public int bla(A sth) { return 2; }
                public static void main(String[] args) {
                    System.out.println(new A().bla(new A()));
                }
            }
        """
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
    fun testThisInBinopInCondition() {
        check(false) {
            """
                class Test {
                    public static void main(String[] args) {}
                    public void a(int someVal) {
                        if (this*2 == 10) {
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

    @Test
    fun testReturns() {
        check(true) {
            """
            class Fibonacci {
                public int fib(int n) {
                    int a = 0;
                    int b = 1;
                    if (true) {
                        if (false) {
                            return 0;
                        } else {
                            if (false) {
                                return 2;
                            } else {
                                return 3;
                            }
                        }
                    } else {
                        return 3;
                    }
                    while (n > 0) {
                        int c = a + b;
                        a = b;
                        b = c;
                        n = n - 1;
                    }
                }
                public static void main(String[] args) {}
            }

            """.trimIndent()
        }
    }

    @Test
    fun testDeeplyNested() {
        check(false, listOf("array access on non-array type `int`")) {
            """
            class Test {
                public static void main(String[] args) {}

                public int[] arr;
                public void test(Test t) {
                    arr[0][0].foo();
                }
            }
            """.trimIndent()
        }
        check(false, listOf("unknown class `Foo`")) {
            """
            class Test {
                public static void main(String[] args) {}

                public Foo[] arr;
                public void test(Test t) {
                    arr[0].foo();
                }
            }
            """.trimIndent()
        }
    }

    @Test
    fun testInvalidTypeInIfCondition() {
        check(false) {
            """
            class Test {
                public static void main(String[] args) {}

                public Test get() { return null; }
                public void test() {
                    if (get()) {
                        /* something */
                    }
                }
            }
            """.trimIndent()
        }
    }

    @Test
    fun testErrorMessages() {
        check(
            false,
            listOf(
                "incompatible types: expected `int`, but got `boolean`"
            )
        ) {
            """
            class Test {
                public static void main(String[] args) {}

                public void test() {
                    int a;
                    a = !a;
                }
            }
            """.trimIndent()
        }
        check(
            false,
            listOf(
                "method `bar` requires 1 argument(s), but got 2",
                "incompatible types: expected `boolean`, but got `int`"
            )
        ) {
            """
            class Test {
                public static void main(String[] args) {}

                public int bar(int i) { return 1; }
                public void test() {
                    Test foo;
                    boolean b = foo.bar(1, 2);
                }
            }
            """.trimIndent()
        }
        check(
            false,
            listOf(
                "method `bar` requires 1 argument(s), but got 2",
                "incompatible types: expected `boolean`, but got `int`"
            )
        ) {
            """
            class Test {
                public static void main(String[] args) {}

                public int bar(int i) { return 1; }
                public void test() {
                    Test foo;
                    if (foo.bar(1, 2)) {
                        /* something */
                    }
                }
            }
            """.trimIndent()
        }
    }

    @Test
    fun testMethodCallOnNonClassTarget() {
        check(false) {
            """
                class Test {
                    public void test() {
                        (5).foo();
                    }
                }
            """.trimIndent()
        }
    }

    @Test
    fun testArrayAccess() {
        check(false, listOf("incompatible types: expected `Bait[]`, but got `int[]`")) {
            """
                class Bait {
                    public int[] Z;

                    public static void main(String[] args) {

                    }

                    public Bait test(int x) {
                        return Z[3];
                    }
                }
            """.trimIndent()
        }
    }

    @Test
    fun testReturnValueFromVoidFunction() {
        check(false, listOf("cannot return value from method with return type `void`")) {
            """
                class Bait {
                    public void test() {
                        return 1;
                    }
                    public static void main(String[] args) {}
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
