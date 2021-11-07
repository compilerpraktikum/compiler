package edu.kit.compiler.parser

import edu.kit.compiler.ast.AST
import edu.kit.compiler.utils.TestUtils.expectNode
import org.junit.jupiter.api.Test

@ExperimentalStdlibApi
internal class ExpressionParseTest {
    private fun expectAst(input: String, expectedAST: AST.Expression) =
        expectNode(input, expectedAST) { parseExpression() }

    @Test
    fun testParseLiteral() = expectAst("1", AST.LiteralExpression("1"))

    @Test
    fun testParseIdentInExpr() = expectAst("myident", AST.IdentifierExpression("myident"))

    @Test
    fun testParseLocalInvocation() =
        expectAst("myident()", AST.MethodInvocationExpression(null, "myident", listOf()))

    @Test
    fun testParseLocalInvocationArg() = expectAst(
        "myident(1)",
        AST.MethodInvocationExpression(null, "myident", listOf(AST.LiteralExpression("1")))
    )

    @Test
    fun testParseLocalInvocation3Arg() = expectAst(
        "myident(1,2,2)",
        AST.MethodInvocationExpression(
            null,
            "myident",
            listOf(AST.LiteralExpression("1"), AST.LiteralExpression("2"), AST.LiteralExpression("2"))
        )
    )

    @Test
    fun testLeftAssoc() {
        expectAst(
            "1+2+3",
            AST.BinaryExpression(
                AST.BinaryExpression(
                    AST.LiteralExpression("1"),
                    AST.LiteralExpression("2"),
                    AST.BinaryExpression.Operation.ADDITION
                ),
                AST.LiteralExpression("3"),
                AST.BinaryExpression.Operation.ADDITION
            )
        )
    }

    @Test
    fun testMultipleAssoc() {
        expectAst(
            "1+2*3",
            AST.BinaryExpression(
                AST.LiteralExpression("1"),
                AST.BinaryExpression(
                    AST.LiteralExpression("2"),
                    AST.LiteralExpression("3"),
                    AST.BinaryExpression.Operation.MULTIPLICATION
                ),
                AST.BinaryExpression.Operation.ADDITION
            )
        )
    }

    @Test
    fun testMultipleNested() {
        expectAst(
            "1+2*3+4",
            AST.BinaryExpression(
                AST.BinaryExpression(
                    AST.LiteralExpression("1"),
                    AST.BinaryExpression(
                        AST.LiteralExpression("2"),
                        AST.LiteralExpression("3"),
                        AST.BinaryExpression.Operation.MULTIPLICATION
                    ),
                    AST.BinaryExpression.Operation.ADDITION
                ),
                AST.LiteralExpression("4"),
                AST.BinaryExpression.Operation.ADDITION
            )
        )
    }

    @Test
    fun testAssignmentAssociativity() {
        expectAst(
            "2 = 3",
            AST.BinaryExpression(
                AST.LiteralExpression("2"),
                AST.LiteralExpression("3"),
                AST.BinaryExpression.Operation.ASSIGNMENT
            )
        )
    }

    @Test
    fun testAssignmentAssociativityMultiple() {
        expectAst(
            "2 = 3 = 4",
            AST.BinaryExpression(
                AST.LiteralExpression("2"),
                AST.BinaryExpression(
                    AST.LiteralExpression("3"),
                    AST.LiteralExpression("4"),
                    AST.BinaryExpression.Operation.ASSIGNMENT
                ),
                AST.BinaryExpression.Operation.ASSIGNMENT
            )
        )
    }

    @Test
    fun testDifferenceAssociativity() {
        expectAst(
            "2 + (3 = 2 = 2) * 3 + 4",
            AST.BinaryExpression(
                AST.BinaryExpression(
                    AST.LiteralExpression("2"),
                    AST.BinaryExpression(
                        AST.BinaryExpression(
                            AST.LiteralExpression("3"),
                            AST.BinaryExpression(
                                AST.LiteralExpression("2"),
                                AST.LiteralExpression("2"),
                                AST.BinaryExpression.Operation.ASSIGNMENT
                            ),
                            AST.BinaryExpression.Operation.ASSIGNMENT
                        ),
                        AST.LiteralExpression("3"),
                        AST.BinaryExpression.Operation.MULTIPLICATION
                    ),
                    AST.BinaryExpression.Operation.ADDITION
                ),
                AST.LiteralExpression("4"),
                AST.BinaryExpression.Operation.ADDITION
            )
        )
    }
}
