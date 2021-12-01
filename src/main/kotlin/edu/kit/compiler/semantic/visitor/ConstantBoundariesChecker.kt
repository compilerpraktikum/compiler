package edu.kit.compiler.semantic.visitor

import edu.kit.compiler.ast.AST
import edu.kit.compiler.lex.SourceFile
import edu.kit.compiler.semantic.AstNode
import java.lang.Long
import java.math.BigInteger
import kotlin.Boolean
import kotlin.toUInt

class ConstantBoundariesChecker(val sourceFile: SourceFile) : AbstractVisitor() {

    var parentIsUnaryMinus: Boolean = false

    override fun visitUnaryOperation(unaryOperation: AstNode.Expression.UnaryOperation) {
        if (unaryOperation.operation == AST.UnaryExpression.Operation.MINUS) {
            parentIsUnaryMinus = true
        }
        super.visitUnaryOperation(unaryOperation)
        parentIsUnaryMinus = false
    }

    override fun visitLiteralIntExpression(literalIntExpression: AstNode.Expression.LiteralExpression.LiteralIntExpression) {
        errorIfFalse(sourceFile, literalIntExpression.sourceRange, "The integer literal value is out of range.") {
            val valid = if (parentIsUnaryMinus) {
                if (literalIntExpression.isParentized) {
                    BigInteger(literalIntExpression.value) < BigInteger("2147483648")
                } else {
                    BigInteger(literalIntExpression.value) < BigInteger("2147483649")
                }
            } else BigInteger(literalIntExpression.value) < BigInteger("2147483648")
            // todo add annotation to parser and check here!

            if (valid) {
                literalIntExpression.parsedValue = Long.parseUnsignedLong(literalIntExpression.value).toUInt()
            }
            valid
        }
        literalIntExpression.value
        super.visitLiteralIntExpression(literalIntExpression)
    }
}
