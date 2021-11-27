package edu.kit.compiler.semantic.visitor

import edu.kit.compiler.ast.AST
import edu.kit.compiler.lex.SourceFile
import edu.kit.compiler.semantic.AstNode
import java.math.BigInteger

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
        checkAndAnnotateSourceFileIfNot(sourceFile, literalIntExpression.sourceRange, "The integer literal value is out of range.") {
            if (parentIsUnaryMinus) {
                if (literalIntExpression.isParentized) {
                    BigInteger(literalIntExpression.value) < BigInteger("2147483648")
                } else {
                    BigInteger(literalIntExpression.value) < BigInteger("2147483649")
                }
            } else BigInteger(literalIntExpression.value) < BigInteger("2147483648")
            // todo add annotation to parser and check here!
        }
        literalIntExpression.value
        super.visitLiteralIntExpression(literalIntExpression)
    }
}
