@file:Suppress("unused")

package edu.kit.compiler.ast

sealed class Type {
    object Void : Type()

    object Integer : Type()

    object Boolean : Type()

    class Array(
        val elementType: Type
    ) : Type()

    class ClassType(
        val identifier: String
    ) : Type()
}

/**
 * Sealed AST-Node class structure
 */
sealed class Node {

    /************************************************
     ** Class
     ************************************************/

    class Program(
        val classes: List<ClassDeclaration>,
    ) : Node()

    class ClassDeclaration(
        val name: String,
        val member: List<ClassMember>,
    ) : Node()

    open class ClassMember(
        val name: String
    ) : Node()

    class Field(
        name: String,
        val type: Type,
    ) : ClassMember(name)

    open class Method(
        name: String,
        val returnType: Type,
        val parameters: List<Parameter>,
        // TODO MethodRest,
        val block: Block,
    ) : ClassMember(name)

    class MainMethod(
        block: Block,
    ) : Method(
        "main",
        Type.Void,
        listOf(
            Parameter(
                "args", // TODO argument name constant? -> doesn't matter, because it cannot be used
                Type.Array(Type.ClassType("String"))
            )
        ),
        block
    )

    class Parameter(
        val name: String,
        val type: Type,
    ) : Node()

    /************************************************
     ** Statement
     ************************************************/

    open class BlockStatement : Node()

    class LocalVariableDeclarationStatement(
        val name: String,
        val type: Type,
        val initializer: Expression?,
    ) : BlockStatement()

    open class Statement : BlockStatement()

    object EmptyStatement : Statement()

    class Block(
        val statements: List<BlockStatement>,
    ) : Statement()

    class IfStatement(
        val condition: Expression,
        val trueStatement: Statement,
        val falseStatement: Statement?,
    ) : Statement()

    class WhileStatement(
        val condition: Expression,
        val statement: Statement,
    ) : Statement()

    class ReturnStatement(
        val expression: Expression?,
    ) : Statement()

    class ExpressionStatement(
        val expression: Expression,
    ) : Statement()

    /************************************************
     ** Expression
     ************************************************/

    open class Expression : Node()

    class AssignmentExpression(
        val target: Expression, // TODO do we want the type to be Expression?
        val expression: Expression,
    ) : Expression()

    open class BinaryExpression(
        val left: Expression,
        val right: Expression,
    ) : Expression()

    class LogicalExpression(
        left: Expression,
        right: Expression,
        val operation: Operation
    ) : BinaryExpression(left, right) {
        enum class Operation {
            OR,
            AND,
        }
    }

    class RelationalExpression(
        left: Expression,
        right: Expression,
        val relation: Relation, // TODO maybe separate classes?
    ) : BinaryExpression(left, right) {
        enum class Relation {
            EQUALS,
            NOT_EQUALS,
            LESS_THAN,
            MORE_THAN,
            LESS_EQUALS,
            MORE_EQUALS,
        }
    }

    class CalculationExpression(
        left: Expression,
        right: Expression,
        val operation: Operation
    ) : BinaryExpression(left, right) {
        enum class Operation {
            ADDITION,
            SUBTRACTION,
            MULTIPLICATION,
            DIVISION,
        }
    }

    class UnaryExpression(
        val expression: Expression,
        val operation: Operation
    ) {
        enum class Operation {
            NOT,
            MINUS,
        }
    }

    class MethodInvocationExpression(
        val target: Expression?,
        val method: String,
        val arguments: List<Expression>
    ) : Expression()

    class FieldAccessExpression(
        val target: Expression,
        val field: String,
    ) : Expression()

    class ArrayAccessExpression(
        val target: Expression,
        val index: Expression,
    ) : Expression()

    /************************************************
     ** Primary expression
     ************************************************/

    class IdentifierExpression(
        val name: String,
    ) : Expression()

    class LiteralExpression<T>(
        val value: T,
    ) : Expression()

    class NewObjectExpression(
        val clazz: String,
    ) : Expression()

    class NewArrayExpression(
        val type: Type.Array,
        val length: Expression,
    ) : Expression()
}
