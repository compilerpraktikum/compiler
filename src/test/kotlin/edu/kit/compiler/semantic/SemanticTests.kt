package edu.kit.compiler.semantic

import edu.kit.compiler.lex.SourceFile
import edu.kit.compiler.parser.Parser
import edu.kit.compiler.semantic.visitor.AbstractVisitor
import edu.kit.compiler.semantic.visitor.ConstantBoundariesChecker
import edu.kit.compiler.semantic.visitor.MainMethodCounter
import edu.kit.compiler.semantic.visitor.MainMethodVerifier
import edu.kit.compiler.semantic.visitor.accept
import edu.kit.compiler.utils.createLexer
import edu.kit.compiler.wrapper.wrappers.validate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

/**
 * Some manually implemented semantic tests
 */
internal class SemanticTests {

    @Test
    fun testMain() {
        testCheck(
            """
            class A {
                public static void main(String[] args) {}
            }

            class B {
                public static void main(String[] args) {}
            }
            """,
            ::MainMethodCounter
        )
    }

    @Test
    fun testMainSucceed() {
        testCheck(
            """class A { public static void main(String[] args) {} }""",
            ::MainMethodCounter,
            shouldSucceed = true
        )
    }

    @Test
    fun testMainVerifier() {
        testCheck(
            """
            class A {
                public static void main2(String[] args) {}
            }
            """,
            ::MainMethodVerifier
        )
        testCheck(
            """
            class B {
                public static void main(String args) {}
            }
            """,
            ::MainMethodVerifier
        )
        testCheck(
            """
            class B {
                public static void main(String[] args) {}
            }
            """,
            ::MainMethodVerifier,
            shouldSucceed = true
        )
    }

    /**
     * Additional test case for correctness of integer boundaries. Related to [regressionMinInteger], to verify fixes
     * for that bug do not affect correctness.
     */
    @Test
    fun testIntegerBoundaries() {
        testCheck(
            """
            class A {
                public static void main2(String[] args) { int x = 2147483648; }
            }
            """,
            ::ConstantBoundariesChecker,
            shouldSucceed = false
        )
        testCheck(
            """
            class B {
                public static void main(String[] args) { int x = -2147483648; }
            }
            """,
            ::ConstantBoundariesChecker,
            shouldSucceed = true
        )
        testCheck(
            """
            class B {
                public static void main(String[] args) { int x = 2147483647; }
            }
            """,
            ::ConstantBoundariesChecker,
            shouldSucceed = true
        )
    }

    /**
     * Regression test for a bug where a unary minus would propagate too far through the AST when integer literals were
     * tested.
     */
    @Test
    fun regressionMinInteger() {
        testCheck(
            """
            class B {
                public static void main(String[] args) { int x = -new B().foo(2147483648); }

                public int foo(int x) { return 0; }
            }
            """,
            ::ConstantBoundariesChecker,
        )
    }

    @Test
    fun testMinInteger() {
        testCheck(
            """
                class Test {
                    public void test() {
                        int x = 2147483648;
                    }
                }
            """.trimIndent(),
            ::ConstantBoundariesChecker,
            shouldSucceed = false
        )
        testCheck(
            """
                class Test {
                    public void test() {
                        int x = -2147483648;
                    }
                }
            """.trimIndent(),
            ::ConstantBoundariesChecker,
            shouldSucceed = true
        )
        testCheck(
            """
                class Test {
                    public void test() {
                        int x = - -2147483648;
                    }
                }
            """.trimIndent(),
            ::ConstantBoundariesChecker,
            shouldSucceed = true
        )
    }

    @OptIn(ExperimentalStdlibApi::class)
    private fun testCheck(input: String, acceptor: (SourceFile) -> AbstractVisitor, shouldSucceed: Boolean = false) {
        val (lexer, sourceFile) = createLexer(input, 3)
        val parser = Parser(sourceFile, lexer.tokens())
        val ast = parser.parse().validate() ?: run {
            sourceFile.printAnnotations()
            fail("failed to parse source")
        }
        ast.accept(acceptor(sourceFile))

        sourceFile.printAnnotations()

        assertEquals(shouldSucceed, !sourceFile.hasError)
    }
}
