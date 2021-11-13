package edu.kit.compiler.ast

import edu.kit.compiler.Token

sealed class Type {
    object Void : Type()

    object Integer : Type()

    object Boolean : Type()

    data class Array(
        val elementType: Type
    ) : Type()

    data class ClassType(
        val identifier: String
    ) : Type()
}

/**
 * Sealed AST-Node class structure
 */
object AST {

    /************************************************
     ** Class
     ************************************************/

    data class Program<E, S, D, C>(
        val classes: List<Kind<C, ClassDeclaration<E, S, D>>>,
    )

    data class ClassDeclaration<E, S, M>(
        val name: String,
        val member: List<Kind<M, ClassMember<E, S>>>,
    )

    sealed class ClassMember<out E, out S>

    data class Field(
        val name: String,
        val type: Type,
    ) : ClassMember<Nothing, Nothing>()

    data class Method<out E, out S>(
        val name: String,
        val returnType: Type,
        val parameters: List<Parameter>,
        val block: Kind<S, Block<E, S>>,
        val throwException: Token.Identifier? = null,
    ) : ClassMember<E, S>()

    data class MainMethod<out E, out S>(
        // we need not only block but the rest too, for in semantical analysis we need to check exact match on
        // "public static void main(String[] $SOMEIDENTIFIER)"
        val name: String,
        val returnType: Type,
        val parameters: List<Parameter>,
        val block: Kind<S, Block<E, S>>,
        val throwException: Token.Identifier? = null,
    ) : ClassMember<E, S>()

    data class Parameter(
        val name: String,
        val type: Type,
    )

    /************************************************
     ** Statement
     ************************************************/

    sealed class BlockStatement<out S, out E> : Kind<Statement<Of, E>, S>

    data class LocalVariableDeclarationStatement<E>(
        val name: String,
        val type: Type,
        val initializer: Kind<E, Kind<Expression<Of>, E>>?,
    ) : BlockStatement<Nothing, E>()

    sealed class Statement<out S, out E> : BlockStatement<S, E>()

    object EmptyStatement : Statement<Nothing, Nothing>()

    data class Block<out S, out E>(
        val statements: List<Kind<S, Kind<Statement<Of, E>, S>>>,
    ) : Statement<S, E>()

    data class IfStatement<out S, out E>(
        val condition: Kind<E, Kind<Expression<Of>, E>>,
        val trueStatement: Kind<S, Kind<Statement<Of, E>, S>>,
        val falseStatement: Kind<S, Kind<Statement<Of, E>, S>>?
    ) : Statement<S, E>()

    data class WhileStatement<out S, out E>(
        val condition: Kind<E, Kind<Expression<Of>, E>>,
        val statement: Kind<S, Kind<Statement<Of, E>, S>>,
    ) : Statement<S, E>()

    data class ReturnStatement<out E>(
        val expression: Kind<E, Kind<Expression<Of>, E>>?,
    ) : Statement<Nothing, E>()

    data class ExpressionStatement<out E>(
        val expression: Kind<E, Kind<Expression<Of>, E>>,
    ) : Statement<Nothing, E>()

    /************************************************
     ** Expression
     ************************************************/

    sealed class Expression<out E> : Kind<Expression<Of>, E>

    data class BinaryExpression<E>(
        val left: Kind<E, Kind<Expression<Of>, E>>,
        val right: Kind<E, Kind<Expression<Of>, E>>,
        val operation: Operation
    ) : Expression<E>() {
        enum class Operation(
            val precedence: Int,
            val associativity: Associativity
        ) {
            ASSIGNMENT(1, Associativity.RIGHT),

            // logical
            OR(2, Associativity.LEFT),

            AND(3, Associativity.LEFT),

            // relational
            EQUALS(4, Associativity.LEFT),
            NOT_EQUALS(4, Associativity.LEFT),

            LESS_THAN(5, Associativity.LEFT),
            GREATER_THAN(5, Associativity.LEFT),
            LESS_EQUALS(5, Associativity.LEFT),
            GREATER_EQUALS(5, Associativity.LEFT),

            // arithmetical
            ADDITION(6, Associativity.LEFT),
            SUBTRACTION(6, Associativity.LEFT),

            MULTIPLICATION(7, Associativity.LEFT),
            DIVISION(7, Associativity.LEFT),
            MODULO(7, Associativity.LEFT);

            enum class Associativity {
                LEFT,
                RIGHT,
            }
        }
    }

    data class UnaryExpression<out E>(
        val expression: Kind<E, Kind<Expression<Of>, E>>,
        val operation: Operation
    ) : Expression<E>() {
        enum class Operation {
            NOT,
            MINUS,
        }
    }

    data class MethodInvocationExpression<E>(
        val target: Kind<E, Kind<Expression<Of>, E>>?,
        val method: String,
        val arguments: List<Kind<E, Kind<Expression<Of>, E>>>
    ) : Expression<E>()

    data class FieldAccessExpression<E>(
        val target: Kind<E, Kind<Expression<Of>, E>>,
        val field: String,
    ) : Expression<E>()

    data class ArrayAccessExpression<E>(
        val target: Kind<E, Kind<Expression<Of>, E>>,
        val index: Kind<E, Kind<Expression<Of>, E>>,
    ) : Expression<E>()

    /************************************************
     ** Primary expression
     ************************************************/

    data class IdentifierExpression(
        val name: String,
    ) : Expression<Nothing>()

    data class LiteralExpression<T>(
        val value: T,
    ) : Expression<Nothing>()

    data class NewObjectExpression(
        val clazz: String,
    ) : Expression<Nothing>()

    data class NewArrayExpression<E>(
        val type: Type.Array,
        val length: Kind<E, Expression<E>>,
    ) : Expression<E>()
}

fun Token.Operator.Type.toASTOperation(): AST.BinaryExpression.Operation? = when (this) {
    Token.Operator.Type.NoEq -> AST.BinaryExpression.Operation.NOT_EQUALS
    Token.Operator.Type.Mul -> AST.BinaryExpression.Operation.MULTIPLICATION
    Token.Operator.Type.Plus -> AST.BinaryExpression.Operation.ADDITION
    Token.Operator.Type.Minus -> AST.BinaryExpression.Operation.SUBTRACTION
    Token.Operator.Type.Div -> AST.BinaryExpression.Operation.DIVISION
    Token.Operator.Type.LtEq -> AST.BinaryExpression.Operation.LESS_EQUALS
    Token.Operator.Type.Lt -> AST.BinaryExpression.Operation.LESS_THAN
    Token.Operator.Type.Eq -> AST.BinaryExpression.Operation.EQUALS
    Token.Operator.Type.Assign -> AST.BinaryExpression.Operation.ASSIGNMENT
    Token.Operator.Type.GtEq -> AST.BinaryExpression.Operation.GREATER_EQUALS
    Token.Operator.Type.Gt -> AST.BinaryExpression.Operation.GREATER_THAN
    Token.Operator.Type.Mod -> AST.BinaryExpression.Operation.MODULO
    Token.Operator.Type.And -> AST.BinaryExpression.Operation.AND
    Token.Operator.Type.Or -> AST.BinaryExpression.Operation.OR
    else -> null
}

abstract class AstDsl<T>(var res: MutableList<T> = mutableListOf())

class ClassDeclarationDsl(res: MutableList<Lenient<AST.ClassDeclaration<Lenient<Of>, Lenient<Of>, Lenient<Of>>>> = mutableListOf()) :
    AstDsl<Lenient<AST.ClassDeclaration<Lenient<Of>, Lenient<Of>, Lenient<Of>>>>(res) {
    fun clazz(name: String, block: ClassMemberDsl.() -> Unit) {
        val members = ClassMemberDsl().also { it.block() }
        this.res.add(AST.ClassDeclaration(name, members.res).wrapValid())
    }
}

class ClassMemberDsl(res: MutableList<Lenient<AST.ClassMember<Lenient<Of>, Lenient<Of>>>> = mutableListOf()) :
    AstDsl<Lenient<AST.ClassMember<Lenient<Of>, Lenient<Of>>>>(res) {

    fun param(name: String, type: Type) = AST.Parameter(name, type)

    fun field(name: String, type: Type) {
        this.res.add(AST.Field(name, type).wrapValid())
    }

    fun mainMethod(
        name: String,
        returnType: Type,
        vararg parameters: AST.Parameter,
        throws: String? = null,
        block: BlockStatementDsl.() -> Unit
    ) {

        this.res.add(
            AST.MainMethod<Lenient<Of>, Lenient<Of>>(
                name,
                returnType,
                parameters.toList(),
                AST.Block(BlockStatementDsl().also(block).res).wrapValid(),
                if (throws == null) {
                    null
                } else {
                    Token.Identifier(throws)
                }
            ).wrapValid()
        )
    }

    fun method(
        name: String,
        returnType: Type,
        vararg parameters: AST.Parameter,
        throws: String? = null,
        block: BlockStatementDsl.() -> Unit
    ) {
        this.res.add(
            AST.Method<Lenient<Of>, Lenient<Of>>(
                name,
                returnType,
                parameters.toList(),
                AST.Block(BlockStatementDsl().also(block).res).wrapValid(),
                if (throws == null) {
                    null
                } else {
                    Token.Identifier(throws)
                }
            ).wrapValid()
        )
    }
}

object ExprDsl {
    fun <T> literal(v: T) = AST.LiteralExpression(v)
    fun binOp(
        op: AST.BinaryExpression.Operation,
        left: ExprDsl.() -> AST.Expression<Lenient<Of>>,
        right: ExprDsl.() -> AST.Expression<Lenient<Of>>
    ) =
        AST.BinaryExpression(ExprDsl.left().wrapValid(), ExprDsl.right().wrapValid(), op)

    fun ident(name: String) = AST.IdentifierExpression(name)

    fun arrayAccess(
        target: ExprDsl.() -> AST.Expression<Lenient<Of>>,
        index: ExprDsl.() -> AST.Expression<Lenient<Of>>
    ) = AST.ArrayAccessExpression(ExprDsl.target().wrapValid(), ExprDsl.index().wrapValid())

    fun newArrayOf(
        type: Type.Array,
        length: ExprDsl.() -> AST.Expression<Lenient<Of>>
    ) = AST.NewArrayExpression(type, ExprDsl.length().wrapValid())
}

class BlockStatementDsl(val res: MutableList<Lenient<AST.BlockStatement<Lenient<Of>, Lenient<Of>>>> = mutableListOf()) {
    fun localDeclaration(name: String, type: Type, initializer: (ExprDsl.() -> AST.Expression<Lenient<Of>>)? = null) {
        res.add(
            AST.LocalVariableDeclarationStatement<Lenient<Of>>(
                name, type,
                if (initializer != null) {
                    ExprDsl.initializer().wrapValid()
                } else null
            ).wrapValid()
        )
    }

    fun emptyStatement() = res.add(AST.EmptyStatement.wrapValid())
    fun block(b: StatementsDsl.() -> Unit) {
        res.add(AST.Block(StatementsDsl().also(b).res).wrapValid())
    }

    fun expressionStatement(expr: ExprDsl.() -> AST.Expression<Lenient<Of>>) {
        res.add(AST.ExpressionStatement(ExprDsl.expr().wrapValid()).wrapValid())
    }
}

open class StatementsDsl(val res: MutableList<Lenient<AST.Statement<Lenient<Of>, Lenient<Of>>>> = mutableListOf()) {
    fun emptyStatement() = res.add(AST.EmptyStatement.wrapValid())
    fun block(b: StatementsDsl.() -> Unit) {
        res.add(AST.Block(StatementsDsl().also(b).res).wrapValid())
    }
}

fun astOf(block: ClassDeclarationDsl.() -> Unit) =
    ClassDeclarationDsl().also(block).res
