package edu.kit.compiler.ast

/**
 * This interface is inhabited by Wrappers [F], where
 * their wrapped value can be extracted from:
 * @sample IntoWrapperExample
 */
interface IntoWrapper<F> {
    fun <A> intoW(fa: Kind<F, A>): A
}

object IntoWrapperExample {
    object IntoWrapperIdentity : IntoWrapper<Identity<Of>> {
        override fun <A> intoW(fa: Kind<Identity<Of>, A>) = fa.into().v
    }
    val wrappedValue: Kind<Identity<Of>, AST.Expression<Of, Identity<Of>>> = TODO()
    val notWrappedValue: AST.Expression<Of, Identity<Of>> = IntoWrapperIdentity.intoW(wrappedValue)
}

abstract class TreeWalkingAstVisitor<ExprW, StmtW, DeclW, ClassW, OtherW>(
    val intoExpr: IntoWrapper<ExprW>,
    val intoStmt: IntoWrapper<StmtW>,
    val intoDecl: IntoWrapper<DeclW>,
    private val intoClass: IntoWrapper<ClassW>,
    val intoOther: IntoWrapper<OtherW>
) :
    AbstractASTVisitor<ExprW, StmtW, DeclW, ClassW, OtherW> {
    override fun visit(program: AST.Program<ExprW, StmtW, DeclW, ClassW, OtherW>) {
        program.classes.map { intoClass.intoW(it).accept(this) }
    }

    override fun visit(classDeclaration: AST.ClassDeclaration<ExprW, StmtW, DeclW, OtherW>) {
        TODO("Not yet implemented")
    }

    override fun visit(field: AST.Field<OtherW>) {
        TODO("Not yet implemented")
    }

    override fun visit(method: AST.Method<ExprW, StmtW, OtherW>) {
        TODO("Not yet implemented")
    }

    override fun visit(mainMethod: AST.MainMethod<ExprW, StmtW, OtherW>) {
        TODO("Not yet implemented")
    }

    override fun visit(parameter: AST.Parameter<OtherW>) {
        TODO("Not yet implemented")
    }

    override fun visit(localVariableDeclarationStatement: AST.LocalVariableDeclarationStatement<ExprW, OtherW>) {
        TODO("Not yet implemented")
    }

    override fun visit(block: AST.Block<StmtW, ExprW, OtherW>) {
        TODO("Not yet implemented")
    }

    override fun visit(ifStatement: AST.IfStatement<StmtW, ExprW, OtherW>) {
        TODO("Not yet implemented")
    }

    override fun visit(whileStatement: AST.WhileStatement<StmtW, ExprW, OtherW>) {
        TODO("Not yet implemented")
    }

    override fun visit(returnStatement: AST.ReturnStatement<ExprW, OtherW>) {
        TODO("Not yet implemented")
    }

    override fun visit(binaryExpression: AST.BinaryExpression<ExprW, OtherW>) {
        TODO("Not yet implemented")
    }

    override fun visit(unaryExpression: AST.UnaryExpression<ExprW, OtherW>) {
        TODO("Not yet implemented")
    }

    override fun visit(methodInvocationExpression: AST.MethodInvocationExpression<ExprW, OtherW>) {
        TODO("Not yet implemented")
    }

    override fun visit(fieldAccessExpression: AST.FieldAccessExpression<ExprW, OtherW>) {
        TODO("Not yet implemented")
    }

    override fun visit(arrayAccessExpression: AST.ArrayAccessExpression<ExprW, OtherW>) {
        TODO("Not yet implemented")
    }

    override fun visit(identifierExpression: AST.IdentifierExpression) {
        TODO("Not yet implemented")
    }

    override fun <T> visit(literalExpression: AST.LiteralExpression<T>) {
        TODO("Not yet implemented")
    }

    override fun visit(newObjectExpression: AST.NewObjectExpression) {
        TODO("Not yet implemented")
    }

    override fun visit(newArrayExpression: AST.NewArrayExpression<ExprW, OtherW>) {
        TODO("Not yet implemented")
    }

    override fun visit(voidType: Type.Void) {
        TODO("Not yet implemented")
    }

    override fun visit(integerType: Type.Integer) {
        TODO("Not yet implemented")
    }

    override fun visit(booleanType: Type.Boolean) {
        TODO("Not yet implemented")
    }

    override fun visit(arrayType: Type.Array<OtherW>) {
        TODO("Not yet implemented")
    }

    override fun visit(classType: Type.Class) {
        TODO("Not yet implemented")
    }

    override fun visit(operation: AST.BinaryExpression.Operation) {
        TODO("Not yet implemented")
    }

    override fun visit(expressionStatement: AST.ExpressionStatement<ExprW, OtherW>) {
        TODO("Not yet implemented")
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

    abstract fun visit(block: AST.Block<S, E, O>)

    abstract fun visit(ifStatement: AST.IfStatement<S, E, O>)

    abstract fun visit(whileStatement: AST.WhileStatement<S, E, O>)

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

    abstract fun visit(classType: Type.Class)

    abstract fun visit(operation: AST.BinaryExpression.Operation)

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

fun <E, S, D, C, O> AST.Statement<S, E, O>.accept(visitor: AbstractASTVisitor<E, S, D, C, O>) = when (this) {
    is AST.Block -> visitor.visit(this)
    is AST.ExpressionStatement -> visitor.visit(this)
    is AST.IfStatement -> visitor.visit(this)
    is AST.ReturnStatement -> visitor.visit(this)
    is AST.WhileStatement -> visitor.visit(this)
}

fun <E, S, D, C, O> AST.BlockStatement<S, E, O>.accept(visitor: AbstractASTVisitor<E, S, D, C, O>) = when (this) {
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

fun <E, S, D, C, O> AST.ClassDeclaration<E, S, D, O>.accept(visitor: AbstractASTVisitor<E, S, D, C, O>) =
    visitor.visit(this)

fun <E, S, D, C, O> AST.ClassMember<E, S, O>.accept(visitor: AbstractASTVisitor<E, S, D, C, O>) = when (this) {
    is AST.Field -> visitor.visit(this)
    is AST.MainMethod -> visitor.visit(this)
    is AST.Method -> visitor.visit(this)
}

fun <E, S, D, C, O> AST.Program<E, S, D, C, O>.accept(visitor: AbstractASTVisitor<E, S, D, C, O>) = visitor.visit(this)
