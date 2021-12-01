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
        fun AstNode.Expression.LiteralExpression.LiteralIntExpression.isValidLiteral(): Boolean {
            return if (parentIsUnaryMinus && !isParentized) {
                BigInteger(value) <= BigInteger("2147483648")
            } else {
                BigInteger(value) <= BigInteger("2147483647")
            }
        }
        sourceFile.errorIfNot(literalIntExpression.isValidLiteral()) {
            // todo add annotation to parser and check here!
            "integer literal value is out of range" at literalIntExpression.sourceRange
        }
        literalIntExpression.value
        super.visitLiteralIntExpression(literalIntExpression)
    }
}

fun doConstantBoundariesCheck(program: AstNode.Program, sourceFile: SourceFile) {
    program.accept(ConstantBoundariesChecker(sourceFile))
}
