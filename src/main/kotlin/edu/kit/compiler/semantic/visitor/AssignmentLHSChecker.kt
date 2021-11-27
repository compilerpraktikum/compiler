package edu.kit.compiler.semantic.visitor

import edu.kit.compiler.ast.AST
import edu.kit.compiler.lex.SourceFile
import edu.kit.compiler.lex.SourceRange
import edu.kit.compiler.semantic.AstNode

class AssignmentLHSChecker(val sourceFile: SourceFile) : AbstractVisitor() {

    override fun visitBinaryOperation(binaryOperation: AstNode.Expression.BinaryOperation) {
        if (binaryOperation.operation == AST.BinaryExpression.Operation.ASSIGNMENT) {
            checkAndMessageIfNot(binaryOperation.left.sourceRange, "Left hand side of an assignment must be an IdentifierExpression, a FieldAccessExpression or an ArrayAccessExpression") {
                binaryOperation.left is AstNode.Expression.IdentifierExpression ||
                    binaryOperation.left is AstNode.Expression.FieldAccessExpression ||
                    binaryOperation.left is AstNode.Expression.ArrayAccessExpression
            }
        }
        super.visitBinaryOperation(binaryOperation)
    }

    private fun checkAndMessageIfNot(sourceRange: SourceRange, errorMsg: String, function: () -> kotlin.Boolean) {
        checkAndAnnotateSourceFileIfNot(sourceFile, sourceRange, errorMsg, function)
    }
}
