package edu.kit.compiler.semantic.visitor

import edu.kit.compiler.ast.AST
import edu.kit.compiler.lex.SourceFile
import edu.kit.compiler.lex.SourceRange
import edu.kit.compiler.semantic.AstNode

class AssignmentLHSChecker(val sourceFile: SourceFile) : AbstractVisitor() {

    override fun visitBinaryOperation(binaryOperation: AstNode.Expression.BinaryOperation) {
        if (binaryOperation.operation == AST.BinaryExpression.Operation.ASSIGNMENT) {
            checkAndMessageIfNot(binaryOperation.left.sourceRange, "target of assignment is not an lvalue (identifier, field access or array access)") {
                when (binaryOperation.left) {
                    is AstNode.Expression.IdentifierExpression,
                    is AstNode.Expression.FieldAccessExpression,
                    is AstNode.Expression.ArrayAccessExpression -> true
                    else -> false
                }
            }
        }
        super.visitBinaryOperation(binaryOperation)
    }

    private fun checkAndMessageIfNot(sourceRange: SourceRange, errorMsg: String, function: () -> kotlin.Boolean) {
        errorIfFalse(sourceFile, sourceRange, errorMsg, function)
    }
}

fun doAssignmentLHSChecking(program: AstNode.Program, sourceFile: SourceFile) {
    program.accept(AssignmentLHSChecker(sourceFile))
}
