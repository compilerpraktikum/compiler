package edu.kit.compiler.ast

import edu.kit.compiler.Token
import edu.kit.compiler.lex.Symbol
import edu.kit.compiler.wrapper.wrappers.Lenient

sealed class Type() {

    companion object {
        fun arrayOf(elementType: Lenient<Type>) =
            Array(Array.ArrayType(elementType))
    }

    object Void : Type()

    object Integer : Type()

    object Boolean : Type()

    data class Array(
        val arrayType: ArrayType
    ) : Type() {
        @JvmInline
        value class ArrayType(
            val elementType: Lenient<Type>
        ) {
            fun wrapArray() = Array(this)
        }
    }

    data class Class(
        val name: Symbol
    ) : Type()
}

public val Type.baseType: Type
    get() = when (this) {
        is Type.Array -> this.arrayType.elementType.getAsValid()?.baseType
            ?: throw IllegalStateException("invalid type")
        is Type.Void -> this
        is Type.Integer -> this
        is Type.Class -> this
        is Type.Boolean -> this
    }

public val Type.dimension: Int
    get() {
        var dim = 0
        var type = this
        while (type is Type.Array) {
            dim += 1
            type = type.arrayType.elementType.getAsValid()?.baseType ?: throw IllegalStateException("invalid type")
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
    data class Program(
        val classes: List<Lenient<ClassDeclaration>>,
    )

    data class ClassDeclaration(
        val name: Symbol,
        val member: List<Lenient<ClassMember>>,
    )

    sealed class ClassMember {
        val memberName: Symbol
            get() = when (this) {
                is Field -> name
                is MainMethod -> name
                is Method -> name
            }
    }

    data class Field(
        val name: Symbol,
        val type: Lenient<Type>,
    ) : ClassMember()

    data class Method(
        val name: Symbol,
        val returnType: Lenient<Type>,
        val parameters: List<Lenient<Parameter>>,
        val block: Lenient<Block>,
        val throwsException: Symbol? = null,
    ) : ClassMember()

    data class MainMethod(
        // we need not only block but the rest too, for in semantical analysis we need to check exact match on
        // "public static void main(String[] $SOMEIDENTIFIER)"
        val name: Symbol,
        val returnType: Lenient<Type>,
        val parameters: List<Lenient<Parameter>>,
        val block: Lenient<Block>,
        val throwsException: Symbol? = null,
    ) : ClassMember()

    data class Parameter(
        val name: Symbol,
        val type: Lenient<Type>,
    )

    /************************************************
     ** Statement
     ************************************************/

    sealed class BlockStatement

    data class LocalVariableDeclarationStatement(
        val name: Symbol,
        val type: Lenient<Type>,
        val initializer: Lenient<Expression>?,
    ) : BlockStatement()

    data class StmtWrapper(val statement: Statement) : BlockStatement()

    sealed class Statement

    fun Statement.wrapBlockStatement(): StmtWrapper = StmtWrapper(this)

    val emptyStatement = Block(listOf())

    data class Block(
        val statements: List<Lenient<BlockStatement>>,
    ) : Statement()

    data class IfStatement(
        val condition: Lenient<Expression>,
        val trueStatement: Lenient<Statement>,
        val falseStatement: Lenient<Statement>?
    ) : Statement()

    data class WhileStatement(
        val condition: Lenient<Expression>,
        val statement: Lenient<Statement>,
    ) : Statement()

    data class ReturnStatement(
        val expression: Lenient<Expression>?,
    ) : Statement()

    data class ExpressionStatement(
        val expression: Lenient<Expression>,
    ) : Statement()

    /************************************************
     ** Expression
     ************************************************/

    sealed class Expression

    data class BinaryExpression(
        val left: Lenient<Expression>,
        val right: Lenient<Expression>,
        val operation: Operation
    ) : Expression() {
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

    data class UnaryExpression(
        val expression: Lenient<Expression>,
        val operation: Operation
    ) : Expression() {
        enum class Operation(
            val repr: String
        ) {
            NOT("!"),
            MINUS("-"),
        }
    }

    data class MethodInvocationExpression(
        val target: Lenient<Expression>?,
        val method: Symbol,
        val arguments: List<Lenient<Expression>>
    ) : Expression()

    data class FieldAccessExpression(
        val target: Lenient<Expression>,
        val field: Symbol,
    ) : Expression()

    data class ArrayAccessExpression(
        val target: Lenient<Expression>,
        val index: Lenient<Expression>,
    ) : Expression()

    /************************************************
     ** Primary expression
     ************************************************/

    data class IdentifierExpression(
        val name: Symbol,
    ) : Expression()

    data class LiteralExpression<T>(
        val value: T,
    ) : Expression()

    data class NewObjectExpression(
        val clazz: Symbol,
    ) : Expression()

    data class NewArrayExpression(
        val type: Lenient<Type.Array.ArrayType>,
        val length: Lenient<Expression>,
    ) : Expression()
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
