package edu.kit.compiler.ast

interface ASTVisitor<E, S, D, C, O> {

    fun visit(program: AST.Program<E, S, D, C, O>)

    fun visit(classDeclaration: AST.ClassDeclaration<E, S, D, O>)

    fun visit(field: AST.Field<O>)

    fun visit(method: AST.Method<E, S, O>)

    fun visit(mainMethod: AST.MainMethod<E, S, O>)

    fun visit(parameter: AST.Parameter<O>)

    fun visit(localVariableDeclarationStatement: AST.LocalVariableDeclarationStatement<E, O>)

    fun visit(block: AST.Block<E, S, O>)

    fun visit(ifStatement: AST.IfStatement<E, S, O>)

    fun visit(whileStatement: AST.WhileStatement<E, S, O>)

    fun visit(returnStatement: AST.ReturnStatement<E, O>)

    fun visit(binaryExpression: AST.BinaryExpression<E, O>)

    fun visit(unaryExpression: AST.UnaryExpression<E, O>)

    fun visit(methodInvocationExpression: AST.MethodInvocationExpression<E, O>)

    fun visit(fieldAccessExpression: AST.FieldAccessExpression<E, O>)

    fun visit(arrayAccessExpression: AST.ArrayAccessExpression<E, O>)

    fun visit(identifierExpression: AST.IdentifierExpression)

    fun <T> visit(literalExpression: AST.LiteralExpression<T>)

    fun visit(newObjectExpression: AST.NewObjectExpression)

    fun visit(newArrayExpression: AST.NewArrayExpression<E, O>)

    fun visit(voidType: Type.Void)

    fun visit(integerType: Type.Integer)

    fun visit(booleanType: Type.Boolean)

    fun visit(arrayType: Type.Array<O>)

    fun visit(arrayType: Type.Array.ArrayType<O>)

    fun visit(classType: Type.Class)

    fun visit(operation: AST.BinaryExpression.Operation)

    fun visit(operation: AST.UnaryExpression.Operation)

    fun visit(expressionStatement: AST.ExpressionStatement<E, O>)
}

fun <E, S, D, C, O> AST.Expression<E, O>.accept(visitor: ASTVisitor<E, S, D, C, O>) = when (this) {
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
fun <S, D, C, O> Kind<Identity<Of>, Kind<AST.Expression<Of, O>, Identity<Of>>>.accept(visitor: ASTVisitor<Identity<Of>, S, D, C, O>) =
    this.into().v.into().accept(visitor)

fun <E, S, D, C, O> AST.Statement<E, S, O>.accept(visitor: ASTVisitor<E, S, D, C, O>) = when (this) {
    is AST.Block -> visitor.visit(this)
    is AST.ExpressionStatement -> visitor.visit(this)
    is AST.IfStatement -> visitor.visit(this)
    is AST.ReturnStatement -> visitor.visit(this)
    is AST.WhileStatement -> visitor.visit(this)
}

fun <E, S, D, C, O> AST.BlockStatement<E, S, O>.accept(visitor: ASTVisitor<E, S, D, C, O>) = when (this) {
    is AST.LocalVariableDeclarationStatement -> visitor.visit(this)
    is AST.StmtWrapper -> this.statement.accept(visitor)
}

fun <E, S, D, C, O> AST.Parameter<O>.accept(visitor: ASTVisitor<E, S, D, C, O>) = visitor.visit(this)

fun <E, S, D, C, O> Type<O>.accept(visitor: ASTVisitor<E, S, D, C, O>) = when (this) {
    is Type.Array -> visitor.visit(this)
    is Type.Boolean -> visitor.visit(this)
    is Type.Class -> visitor.visit(this)
    is Type.Integer -> visitor.visit(this)
    is Type.Void -> visitor.visit(this)
}

fun <E, S, D, C, O> Type.Array.ArrayType<O>.accept(visitor: ASTVisitor<E, S, D, C, O>) = visitor.visit(this)

fun <E, S, D, C, O> AST.ClassDeclaration<E, S, D, O>.accept(visitor: ASTVisitor<E, S, D, C, O>) =
    visitor.visit(this)

fun <E, S, D, C, O> AST.ClassMember<E, S, O>.accept(visitor: ASTVisitor<E, S, D, C, O>) = when (this) {
    is AST.Field -> visitor.visit(this)
    is AST.MainMethod -> visitor.visit(this)
    is AST.Method -> visitor.visit(this)
}

fun <E, S, D, C, O> AST.Program<E, S, D, C, O>.accept(visitor: ASTVisitor<E, S, D, C, O>) = visitor.visit(this)

fun <E, S, D, C, O> AST.BinaryExpression.Operation.accept(visitor: ASTVisitor<E, S, D, C, O>) =
    visitor.visit(this)

fun <E, S, D, C, O> AST.UnaryExpression.Operation.accept(visitor: ASTVisitor<E, S, D, C, O>) =
    visitor.visit(this)
