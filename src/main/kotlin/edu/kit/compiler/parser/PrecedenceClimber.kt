package edu.kit.compiler.parser

import edu.kit.compiler.Token
import edu.kit.compiler.Token.Operator.Type.*
import kotlinx.coroutines.flow.Flow


private val Token.Operator.Type.asOp: PrecedenceClimber.BinOp?
    get() = when(this) {
        NoEq -> TODO()
        Not -> TODO()
        LParen -> TODO()
        RParen -> TODO()
        MulAssign -> TODO()
        Mul -> PrecedenceClimber.BinOp.Mul
        PlusPlus -> TODO()
        PlusAssign -> TODO()
        Plus -> PrecedenceClimber.BinOp.Add
        Comma -> TODO()
        MinusAssign -> TODO()
        MinusMinus -> TODO()
        Minus -> TODO()
        Dot -> TODO()
        DivAssign -> TODO()
        Div -> TODO()
        Colon -> TODO()
        Semicolon -> TODO()
        LeftShiftAssign -> TODO()
        LeftShift -> TODO()
        LtEq -> TODO()
        Lt -> TODO()
        Eq -> TODO()
        Assign -> TODO()
        GtEq -> TODO()
        RightShiftSEAssign -> TODO()
        RightShiftAssign -> TODO()
        RightShift -> TODO()
        RightShiftSE -> TODO()
        Gt -> TODO()
        QuestionMark -> TODO()
        ModAssign -> TODO()
        Mod -> TODO()
        AndAssign -> TODO()
        And -> TODO()
        BitAnd -> TODO()
        LeftBracket -> TODO()
        RightBracket -> TODO()
        XorAssign -> TODO()
        Xor -> PrecedenceClimber.BinOp.Xor
        LeftBrace -> TODO()
        RightBrace -> TODO()
        BitNot -> TODO()
        OrAssign -> TODO()
        Or -> TODO()
        BitOr -> TODO()
    }

class PrecedenceClimber(private val tokens: Flow<Token>): AbstractParser<PrecedenceClimber.Expr>(tokens) {
    enum class Associativity {
        LeftAssociative, RightAssociative
    }
    enum class BinOp(val prec: Int, val associativity: Associativity) {
        Add(1, Associativity.LeftAssociative),
        Mul(2, Associativity.LeftAssociative),
        Xor(3, Associativity.RightAssociative)
    }

    sealed class Expr {
        data class Literal(val v: Int) : Expr()
        data class BinOp(val left: Expr, val right: Expr, val op: PrecedenceClimber.BinOp) : Expr()
    }

    private suspend fun parsePrimaryExpression() : Expr {
        return when(val next = peek()) {
            is Token.Literal -> {
                next()
                Expr.Literal(next.value.toInt())
            }
            is Token.Operator ->
                if (next.type == LParen) {
                    next()
                    val innerExpr = parseExpression(1)
                    val tokenAfterParens = this.next()
                    if (tokenAfterParens is Token.Operator && tokenAfterParens.type == RParen) {
                        innerExpr
                    } else {
                        TODO("expected closing RPAREN")
                    }
                } else {
                    TODO("")
                }
            is Token.Eof -> TODO("unexpected eof")
            else -> TODO("unexpected token $next")
        }
    }

    // 1+2+3
    // --> result = 1
    // --> while currentToken = +
    // -->    computeExpr(2)
    //         <-- 2
    // --> result = 1 + 2
    // --> currentToken = +
    // --> while currentToken = +
    // -->     computeExpr(2)
    //         <-- 3
    // --> result = (1+2) + 3

    // 1 + !2
    private suspend fun parseExpression(minPrecedence: Int): Expr {
        var result = parsePrimaryExpression()
        var currentToken = this.peek()
        println("\nbefore while: current token $currentToken lhs: $result")


        while(currentToken is Token.Operator && currentToken.type.asOp != null && currentToken.type.asOp!!.prec >= minPrecedence) {
            println("in while: current token $currentToken")
            val currentPrecedence = currentToken.type.asOp!!.prec
            val currentAssociativity = currentToken.type.asOp!!.associativity


            this.next()
            val rhs = parseExpression(when(currentAssociativity) {
                Associativity.LeftAssociative -> currentPrecedence + 1
                else -> currentPrecedence
            })
            println("rhs $rhs")
            result = Expr.BinOp(result, rhs, currentToken.type.asOp!!)
            println("result is $result")
            currentToken = peek()
        }
        println("end: $result")
        return result
    }

    override suspend fun parseAST(): Expr =
        parseExpression(1)

}
