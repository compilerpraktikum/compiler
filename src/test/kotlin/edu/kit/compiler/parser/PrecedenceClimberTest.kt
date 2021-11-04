package edu.kit.compiler.parser

import edu.kit.compiler.Token
import edu.kit.compiler.initializeKeywords
import edu.kit.compiler.lex.Lexer
import edu.kit.compiler.lex.LexerTest
import edu.kit.compiler.lex.StringTable
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import edu.kit.compiler.parser.PrecedenceClimber.*
import edu.kit.compiler.utils.InlineInputProvider
import edu.kit.compiler.utils.setupLexer
import kotlin.math.exp

internal class PrecedenceClimberTest {

    private fun expectAST(input: String, expectedAST: Expr) {
        val lexer = setupLexer(input)
        val res = runBlocking {
            PrecedenceClimber(lexer.tokens()).parse()
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
        expectAST("1+2+3",
            Expr.BinOp(Expr.BinOp(Expr.Literal(1),
                Expr.Literal(2), BinOp.Add), Expr.Literal(3), BinOp.Add)
        )
    }

    @Test
    fun testMultipleAssoc() {
        expectAST("1+2*3",
            Expr.BinOp(Expr.Literal(1), Expr.BinOp(Expr.Literal(2), Expr.Literal(3), BinOp.Mul), BinOp.Add)
        )
    }

    @Test
    fun testMultipleNested() {
        expectAST(
            "1+2*3+4",
            Expr.BinOp(
                Expr.BinOp(
                    Expr.Literal(1),
                    Expr.BinOp(
                        Expr.Literal(2),
                        Expr.Literal(3),
                        BinOp.Mul
                    ),
                    BinOp.Add
                ),
                Expr.Literal(4),
                BinOp.Add
            )
        )
    }

    @Test
    fun testDifferenceAssociativity() {
        expectAST(
            "2 + 3 ^ 2 ^ 2 * 3 + 4",
            Expr.BinOp(
                Expr.BinOp(
                    Expr.Literal(2),
                    Expr.BinOp(
                        Expr.BinOp(
                            Expr.Literal(3),
                            Expr.BinOp(
                                Expr.Literal(2),
                                Expr.Literal(2),
                                BinOp.Xor
                            ),
                            BinOp.Xor
                        ),
                        Expr.Literal(3),
                        BinOp.Mul
                    ),
                    BinOp.Add
                ),
                Expr.Literal(4),
                BinOp.Add
            )
        )
    }
}
