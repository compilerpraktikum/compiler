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
        errorIfFalse(sourceFile, literalIntExpression.sourceRange, "integer literal value is out of range") {
            if (parentIsUnaryMinus && !literalIntExpression.isParentized) {
                BigInteger(literalIntExpression.value) <= BigInteger("2147483648")
            } else {
                BigInteger(literalIntExpression.value) <= BigInteger("2147483647")
            }
            // todo add annotation to parser and check here!
        }
        literalIntExpression.value
        super.visitLiteralIntExpression(literalIntExpression)
    }
}

fun doConstantBoundariesCheck(program: AstNode.Program, sourceFile: SourceFile) {
    program.accept(ConstantBoundariesChecker(sourceFile))
}
