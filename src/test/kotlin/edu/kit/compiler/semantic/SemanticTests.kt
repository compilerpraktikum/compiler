package edu.kit.compiler.semantic

import edu.kit.compiler.lex.SourceFile
import edu.kit.compiler.parser.Parser
import edu.kit.compiler.semantic.visitor.AbstractVisitor
import edu.kit.compiler.semantic.visitor.MainMethodCounter
import edu.kit.compiler.semantic.visitor.MainMethodVerifier
import edu.kit.compiler.semantic.visitor.accept
import edu.kit.compiler.utils.createLexer
import edu.kit.compiler.wrapper.wrappers.validate
import kotlin.test.Test
import kotlin.test.assertEquals

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

    @OptIn(ExperimentalStdlibApi::class)
    private fun testCheck(input: String, acceptor: (SourceFile) -> AbstractVisitor, shouldSucceed: Boolean = false) {
        val (lexer, sourceFile) = createLexer(input, 4)
        val parser = Parser(sourceFile, lexer.tokens())
        val ast = parser.parse().validate()!!
        ast.accept(acceptor(sourceFile))

        sourceFile.printAnnotations()

        assertEquals(shouldSucceed, !sourceFile.hasError)
    }
}
