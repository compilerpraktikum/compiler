package edu.kit.compiler.semantic.visitor

import edu.kit.compiler.semantic.SemanticAST
import edu.kit.compiler.semantic.SemanticType

/**
 * Abstract visitor pattern for [SemanticAST] structure. When overridden, child nodes have to be visited manually (with [accept])
 */
abstract class AbstractVisitor {

    open fun visitProgram(program: SemanticAST.Program) {
        program.classes.forEach { it.accept(this) }
    }

    open fun visitClassDeclaration(classDeclaration: SemanticAST.ClassDeclaration) {
        classDeclaration.members.forEach { it.accept(this) }
    }

    open fun visitFieldDeclaration(fieldDeclaration: SemanticAST.ClassMember.FieldDeclaration) {
        fieldDeclaration.type.accept(this)
    }

    open fun visitMethodDeclaration(methodDeclaration: SemanticAST.ClassMember.SubroutineDeclaration.MethodDeclaration) {
        methodDeclaration.returnType.accept(this)
        methodDeclaration.parameters.forEach { it.accept(this) }
        methodDeclaration.block.accept(this)
    }

    open fun visitMainMethodDeclaration(mainMethodDeclaration: SemanticAST.ClassMember.SubroutineDeclaration.MainMethodDeclaration) {
        mainMethodDeclaration.returnType.accept(this)
        mainMethodDeclaration.parameters.forEach { it.accept(this) }
        mainMethodDeclaration.block.accept(this)
    }

    open fun visitParameter(parameter: SemanticAST.ClassMember.SubroutineDeclaration.Parameter) {
        parameter.type.accept(this)
    }

    open fun visitStatement(statement: SemanticAST.Statement) {
        statement.acceptStatement(this)
    }

    open fun visitBlock(block: SemanticAST.Statement.Block) {
        block.statements.forEach { it.accept(this) }
    }

    open fun visitExpressionStatement(expressionStatement: SemanticAST.Statement.ExpressionStatement) {
        expressionStatement.expression.accept(this)
    }

    open fun visitIfStatement(ifStatement: SemanticAST.Statement.IfStatement) {
        ifStatement.condition.accept(this)
        ifStatement.thenCase.accept(this)
        ifStatement.elseCase?.accept(this)
    }

    open fun visitLocalVariableDeclaration(localVariableDeclaration: SemanticAST.Statement.LocalVariableDeclaration) {
        localVariableDeclaration.type.accept(this)
        localVariableDeclaration.initializer?.accept(this)
    }

    open fun visitReturnStatement(returnStatement: SemanticAST.Statement.ReturnStatement) {
        returnStatement.expression?.accept(this)
    }

    open fun visitWhileStatement(whileStatement: SemanticAST.Statement.WhileStatement) {
        whileStatement.condition.accept(this)
        whileStatement.statement.accept(this)
    }

    open fun visitExpression(expression: SemanticAST.Expression) {
        expression.acceptExpression(this)
    }

    open fun visitArrayAccessExpression(arrayAccessExpression: SemanticAST.Expression.ArrayAccessExpression) {
        arrayAccessExpression.target.accept(this)
        arrayAccessExpression.index.accept(this)
    }

    open fun visitBinaryOperation(binaryOperation: SemanticAST.Expression.BinaryOperation) {
        binaryOperation.left.accept(this)
        binaryOperation.right.accept(this)
    }

    open fun visitFieldAccessExpression(fieldAccessExpression: SemanticAST.Expression.FieldAccessExpression) {
        fieldAccessExpression.target.accept(this)
    }

    open fun visitIdentifierExpression(identifierExpression: SemanticAST.Expression.IdentifierExpression) {
    }

    open fun visitLiteralBoolExpression(literalBoolExpression: SemanticAST.Expression.LiteralExpression.LiteralBoolExpression) {
    }

    open fun visitLiteralIntExpression(literalIntExpression: SemanticAST.Expression.LiteralExpression.LiteralIntExpression) {
    }

    open fun visitLiteralNullExpression(literalNullExpression: SemanticAST.Expression.LiteralExpression.LiteralNullExpression) {
    }

    open fun visitLiteralThisExpression(literalThisExpression: SemanticAST.Expression.LiteralExpression.LiteralThisExpression) {
    }

    open fun visitMethodInvocationExpression(methodInvocationExpression: SemanticAST.Expression.MethodInvocationExpression) {
        methodInvocationExpression.target?.accept(this)
        methodInvocationExpression.arguments.forEach { it.accept(this) }
    }

    open fun visitNewArrayExpression(newArrayExpression: SemanticAST.Expression.NewArrayExpression) {
        newArrayExpression.type.accept(this)
        newArrayExpression.length.accept(this)
    }

    open fun visitNewObjectExpression(newObjectExpression: SemanticAST.Expression.NewObjectExpression) {
        newObjectExpression.type.accept(this)
    }

    open fun visitUnaryOperation(unaryOperation: SemanticAST.Expression.UnaryOperation) {
        unaryOperation.inner.accept(this)
    }

    open fun visitIntType() {
    }

    open fun visitBoolType() {
    }

    open fun visitVoidType() {
    }

    open fun visitArrayType(arrayType: SemanticType.Array) {
        arrayType.elementType.accept(this)
    }

    open fun visitClassType(clazz: SemanticType.Class) {
    }
}

fun SemanticAST.accept(visitor: AbstractVisitor) {
    when (this) {
        is SemanticAST.Program -> visitor.visitProgram(this)
        is SemanticAST.ClassDeclaration -> visitor.visitClassDeclaration(this)
        is SemanticAST.ClassMember -> this.accept(visitor)
        is SemanticAST.ClassMember.SubroutineDeclaration.Parameter -> visitor.visitParameter(this)
        is SemanticAST.Expression -> this.accept(visitor)
        is SemanticAST.Statement -> this.accept(visitor)
    }
}

fun SemanticAST.ClassMember.accept(visitor: AbstractVisitor) {
    when (this) {
        is SemanticAST.ClassMember.FieldDeclaration -> visitor.visitFieldDeclaration(this)
        is SemanticAST.ClassMember.SubroutineDeclaration.MainMethodDeclaration -> visitor.visitMainMethodDeclaration(this)
        is SemanticAST.ClassMember.SubroutineDeclaration.MethodDeclaration -> visitor.visitMethodDeclaration(this)
    }
}

fun SemanticAST.Statement.accept(visitor: AbstractVisitor) {
    visitor.visitStatement(this)
}

fun SemanticAST.Statement.acceptStatement(visitor: AbstractVisitor) {
    when (this) {
        is SemanticAST.Statement.Block -> visitor.visitBlock(this)
        is SemanticAST.Statement.ExpressionStatement -> visitor.visitExpressionStatement(this)
        is SemanticAST.Statement.IfStatement -> visitor.visitIfStatement(this)
        is SemanticAST.Statement.LocalVariableDeclaration -> visitor.visitLocalVariableDeclaration(this)
        is SemanticAST.Statement.ReturnStatement -> visitor.visitReturnStatement(this)
        is SemanticAST.Statement.WhileStatement -> visitor.visitWhileStatement(this)
    }
}

fun SemanticAST.Expression.accept(visitor: AbstractVisitor) {
    visitor.visitExpression(this)
}

fun SemanticAST.Expression.acceptExpression(visitor: AbstractVisitor) {
    when (this) {
        is SemanticAST.Expression.ArrayAccessExpression -> visitor.visitArrayAccessExpression(this)
        is SemanticAST.Expression.BinaryOperation -> visitor.visitBinaryOperation(this)
        is SemanticAST.Expression.FieldAccessExpression -> visitor.visitFieldAccessExpression(this)
        is SemanticAST.Expression.IdentifierExpression -> visitor.visitIdentifierExpression(this)
        is SemanticAST.Expression.LiteralExpression -> this.accept(visitor)
        is SemanticAST.Expression.MethodInvocationExpression -> visitor.visitMethodInvocationExpression(this)
        is SemanticAST.Expression.NewArrayExpression -> visitor.visitNewArrayExpression(this)
        is SemanticAST.Expression.NewObjectExpression -> visitor.visitNewObjectExpression(this)
        is SemanticAST.Expression.UnaryOperation -> visitor.visitUnaryOperation(this)
    }
}

fun SemanticAST.Expression.LiteralExpression.accept(visitor: AbstractVisitor) {
    when (this) {
        is SemanticAST.Expression.LiteralExpression.LiteralBoolExpression -> visitor.visitLiteralBoolExpression(this)
        is SemanticAST.Expression.LiteralExpression.LiteralIntExpression -> visitor.visitLiteralIntExpression(this)
        is SemanticAST.Expression.LiteralExpression.LiteralNullExpression -> visitor.visitLiteralNullExpression(this)
        is SemanticAST.Expression.LiteralExpression.LiteralThisExpression -> visitor.visitLiteralThisExpression(this)
    }
}

fun SemanticType.accept(visitor: AbstractVisitor) {
    when (this) {
        SemanticType.Integer -> visitor.visitIntType()
        SemanticType.Boolean -> visitor.visitBoolType()
        SemanticType.Void -> visitor.visitVoidType()
        is SemanticType.Array -> visitor.visitArrayType(this)
        is SemanticType.Class -> visitor.visitClassType(this)
        else -> throw IllegalStateException("internal error")
    }
}
