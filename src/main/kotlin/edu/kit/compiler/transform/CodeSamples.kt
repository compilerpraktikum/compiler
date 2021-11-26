// ktlint-disable filename
package edu.kit.compiler.transform

import edu.kit.compiler.semantic.AstNode
import edu.kit.compiler.semantic.visitor.AbstractVisitor

class ConstructExpressions : AbstractVisitor() {
    override fun visitBinaryOperation(binaryOperation: AstNode.Expression.BinaryOperation) {
        FirmContext.binaryExpression(binaryOperation.operation) {
            // call the visitor for all inner expressions here, so the constructed inner expressions are used as
            // parameters for the outer expression
            super.visitBinaryOperation(binaryOperation)
        }
    }
}
