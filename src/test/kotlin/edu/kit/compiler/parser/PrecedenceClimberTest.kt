package edu.kit.compiler.parser

import edu.kit.compiler.Token
import edu.kit.compiler.ast.AST
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import edu.kit.compiler.parser.PrecedenceClimber.*
import edu.kit.compiler.utils.setupLexer

internal class PrecedenceClimberTest {

    private fun expectAst(input: String, expectedAST: AST.Expression) {
        val lexer = setupLexer(input)
        val res = runBlocking {
            Parser(lexer.tokens()).also { it.initialize() }.parseExpr()
        }
        assertEquals(expectedAST, res)
    }

    private infix fun List<Token>.shouldParseAs(expected: () -> PrecedenceClimber.Expr) {
        val res = runBlocking {
            PrecedenceClimber(this@shouldParseAs.asFlow()).parse()
        }
        assertEquals(expected(), res)
    }

    @Test
    fun testComputeExpr() {
        val tokens = listOf(
            Token.Literal("1"),
            Token.Operator(type = Token.Operator.Type.Plus),
            Token.Literal("3"),
            Token.Eof
        ) shouldParseAs {
            Expr.BinOp(Expr.Literal(1), Expr.Literal(3), BinOp.Add)
        }
    }

    @Test
    fun testLeftAssoc() {
        expectAst("1+2+3",
            AST.BinaryExpression(AST.BinaryExpression(AST.LiteralExpression(1),
                AST.LiteralExpression(2), AST.BinaryExpression.Operation.ADDITION),
                AST.LiteralExpression(3),
                AST.BinaryExpression.Operation.ADDITION)
        )
    }

    @Test
    fun testMultipleAssoc() {
        expectAst("1+2*3",
            AST.BinaryExpression(AST.LiteralExpression(1),
                AST.BinaryExpression(AST.LiteralExpression(2),
                    AST.LiteralExpression(3),
                    AST.BinaryExpression.Operation.MULTIPLICATION),
                AST.BinaryExpression.Operation.ADDITION)
        )
    }

    @Test
    fun testMultipleNested() {
        expectAst(
            "1+2*3+4",
            AST.BinaryExpression(
                AST.BinaryExpression(
                    AST.LiteralExpression(1),
                    AST.BinaryExpression(
                        AST.LiteralExpression(2),
                        AST.LiteralExpression(3),
                        AST.BinaryExpression.Operation.MULTIPLICATION
                    ),
                    AST.BinaryExpression.Operation.ADDITION
                ),
                AST.LiteralExpression(4),
                AST.BinaryExpression.Operation.ADDITION
            )
        )
    }

    @Test
    fun testAssignmentAssociativity() {
        expectAst("2 = 3",
            AST.BinaryExpression(AST.LiteralExpression(2),
                AST.LiteralExpression(3),
                AST.BinaryExpression.Operation.ASSIGNMENT))
    }

    @Test
    fun testAssignmentAssociativityMultiple() {
        expectAst("2 = 3 = 4",
            AST.BinaryExpression(AST.LiteralExpression(2),
                AST.BinaryExpression(AST.LiteralExpression(3),
                    AST.LiteralExpression(4),
                    AST.BinaryExpression.Operation.ASSIGNMENT),
                AST.BinaryExpression.Operation.ASSIGNMENT))
    }

    @Test
    fun testDifferenceAssociativity() {
        expectAst(
            "2 + (3 = 2 = 2) * 3 + 4",
            AST.BinaryExpression(
                AST.BinaryExpression(
                    AST.LiteralExpression(2),
                    AST.BinaryExpression(
                        AST.BinaryExpression(
                            AST.LiteralExpression(3),
                            AST.BinaryExpression(
                                AST.LiteralExpression(2),
                                AST.LiteralExpression(2),
                                AST.BinaryExpression.Operation.ASSIGNMENT
                            ),
                            AST.BinaryExpression.Operation.ASSIGNMENT
                        ),
                        AST.LiteralExpression(3),
                        AST.BinaryExpression.Operation.MULTIPLICATION
                    ),
                    AST.BinaryExpression.Operation.ADDITION
                ),
                AST.LiteralExpression(4),
                AST.BinaryExpression.Operation.ADDITION
            )
        )
    }
}
