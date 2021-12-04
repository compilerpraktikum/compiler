// ktlint-disable filename
package edu.kit.compiler.transform

import edu.kit.compiler.semantic.AstNode
import edu.kit.compiler.semantic.visitor.AbstractVisitor

class ConstructExpressions : AbstractVisitor() {
    override fun visitBinaryOperation(binaryOperation: AstNode.Expression.BinaryOperation) {
        super.visitBinaryOperation(binaryOperation)
        FirmContext.binaryExpression(binaryOperation, this)
    }
}
