package edu.kit.compiler.parser

import edu.kit.compiler.ast.AST
import edu.kit.compiler.utils.TestUtils.expectNode
import edu.kit.compiler.utils.toSymbol
import edu.kit.compiler.wrapper.Of
import edu.kit.compiler.wrapper.wrappers.Lenient
import edu.kit.compiler.wrapper.wrappers.wrapValid
import org.junit.jupiter.api.Test

@ExperimentalStdlibApi
internal class ExpressionParseTest {
    private val emptyAnchorSet = anchorSetOf().intoUnion()

    private fun expectAst(input: String, expectedAST: Lenient<AST.Expression<Lenient<Of>, Lenient<Of>>>) =
        expectNode(input, expectedAST) { parseExpression(anc = emptyAnchorSet) }

    @Test
    fun testParseLiteral() = expectAst("1", AST.LiteralInt("1").wrapValid())

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
        AST.MethodInvocationExpression(null, "myident".toSymbol(), listOf(AST.LiteralInt("1").wrapValid())).wrapValid()
    )

    @Test
    fun testParseLocalInvocation3Arg() = expectAst(
        "myident(1,2,2)",
        AST.MethodInvocationExpression(
            null,
            "myident".toSymbol(),
            listOf(AST.LiteralInt("1"), AST.LiteralInt("2"), AST.LiteralInt("2")).map { it.wrapValid() }
        ).wrapValid()
    )

    @Test
    fun testLeftAssoc() {
        expectAst(
            "1+2+3",
            AST.BinaryExpression(
                AST.BinaryExpression(
                    AST.LiteralInt("1").wrapValid(),
                    AST.LiteralInt("2").wrapValid(),
                    AST.BinaryExpression.Operation.ADDITION
                ).wrapValid(),
                AST.LiteralInt("3").wrapValid(),
                AST.BinaryExpression.Operation.ADDITION
            ).wrapValid()
        )
    }

    @Test
    fun testMultipleAssoc() {
        expectAst(
            "1+2*3",
            AST.BinaryExpression(
                AST.LiteralInt("1").wrapValid(),
                AST.BinaryExpression(
                    AST.LiteralInt("2").wrapValid(),
                    AST.LiteralInt("3").wrapValid(),
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
                    AST.LiteralInt("1").wrapValid(),
                    AST.BinaryExpression(
                        AST.LiteralInt("2").wrapValid(),
                        AST.LiteralInt("3").wrapValid(),
                        AST.BinaryExpression.Operation.MULTIPLICATION
                    ).wrapValid(),
                    AST.BinaryExpression.Operation.ADDITION
                ).wrapValid(),
                AST.LiteralInt("4").wrapValid(),
                AST.BinaryExpression.Operation.ADDITION
            ).wrapValid()
        )
    }

    @Test
    fun testAssignmentAssociativity() {
        expectAst(
            "2 = 3",
            AST.BinaryExpression(
                AST.LiteralInt("2").wrapValid(),
                AST.LiteralInt("3").wrapValid(),
                AST.BinaryExpression.Operation.ASSIGNMENT
            ).wrapValid()
        )
    }

    @Test
    fun testAssignmentAssociativityMultiple() {
        expectAst(
            "2 = 3 = 4",
            AST.BinaryExpression(
                AST.LiteralInt("2").wrapValid(),
                AST.BinaryExpression(
                    AST.LiteralInt("3").wrapValid(),
                    AST.LiteralInt("4").wrapValid(),
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
                    AST.LiteralInt("2").wrapValid(),
                    AST.BinaryExpression(
                        AST.BinaryExpression(
                            AST.LiteralInt("3").wrapValid(),
                            AST.BinaryExpression(
                                AST.LiteralInt("2").wrapValid(),
                                AST.LiteralInt("2").wrapValid(),
                                AST.BinaryExpression.Operation.ASSIGNMENT
                            ).wrapValid(),
                            AST.BinaryExpression.Operation.ASSIGNMENT
                        ).wrapValid(),
                        AST.LiteralInt("3").wrapValid(),
                        AST.BinaryExpression.Operation.MULTIPLICATION
                    ).wrapValid(),
                    AST.BinaryExpression.Operation.ADDITION
                ).wrapValid(),
                AST.LiteralInt("4").wrapValid(),
                AST.BinaryExpression.Operation.ADDITION
            ).wrapValid()
        )
    }
}
