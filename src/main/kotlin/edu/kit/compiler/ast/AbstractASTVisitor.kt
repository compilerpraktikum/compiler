package edu.kit.compiler.ast

abstract class AbstractASTVisitor<E, S, D, C> {

    abstract fun visit(program: AST.Program<E, S, D, C>)

    abstract fun visit(classDeclaration: AST.ClassDeclaration<E, S, D>)

    abstract fun visit(field: AST.Field)

    abstract fun visit(method: AST.Method<E, S>)

    abstract fun visit(mainMethod: AST.MainMethod<E, S>)

    abstract fun visit(parameter: AST.Parameter)

    abstract fun visit(localVariableDeclarationStatement: AST.LocalVariableDeclarationStatement<E>)

    abstract fun visit(block: AST.Block<S, E>)

    abstract fun visit(emptyStatement: AST.EmptyStatement)

    abstract fun visit(ifStatement: AST.IfStatement<S, E>)

    abstract fun visit(whileStatement: AST.WhileStatement<S, E>)

    abstract fun visit(returnStatement: AST.ReturnStatement<E>)

    abstract fun visit(expression: AST.Expression<E>)

    abstract fun visit(binaryExpression: AST.BinaryExpression<E>)

    abstract fun visit(unaryExpression: AST.UnaryExpression<E>)

    abstract fun visit(methodInvocationExpression: AST.MethodInvocationExpression<E>)

    abstract fun visit(fieldAccessExpression: AST.FieldAccessExpression<E>)

    abstract fun visit(arrayAccessExpression: AST.ArrayAccessExpression<E>)

    abstract fun visit(identifierExpression: AST.IdentifierExpression)

    abstract fun <T> visit(literalExpression: AST.LiteralExpression<T>)

    abstract fun visit(newObjectExpression: AST.NewObjectExpression)

    abstract fun visit(newArrayExpression: AST.NewArrayExpression<E>)

    abstract fun visit(voidType: Type.Void)

    abstract fun visit(integerType: Type.Integer)

    abstract fun visit(booleanType: Type.Boolean)

    abstract fun visit(arrayType: Type.Array)

    abstract fun visit(classType: Type.ClassType)

    abstract fun visit(operation: AST.BinaryExpression.Operation)

    abstract fun visit(expressionStatement: AST.ExpressionStatement<E>)
}

fun <E, S, D, C> AST.Expression<E>.accept(visitor: AbstractASTVisitor<E, S, D, C>) = when (this) {
    is AST.ArrayAccessExpression -> visitor.visit(this)
    is AST.BinaryExpression -> visitor.visit(this)
    is AST.FieldAccessExpression -> visitor.visit(this)
    is AST.IdentifierExpression -> visitor.visit(this)
    is AST.LiteralExpression<*> -> visitor.visit(this)
    is AST.MethodInvocationExpression -> visitor.visit(this)
    is AST.NewArrayExpression -> visitor.visit(this)
    is AST.NewObjectExpression -> visitor.visit(this)
    is AST.UnaryExpression -> visitor.visit(this)
}

@JvmName("acceptExpression")
fun <S, D, C> Kind<Identity<Of>, Kind<AST.Expression<Of>, Identity<Of>>>.accept(visitor: AbstractASTVisitor<Identity<Of>, S, D, C>) =
    this.into().v.into().accept(visitor)

fun <E, S, D, C> AST.BlockStatement<S, E>.accept(visitor: AbstractASTVisitor<E, S, D, C>) = when (this) {
    is AST.LocalVariableDeclarationStatement -> visitor.visit(this)
    is AST.Block -> visitor.visit(this)
    is AST.EmptyStatement -> visitor.visit(this)
    is AST.ExpressionStatement -> visitor.visit(this)
    is AST.IfStatement -> visitor.visit(this)
    is AST.ReturnStatement -> visitor.visit(this)
    is AST.WhileStatement -> visitor.visit(this)
}

fun <E, S, D, C> Type.accept(visitor: AbstractASTVisitor<E, S, D, C>) = when (this) {
    is Type.Array -> visitor.visit(this)
    is Type.Boolean -> visitor.visit(this)
    is Type.ClassType -> visitor.visit(this)
    is Type.Integer -> visitor.visit(this)
    is Type.Void -> visitor.visit(this)
}

fun <E, S, D, C> AST.ClassDeclaration<E, S, D>.accept(visitor: AbstractASTVisitor<E, S, D, C>) =
    visitor.visit(this)

fun <E, S, D, C> AST.ClassMember<E, S>.accept(visitor: AbstractASTVisitor<E, S, D, C>) = when (this) {
    is AST.Field -> visitor.visit(this)
    is AST.MainMethod -> visitor.visit(this)
    is AST.Method -> visitor.visit(this)
}

fun <E, S, D, C> AST.Program<E, S, D, C>.accept(visitor: AbstractASTVisitor<E, S, D, C>) = visitor.visit(this)
