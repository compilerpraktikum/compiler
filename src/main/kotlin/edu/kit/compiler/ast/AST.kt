package edu.kit.compiler.ast

import edu.kit.compiler.Token
import edu.kit.compiler.lex.Symbol
import edu.kit.compiler.wrapper.Kind
import edu.kit.compiler.wrapper.Of
import edu.kit.compiler.wrapper.Unwrappable
import edu.kit.compiler.wrapper.into
import edu.kit.compiler.wrapper.unwrap
import edu.kit.compiler.wrapper.wrappers.Identity
import edu.kit.compiler.wrapper.wrappers.Positioned
import edu.kit.compiler.wrapper.wrappers.PositionedUnwrapper
import edu.kit.compiler.wrapper.wrappers.UnwrappableAnnotated
import edu.kit.compiler.wrapper.wrappers.UnwrappableIdentity
import edu.kit.compiler.wrapper.wrappers.into

sealed class Type<out TypeWrapper>() : Kind<Type<Of>, TypeWrapper> {

    companion object {
        fun <TypeWrapper> arrayOf(elementType: Kind<TypeWrapper, Kind<Type<Of>, TypeWrapper>>) =
            Array(Array.ArrayType(elementType))
    }

    object Void : Type<Nothing>()

    object Integer : Type<Nothing>()

    object Boolean : Type<Nothing>()

    data class Array<out TypeWrapper>(
        val arrayType: ArrayType<TypeWrapper>
    ) : Type<TypeWrapper>() {
        @JvmInline
        value class ArrayType<out TypeWrapper>(
            val elementType: Kind<TypeWrapper, Kind<Type<Of>, TypeWrapper>>
        ) : Kind<ArrayType<Of>, TypeWrapper> {
            fun wrapArray() = Array(this)
        }
    }

    data class Class(
        val name: Symbol
    ) : Type<Nothing>()
}

private tailrec fun <T> Type<T>.getBaseType(unwrap: Unwrappable<T>): Type<Nothing> = when (this) {
    is Type.Array -> this.arrayType.elementType.unwrap(unwrap).into().getBaseType(unwrap)
    is Type.Void -> this
    is Type.Integer -> this
    is Type.Class -> this
    is Type.Boolean -> this
}

val Type<Positioned<Of>>.baseType
    get() = getBaseType(PositionedUnwrapper)

/**
 * @unwrapTypeW is usually [UnwrappableIdentity] or [UnwrappableAnnotated]
 */
public fun <TypeW> Type<TypeW>.getDimension(unwrapTypeW: Unwrappable<TypeW>): Int {
    var dim = 0
    var type = this
    while (type is Type.Array) {
        dim += 1
        type = type.arrayType.elementType.unwrap(unwrapTypeW).into().getBaseType(unwrapTypeW)
    }
    return dim
}

val Type<Positioned<Of>>.dimension: Int
    get() = getDimension(PositionedUnwrapper)

public val Type<Identity<Of>>.dimension: Int
    @JvmName("typedimension")
    get() {
        var dim = 0
        var type = this
        while (type is Type.Array) {
            dim += 1
            type = type.arrayType.elementType.into().v.into().getBaseType(UnwrappableIdentity)
        }
        return dim
    }

/**
 * Sealed AST-Node class structure
 */
object AST {

    /************************************************
     ** Class
     ************************************************/

    /**
     * @param ExprW Wrapper for Expression Nodes
     * @param StmtW Wrapper for Statements and Block Statement Nodes
     * @param MethodW Wrapper for Method and Field Nodes
     * @param ClassW Wrapper for Classes
     * @param OtherW Wrapper for the rest other Nodes, could fail in parsing
     */
    data class Program<ExprW, StmtW, MethodW, ClassW, OtherW>(
        val classes: List<Kind<ClassW, ClassDeclaration<ExprW, StmtW, MethodW, OtherW>>>,
    )

    data class ClassDeclaration<ExprW, StmtW, MethodW, OtherW>(
        val name: Symbol,
        val member: List<Kind<MethodW, ClassMember<ExprW, StmtW, OtherW>>>,
    )

    sealed class ClassMember<out ExprW, out StmtW, out OtherW> {
        val memberName: Symbol
            get() = when (this) {
                is Field -> name
                is MainMethod -> name
                is Method -> name
            }
    }

    data class Field<out OtherW>(
        val name: Symbol,
        val type: Kind<OtherW, Kind<Type<Of>, OtherW>>,
    ) : ClassMember<Nothing, Nothing, OtherW>()

    data class Method<out ExprW, out StmtW, out OtherW>(
        val name: Symbol,
        val returnType: Kind<OtherW, Kind<Type<Of>, OtherW>>,
        val parameters: List<Kind<OtherW, Kind<Parameter<Of>, OtherW>>>,
        val block: Kind<StmtW, Block<ExprW, StmtW, OtherW>>,
        val throwsException: Symbol? = null,
    ) : ClassMember<ExprW, StmtW, OtherW>()

    data class MainMethod<out ExprW, out StmtW, OtherW>(
        // we need not only block but the rest too, for in semantical analysis we need to check exact match on
        // "public static void main(String[] $SOMEIDENTIFIER)"
        val name: Symbol,
        val returnType: Kind<OtherW, Kind<Type<Of>, OtherW>>,
        val parameters: List<Kind<OtherW, Kind<Parameter<Of>, OtherW>>>,
        val block: Kind<StmtW, Block<ExprW, StmtW, OtherW>>,
        val throwsException: Symbol? = null,
    ) : ClassMember<ExprW, StmtW, OtherW>()

    data class Parameter<out OtherW>(
        val name: Symbol,
        val type: Kind<OtherW, Kind<Type<Of>, OtherW>>,
    ) : Kind<Parameter<Of>, OtherW>

    /************************************************
     ** Statement
     ************************************************/

    sealed class BlockStatement<out ExprW, out StmtW, out OtherW> : Kind<BlockStatement<ExprW, Of, OtherW>, StmtW>

    data class LocalVariableDeclarationStatement<ExprW, OtherW>(
        val name: Symbol,
        val type: Kind<OtherW, Kind<Type<Of>, OtherW>>,
        val initializer: Kind<ExprW, Kind<Expression<Of, OtherW>, ExprW>>?,
    ) : BlockStatement<ExprW, Nothing, OtherW>()

    data class StmtWrapper<ExprW, StmtW, OtherW>(val statement: Statement<ExprW, StmtW, OtherW>) : BlockStatement<ExprW, StmtW, OtherW>()

    sealed class Statement<out ExprW, out StmtW, out OtherW> : Kind<Statement<ExprW, Of, OtherW>, StmtW>

    fun <ExprW, StmtW, OtherW> Statement<ExprW, StmtW, OtherW>.wrapBlockStatement(): StmtWrapper<ExprW, StmtW, OtherW> = StmtWrapper(this)

    val emptyStatement = Block<Nothing, Nothing, Nothing>(listOf())

    data class Block<out ExprW, out StmtW, out OtherW>(
        val statements: List<Kind<StmtW, Kind<BlockStatement<ExprW, Of, OtherW>, StmtW>>>,
    ) : Statement<ExprW, StmtW, OtherW>()

    data class IfStatement<out ExprW, out StmtW, out OtherW>(
        val condition: Kind<ExprW, Kind<Expression<Of, OtherW>, ExprW>>,
        val trueStatement: Kind<StmtW, Kind<Statement<ExprW, Of, OtherW>, StmtW>>,
        val falseStatement: Kind<StmtW, Kind<Statement<ExprW, Of, OtherW>, StmtW>>?
    ) : Statement<ExprW, StmtW, OtherW>()

    data class WhileStatement<out ExprW, out StmtW, out OtherW>(
        val condition: Kind<ExprW, Kind<Expression<Of, OtherW>, ExprW>>,
        val statement: Kind<StmtW, Kind<Statement<ExprW, Of, OtherW>, StmtW>>,
    ) : Statement<ExprW, StmtW, OtherW>()

    data class ReturnStatement<out ExprW, out OtherW>(
        val expression: Kind<ExprW, Kind<Expression<Of, OtherW>, ExprW>>?,
    ) : Statement<ExprW, Nothing, OtherW>()

    data class ExpressionStatement<out ExprW, out OtherW>(
        val expression: Kind<ExprW, Kind<Expression<Of, OtherW>, ExprW>>,
    ) : Statement<ExprW, Nothing, OtherW>()

    /************************************************
     ** Expression
     ************************************************/

    sealed class Expression<out ExprW, out OtherW> : Kind<Expression<Of, OtherW>, ExprW>

    data class BinaryExpression<ExprW, OtherW>(
        val left: Kind<ExprW, Kind<Expression<Of, OtherW>, ExprW>>,
        val right: Kind<ExprW, Kind<Expression<Of, OtherW>, ExprW>>,
        val operation: Operation
    ) : Expression<ExprW, OtherW>() {
        enum class Operation(
            val precedence: Int,
            val associativity: Associativity,
            val repr: String
        ) {
            ASSIGNMENT(1, Associativity.RIGHT, "="),

            // logical
            OR(2, Associativity.LEFT, "||"),

            AND(3, Associativity.LEFT, "&&"),

            // relational
            EQUALS(4, Associativity.LEFT, "=="),
            NOT_EQUALS(4, Associativity.LEFT, "!="),

            LESS_THAN(5, Associativity.LEFT, "<"),
            GREATER_THAN(5, Associativity.LEFT, ">"),
            LESS_EQUALS(5, Associativity.LEFT, "<="),
            GREATER_EQUALS(5, Associativity.LEFT, ">="),

            // arithmetical
            ADDITION(6, Associativity.LEFT, "+"),
            SUBTRACTION(6, Associativity.LEFT, "-"),

            MULTIPLICATION(7, Associativity.LEFT, "*"),
            DIVISION(7, Associativity.LEFT, "/"),
            MODULO(7, Associativity.LEFT, "%");

            enum class Associativity {
                LEFT,
                RIGHT,
            }
        }
    }

    data class UnaryExpression<out ExprW, out OtherW>(
        val expression: Kind<ExprW, Kind<Expression<Of, OtherW>, ExprW>>,
        val operation: Operation
    ) : Expression<ExprW, OtherW>() {
        enum class Operation(
            val repr: String
        ) {
            NOT("!"),
            MINUS("-"),
        }
    }

    data class MethodInvocationExpression<ExprW, OtherW>(
        val target: Kind<ExprW, Kind<Expression<Of, OtherW>, ExprW>>?,
        val method: Symbol,
        val arguments: List<Kind<ExprW, Kind<Expression<Of, OtherW>, ExprW>>>
    ) : Expression<ExprW, OtherW>()

    data class FieldAccessExpression<ExprW, OtherW>(
        val target: Kind<ExprW, Kind<Expression<Of, OtherW>, ExprW>>,
        val field: Symbol,
    ) : Expression<ExprW, OtherW>()

    data class ArrayAccessExpression<ExprW, OtherW>(
        val target: Kind<ExprW, Kind<Expression<Of, OtherW>, ExprW>>,
        val index: Kind<ExprW, Kind<Expression<Of, OtherW>, ExprW>>,
    ) : Expression<ExprW, OtherW>()

    /************************************************
     ** Primary expression
     ************************************************/

    data class IdentifierExpression(
        val name: Symbol,
    ) : Expression<Nothing, Nothing>()

    data class LiteralExpression<T>(
        val value: T,
    ) : Expression<Nothing, Nothing>()

    data class NewObjectExpression(
        val clazz: Symbol,
    ) : Expression<Nothing, Nothing>()

    data class NewArrayExpression<ExprW, out OtherW>(
        val type: Kind<OtherW, Kind<Type.Array.ArrayType<Of>, OtherW>>,
        val length: Kind<ExprW, Kind<Expression<Of, OtherW>, ExprW>>,
    ) : Expression<ExprW, OtherW>()
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
