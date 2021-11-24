package edu.kit.compiler.parser

import edu.kit.compiler.ast.AST
import edu.kit.compiler.ast.wrapMockValid
import edu.kit.compiler.utils.TestUtils.expectNode
import edu.kit.compiler.utils.toSymbol
import edu.kit.compiler.wrapper.wrappers.Parsed
import org.junit.jupiter.api.Test

@ExperimentalStdlibApi
internal class ExpressionParseTest {
    private val emptyAnchorSet = anchorSetOf().intoUnion()

    private fun expectAst(input: String, expectedAST: Parsed<AST.Expression>) =
        expectNode(input, expectedAST) { parseExpression(anc = emptyAnchorSet) }

    @Test
    fun testParseLiteral() = expectAst("1", AST.LiteralExpression("1").wrapMockValid())

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
            listOf(AST.LiteralExpression("1").wrapMockValid())
        ).wrapMockValid()
    )

    @Test
    fun testParseLocalInvocation3Arg() = expectAst(
        "myident(1,2,2)",
        AST.MethodInvocationExpression(
            null,
            "myident".toSymbol().wrapMockValid(),
            listOf(
                AST.LiteralExpression("1"),
                AST.LiteralExpression("2"),
                AST.LiteralExpression("2")
            ).map { it.wrapMockValid() }
        ).wrapMockValid()
    )

    @Test
    fun testLeftAssoc() {
        expectAst(
            "1+2+3",
            AST.BinaryExpression(
                AST.BinaryExpression(
                    AST.LiteralExpression("1").wrapMockValid(),
                    AST.LiteralExpression("2").wrapMockValid(),
                    AST.BinaryExpression.Operation.ADDITION
                ).wrapMockValid(),
                AST.LiteralExpression("3").wrapMockValid(),
                AST.BinaryExpression.Operation.ADDITION
            ).wrapMockValid()
        )
    }

    @Test
    fun testMultipleAssoc() {
        expectAst(
            "1+2*3",
            AST.BinaryExpression(
                AST.LiteralExpression("1").wrapMockValid(),
                AST.BinaryExpression(
                    AST.LiteralExpression("2").wrapMockValid(),
                    AST.LiteralExpression("3").wrapMockValid(),
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
                    AST.LiteralExpression("1").wrapMockValid(),
                    AST.BinaryExpression(
                        AST.LiteralExpression("2").wrapMockValid(),
                        AST.LiteralExpression("3").wrapMockValid(),
                        AST.BinaryExpression.Operation.MULTIPLICATION
                    ).wrapMockValid(),
                    AST.BinaryExpression.Operation.ADDITION
                ).wrapMockValid(),
                AST.LiteralExpression("4").wrapMockValid(),
                AST.BinaryExpression.Operation.ADDITION
            ).wrapMockValid()
        )
    }

    @Test
    fun testAssignmentAssociativity() {
        expectAst(
            "2 = 3",
            AST.BinaryExpression(
                AST.LiteralExpression("2").wrapMockValid(),
                AST.LiteralExpression("3").wrapMockValid(),
                AST.BinaryExpression.Operation.ASSIGNMENT
            ).wrapMockValid()
        )
    }

    @Test
    fun testAssignmentAssociativityMultiple() {
        expectAst(
            "2 = 3 = 4",
            AST.BinaryExpression(
                AST.LiteralExpression("2").wrapMockValid(),
                AST.BinaryExpression(
                    AST.LiteralExpression("3").wrapMockValid(),
                    AST.LiteralExpression("4").wrapMockValid(),
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
                    AST.LiteralExpression("2").wrapMockValid(),
                    AST.BinaryExpression(
                        AST.BinaryExpression(
                            AST.LiteralExpression("3").wrapMockValid(),
                            AST.BinaryExpression(
                                AST.LiteralExpression("2").wrapMockValid(),
                                AST.LiteralExpression("2").wrapMockValid(),
                                AST.BinaryExpression.Operation.ASSIGNMENT
                            ).wrapMockValid(),
                            AST.BinaryExpression.Operation.ASSIGNMENT
                        ).wrapMockValid(),
                        AST.LiteralExpression("3").wrapMockValid(),
                        AST.BinaryExpression.Operation.MULTIPLICATION
                    ).wrapMockValid(),
                    AST.BinaryExpression.Operation.ADDITION
                ).wrapMockValid(),
                AST.LiteralExpression("4").wrapMockValid(),
                AST.BinaryExpression.Operation.ADDITION
            ).wrapMockValid()
        )
    }
}
