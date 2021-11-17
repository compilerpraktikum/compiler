package edu.kit.compiler.ast

interface AbstractASTVisitor<E, S, D, C, O> {

    abstract fun visit(program: AST.Program<E, S, D, C, O>)

    abstract fun visit(classDeclaration: AST.ClassDeclaration<E, S, D, O>)

    abstract fun visit(field: AST.Field<O>)

    abstract fun visit(method: AST.Method<E, S, O>)

    abstract fun visit(mainMethod: AST.MainMethod<E, S, O>)

    abstract fun visit(parameter: AST.Parameter<O>)

    abstract fun visit(localVariableDeclarationStatement: AST.LocalVariableDeclarationStatement<E, O>)

    abstract fun visit(block: AST.Block<E, S, O>)

    abstract fun visit(ifStatement: AST.IfStatement<E, S, O>)

    abstract fun visit(whileStatement: AST.WhileStatement<E, S, O>)

    abstract fun visit(returnStatement: AST.ReturnStatement<E, O>)

    abstract fun visit(binaryExpression: AST.BinaryExpression<E, O>)

    abstract fun visit(unaryExpression: AST.UnaryExpression<E, O>)

    abstract fun visit(methodInvocationExpression: AST.MethodInvocationExpression<E, O>)

    abstract fun visit(fieldAccessExpression: AST.FieldAccessExpression<E, O>)

    abstract fun visit(arrayAccessExpression: AST.ArrayAccessExpression<E, O>)

    abstract fun visit(identifierExpression: AST.IdentifierExpression)

    abstract fun <T> visit(literalExpression: AST.LiteralExpression<T>)

    abstract fun visit(newObjectExpression: AST.NewObjectExpression)

    abstract fun visit(newArrayExpression: AST.NewArrayExpression<E, O>)

    abstract fun visit(voidType: Type.Void)

    abstract fun visit(integerType: Type.Integer)

    abstract fun visit(booleanType: Type.Boolean)

    abstract fun visit(arrayType: Type.Array<O>)

    abstract fun visit(arrayType: Type.Array.ArrayType<O>)

    abstract fun visit(classType: Type.Class)

    abstract fun visit(operation: AST.BinaryExpression.Operation)

    abstract fun visit(operation: AST.UnaryExpression.Operation)

    abstract fun visit(expressionStatement: AST.ExpressionStatement<E, O>)
}

fun <E, S, D, C, O> AST.Expression<E, O>.accept(visitor: AbstractASTVisitor<E, S, D, C, O>) = when (this) {
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
fun <S, D, C, O> Kind<Identity<Of>, Kind<AST.Expression<Of, O>, Identity<Of>>>.accept(visitor: AbstractASTVisitor<Identity<Of>, S, D, C, O>) =
    this.into().v.into().accept(visitor)

fun <E, S, D, C, O> AST.Statement<E, S, O>.accept(visitor: AbstractASTVisitor<E, S, D, C, O>) = when (this) {
    is AST.Block -> visitor.visit(this)
    is AST.ExpressionStatement -> visitor.visit(this)
    is AST.IfStatement -> visitor.visit(this)
    is AST.ReturnStatement -> visitor.visit(this)
    is AST.WhileStatement -> visitor.visit(this)
}

fun <E, S, D, C, O> AST.BlockStatement<E, S, O>.accept(visitor: AbstractASTVisitor<E, S, D, C, O>) = when (this) {
    is AST.LocalVariableDeclarationStatement -> visitor.visit(this)
    is AST.StmtWrapper -> this.statement.accept(visitor)
}

fun <E, S, D, C, O> AST.Parameter<O>.accept(visitor: AbstractASTVisitor<E, S, D, C, O>) = visitor.visit(this)

fun <E, S, D, C, O> Type<O>.accept(visitor: AbstractASTVisitor<E, S, D, C, O>) = when (this) {
    is Type.Array -> visitor.visit(this)
    is Type.Boolean -> visitor.visit(this)
    is Type.Class -> visitor.visit(this)
    is Type.Integer -> visitor.visit(this)
    is Type.Void -> visitor.visit(this)
}

fun <E, S, D, C, O> Type.Array.ArrayType<O>.accept(visitor: AbstractASTVisitor<E, S, D, C, O>) = visitor.visit(this)

fun <E, S, D, C, O> AST.ClassDeclaration<E, S, D, O>.accept(visitor: AbstractASTVisitor<E, S, D, C, O>) =
    visitor.visit(this)

fun <E, S, D, C, O> AST.ClassMember<E, S, O>.accept(visitor: AbstractASTVisitor<E, S, D, C, O>) = when (this) {
    is AST.Field -> visitor.visit(this)
    is AST.MainMethod -> visitor.visit(this)
    is AST.Method -> visitor.visit(this)
}

fun <E, S, D, C, O> AST.Program<E, S, D, C, O>.accept(visitor: AbstractASTVisitor<E, S, D, C, O>) = visitor.visit(this)

fun <E, S, D, C, O> AST.BinaryExpression.Operation.accept(visitor: AbstractASTVisitor<E, S, D, C, O>) =
    visitor.visit(this)

fun <E, S, D, C, O> AST.UnaryExpression.Operation.accept(visitor: AbstractASTVisitor<E, S, D, C, O>) =
    visitor.visit(this)
