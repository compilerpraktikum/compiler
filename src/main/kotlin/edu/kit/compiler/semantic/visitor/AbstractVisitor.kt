package edu.kit.compiler.semantic.visitor

import edu.kit.compiler.semantic.AstNode
import edu.kit.compiler.semantic.ParsedType

/**
 * Abstract visitor pattern for [AstNode] structure. When overridden, child nodes have to be visited manually (with [accept])
 */
abstract class AbstractVisitor {

    open fun visitProgram(program: AstNode.Program) {
        program.classes.forEach { it.accept(this) }
    }

    open fun visitClassDeclaration(classDeclaration: AstNode.ClassDeclaration) {
        classDeclaration.members.forEach { it.accept(this) }
    }

    open fun visitFieldDeclaration(fieldDeclaration: AstNode.ClassMember.FieldDeclaration) {
    }

    open fun visitMethodDeclaration(methodDeclaration: AstNode.ClassMember.SubroutineDeclaration.MethodDeclaration) {
        methodDeclaration.parameters.forEach { it.accept(this) }
        methodDeclaration.block.accept(this)
    }

    open fun visitMainMethodDeclaration(mainMethodDeclaration: AstNode.ClassMember.SubroutineDeclaration.MainMethodDeclaration) {
        mainMethodDeclaration.parameters.forEach { it.accept(this) }
        mainMethodDeclaration.block.accept(this)
    }

    open fun visitParameter(parameter: AstNode.ClassMember.SubroutineDeclaration.Parameter) {
    }

    open fun visitArrayAccessExpression(arrayAccessExpression: AstNode.Expression.ArrayAccessExpression) {
        arrayAccessExpression.target.accept(this)
        arrayAccessExpression.index.accept(this)
    }

    open fun visitBinaryOperation(binaryOperation: AstNode.Expression.BinaryOperation) {
        binaryOperation.left.accept(this)
        binaryOperation.right.accept(this)
    }

    open fun visitFieldAccessExpression(fieldAccessExpression: AstNode.Expression.FieldAccessExpression) {
        fieldAccessExpression.target.accept(this)
    }

    open fun visitIdentifierExpression(identifierExpression: AstNode.Expression.IdentifierExpression) {
    }

    open fun visitLiteralBoolExpression(literalBoolExpression: AstNode.Expression.LiteralExpression.LiteralBoolExpression) {
    }

    open fun visitLiteralIntExpression(literalIntExpression: AstNode.Expression.LiteralExpression.LiteralIntExpression) {
    }

    open fun visitLiteralNullExpression(literalNullExpression: AstNode.Expression.LiteralExpression.LiteralNullExpression) {
    }

    open fun visitMethodInvocationExpression(methodInvocationExpression: AstNode.Expression.MethodInvocationExpression) {
        methodInvocationExpression.target?.accept(this)
        methodInvocationExpression.arguments.forEach { it.accept(this) }
    }

    open fun visitNewArrayExpression(newArrayExpression: AstNode.Expression.NewArrayExpression) {
        newArrayExpression.length.accept(this)
    }

    open fun visitNewObjectExpression(newObjectExpression: AstNode.Expression.NewObjectExpression) {
    }

    open fun visitUnaryOperation(unaryOperation: AstNode.Expression.UnaryOperation) {
        unaryOperation.inner.accept(this)
    }

    open fun visitBlock(block: AstNode.Statement.Block) {
        block.statements.forEach { it.accept(this) }
    }

    open fun visitExpressionStatement(expressionStatement: AstNode.Statement.ExpressionStatement) {
        expressionStatement.expression.accept(this)
    }

    open fun visitIfStatement(ifStatement: AstNode.Statement.IfStatement) {
        ifStatement.condition.accept(this)
        ifStatement.thenCase.accept(this)
        ifStatement.elseCase?.accept(this)
    }

    open fun visitLocalVariableDeclaration(localVariableDeclaration: AstNode.Statement.LocalVariableDeclaration) {
        localVariableDeclaration.initializer?.accept(this)
    }

    open fun visitReturnStatement(returnStatement: AstNode.Statement.ReturnStatement) {
        returnStatement.expression?.accept(this)
    }

    open fun visitWhileStatement(whileStatement: AstNode.Statement.WhileStatement) {
        whileStatement.condition.accept(this)
        whileStatement.statement.accept(this)
    }

    open fun visitIntType() {
    }

    open fun visitBoolType() {
    }

    open fun visitVoidType() {
    }

    open fun visitArrayType(arrayType: ParsedType.ArrayType) {
        arrayType.elementType.accept(this)
    }

    open fun visitComplexType(complexType: ParsedType.ComplexType) {
    }
}

fun AstNode.accept(visitor: AbstractVisitor) {
    when (this) {
        is AstNode.Program -> visitor.visitProgram(this)
        is AstNode.ClassDeclaration -> visitor.visitClassDeclaration(this)
        is AstNode.ClassMember -> this.accept(visitor)
        is AstNode.ClassMember.SubroutineDeclaration.Parameter -> visitor.visitParameter(this)
        is AstNode.Expression -> this.accept(visitor)
        is AstNode.Statement -> this.accept(visitor)
    }
}

fun AstNode.ClassMember.accept(visitor: AbstractVisitor) {
    when (this) {
        is AstNode.ClassMember.FieldDeclaration -> visitor.visitFieldDeclaration(this)
        is AstNode.ClassMember.SubroutineDeclaration.MainMethodDeclaration -> visitor.visitMainMethodDeclaration(this)
        is AstNode.ClassMember.SubroutineDeclaration.MethodDeclaration -> visitor.visitMethodDeclaration(this)
    }
}

fun AstNode.Expression.accept(visitor: AbstractVisitor) {
    when (this) {
        is AstNode.Expression.ArrayAccessExpression -> visitor.visitArrayAccessExpression(this)
        is AstNode.Expression.BinaryOperation -> visitor.visitBinaryOperation(this)
        is AstNode.Expression.FieldAccessExpression -> visitor.visitFieldAccessExpression(this)
        is AstNode.Expression.IdentifierExpression -> visitor.visitIdentifierExpression(this)
        is AstNode.Expression.LiteralExpression -> this.accept(visitor)
        is AstNode.Expression.MethodInvocationExpression -> visitor.visitMethodInvocationExpression(this)
        is AstNode.Expression.NewArrayExpression -> visitor.visitNewArrayExpression(this)
        is AstNode.Expression.NewObjectExpression -> visitor.visitNewObjectExpression(this)
        is AstNode.Expression.UnaryOperation -> visitor.visitUnaryOperation(this)
    }
}

fun AstNode.Expression.LiteralExpression.accept(visitor: AbstractVisitor) {
    when (this) {
        is AstNode.Expression.LiteralExpression.LiteralBoolExpression -> visitor.visitLiteralBoolExpression(this)
        is AstNode.Expression.LiteralExpression.LiteralIntExpression -> visitor.visitLiteralIntExpression(this)
        is AstNode.Expression.LiteralExpression.LiteralNullExpression -> visitor.visitLiteralNullExpression(this)
    }
}

fun AstNode.Statement.accept(visitor: AbstractVisitor) {
    when (this) {
        is AstNode.Statement.Block -> visitor.visitBlock(this)
        is AstNode.Statement.ExpressionStatement -> visitor.visitExpressionStatement(this)
        is AstNode.Statement.IfStatement -> visitor.visitIfStatement(this)
        is AstNode.Statement.LocalVariableDeclaration -> visitor.visitLocalVariableDeclaration(this)
        is AstNode.Statement.ReturnStatement -> visitor.visitReturnStatement(this)
        is AstNode.Statement.WhileStatement -> visitor.visitWhileStatement(this)
    }
}

fun ParsedType.accept(visitor: AbstractVisitor) {
    when (this) {
        ParsedType.IntType -> visitor.visitIntType()
        ParsedType.BoolType -> visitor.visitBoolType()
        ParsedType.VoidType -> visitor.visitVoidType()
        is ParsedType.ArrayType -> visitor.visitArrayType(this)
        is ParsedType.ComplexType -> visitor.visitComplexType(this)
    }
}
