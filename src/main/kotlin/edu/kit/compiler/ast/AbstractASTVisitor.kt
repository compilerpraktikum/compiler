package edu.kit.compiler.ast

import edu.kit.compiler.lex.Symbol

/**
 * This interface is inhabited by Wrappers [F], where
 * their wrapped value can be extracted from:
 * @sample IntoWrapperExample
 */
interface Unwrappable<F> {
    fun <A> unwrapValue(fa: Kind<F, A>): A
}

fun <A, F> Kind<F, A>.unwrap(wrapper: Unwrappable<F>): A = wrapper.unwrapValue(this)

private object IntoWrapperExample {
    object IntoWrapperIdentity : Unwrappable<Identity<Of>> {
        override fun <A> unwrapValue(fa: Kind<Identity<Of>, A>) = fa.into().v
    }

    val wrappedValue: Kind<Identity<Of>, AST.Expression<Of, Identity<Of>>> = TODO()
    val notWrappedValue: AST.Expression<Of, Identity<Of>> = wrappedValue.unwrap(IntoWrapperIdentity)
}

abstract class TreeWalkingAstVisitor<ExprW, StmtW, DeclW, ClassW, OtherW>(
    val unwrapExpr: Unwrappable<ExprW>,
    private val unwrapStmt: Unwrappable<StmtW>,
    private val unwrapDecl: Unwrappable<DeclW>,
    private val unwrapClass: Unwrappable<ClassW>,
    private val unwrapOther: Unwrappable<OtherW>
) :
    AbstractASTVisitor<ExprW, StmtW, DeclW, ClassW, OtherW> {
    override fun visit(program: AST.Program<ExprW, StmtW, DeclW, ClassW, OtherW>) {
        program.classes.forEach { it.unwrap(unwrapClass).accept(this) }
    }

    override fun visit(classDeclaration: AST.ClassDeclaration<ExprW, StmtW, DeclW, OtherW>) {
        classDeclaration.member.forEach { it.unwrap(unwrapDecl).accept(this) }
    }

    override fun visit(field: AST.Field<OtherW>) {
        field.type.unwrap(unwrapOther).into().accept(this)
    }

    override fun visit(method: AST.Method<ExprW, StmtW, OtherW>) {
        method.returnType.unwrap(unwrapOther).into().accept(this)
        method.parameters.forEach { it.unwrap(unwrapOther).into().accept(this) }
        method.block.unwrap(unwrapStmt).accept(this)
    }

    override fun visit(mainMethod: AST.MainMethod<ExprW, StmtW, OtherW>) {
        mainMethod.returnType.unwrap(unwrapOther).into().accept(this)
        mainMethod.parameters.forEach { it.unwrap(unwrapOther).into().accept(this) }
        mainMethod.block.unwrap(unwrapStmt).accept(this)
    }

    override fun visit(parameter: AST.Parameter<OtherW>) {
        parameter.type.unwrap(unwrapOther).into().accept(this)
    }

    override fun visit(localVariableDeclarationStatement: AST.LocalVariableDeclarationStatement<ExprW, OtherW>) {
        localVariableDeclarationStatement.type.unwrap(unwrapOther).into().accept(this)
        localVariableDeclarationStatement.initializer?.also { it.unwrap(unwrapExpr) }
    }

    override fun visit(block: AST.Block<ExprW, StmtW, OtherW>) {
        block.statements.forEach { it.unwrap(unwrapStmt).into() }
    }

    override fun visit(ifStatement: AST.IfStatement<ExprW, StmtW, OtherW>) {
        ifStatement.condition.unwrap(unwrapExpr).into().accept(this)
        ifStatement.trueStatement.unwrap(unwrapStmt).into().accept(this)
        ifStatement.falseStatement?.also { it.unwrap(unwrapStmt).into().accept(this) }
    }

    override fun visit(whileStatement: AST.WhileStatement<ExprW, StmtW, OtherW>) {
        whileStatement.condition.unwrap(unwrapExpr).into().accept(this)
        whileStatement.statement.unwrap(unwrapStmt).into().accept(this)
    }

    override fun visit(returnStatement: AST.ReturnStatement<ExprW, OtherW>) {
        returnStatement.expression?.also { it.unwrap(unwrapExpr).into().accept(this) }
    }

    override fun visit(binaryExpression: AST.BinaryExpression<ExprW, OtherW>) {
        binaryExpression.left.unwrap(unwrapExpr).into().accept(this)
        binaryExpression.right.unwrap(unwrapExpr).into().accept(this)
        binaryExpression.operation.accept(this)
    }

    override fun visit(unaryExpression: AST.UnaryExpression<ExprW, OtherW>) {
        unaryExpression.expression.unwrap(unwrapExpr).into().accept(this)
        unaryExpression.operation.accept(this)
    }

    override fun visit(methodInvocationExpression: AST.MethodInvocationExpression<ExprW, OtherW>) {
        methodInvocationExpression.target?.also { it.unwrap(unwrapExpr).into().accept(this) }
        methodInvocationExpression.arguments.forEach { it.unwrap(unwrapExpr).into().accept(this) }
    }

    override fun visit(fieldAccessExpression: AST.FieldAccessExpression<ExprW, OtherW>) {
        fieldAccessExpression.target.unwrap(unwrapExpr).into().accept(this)
    }

    override fun visit(arrayAccessExpression: AST.ArrayAccessExpression<ExprW, OtherW>) {
        arrayAccessExpression.index.unwrap(unwrapExpr).into().accept(this)
        arrayAccessExpression.target.unwrap(unwrapExpr).into().accept(this)
    }

    override fun visit(identifierExpression: AST.IdentifierExpression) {
        //Nothing to do
    }

    override fun <T> visit(literalExpression: AST.LiteralExpression<T>) {
        //Nothing to do
    }

    override fun visit(newObjectExpression: AST.NewObjectExpression) {
        //Nothing to do
    }

    override fun visit(newArrayExpression: AST.NewArrayExpression<ExprW, OtherW>) {
        newArrayExpression.type.unwrap(unwrapOther).into().accept(this)
        newArrayExpression.length.unwrap(unwrapExpr).into().accept(this)
    }

    override fun visit(voidType: Type.Void) {
        //Nothing to do
    }

    override fun visit(integerType: Type.Integer) {
        //Nothing to do
    }

    override fun visit(booleanType: Type.Boolean) {
        //Nothing to do
    }

    override fun visit(arrayType: Type.Array<OtherW>) {
        arrayType.arrayType.accept(this)
    }

    override fun visit(classType: Type.Class) {
        //Nothing to do
    }

    override fun visit(operation: AST.BinaryExpression.Operation) {
        //Nothing to do
    }

    override fun visit(expressionStatement: AST.ExpressionStatement<ExprW, OtherW>) {
        expressionStatement.expression.unwrap(unwrapExpr).into().accept(this)
    }
}

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
