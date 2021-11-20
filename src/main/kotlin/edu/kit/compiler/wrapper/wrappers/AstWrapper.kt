package edu.kit.compiler.wrapper.wrappers

import edu.kit.compiler.ast.AST
import edu.kit.compiler.ast.Type
import edu.kit.compiler.wrapper.Functor
import edu.kit.compiler.wrapper.Kind
import edu.kit.compiler.wrapper.Of
import edu.kit.compiler.wrapper.fmap
import edu.kit.compiler.wrapper.into
import java.beans.Expression

// -------------------------- Unwrappable Instance ----------------------------//

interface NaturalTransformation<F, G> {
    fun <A> trans(fa: Kind<F, A>): Kind<G, A>
}

fun <OtherW1, OtherW2> Type<OtherW1>.map1(
    functorOther: Functor<OtherW2>,
    transOther: NaturalTransformation<OtherW1, OtherW2>
): Type<OtherW2> =
    this.mapOtherW {
        transOther.trans(it).fmap(functorOther) {
            it.into().map1(functorOther, transOther)
        }
    }

fun <OtherW1, OtherW2> Kind<OtherW1, Kind<Type<Of>, OtherW1>>.map1(
    functorOther: Functor<OtherW2>,
    transOther: NaturalTransformation<OtherW1, OtherW2>,
): Kind<OtherW2, Type<OtherW2>> =
    transOther.trans(this).fmap(functorOther) {
        it.into().map1(functorOther, transOther)
    }

fun <WrapperBefore, WrapperAfter> AST.Program<WrapperBefore, WrapperBefore, WrapperBefore, WrapperBefore, WrapperBefore>.map1(
    functor: Functor<WrapperAfter>,
    transformation: NaturalTransformation<WrapperBefore, WrapperAfter>
): AST.Program<WrapperAfter, WrapperAfter, WrapperAfter, WrapperAfter, WrapperAfter> = this.map1(
    functor,
    functor,
    functor,
    functor,
    functor,
    transformation,
    transformation,
    transformation,
    transformation,
    transformation
)

fun <ExprW1, ExprW2, StmtW1, StmtW2, MethodW1, MethodW2, ClassW1, ClassW2, OtherW1, OtherW2> AST.Program<ExprW1, StmtW1, MethodW1, ClassW1, OtherW1>.map1(
    functorExpr: Functor<ExprW2>,
    functorStmt: Functor<StmtW2>,
    functorMethod: Functor<MethodW2>,
    functorClass: Functor<ClassW2>,
    functorOther: Functor<OtherW2>,
    transExpr: NaturalTransformation<ExprW1, ExprW2>,
    transStmt: NaturalTransformation<StmtW1, StmtW2>,
    transMethod: NaturalTransformation<MethodW1, MethodW2>,
    transClass: NaturalTransformation<ClassW1, ClassW2>,
    transOther: NaturalTransformation<OtherW1, OtherW2>,
): AST.Program<ExprW2, StmtW2, MethodW2, ClassW2, OtherW2> =
    this.mapClassW { it ->
        transClass.trans(it).fmap(functorClass) {
            it.map1(
                functorExpr,
                functorStmt,
                functorMethod,
                functorOther,
                transExpr,
                transStmt,
                transMethod,
                transOther
            )
        }
    }

fun <ExprW1, ExprW2, StmtW1, StmtW2, MethodW1, MethodW2, OtherW1, OtherW2> AST.ClassDeclaration<ExprW1, StmtW1, MethodW1, OtherW1>.map1(
    functorExpr: Functor<ExprW2>,
    functorStmt: Functor<StmtW2>,
    functorMethod: Functor<MethodW2>,
    functorOther: Functor<OtherW2>,
    transExpr: NaturalTransformation<ExprW1, ExprW2>,
    transStmt: NaturalTransformation<StmtW1, StmtW2>,
    transMethod: NaturalTransformation<MethodW1, MethodW2>,
    transOther: NaturalTransformation<OtherW1, OtherW2>,
): AST.ClassDeclaration<ExprW2, StmtW2, MethodW2, OtherW2> =
    this.mapMethodW {
        transMethod.trans(it).fmap(functorMethod) {
            it.mapMember({
                it.map1(functorOther, transOther)
            }, {
                transOther.trans(it).fmap(functorOther) {
                    it.into().mapOtherW {
                        it.map1(functorOther, transOther)
                    }
                }
            }, {
                transStmt.trans(it).fmap(functorStmt) {
                    it.mapStmt {
                        transStmt.trans(it).fmap(functorStmt) {
                            it.into().map1(
                                functorExpr,
                                functorStmt,
                                functorOther,
                                transExpr,
                                transStmt,
                                transOther
                            )
                        }
                    }
                }
            })
        }
    }

inline fun <ExprW1, ExprW2, StmtW1, StmtW2, OtherW1, OtherW2> AST.BlockStatement<ExprW1, StmtW1, OtherW1>.map1(
    functorExpr: Functor<ExprW2>,
    functorStmt: Functor<StmtW2>,
    functorOther: Functor<OtherW2>,
    transExpr: NaturalTransformation<ExprW1, ExprW2>,
    transStmt: NaturalTransformation<StmtW1, StmtW2>,
    transOther: NaturalTransformation<OtherW1, OtherW2>,
): AST.BlockStatement<ExprW2, StmtW2, OtherW2> =
    this.mapBlockStmt({
        it.map1(functorExpr, functorStmt, functorOther, transExpr, transStmt, transOther)
    }, {
        transExpr.trans(it).fmap(functorExpr) {
            it.into().map1(functorExpr, functorOther, transExpr, transOther)
        }
    }, {
        transOther.trans(it).fmap(functorOther) {
            it.into().map1(functorOther, transOther)
        }
    })

fun <ExprW1, ExprW2, StmtW1, StmtW2, OtherW1, OtherW2> AST.Block<ExprW1, StmtW1, OtherW1>.map1(
    functorExpr: Functor<ExprW2>,
    functorStmt: Functor<StmtW2>,
    functorOther: Functor<OtherW2>,
    transExpr: NaturalTransformation<ExprW1, ExprW2>,
    transStmt: NaturalTransformation<StmtW1, StmtW2>,
    transOther: NaturalTransformation<OtherW1, OtherW2>,
): AST.Block<ExprW2, StmtW2, OtherW2> =
    this.mapStmt {
        transStmt.trans(it).fmap(functorStmt) {
            it.into().map1(functorExpr, functorStmt, functorOther, transExpr, transStmt, transOther)
        }
    }

fun <ExprW1, ExprW2, StmtW1, StmtW2, OtherW1, OtherW2> AST.Statement<ExprW1, StmtW1, OtherW1>.map1(
    functorExpr: Functor<ExprW2>,
    functorStmt: Functor<StmtW2>,
    functorOther: Functor<OtherW2>,
    transExpr: NaturalTransformation<ExprW1, ExprW2>,
    transStmt: NaturalTransformation<StmtW1, StmtW2>,
    transOther: NaturalTransformation<OtherW1, OtherW2>,
): AST.Statement<ExprW2, StmtW2, OtherW2> =
    this.mapStmt({
        transStmt.trans(it).fmap(functorStmt) {
            it.into().map1(functorExpr, functorStmt, functorOther, transExpr, transStmt, transOther)
        }
    }, {
        transStmt.trans(it).fmap(functorStmt) {
            it.into().map1(functorExpr, functorStmt, functorOther, transExpr, transStmt, transOther)
        }
    }, {
        transExpr.trans(it).fmap(functorExpr) {
            it.into().map1(functorExpr, functorOther, transExpr, transOther)
        }
    })

fun <ExprW1, ExprW2, OtherW1, OtherW2> AST.Expression<ExprW1, OtherW1>.map1(
    functorExpr: Functor<ExprW2>,
    functorOther: Functor<OtherW2>,
    transExpr: NaturalTransformation<ExprW1, ExprW2>,
    transOther: NaturalTransformation<OtherW1, OtherW2>,
): AST.Expression<ExprW2, OtherW2> =
    this.mapExprW({
        transExpr.trans(it).fmap(functorExpr) {
            it.into().map1(functorExpr, functorOther, transExpr, transOther)
        }
    }, {
        transOther.trans(it).fmap(functorOther) {
            Type.Array.ArrayType(
                it.into().elementType
                    .let { transOther.trans(it) }
                    .fmap(functorOther) {
                        it.into().map1(functorOther, transOther)
                    }
            )
        }
    })

/**
 * `mapClassW` converts from one `ClassWrapper` generic to another.
 * The function is marked `inline` to allow early returns from the lambda expression
 */
inline fun <ExprW1, ExprW2, StmtW1, StmtW2, MethW1, MethW2, ClassW1, ClassW2, OtherW1, OtherW2> AST.Program<ExprW1, StmtW1, MethW1, ClassW1, OtherW1>.mapClassW(
    f: (Kind<ClassW1, AST.ClassDeclaration<ExprW1, StmtW1, MethW1, OtherW1>>)
    -> Kind<ClassW2, AST.ClassDeclaration<ExprW2, StmtW2, MethW2, OtherW2>>
): AST.Program<ExprW2, StmtW2, MethW2, ClassW2, OtherW2> =
    AST.Program(this.classes.map(f))

inline fun <ExprW1, ExprW2, StmtW1, StmtW2, MethW1, MethW2, OtherW1, OtherW2> AST.ClassDeclaration<ExprW1, StmtW1, MethW1, OtherW1>.mapMethodW(
    f: (Kind<MethW1, AST.ClassMember<ExprW1, StmtW1, OtherW1>>)
    -> Kind<MethW2, AST.ClassMember<ExprW2, StmtW2, OtherW2>>
): AST.ClassDeclaration<ExprW2, StmtW2, MethW2, OtherW2> =
    AST.ClassDeclaration(name, member.map(f))

inline fun <OtherW1, OtherW2> Type<OtherW1>.mapOtherW(
    f: (Kind<OtherW1, Kind<Type<Of>, OtherW1>>) -> Kind<OtherW2, Kind<Type<Of>, OtherW2>>
): Type<OtherW2> =
    when (this) {
        is Type.Array -> Type.Array(Type.Array.ArrayType(f(arrayType.elementType)))
        is Type.Boolean -> this
        is Type.Class -> this
        is Type.Integer -> this
        is Type.Void -> this
    }

inline fun <OtherW1, OtherW2> AST.Field<OtherW1>.mapOtherW(
    f: (Kind<OtherW1, Kind<Type<Of>, OtherW1>>) -> Kind<OtherW2, Kind<Type<Of>, OtherW2>>
): AST.Field<OtherW2> =
    AST.Field(name, f(type))

inline fun <OtherW1, OtherW2> AST.Parameter<OtherW1>.mapOtherW(
    f: (Kind<OtherW1, Kind<Type<Of>, OtherW1>>) -> Kind<OtherW2, Kind<Type<Of>, OtherW2>>
): AST.Parameter<OtherW2> =
    AST.Parameter(name, f(type))

inline fun <ExprW1, ExprW2, StmtW1, StmtW2, OtherW1, OtherW2> AST.MainMethod<ExprW1, StmtW1, OtherW1>.mapStmt(
    mapType: (Kind<OtherW1, Kind<Type<Of>, OtherW1>>) -> Kind<OtherW2, Kind<Type<Of>, OtherW2>>,
    mapParameter: (Kind<OtherW1, Kind<AST.Parameter<Of>, OtherW1>>) -> Kind<OtherW2, Kind<AST.Parameter<Of>, OtherW2>>,
    mapBlock: (Kind<StmtW1, AST.Block<ExprW1, StmtW1, OtherW1>>) -> Kind<StmtW2, AST.Block<ExprW2, StmtW2, OtherW2>>,
): AST.MainMethod<ExprW2, StmtW2, OtherW2> = AST.MainMethod(
    name, mapType(returnType), parameters.map { mapParameter(it) }, mapBlock(block), throwsException
)

inline fun <ExprW1, ExprW2, StmtW1, StmtW2, OtherW1, OtherW2> AST.Method<ExprW1, StmtW1, OtherW1>.mapStmt(
    mapType: (Kind<OtherW1, Kind<Type<Of>, OtherW1>>) -> Kind<OtherW2, Kind<Type<Of>, OtherW2>>,
    mapParameter: (Kind<OtherW1, Kind<AST.Parameter<Of>, OtherW1>>) -> Kind<OtherW2, Kind<AST.Parameter<Of>, OtherW2>>,
    mapBlock: (Kind<StmtW1, AST.Block<ExprW1, StmtW1, OtherW1>>) -> Kind<StmtW2, AST.Block<ExprW2, StmtW2, OtherW2>>,
): AST.Method<ExprW2, StmtW2, OtherW2> = AST.Method(
    name, mapType(returnType), parameters.map { mapParameter(it) }, mapBlock(block), throwsException
)

inline fun <ExprW1, ExprW2, StmtW1, StmtW2, OtherW1, OtherW2> AST.ClassMember<ExprW1, StmtW1, OtherW1>.mapMember(
    mapType: (Kind<OtherW1, Kind<Type<Of>, OtherW1>>) -> Kind<OtherW2, Kind<Type<Of>, OtherW2>>,
    mapParameter: (Kind<OtherW1, Kind<AST.Parameter<Of>, OtherW1>>) -> Kind<OtherW2, Kind<AST.Parameter<Of>, OtherW2>>,
    mapBlock: (Kind<StmtW1, AST.Block<ExprW1, StmtW1, OtherW1>>) -> Kind<StmtW2, AST.Block<ExprW2, StmtW2, OtherW2>>,
): AST.ClassMember<ExprW2, StmtW2, OtherW2> =
    when (this) {
        is AST.Field -> this.mapOtherW(mapType)
        is AST.MainMethod -> this.mapStmt(mapType, mapParameter, mapBlock)
        is AST.Method -> this.mapStmt(mapType, mapParameter, mapBlock)
    }

inline fun <StmtW1, StmtW2, ExprW1, ExprW2, OtherW1, OtherW2> AST.Block<ExprW1, StmtW1, OtherW1>.mapStmt(
    mapBlockStatement: (Kind<StmtW1, Kind<AST.BlockStatement<ExprW1, Of, OtherW1>, StmtW1>>) -> Kind<StmtW2, Kind<AST.BlockStatement<ExprW2, Of, OtherW2>, StmtW2>>
): AST.Block<ExprW2, StmtW2, OtherW2> =
    AST.Block(this.statements.map { mapBlockStatement(it) })

inline fun <ExprW1, ExprW2, StmtW1, StmtW2, OtherW1, OtherW2> AST.Statement<ExprW1, StmtW1, OtherW1>.mapStmt(
    mapBlockStatement: (Kind<StmtW1, Kind<AST.BlockStatement<ExprW1, Of, OtherW1>, StmtW1>>) -> Kind<StmtW2, Kind<AST.BlockStatement<ExprW2, Of, OtherW2>, StmtW2>>,
    mapStatement: (Kind<StmtW1, Kind<AST.Statement<ExprW1, Of, OtherW1>, StmtW1>>) -> Kind<StmtW2, Kind<AST.Statement<ExprW2, Of, OtherW2>, StmtW2>>,
    mapExpression: (Kind<ExprW1, Kind<AST.Expression<Of, OtherW1>, ExprW1>>) -> Kind<ExprW2, Kind<AST.Expression<Of, OtherW2>, ExprW2>>,
):
    AST.Statement<ExprW2, StmtW2, OtherW2> = when (this) {
    is AST.Block -> AST.Block(
        this.statements.map { mapBlockStatement(it) }
    )
    is AST.ExpressionStatement ->
        AST.ExpressionStatement(
            mapExpression(expression)
        )
    is AST.IfStatement ->
        AST.IfStatement(
            mapExpression(condition),
            mapStatement(trueStatement),
            falseStatement?.let { mapStatement(it) }
        )
    is AST.ReturnStatement ->
        AST.ReturnStatement(
            expression?.let { justExpr ->
                mapExpression(justExpr)
            }
        )

    is AST.WhileStatement ->
        AST.WhileStatement(
            mapExpression(condition),
            mapStatement(statement),
        )
}

inline fun <ExprW1, ExprW2, StmtW1, StmtW2, OtherW1, OtherW2> AST.BlockStatement<ExprW1, StmtW1, OtherW1>.mapBlockStmt(
    mapStatement: (AST.Statement<ExprW1, StmtW1, OtherW1>) -> AST.Statement<ExprW2, StmtW2, OtherW2>,
    mapExpression: (Kind<ExprW1, Kind<AST.Expression<Of, OtherW1>, ExprW1>>) -> Kind<ExprW2, Kind<AST.Expression<Of, OtherW2>, ExprW2>>,
    mapType: (Kind<OtherW1, Kind<Type<Of>, OtherW1>>) -> Kind<OtherW2, Kind<Type<Of>, OtherW2>>
):
    AST.BlockStatement<ExprW2, StmtW2, OtherW2> =
    when (this) {
        is AST.LocalVariableDeclarationStatement ->
            AST.LocalVariableDeclarationStatement(name, mapType(type), initializer?.let { mapExpression(it) })
        is AST.StmtWrapper ->
            AST.StmtWrapper(mapStatement(statement))
    }

inline fun <ExprW1, ExprW2, OtherW1, OtherW2> AST.Expression<ExprW1, OtherW1>.mapExprW(
    f: (Kind<ExprW1, Kind<AST.Expression<Of, OtherW1>, ExprW1>>) -> Kind<ExprW2, Kind<AST.Expression<Of, OtherW2>, ExprW2>>,
    g: (Kind<OtherW1, Kind<Type.Array.ArrayType<Of>, OtherW1>>) -> Kind<OtherW2, Kind<Type.Array.ArrayType<Of>, OtherW2>>
): AST.Expression<ExprW2, OtherW2> =
    when (this) {
        is AST.ArrayAccessExpression ->
            AST.ArrayAccessExpression(
                f(target),
                f(index)
            )
        is AST.BinaryExpression ->
            AST.BinaryExpression(
                f(left),
                f(right),
                operation
            )
        is AST.FieldAccessExpression ->
            AST.FieldAccessExpression(f(target), field)
        is AST.IdentifierExpression -> this
        is AST.LiteralExpression<*> -> this
        is AST.MethodInvocationExpression ->
            AST.MethodInvocationExpression(
                target?.let { target -> f(target) },
                method,
                arguments.map { arg -> f(arg) }
            )
        is AST.NewArrayExpression ->
            AST.NewArrayExpression(
                g(type),
                f(length)
            )
        is AST.NewObjectExpression -> this
        is AST.UnaryExpression ->
            AST.UnaryExpression(f(expression), operation)
    }
