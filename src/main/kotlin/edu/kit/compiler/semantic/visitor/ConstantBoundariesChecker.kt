package edu.kit.compiler.semantic.visitor

import edu.kit.compiler.ast.AST
import edu.kit.compiler.lex.SourceFile
import edu.kit.compiler.semantic.AstNode
import java.math.BigInteger

/**
 * Verify that integer literals are within 32-bit two's complement boundaries
 */
class ConstantBoundariesChecker(val sourceFile: SourceFile) : AbstractVisitor() {

    override fun visitUnaryOperation(unaryOperation: AstNode.Expression.UnaryOperation) {
        if (unaryOperation.operation == AST.UnaryExpression.Operation.MINUS) {
            when (unaryOperation.inner) {
                is AstNode.Expression.LiteralExpression.LiteralIntExpression -> {
                    this.specialVisitLiteralIntExpression(unaryOperation.inner, true)
                    // do not descent to avoid double-visit
                }
                else -> super.visitUnaryOperation(unaryOperation)
            }
        } else {
            super.visitUnaryOperation(unaryOperation)
        }
    }

    override fun visitLiteralIntExpression(literalIntExpression: AstNode.Expression.LiteralExpression.LiteralIntExpression) {
        // if this method is visited, it means that the parent cannot be a unary minus expression,
        // since the visitor does not descent into an integer literal in that case
        this.specialVisitLiteralIntExpression(literalIntExpression, false)
    }

    /**
     * A special visitor that gets a flag whether the parent expression is a unary minus as additional context.
     *
     * @param literalIntExpression a [AstNode.Expression.LiteralExpression.LiteralIntExpression]
     * @param parentIsUnaryMinus true, iff [literalIntExpression] is directly (i.e. not transitively) embedded in a
     * [AstNode.Expression.UnaryOperation] with [AST.UnaryExpression.Operation.MINUS] as its type.
     */
    private fun specialVisitLiteralIntExpression(
        literalIntExpression: AstNode.Expression.LiteralExpression.LiteralIntExpression,
        parentIsUnaryMinus: Boolean
    ) {
        fun AstNode.Expression.LiteralExpression.LiteralIntExpression.isValidLiteral(): Boolean {
            return if (parentIsUnaryMinus && !isParentized) {
                BigInteger(value) <= BigInteger("2147483648")
            } else {
                BigInteger(value) <= BigInteger("2147483647")
            }
        }

        if (literalIntExpression.isValidLiteral()) {
            literalIntExpression.parsedValue = literalIntExpression.value.toUInt()
        } else {
            sourceFile.error {
                "integer literal value is out of range" at literalIntExpression.sourceRange
            }
        }
    }
}
