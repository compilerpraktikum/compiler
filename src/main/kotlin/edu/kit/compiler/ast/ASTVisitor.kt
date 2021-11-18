package edu.kit.compiler.ast

import edu.kit.compiler.wrapper.Identity
import edu.kit.compiler.wrapper.Kind
import edu.kit.compiler.wrapper.Of
import edu.kit.compiler.wrapper.into

interface ASTVisitor<ExprW, StmtW, MethodW, ClassW, OtherW> {

    fun visit(program: AST.Program<ExprW, StmtW, MethodW, ClassW, OtherW>)

    fun visit(classDeclaration: AST.ClassDeclaration<ExprW, StmtW, MethodW, OtherW>)

    fun visit(field: AST.Field<OtherW>)

    fun visit(method: AST.Method<ExprW, StmtW, OtherW>)

    fun visit(mainMethod: AST.MainMethod<ExprW, StmtW, OtherW>)

    fun visit(parameter: AST.Parameter<OtherW>)

    fun visit(localVariableDeclarationStatement: AST.LocalVariableDeclarationStatement<ExprW, OtherW>)

    fun visit(block: AST.Block<ExprW, StmtW, OtherW>)

    fun visit(ifStatement: AST.IfStatement<ExprW, StmtW, OtherW>)

    fun visit(whileStatement: AST.WhileStatement<ExprW, StmtW, OtherW>)

    fun visit(returnStatement: AST.ReturnStatement<ExprW, OtherW>)

    fun visit(binaryExpression: AST.BinaryExpression<ExprW, OtherW>)

    fun visit(unaryExpression: AST.UnaryExpression<ExprW, OtherW>)

    fun visit(methodInvocationExpression: AST.MethodInvocationExpression<ExprW, OtherW>)

    fun visit(fieldAccessExpression: AST.FieldAccessExpression<ExprW, OtherW>)

    fun visit(arrayAccessExpression: AST.ArrayAccessExpression<ExprW, OtherW>)

    fun visit(identifierExpression: AST.IdentifierExpression)

    fun <T> visit(literalExpression: AST.LiteralExpression<T>)

    fun visit(newObjectExpression: AST.NewObjectExpression)

    fun visit(newArrayExpression: AST.NewArrayExpression<ExprW, OtherW>)

    fun visit(voidType: Type.Void)

    fun visit(integerType: Type.Integer)

    fun visit(booleanType: Type.Boolean)

    fun visit(arrayType: Type.Array<OtherW>)

    fun visit(arrayType: Type.Array.ArrayType<OtherW>)

    fun visit(classType: Type.Class)

    fun visit(operation: AST.BinaryExpression.Operation)

    fun visit(operation: AST.UnaryExpression.Operation)

    fun visit(expressionStatement: AST.ExpressionStatement<ExprW, OtherW>)
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
