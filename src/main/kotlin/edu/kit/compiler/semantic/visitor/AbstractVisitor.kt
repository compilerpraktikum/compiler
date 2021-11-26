package edu.kit.compiler.semantic.visitor

import edu.kit.compiler.semantic.AstNode
import edu.kit.compiler.semantic.SemanticType

/**
 * Abstract visitor pattern for [AstNode] structure. When overridden, child nodes have to be visited manually (with [accept])
 */
abstract class AbstractVisitor : AnyVisitor {

    override fun visitProgram(program: AstNode.Program) {
        program.classes.forEach { it.accept(this) }
    }

    override fun visitClassDeclaration(classDeclaration: AstNode.ClassDeclaration) {
        classDeclaration.members.forEach { it.accept(this) }
    }

    override fun visitFieldDeclaration(fieldDeclaration: AstNode.ClassMember.FieldDeclaration) {
        fieldDeclaration.type.accept(this)
    }

    override fun visitMethodDeclaration(methodDeclaration: AstNode.ClassMember.SubroutineDeclaration.MethodDeclaration) {
        methodDeclaration.returnType.accept(this)
        methodDeclaration.parameters.forEach { it.accept(this) }
        methodDeclaration.block.accept(this)
    }

    override fun visitMainMethodDeclaration(mainMethodDeclaration: AstNode.ClassMember.SubroutineDeclaration.MainMethodDeclaration) {
        mainMethodDeclaration.returnType.accept(this)
        mainMethodDeclaration.parameters.forEach { it.accept(this) }
        mainMethodDeclaration.block.accept(this)
    }

    override fun visitParameter(parameter: AstNode.ClassMember.SubroutineDeclaration.Parameter) {
        parameter.type.accept(this)
    }

    override fun visitArrayAccessExpression(arrayAccessExpression: AstNode.Expression.ArrayAccessExpression) {
        arrayAccessExpression.target.accept(this)
        arrayAccessExpression.index.accept(this)
    }

    override fun visitBinaryOperation(binaryOperation: AstNode.Expression.BinaryOperation) {
        binaryOperation.left.accept(this)
        binaryOperation.right.accept(this)
    }

    override fun visitFieldAccessExpression(fieldAccessExpression: AstNode.Expression.FieldAccessExpression) {
        fieldAccessExpression.target.accept(this)
    }

    override fun visitIdentifierExpression(identifierExpression: AstNode.Expression.IdentifierExpression) {
    }

    override fun visitLiteralBoolExpression(literalBoolExpression: AstNode.Expression.LiteralExpression.LiteralBoolExpression) {
    }

    override fun visitLiteralIntExpression(literalIntExpression: AstNode.Expression.LiteralExpression.LiteralIntExpression) {
    }

    override fun visitLiteralNullExpression(literalNullExpression: AstNode.Expression.LiteralExpression.LiteralNullExpression) {
    }

    override fun visitLiteralThisExpression(literalThisExpression: AstNode.Expression.LiteralExpression.LiteralThisExpression) {
    }

    override fun visitMethodInvocationExpression(methodInvocationExpression: AstNode.Expression.MethodInvocationExpression) {
        methodInvocationExpression.target?.accept(this)
        methodInvocationExpression.arguments.forEach { it.accept(this) }
    }

    override fun visitNewArrayExpression(newArrayExpression: AstNode.Expression.NewArrayExpression) {
        newArrayExpression.type.accept(this)
        newArrayExpression.length.accept(this)
    }

    override fun visitNewObjectExpression(newObjectExpression: AstNode.Expression.NewObjectExpression) {
    }

    override fun visitUnaryOperation(unaryOperation: AstNode.Expression.UnaryOperation) {
        unaryOperation.inner.accept(this)
    }

    override fun visitBlock(block: AstNode.Statement.Block) {
        block.statements.forEach { it.accept(this) }
    }

    override fun visitExpressionStatement(expressionStatement: AstNode.Statement.ExpressionStatement) {
        expressionStatement.expression.accept(this)
    }

    override fun visitIfStatement(ifStatement: AstNode.Statement.IfStatement) {
        ifStatement.condition.accept(this)
        ifStatement.thenCase.accept(this)
        ifStatement.elseCase?.accept(this)
    }

    override fun visitLocalVariableDeclaration(localVariableDeclaration: AstNode.Statement.LocalVariableDeclaration) {
        localVariableDeclaration.type.accept(this)
        localVariableDeclaration.initializer?.accept(this)
    }

    override fun visitReturnStatement(returnStatement: AstNode.Statement.ReturnStatement) {
        returnStatement.expression?.accept(this)
    }

    override fun visitWhileStatement(whileStatement: AstNode.Statement.WhileStatement) {
        whileStatement.condition.accept(this)
        whileStatement.statement.accept(this)
    }

    override fun visitIntType() {
    }

    override fun visitBoolType() {
    }

    override fun visitVoidType() {
    }

    override fun visitArrayType(arrayType: SemanticType.Array) {
        arrayType.elementType.accept(this)
    }

    override fun visitClassType(clazz: SemanticType.Class) {
    }

    override fun visitTypeError(semanticType: SemanticType.Error) {
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
        is AstNode.Expression.LiteralExpression.LiteralThisExpression -> visitor.visitLiteralThisExpression(this)
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

fun SemanticType.accept(visitor: AbstractVisitor) {
    when (this) {
        SemanticType.Integer -> visitor.visitIntType()
        SemanticType.Boolean -> visitor.visitBoolType()
        SemanticType.Void -> visitor.visitVoidType()
        is SemanticType.Array -> visitor.visitArrayType(this)
        is SemanticType.Class -> visitor.visitClassType(this)
    }
}
