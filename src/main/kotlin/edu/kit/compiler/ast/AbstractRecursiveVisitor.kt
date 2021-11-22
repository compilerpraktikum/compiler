package edu.kit.compiler.ast

import edu.kit.compiler.wrapper.Kind
import edu.kit.compiler.wrapper.into
import edu.kit.compiler.wrapper.wrappers.mapBlockStmt
import edu.kit.compiler.wrapper.wrappers.mapClassW
import edu.kit.compiler.wrapper.wrappers.mapExprW
import edu.kit.compiler.wrapper.wrappers.mapMember
import edu.kit.compiler.wrapper.wrappers.mapMethodW
import edu.kit.compiler.wrapper.wrappers.mapOtherW
import edu.kit.compiler.wrapper.wrappers.mapStmt

interface Functor<F> {
    fun <A, B> functorMap(f: (A) -> B, fa: Kind<F, A>): Kind<F, B>
}

fun <A, B, F> Kind<F, A>.fmap(wrapper: Functor<F>, f: (A) -> B): Kind<F, B> = wrapper.functorMap(f, this)

data class AllFunctors<ExprW, StmtW, MethodW, ClassW, OtherW>(
    val functorExpr: Functor<ExprW>,
    val functorStmt: Functor<StmtW>,
    val functorDecl: Functor<MethodW>,
    val functorClass: Functor<ClassW>,
    val functorOther: Functor<OtherW>
)

object DefaultVisitors {
    abstract class DefaultStmtVisitor<ExprW, StmtW, MethodW, ClassW, OtherW, ExprW2, MethodW2, ClassW2, OtherW2>(
        allFunctors: AllFunctors<ExprW, StmtW, MethodW, ClassW, OtherW>
    ) :
        AbstractRecursiveVisitor<ExprW, StmtW, MethodW, ClassW, OtherW, ExprW2, StmtW, MethodW2, ClassW2, OtherW2>(
            allFunctors
        ) {
        override fun stepBlock(statement: Kind<StmtW, AST.Block<ExprW2, StmtW, OtherW2>>): Kind<StmtW, AST.Block<ExprW2, StmtW, OtherW2>> =
            statement

        override fun stepStatement(statement: Kind<StmtW, AST.Statement<ExprW2, StmtW, OtherW2>>): Kind<StmtW, AST.Statement<ExprW2, StmtW, OtherW2>> =
            statement

        override fun stepBlockStatement(blockStatement: Kind<StmtW, AST.BlockStatement<ExprW2, StmtW, OtherW2>>): Kind<StmtW, AST.BlockStatement<ExprW2, StmtW, OtherW2>> =
            blockStatement
    }

    abstract class DefaultMethodVisitor<ExprW, StmtW, MethodW, ClassW, OtherW, ExprW2, StmtW2, ClassW2, OtherW2>(
        allFunctors: AllFunctors<ExprW, StmtW, MethodW, ClassW, OtherW>
    ) :
        AbstractRecursiveVisitor<ExprW, StmtW, MethodW, ClassW, OtherW, ExprW2, StmtW2, MethodW, ClassW2, OtherW2>(
            allFunctors
        ) {
        override fun stepClassMember(classDeclaration: Kind<MethodW, AST.ClassMember<ExprW2, StmtW2, OtherW2>>): Kind<MethodW, AST.ClassMember<ExprW2, StmtW2, OtherW2>> =
            classDeclaration
    }

    abstract class DefaultOtherVisitor<ExprW, StmtW, MethodW, ClassW, OtherW, ExprW2, StmtW2, MethodW2, ClassW2>(
        allFunctors: AllFunctors<ExprW, StmtW, MethodW, ClassW, OtherW>
    ) :
        AbstractRecursiveVisitor<ExprW, StmtW, MethodW, ClassW, OtherW, ExprW2, StmtW2, MethodW2, ClassW2, OtherW>(
            allFunctors
        ) {
        override fun stepParameter(parameter: Kind<OtherW, AST.Parameter<OtherW>>): Kind<OtherW, AST.Parameter<OtherW>> =
            parameter

        override fun stepType(type: Kind<OtherW, Type<OtherW>>): Kind<OtherW, Type<OtherW>> =
            type

        override fun stepArrayType(arrayType: Kind<OtherW, Type.Array.ArrayType<OtherW>>): Kind<OtherW, Type.Array.ArrayType<OtherW>> =
            arrayType
    }

    abstract class ExpressionOnlyVisitor<ExprW, StmtW, MethodW, ClassW, OtherW, ExprW2>(
        allFunctors: AllFunctors<ExprW, StmtW, MethodW, ClassW, OtherW>
    ) : AbstractRecursiveVisitor<ExprW, StmtW, MethodW, ClassW, OtherW, ExprW2, StmtW, MethodW, ClassW, OtherW>(
        allFunctors
    ) {
        override fun stepClass(classDeclaration: Kind<ClassW, AST.ClassDeclaration<ExprW2, StmtW, MethodW, OtherW>>): Kind<ClassW, AST.ClassDeclaration<ExprW2, StmtW, MethodW, OtherW>> = classDeclaration

        override fun stepClassMember(classDeclaration: Kind<MethodW, AST.ClassMember<ExprW2, StmtW, OtherW>>): Kind<MethodW, AST.ClassMember<ExprW2, StmtW, OtherW>> = classDeclaration

        override fun stepParameter(parameter: Kind<OtherW, AST.Parameter<OtherW>>): Kind<OtherW, AST.Parameter<OtherW>> = parameter

        override fun stepBlock(statement: Kind<StmtW, AST.Block<ExprW2, StmtW, OtherW>>): Kind<StmtW, AST.Block<ExprW2, StmtW, OtherW>> = statement

        override fun stepStatement(statement: Kind<StmtW, AST.Statement<ExprW2, StmtW, OtherW>>): Kind<StmtW, AST.Statement<ExprW2, StmtW, OtherW>> = statement

        override fun stepBlockStatement(blockStatement: Kind<StmtW, AST.BlockStatement<ExprW2, StmtW, OtherW>>): Kind<StmtW, AST.BlockStatement<ExprW2, StmtW, OtherW>> = blockStatement

        override fun convertStatement(satement: AST.Statement<ExprW, StmtW, OtherW>): AST.Statement<ExprW2, StmtW, OtherW> = when (satement) {
            is AST.Block -> TODO()
            is AST.ExpressionStatement -> TODO()
            is AST.IfStatement -> TODO()
            is AST.ReturnStatement -> TODO()
            is AST.WhileStatement -> TODO()
        }

        override fun stepType(type: Kind<OtherW, Type<OtherW>>): Kind<OtherW, Type<OtherW>> = type

        override fun stepArrayType(arrayType: Kind<OtherW, Type.Array.ArrayType<OtherW>>): Kind<OtherW, Type.Array.ArrayType<OtherW>> = arrayType
    }
}

abstract class AbstractRecursiveVisitor<ExprW, StmtW, MethodW, ClassW, OtherW, ExprW2, StmtW2, MethodW2, ClassW2, OtherW2>(
    private val functorExpr: Functor<ExprW>,
    private val functorStmt: Functor<StmtW>,
    private val functorDecl: Functor<MethodW>,
    private val functorClass: Functor<ClassW>,
    private val functorOther: Functor<OtherW>
) {
    constructor(allFunctors: AllFunctors<ExprW, StmtW, MethodW, ClassW, OtherW>) : this(
        allFunctors.functorExpr,
        allFunctors.functorStmt,
        allFunctors.functorDecl,
        allFunctors.functorClass,
        allFunctors.functorOther,
    )

    fun visit(program: AST.Program<ExprW, StmtW, MethodW, ClassW, OtherW>): AST.Program<ExprW2, StmtW2, MethodW2, ClassW2, OtherW2> =
        program.mapClassW {
            visit(it)
        }

    abstract fun stepClass(classDeclaration: Kind<ClassW, AST.ClassDeclaration<ExprW2, StmtW2, MethodW2, OtherW2>>): Kind<ClassW2, AST.ClassDeclaration<ExprW2, StmtW2, MethodW2, OtherW2>>

    fun visit(classDeclaration: Kind<ClassW, AST.ClassDeclaration<ExprW, StmtW, MethodW, OtherW>>): Kind<ClassW2, AST.ClassDeclaration<ExprW2, StmtW2, MethodW2, OtherW2>> =
        classDeclaration.fmap(functorClass) {
            it.mapMethodW { visit(it) }
        }.let { stepClass(it) }

    abstract fun stepClassMember(classDeclaration: Kind<MethodW, AST.ClassMember<ExprW2, StmtW2, OtherW2>>): Kind<MethodW2, AST.ClassMember<ExprW2, StmtW2, OtherW2>>

    @JvmName("visitdecl")
    fun visit(decl: Kind<MethodW, AST.ClassMember<ExprW, StmtW, OtherW>>): Kind<MethodW2, AST.ClassMember<ExprW2, StmtW2, OtherW2>> =
        decl.fmap<AST.ClassMember<ExprW, StmtW, OtherW>, AST.ClassMember<ExprW2, StmtW2, OtherW2>, MethodW>(functorDecl) {
            it.mapMember({
                visit(it.fmap(functorOther) { it.into() })
            }, {
                visit(it.fmap(functorOther) { it.into() })
            }, {
                visit(it)
            })
        }.let { stepClassMember(it) }

    abstract fun stepParameter(parameter: Kind<OtherW, AST.Parameter<OtherW2>>): Kind<OtherW2, AST.Parameter<OtherW2>>

    @JvmName("visitparameter")
    fun visit(parameter: Kind<OtherW, AST.Parameter<OtherW>>): Kind<OtherW2, AST.Parameter<OtherW2>> =
        parameter.fmap(functorOther) {
            it.mapOtherW {
                visit(it.fmap(functorOther) { it.into() })
            }
        }.let { stepParameter(it) }

    abstract fun stepBlock(statement: Kind<StmtW, AST.Block<ExprW2, StmtW2, OtherW2>>): Kind<StmtW2, AST.Block<ExprW2, StmtW2, OtherW2>>

    @JvmName("visitblock")
    fun visit(statement: Kind<StmtW, AST.Block<ExprW, StmtW, OtherW>>): Kind<StmtW2, AST.Block<ExprW2, StmtW2, OtherW2>> =
        statement.fmap(
            functorStmt
        ) {
            it.mapStmt {
                visit(it.fmap(functorStmt) { it.into() })
            }
        }.let { stepBlock(it) }

    abstract fun stepStatement(statement: Kind<StmtW, AST.Statement<ExprW2, StmtW2, OtherW2>>): Kind<StmtW2, AST.Statement<ExprW2, StmtW2, OtherW2>>

    @JvmName("visitstatement")
    fun visit(statement: Kind<StmtW, AST.Statement<ExprW, StmtW, OtherW>>): Kind<StmtW2, AST.Statement<ExprW2, StmtW2, OtherW2>> =
        statement.fmap(
            functorStmt
        ) {
            it.mapStmt({
                visit(it.fmap(functorStmt) { it.into() })
            }, {
                visit(it.fmap(functorStmt) { it.into() })
            }, {
                visit(it.fmap(functorExpr) { it.into() })
            })
        }.let { stepStatement(it) }

    abstract fun stepBlockStatement(blockStatement: Kind<StmtW, AST.BlockStatement<ExprW2, StmtW2, OtherW2>>): Kind<StmtW2, AST.BlockStatement<ExprW2, StmtW2, OtherW2>>

    abstract fun convertStatement(satement: AST.Statement<ExprW, StmtW, OtherW>): AST.Statement<ExprW2, StmtW2, OtherW2>

    @JvmName("visitblockstatement")
    fun visit(blockStatement: Kind<StmtW, AST.BlockStatement<ExprW, StmtW, OtherW>>): Kind<StmtW2, AST.BlockStatement<ExprW2, StmtW2, OtherW2>> =
        blockStatement.fmap(functorStmt) {
            it.mapBlockStmt({
                convertStatement(it)
            }, {
                visit(it.fmap(functorExpr) { it.into() })
            }, {
                visit(it.fmap(functorOther) { it.into() })
            })
        }.let { stepBlockStatement(it) }

    abstract fun stepType(type: Kind<OtherW, Type<OtherW2>>): Kind<OtherW2, Type<OtherW2>>

    @JvmName("visittype")
    fun visit(type: Kind<OtherW, Type<OtherW>>): Kind<OtherW2, Type<OtherW2>> =
        type.fmap(functorOther) {
            it.mapOtherW {
                visit(it.fmap(functorOther) { it.into() })
            }
        }.let { stepType(it) }

    abstract fun stepExpression(expression: Kind<ExprW, AST.Expression<ExprW2, OtherW2>>): Kind<ExprW2, AST.Expression<ExprW2, OtherW2>>

    @JvmName("visitexpression")
    fun visit(expression: Kind<ExprW, AST.Expression<ExprW, OtherW>>): Kind<ExprW2, AST.Expression<ExprW2, OtherW2>> =
        expression.fmap(functorExpr) {
            it.mapExprW({
                visit(it.fmap(functorExpr) { it.into() })
            }, {
                TODO()
            })
        }.let { stepExpression(it) }

    abstract fun stepArrayType(arrayType: Kind<OtherW, Type.Array.ArrayType<OtherW2>>): Kind<OtherW2, Type.Array.ArrayType<OtherW2>>

    @JvmName("visitarraytype")
    fun visit(arrayType: Kind<OtherW, Type.Array.ArrayType<OtherW>>): Kind<OtherW2, Type.Array.ArrayType<OtherW2>> =
        arrayType.fmap(functorOther) {
            Type.Array.ArrayType(visit(it.elementType.fmap(functorOther) { it.into() }))
        }.let { stepArrayType(it) }
}
