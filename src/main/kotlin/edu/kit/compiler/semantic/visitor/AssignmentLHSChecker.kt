package edu.kit.compiler.semantic.visitor

import edu.kit.compiler.ast.AST
import edu.kit.compiler.lex.SourceFile
import edu.kit.compiler.semantic.AstNode

class AssignmentLHSChecker(val sourceFile: SourceFile) : AbstractVisitor() {

    override fun visitBinaryOperation(binaryOperation: AstNode.Expression.BinaryOperation) {
        if (binaryOperation.operation == AST.BinaryExpression.Operation.ASSIGNMENT) {
            sourceFile.errorIfNot(
                when (binaryOperation.left) {
                    is AstNode.Expression.IdentifierExpression,
                    is AstNode.Expression.FieldAccessExpression,
                    is AstNode.Expression.ArrayAccessExpression -> true
                    else -> false
                }
            ) {
                "target of assignment is not an lvalue (identifier, field access or array access)" at binaryOperation.left.sourceRange
            }
        }
        super.visitBinaryOperation(binaryOperation)
    }
}

fun doAssignmentLHSChecking(program: AstNode.Program, sourceFile: SourceFile) {
    program.accept(AssignmentLHSChecker(sourceFile))
}
