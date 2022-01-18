package edu.kit.compiler.semantic.visitor

import edu.kit.compiler.ast.AST
import edu.kit.compiler.semantic.SemanticAST
import edu.kit.compiler.source.SourceFile

/**
 * Verify that assignments only happen to expressions that actually point to valid memory abstractions.
 */
class AssignmentLHSChecker(val sourceFile: SourceFile) : AbstractVisitor() {

    override fun visitBinaryOperation(binaryOperation: SemanticAST.Expression.BinaryOperation) {
        if (binaryOperation.operation == AST.BinaryExpression.Operation.ASSIGNMENT) {
            sourceFile.errorIfNot(
                when (binaryOperation.left) {
                    is SemanticAST.Expression.IdentifierExpression,
                    is SemanticAST.Expression.FieldAccessExpression,
                    is SemanticAST.Expression.ArrayAccessExpression -> true
                    else -> false
                }
            ) {
                "target of assignment is not an lvalue (identifier, field access or array access)" at binaryOperation.left.sourceRange
            }
        }
        super.visitBinaryOperation(binaryOperation)
    }
}
