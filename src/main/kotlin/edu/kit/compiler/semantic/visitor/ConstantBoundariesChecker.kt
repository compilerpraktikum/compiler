package edu.kit.compiler.semantic.visitor

import edu.kit.compiler.lex.SourceFile
import edu.kit.compiler.semantic.AstNode
import java.math.BigInteger

private val MAX_INT = BigInteger("2147483647")
private val MAX_INT_NEGATED = BigInteger("2147483648")

/**
 * Verify that integer literals are within 32-bit two's complement boundaries
 */
class ConstantBoundariesChecker(val sourceFile: SourceFile) : AbstractVisitor() {

    override fun visitLiteralIntExpression(literalIntExpression: AstNode.Expression.LiteralExpression.LiteralIntExpression) {
        val maxValue = if (literalIntExpression.isNegated) MAX_INT_NEGATED else MAX_INT
        val isValid = (BigInteger(literalIntExpression.value) <= maxValue)

        sourceFile.errorIfNot(isValid) {
            "integer literal value is out of range" at literalIntExpression.sourceRange
        }
    }
}
