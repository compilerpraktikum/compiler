package edu.kit.compiler.parser

import edu.kit.compiler.ast.AST
import edu.kit.compiler.utils.TestUtils.expectNode
import edu.kit.compiler.utils.toSymbol
import edu.kit.compiler.wrapper.Lenient
import edu.kit.compiler.wrapper.Of
import edu.kit.compiler.wrapper.wrapValid
import org.junit.jupiter.api.Test

@ExperimentalStdlibApi
internal class ExpressionParseTest {
    private val emptyAnchorSet = anchorSetOf().intoUnion()

    private fun expectAst(input: String, expectedAST: Lenient<AST.Expression<Lenient<Of>, Lenient<Of>>>) =
        expectNode(input, expectedAST) { parseExpression(anc = emptyAnchorSet) }

    @Test
    fun testParseLiteral() = expectAst("1", AST.LiteralExpression("1").wrapValid())

    @Test
    fun testParseIdentInExpr() = expectAst("myident", AST.IdentifierExpression("myident".toSymbol()).wrapValid())

    @Test
    fun testParseLocalInvocation() =
        expectAst(
            "myident()",
            AST.MethodInvocationExpression<Lenient<Of>, Lenient<Of>>(null, "myident".toSymbol(), listOf()).wrapValid()
        )

    @Test
    fun testParseLocalInvocationArg() = expectAst(
        "myident(1)",
        AST.MethodInvocationExpression(null, "myident".toSymbol(), listOf(AST.LiteralExpression("1").wrapValid())).wrapValid()
    )

    @Test
    fun testParseLocalInvocation3Arg() = expectAst(
        "myident(1,2,2)",
        AST.MethodInvocationExpression(
            null,
            "myident".toSymbol(),
            listOf(AST.LiteralExpression("1"), AST.LiteralExpression("2"), AST.LiteralExpression("2")).map { it.wrapValid() }
        ).wrapValid()
    )

    @Test
    fun testLeftAssoc() {
        expectAst(
            "1+2+3",
            AST.BinaryExpression(
                AST.BinaryExpression(
                    AST.LiteralExpression("1").wrapValid(),
                    AST.LiteralExpression("2").wrapValid(),
                    AST.BinaryExpression.Operation.ADDITION
                ).wrapValid(),
                AST.LiteralExpression("3").wrapValid(),
                AST.BinaryExpression.Operation.ADDITION
            ).wrapValid()
        )
    }

    @Test
    fun testMultipleAssoc() {
        expectAst(
            "1+2*3",
            AST.BinaryExpression(
                AST.LiteralExpression("1").wrapValid(),
                AST.BinaryExpression(
                    AST.LiteralExpression("2").wrapValid(),
                    AST.LiteralExpression("3").wrapValid(),
                    AST.BinaryExpression.Operation.MULTIPLICATION
                ).wrapValid(),
                AST.BinaryExpression.Operation.ADDITION
            ).wrapValid()
        )
    }

    @Test
    fun testMultipleNested() {
        expectAst(
            "1+2*3+4",
            AST.BinaryExpression(
                AST.BinaryExpression(
                    AST.LiteralExpression("1").wrapValid(),
                    AST.BinaryExpression(
                        AST.LiteralExpression("2").wrapValid(),
                        AST.LiteralExpression("3").wrapValid(),
                        AST.BinaryExpression.Operation.MULTIPLICATION
                    ).wrapValid(),
                    AST.BinaryExpression.Operation.ADDITION
                ).wrapValid(),
                AST.LiteralExpression("4").wrapValid(),
                AST.BinaryExpression.Operation.ADDITION
            ).wrapValid()
        )
    }

    @Test
    fun testAssignmentAssociativity() {
        expectAst(
            "2 = 3",
            AST.BinaryExpression(
                AST.LiteralExpression("2").wrapValid(),
                AST.LiteralExpression("3").wrapValid(),
                AST.BinaryExpression.Operation.ASSIGNMENT
            ).wrapValid()
        )
    }

    @Test
    fun testAssignmentAssociativityMultiple() {
        expectAst(
            "2 = 3 = 4",
            AST.BinaryExpression(
                AST.LiteralExpression("2").wrapValid(),
                AST.BinaryExpression(
                    AST.LiteralExpression("3").wrapValid(),
                    AST.LiteralExpression("4").wrapValid(),
                    AST.BinaryExpression.Operation.ASSIGNMENT
                ).wrapValid(),
                AST.BinaryExpression.Operation.ASSIGNMENT
            ).wrapValid()
        )
    }

    @Test
    fun testDifferenceAssociativity() {
        expectAst(
            "2 + (3 = 2 = 2) * 3 + 4",
            AST.BinaryExpression(
                AST.BinaryExpression(
                    AST.LiteralExpression("2").wrapValid(),
                    AST.BinaryExpression(
                        AST.BinaryExpression(
                            AST.LiteralExpression("3").wrapValid(),
                            AST.BinaryExpression(
                                AST.LiteralExpression("2").wrapValid(),
                                AST.LiteralExpression("2").wrapValid(),
                                AST.BinaryExpression.Operation.ASSIGNMENT
                            ).wrapValid(),
                            AST.BinaryExpression.Operation.ASSIGNMENT
                        ).wrapValid(),
                        AST.LiteralExpression("3").wrapValid(),
                        AST.BinaryExpression.Operation.MULTIPLICATION
                    ).wrapValid(),
                    AST.BinaryExpression.Operation.ADDITION
                ).wrapValid(),
                AST.LiteralExpression("4").wrapValid(),
                AST.BinaryExpression.Operation.ADDITION
            ).wrapValid()
        )
    }
}
