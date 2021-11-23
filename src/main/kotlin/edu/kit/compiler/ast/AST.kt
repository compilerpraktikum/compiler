package edu.kit.compiler.ast

import edu.kit.compiler.Token
import edu.kit.compiler.lex.Symbol
import edu.kit.compiler.wrapper.wrappers.Parsed

sealed class Type() {

    companion object {
        fun arrayOf(elementType: Parsed<Type>) =
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
            val elementType: Parsed<Type>
        ) {
            fun wrapArray() = Array(this)
        }
    }

    data class Class(
        val name: Symbol
    ) : Type()
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
        val classes: List<Parsed<ClassDeclaration>>,
    )

    data class ClassDeclaration(
        val name: Symbol,
        val member: List<Parsed<ClassMember>>,
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
        val type: Parsed<Type>,
    ) : ClassMember()

    data class Method(
        val name: Symbol,
        val returnType: Parsed<Type>,
        val parameters: List<Parsed<Parameter>>,
        val block: Parsed<Block>,
        val throwsException: Symbol? = null,
    ) : ClassMember()

    data class MainMethod(
        // we need not only block but the rest too, for in semantical analysis we need to check exact match on
        // "public static void main(String[] $SOMEIDENTIFIER)"
        val name: Symbol,
        val returnType: Parsed<Type>,
        val parameters: List<Parsed<Parameter>>,
        val block: Parsed<Block>,
        val throwsException: Symbol? = null,
    ) : ClassMember()

    data class Parameter(
        val name: Symbol,
        val type: Parsed<Type>,
    )

    /************************************************
     ** Statement
     ************************************************/

    sealed class BlockStatement

    data class LocalVariableDeclarationStatement(
        val name: Symbol,
        val type: Parsed<Type>,
        val initializer: Parsed<Expression>?,
    ) : BlockStatement()

    data class StmtWrapper(val statement: Statement) : BlockStatement()

    sealed class Statement

    fun Statement.wrapBlockStatement(): StmtWrapper = StmtWrapper(this)

    val emptyStatement = Block(listOf())

    data class Block(
        val statements: List<Parsed<BlockStatement>>,
    ) : Statement()

    data class IfStatement(
        val condition: Parsed<Expression>,
        val trueStatement: Parsed<Statement>,
        val falseStatement: Parsed<Statement>?
    ) : Statement()

    data class WhileStatement(
        val condition: Parsed<Expression>,
        val statement: Parsed<Statement>,
    ) : Statement()

    data class ReturnStatement(
        val expression: Parsed<Expression>?,
    ) : Statement()

    data class ExpressionStatement(
        val expression: Parsed<Expression>,
    ) : Statement()

    /************************************************
     ** Expression
     ************************************************/

    sealed class Expression

    data class BinaryExpression(
        val left: Parsed<Expression>,
        val right: Parsed<Expression>,
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
        val expression: Parsed<Expression>,
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
        val target: Parsed<Expression>?,
        val method: Symbol,
        val arguments: List<Parsed<Expression>>
    ) : Expression()

    data class FieldAccessExpression(
        val target: Parsed<Expression>,
        val field: Symbol,
    ) : Expression()

    data class ArrayAccessExpression(
        val target: Parsed<Expression>,
        val index: Parsed<Expression>,
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
        val type: Parsed<Type.Array.ArrayType>,
        val length: Parsed<Expression>,
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
