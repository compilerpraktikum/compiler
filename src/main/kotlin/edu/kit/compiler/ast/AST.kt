@file:Suppress("unused")

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

    data class Program(
        val classes: List<ClassDeclaration>,
    )

    data class ClassDeclaration(
        val name: String,
        val member: List<ClassMember>,
    )

    sealed class ClassMember

    data class Field(
        val name: String,
        val type: Type,
    ) : ClassMember()

    data class Method(
        val name: String,
        val returnType: Type,
        val parameters: List<Parameter>,
        val block: Block,
        val throwException: Token.Identifier? = null,
    ) : ClassMember()

    data class MainMethod(
        // we need not only block but the rest too, for in semantical analysis we need to check exact match on
        // "public static void main(String[] $SOMEIDENTIFIER)"
        val name: String,
        val returnType: Type,
        val parameters: List<Parameter>,
        val block: Block,
        val throwException: Token.Identifier? = null,
    ) : ClassMember()

    data class Parameter(
        val name: String,
        val type: Type,
    )

    /************************************************
     ** Statement
     ************************************************/

    sealed class BlockStatement

    data class LocalVariableDeclarationStatement(
        val name: String,
        val type: Type,
        val initializer: Expression?,
    ) : BlockStatement()

    sealed class Statement : BlockStatement()

    object EmptyStatement : Statement()

    data class Block(
        val statements: List<BlockStatement>,
    ) : Statement()

    data class IfStatement(
        val condition: Expression,
        val trueStatement: Statement,
        val falseStatement: Statement?,
    ) : Statement()

    data class WhileStatement(
        val condition: Expression,
        val statement: Statement,
    ) : Statement()

    data class ReturnStatement(
        val expression: Expression?,
    ) : Statement()

    data class ExpressionStatement(
        val expression: Expression,
    ) : Statement()

    /************************************************
     ** Expression
     ************************************************/

    sealed class Expression

    data class BinaryExpression(
        val left: Expression,
        val right: Expression,
        val operation: Operation
    ) : Expression() {
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

    data class UnaryExpression(
        val expression: Expression,
        val operation: Operation
    ) {
        enum class Operation {
            NOT,
            MINUS,
        }
    }

    data class MethodInvocationExpression(
        val target: Expression?,
        val method: String,
        val arguments: List<Expression>
    ) : Expression()

    data class FieldAccessExpression(
        val target: Expression,
        val field: String,
    ) : Expression()

    data class ArrayAccessExpression(
        val target: Expression,
        val index: Expression,
    ) : Expression()

    /************************************************
     ** Primary expression
     ************************************************/

    data class IdentifierExpression(
        val name: String,
    ) : Expression()

    data class LiteralExpression<T>(
        val value: T,
    ) : Expression()

    data class NewObjectExpression(
        val clazz: String,
    ) : Expression()

    data class NewArrayExpression(
        val type: Type.Array,
        val length: Expression,
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
