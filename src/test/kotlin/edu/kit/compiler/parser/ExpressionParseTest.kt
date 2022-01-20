package edu.kit.compiler.parser

import edu.kit.compiler.ast.AST
import edu.kit.compiler.ast.Parsed
import edu.kit.compiler.ast.expressionOf
import edu.kit.compiler.ast.wrapMockValid
import edu.kit.compiler.utils.emptyAnchorSet
import edu.kit.compiler.utils.expectNode
import edu.kit.compiler.utils.toSymbol
import kotlin.test.Test

@ExperimentalStdlibApi
internal class ExpressionParseTest {

    private fun expectAst(input: String, expectedAST: Parsed<AST.Expression>) =
        expectNode(input, expectedAST) { parseExpression(anc = emptyAnchorSet) }

    @Test
    fun testParseLiteral() = expectAst("1", AST.LiteralExpression.Integer("1", false).wrapMockValid())

    @Test
    fun testParseIdentInExpr() =
        expectAst("myident", AST.IdentifierExpression("myident".toSymbol().wrapMockValid()).wrapMockValid())

    @Test
    fun testParseLocalInvocation() =
        expectAst(
            "myident()",
            AST.MethodInvocationExpression(null, "myident".toSymbol().wrapMockValid(), listOf()).wrapMockValid()
        )

    @Test
    fun testParseLocalInvocationArg() = expectAst(
        "myident(1)",
        AST.MethodInvocationExpression(
            null,
            "myident".toSymbol().wrapMockValid(),
            listOf(AST.LiteralExpression.Integer("1", false).wrapMockValid())
        ).wrapMockValid()
    )

    @Test
    fun testParseLocalInvocation3Arg() = expectAst(
        "myident(1,2,2)",
        AST.MethodInvocationExpression(
            null,
            "myident".toSymbol().wrapMockValid(),
            listOf(
                AST.LiteralExpression.Integer("1", false),
                AST.LiteralExpression.Integer("2", false),
                AST.LiteralExpression.Integer("2", false)
            ).map { it.wrapMockValid() }
        ).wrapMockValid()
    )

    @Test
    fun testLeftAssoc() {
        expectAst(
            "1+2+3",
            AST.BinaryExpression(
                AST.BinaryExpression(
                    AST.LiteralExpression.Integer("1", false).wrapMockValid(),
                    AST.LiteralExpression.Integer("2", false).wrapMockValid(),
                    AST.BinaryExpression.Operation.ADDITION
                ).wrapMockValid(),
                AST.LiteralExpression.Integer("3", false).wrapMockValid(),
                AST.BinaryExpression.Operation.ADDITION
            ).wrapMockValid()
        )
    }

    @Test
    fun testMultipleAssoc() {
        expectAst(
            "1+2*3",
            AST.BinaryExpression(
                AST.LiteralExpression.Integer("1", false).wrapMockValid(),
                AST.BinaryExpression(
                    AST.LiteralExpression.Integer("2", false).wrapMockValid(),
                    AST.LiteralExpression.Integer("3", false).wrapMockValid(),
                    AST.BinaryExpression.Operation.MULTIPLICATION
                ).wrapMockValid(),
                AST.BinaryExpression.Operation.ADDITION
            ).wrapMockValid()
        )
    }

    @Test
    fun testMultipleNested() {
        expectAst(
            "1+2*3+4",
            AST.BinaryExpression(
                AST.BinaryExpression(
                    AST.LiteralExpression.Integer("1", false).wrapMockValid(),
                    AST.BinaryExpression(
                        AST.LiteralExpression.Integer("2", false).wrapMockValid(),
                        AST.LiteralExpression.Integer("3", false).wrapMockValid(),
                        AST.BinaryExpression.Operation.MULTIPLICATION
                    ).wrapMockValid(),
                    AST.BinaryExpression.Operation.ADDITION
                ).wrapMockValid(),
                AST.LiteralExpression.Integer("4", false).wrapMockValid(),
                AST.BinaryExpression.Operation.ADDITION
            ).wrapMockValid()
        )
    }

    @Test
    fun testAssignmentAssociativity() {
        expectAst(
            "2 = 3",
            AST.BinaryExpression(
                AST.LiteralExpression.Integer("2", false).wrapMockValid(),
                AST.LiteralExpression.Integer("3", false).wrapMockValid(),
                AST.BinaryExpression.Operation.ASSIGNMENT
            ).wrapMockValid()
        )
    }

    @Test
    fun testAssignmentAssociativityMultiple() {
        expectAst(
            "2 = 3 = 4",
            AST.BinaryExpression(
                AST.LiteralExpression.Integer("2", false).wrapMockValid(),
                AST.BinaryExpression(
                    AST.LiteralExpression.Integer("3", false).wrapMockValid(),
                    AST.LiteralExpression.Integer("4", false).wrapMockValid(),
                    AST.BinaryExpression.Operation.ASSIGNMENT
                ).wrapMockValid(),
                AST.BinaryExpression.Operation.ASSIGNMENT
            ).wrapMockValid()
        )
    }

    @Test
    fun testDifferenceAssociativity() {
        expectAst(
            "2 + (3 = 2 = 2) * 3 + 4",
            AST.BinaryExpression(
                AST.BinaryExpression(
                    AST.LiteralExpression.Integer("2", false).wrapMockValid(),
                    AST.BinaryExpression(
                        AST.BinaryExpression(
                            AST.LiteralExpression.Integer("3", false).wrapMockValid(),
                            AST.BinaryExpression(
                                AST.LiteralExpression.Integer("2", false).wrapMockValid(),
                                AST.LiteralExpression.Integer("2", false).wrapMockValid(),
                                AST.BinaryExpression.Operation.ASSIGNMENT
                            ).wrapMockValid(),
                            AST.BinaryExpression.Operation.ASSIGNMENT
                        ).wrapMockValid(),
                        AST.LiteralExpression.Integer("3", false).wrapMockValid(),
                        AST.BinaryExpression.Operation.MULTIPLICATION
                    ).wrapMockValid(),
                    AST.BinaryExpression.Operation.ADDITION
                ).wrapMockValid(),
                AST.LiteralExpression.Integer("4", false).wrapMockValid(),
                AST.BinaryExpression.Operation.ADDITION
            ).wrapMockValid()
        )
    }

    @Test
    fun testUnaryMinus() {
        expectAst(
            "123",
            expressionOf {
                literal("123")
            }
        )
        expectAst(
            "-123",
            expressionOf {
                literal("-123")
            }
        )
        expectAst(
            "-(123)",
            expressionOf {
                unOp(AST.UnaryExpression.Operation.MINUS) {
                    literal("123")
                }
            }
        )
        expectAst(
            "123.test()",
            expressionOf {
                invoke({ literal("123") }, "test")
            }
        )
        expectAst(
            "-123.test()",
            expressionOf {
                unOp(AST.UnaryExpression.Operation.MINUS) {
                    invoke({ literal("123") }, "test")
                }
            }
        )
        expectAst(
            "-(123).test()",
            expressionOf {
                unOp(AST.UnaryExpression.Operation.MINUS) {
                    invoke({ literal("123") }, "test")
                }
            }
        )
        expectAst(
            "(-123).test()",
            expressionOf {
                invoke(
                    { literal("-123") },
                    "test"
                )
            }
        )
        expectAst(
            "(-(123)).test()",
            expressionOf {
                invoke(
                    { unOp(AST.UnaryExpression.Operation.MINUS) { literal("123") } },
                    "test"
                )
            }
        )
    }
}
