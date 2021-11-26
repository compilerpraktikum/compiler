package edu.kit.compiler.transform

import edu.kit.compiler.semantic.AstNode
import edu.kit.compiler.semantic.AstNode.ClassMember.SubroutineDeclaration.MethodDeclaration
import edu.kit.compiler.semantic.visitor.AbstractVisitor
import edu.kit.compiler.semantic.visitor.accept
import edu.kit.compiler.transform.FirmContext.intType

/**
 * A visitor implementation that constructs the firm graph of the [AST][AstNode].
 */
class TransformationVisitor : AbstractVisitor() {
    override fun visitClassDeclaration(classDeclaration: AstNode.ClassDeclaration) {
        classDeclaration.accept(TransformationClassVisitor(classDeclaration))
    }
}

/**
 * Visit a single class and construct all its members in the firm graph
 */
private class TransformationClassVisitor(private val surroundingClass: AstNode.ClassDeclaration) : AbstractVisitor() {
    override fun visitMethodDeclaration(methodDeclaration: MethodDeclaration) {
        methodDeclaration.accept(TransformationMethodVisitor(surroundingClass))
    }
}

/**
 * Visit a single method and construct all its code in the firm graph
 *
 * @param surroundingClass the surrounding class declaration (which is used as an implicite parameter to methods)
 */
private class TransformationMethodVisitor(private val surroundingClass: AstNode.ClassDeclaration) : AbstractVisitor() {
    override fun visitMethodDeclaration(methodDeclaration: MethodDeclaration) {
        FirmContext.subroutine(0, "foo", FirmContext.constructMethodType(intType, intType)) {
            super.visitMethodDeclaration(methodDeclaration)
        }
    }

    override fun visitBinaryOperation(binaryOperation: AstNode.Expression.BinaryOperation) {
        FirmContext.binaryExpression(binaryOperation.operation) {
            super.visitBinaryOperation(binaryOperation)
        }
    }

    override fun visitReturnStatement(returnStatement: AstNode.Statement.ReturnStatement) {
        super.visitReturnStatement(returnStatement)
    }
}
