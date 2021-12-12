package edu.kit.compiler.ast

import edu.kit.compiler.Token
import edu.kit.compiler.lex.Symbol
import edu.kit.compiler.wrapper.wrappers.Parsed

/**
 * Sealed AST-Node class structure
 */
sealed class AST {

    /**
     * Parsed types. Those do not have to make semantic sense and do not have to be valid at their location, but are
     * only reported by the parser
     */
    sealed class Type : AST() {

        object Void : Type()

        object Integer : Type()

        object Boolean : Type()

        data class Array(
            val elementType: Parsed<Type>
        ) : Type()

        data class Class(
            val name: Parsed<Symbol>
        ) : Type()
    }

    /************************************************
     ** Class
     ************************************************/

    data class Program(
        val classes: List<Parsed<ClassDeclaration>>,
    ) : AST()

    data class ClassDeclaration(
        val name: Parsed<Symbol>,
        val member: List<Parsed<ClassMember>>,
    ) : AST()

    sealed class ClassMember : AST()

    data class Field(
        val name: Parsed<Symbol>,
        val type: Parsed<Type>,
    ) : ClassMember()

    data class Method(
        val name: Parsed<Symbol>,
        val returnType: Parsed<Type>,
        val parameters: List<Parsed<Parameter>>,
        val block: Parsed<Block>,
        val throwsException: Parsed<Symbol>? = null
    ) : ClassMember()

    data class MainMethod(
        // we need not only block but the rest too, for in semantical analysis we need to check exact match on
        // "public static void main(String[] $SOMEIDENTIFIER)"
        val name: Parsed<Symbol>,
        val returnType: Parsed<Type>,
        val parameters: List<Parsed<Parameter>>,
        val block: Parsed<Block>,
        val throwsException: Parsed<Symbol>? = null,
    ) : ClassMember()

    data class Parameter(
        val name: Parsed<Symbol>,
        val type: Parsed<Type>,
    ) : AST()

    /************************************************
     ** Statement
     ************************************************/

    sealed class BlockStatement : AST()

    data class LocalVariableDeclarationStatement(
        val name: Parsed<Symbol>,
        val type: Parsed<Type>,
        val initializer: Parsed<Expression>?,
    ) : BlockStatement()

    sealed class Statement : BlockStatement()

    data class Block(
        val statements: List<Parsed<BlockStatement>>,
        val openingBraceRange: Parsed<Unit>,
        val closingBraceRange: Parsed<Unit>,
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

    sealed class Expression : AST()

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
        val method: Parsed<Symbol>,
        val arguments: List<Parsed<Expression>>
    ) : Expression()

    data class FieldAccessExpression(
        val target: Parsed<Expression>,
        val field: Parsed<Symbol>,
    ) : Expression()

    data class ArrayAccessExpression(
        val target: Parsed<Expression>,
        val index: Parsed<Expression>,
    ) : Expression()

    /************************************************
     ** Primary expression
     ************************************************/

    data class IdentifierExpression(
        val name: Parsed<Symbol>,
    ) : Expression()

    sealed class LiteralExpression : Expression() {
        data class Integer(val value: String, val isParenthesized: kotlin.Boolean) : LiteralExpression()
        data class Boolean(val value: kotlin.Boolean) : LiteralExpression()
        class Null() : LiteralExpression()
        class This() : LiteralExpression()
    }

    data class NewObjectExpression(
        val clazz: Parsed<Symbol>,
    ) : Expression()

    data class NewArrayExpression(
        val type: Parsed<AST.Type.Array>,
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
