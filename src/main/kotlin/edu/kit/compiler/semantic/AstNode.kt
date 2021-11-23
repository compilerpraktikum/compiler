package edu.kit.compiler.semantic

import edu.kit.compiler.ast.AST
import edu.kit.compiler.lex.SourceRange
import edu.kit.compiler.lex.Symbol
import edu.kit.compiler.semantic.AstNode.ClassMember.FieldDeclaration
import edu.kit.compiler.semantic.AstNode.ClassMember.SubroutineDeclaration

/**
 * Abstract syntax tree for the semantic phase. This is a separate class structure from the parsed AST due to
 * encapsulation concerns and to free it from the [edu.kit.compiler.wrapper.wrappers.Lenient] wrapping.
 *
 * @param sourceRange [SourceRange] that spans the contents of this node in the compilation unit
 */
sealed class AstNode(val sourceRange: SourceRange) {

    /**
     * The entire program that is being compiled from one compilation unit
     *
     * @param classes all classes defined in the compilation unit
     */
    class Program(val classes: List<ClassDeclaration>, sourceRange: SourceRange) : AstNode(sourceRange)

    /**
     * A class with all its members
     *
     * @param name class name
     * @param members all class members (fields, methods and potentially main method(s))
     */
    class ClassDeclaration(
        val name: Symbol,
        val members: List<ClassMember>,
        sourceRange: SourceRange
    ) : AstNode(sourceRange) {
        lateinit var classNamespace: Namespace.ClassNamespace
    }

    /**
     * Class members ([SubroutineDeclaration] and [FieldDeclaration])
     *
     * @param name field/method name
     */
    sealed class ClassMember(val name: Symbol, sourceRange: SourceRange) :
        AstNode(sourceRange) {

        /**
         * @param block the method's code
         * @param throwsException exception class identifier if this method has a `throws` clause
         */
        sealed class SubroutineDeclaration(
            val parsedReturnType: ParsedType,
            name: Symbol,
            val throwsException: Symbol?,
            val block: Statement.Block,
            val parameters: List<Parameter>,
            sourceRange: SourceRange
        ) : ClassMember(name, sourceRange) {
            lateinit var methodNamespace: Namespace.MethodNamespace

            lateinit var returnType: SemanticType

            /**
             * Special case of a [SubroutineDeclaration] that is the main entry point
             */
            class MainMethodDeclaration(
                parsedReturnType: ParsedType,
                name: Symbol,
                throwsException: Symbol?,
                block: Statement.Block,
                parameters: List<Parameter>,
                sourceRange: SourceRange
            ) : SubroutineDeclaration(parsedReturnType, name, throwsException, block, parameters, sourceRange)

            /**
             * A method class member declaration
             */
            class MethodDeclaration(
                parsedReturnType: ParsedType,
                name: Symbol,
                throwsException: Symbol?,
                block: Statement.Block,
                parameters: List<Parameter>,
                sourceRange: SourceRange
            ) : SubroutineDeclaration(parsedReturnType, name, throwsException, block, parameters, sourceRange)

            /**
             * A formal method parameter
             */
            inner class Parameter(
                val name: Symbol,
                val type: ParsedType,
                sourceRange: SourceRange
            ) : AstNode(sourceRange) {
                lateinit var semanticType: SemanticType
            }
        }

        /**
         * A class field declaration
         *
         * @param parsedType the field type as specified by the parser
         */
        class FieldDeclaration(name: Symbol, val parsedType: ParsedType, sourceRange: SourceRange) :
            ClassMember(name, sourceRange)
    }

    sealed class Expression(sourceRange: SourceRange) : AstNode(sourceRange) {
        /**
         * Expression type synthesized from underlying primitives during analysis
         */
        lateinit var actualSemanticType: SemanticType

        /**
         * Expected type (inherited type from outside context), maybe empty (e.g. in a comparison, the left argument has
         * no expected type)
         */
        lateinit var expectedSemanticType: SemanticType

        /**
         * Primary expression encompassing a single identifier
         */
        class IdentifierExpression(val symbol: Symbol, sourceRange: SourceRange) : Expression(sourceRange) {
            /**
             * Definition of the referenced member
             */
            lateinit var definition: Definition
        }

        /**
         * A literal value, not necessarily within legal bounds
         */
        sealed class LiteralExpression(sourceRange: SourceRange) : Expression(sourceRange) {
            /**
             * Integer value expression. The integer may be outside of legal bounds
             */
            class LiteralIntExpression(val value: String, sourceRange: SourceRange) : LiteralExpression(sourceRange)

            /**
             * Boolean literal expression. Value has already been verified and is thus legal. Types have not been set
             * yet though.
             */
            class LiteralBoolExpression(val value: Boolean, sourceRange: SourceRange) : LiteralExpression(sourceRange)

            /**
             * Null value expression. Cannot have an actual type.
             */
            class LiteralNullExpression(sourceRange: SourceRange) : LiteralExpression(sourceRange)
        }

        /**
         * Object instantiation expression
         *
         * @param clazz instantiated class name
         */
        class NewObjectExpression(val clazz: Symbol, sourceRange: SourceRange) : Expression(sourceRange)

        class NewArrayExpression(
            val type: ParsedType.ArrayType,
            val length: Expression,
            sourceRange: SourceRange
        ) : Expression(sourceRange)

        /**
         * Expression with two operands
         */
        class BinaryOperation(
            val left: Expression,
            val right: Expression,
            val operation: AST.BinaryExpression.Operation,
            sourceRange: SourceRange
        ) : Expression(sourceRange)

        /**
         * Expression with one pre- or postfix operand
         */
        class UnaryOperation(
            val inner: Expression,
            val operation: AST.UnaryExpression.Operation,
            sourceRange: SourceRange
        ) : Expression(
            sourceRange
        )

        /**
         * Method call and return value
         *
         * @param target method call target instance, or `null` if implicitly `this`
         * @param method method name
         * @param arguments concrete argument expressions
         */
        class MethodInvocationExpression(
            val target: Expression?,
            val method: Symbol,
            val arguments: List<Expression>,
            sourceRange: SourceRange
        ) : Expression(sourceRange)

        /**
         * Field Access on an Expression
         *
         * @param target field access target instance
         * @param field field symbol the class
         */
        class FieldAccessExpression(
            val target: Expression,
            val field: Symbol,
            sourceRange: SourceRange
        ) : Expression(sourceRange)

        /**
         * Array Element Access
         *
         * @param target accessed array instance
         * @param index index in array to access
         */
        class ArrayAccessExpression(
            val target: Expression,
            val index: Expression,
            sourceRange: SourceRange
        ) : Expression(sourceRange)
    }

    sealed class Statement(sourceRange: SourceRange) : AstNode(sourceRange) {
        /**
         * Declaration (and optional assignment) statement of a local variable in a method
         */
        class LocalVariableDeclaration(
            val name: Symbol,
            val type: ParsedType,
            val initializer: Expression?,
            sourceRange: SourceRange
        ) :
            Statement(sourceRange)

        /**
         * An expression whose return value is ignored
         */
        class ExpressionStatement(val expression: Expression, sourceRange: SourceRange) : Statement(sourceRange)

        /**
         * A statement that returns a method call. May have an expression evaluation to a return value.
         *
         * @param expression to return. May be `null`, if it is a return without value
         */
        class ReturnStatement(val expression: Expression?, sourceRange: SourceRange) : Statement(sourceRange)

        class IfStatement(
            val condition: Expression,
            val thenCase: Statement,
            val elseCase: Statement?,
            sourceRange: SourceRange
        ) : Statement(sourceRange)

        class WhileStatement(
            val condition: Expression,
            val statement: Statement,
            sourceRange: SourceRange
        ) : Statement(sourceRange)

        /**
         * A block of multiple statements. An empty block is equivalent to an empty statement.
         *
         * @param statements a list of [Statements][Statement]
         */
        class Block(val statements: List<Statement>, sourceRange: SourceRange) : Statement(sourceRange) {
            lateinit var localNamespace: Namespace.LocalNamespace
        }
    }
}
