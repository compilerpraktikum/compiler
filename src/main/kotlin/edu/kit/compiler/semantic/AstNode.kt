package edu.kit.compiler.semantic

import edu.kit.compiler.ast.AST
import edu.kit.compiler.lex.SourceRange
import edu.kit.compiler.lex.Symbol
import edu.kit.compiler.semantic.AstNode.ClassMember.FieldDeclaration
import edu.kit.compiler.semantic.AstNode.ClassMember.SubroutineDeclaration

/**
 * Abstract syntax tree for the semantic phase. This is a separate class structure from the parsed AST due to
 * encapsulation concerns and to free it from the [edu.kit.compiler.wrapper.wrappers.Parsed] wrapping.
 *
 * @param sourceRange [SourceRange] that spans the contents of this node in the compilation unit
 */
sealed class AstNode(open val sourceRange: SourceRange) {

    data class Identifier(val symbol: Symbol, val sourceRange: SourceRange) {
        val text: String
            get() = symbol.text
    }

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
        val name: Identifier,
        val members: List<ClassMember>,
        sourceRange: SourceRange
    ) : AstNode(sourceRange) {

        lateinit var namespace: ClassNamespace
    }

    /**
     * Class members ([SubroutineDeclaration] and [FieldDeclaration])
     *
     * @param name field/method name
     */
    sealed class ClassMember(val name: Identifier, sourceRange: SourceRange) :
        AstNode(sourceRange) {

        /**
         * @param block the method's code
         * @param throwsException exception class identifier if this method has a `throws` clause
         */
        sealed class SubroutineDeclaration(
            val returnType: SemanticType,
            name: Identifier,
            val throwsException: Identifier?,
            val block: Statement.Block,
            val parameters: List<Parameter>,
            sourceRange: SourceRange
        ) : ClassMember(name, sourceRange) {

            /**
             * Special case of a [SubroutineDeclaration] that is the main entry point
             */
            class MainMethodDeclaration(
                returnType: SemanticType,
                name: Identifier,
                throwsException: Identifier?,
                block: Statement.Block,
                parameters: List<Parameter>,
                sourceRange: SourceRange
            ) : SubroutineDeclaration(returnType, name, throwsException, block, parameters, sourceRange)

            /**
             * A method class member declaration
             */
            class MethodDeclaration(
                returnType: SemanticType,
                name: Identifier,
                throwsException: Identifier?,
                block: Statement.Block,
                parameters: List<Parameter>,
                sourceRange: SourceRange
            ) : SubroutineDeclaration(returnType, name, throwsException, block, parameters, sourceRange)

            /**
             * A formal method parameter
             */
            class Parameter(
                val name: Identifier,
                val type: SemanticType,
                sourceRange: SourceRange
            ) : AstNode(sourceRange)
        }

        /**
         * A class field declaration
         */
        class FieldDeclaration(
            name: Identifier,
            val type: SemanticType,
            sourceRange: SourceRange
        ) :
            ClassMember(name, sourceRange)
    }

    sealed class Expression(sourceRange: SourceRange) : AstNode(sourceRange) {
        /**
         * Expression type synthesized from underlying primitives during analysis
         */
        abstract val actualType: SemanticType

        /**
         * Expected type (inherited type from outside context), maybe empty (e.g. in a comparison, the left argument has
         * no expected type)
         */
        lateinit var expectedType: SemanticType

        /**
         * Primary expression encompassing a single identifier
         */
        class IdentifierExpression(val name: Identifier, sourceRange: SourceRange) : Expression(sourceRange) {
            /**
             * Definition of the referenced member
             */
            var definition: VariableDefinition? = null

            override val actualType: SemanticType
                get() = when (val node = definition?.node) {
                    is VariableNode.Field -> node.node.type
                    is VariableNode.Parameter -> node.node.type
                    is VariableNode.LocalVariable -> node.node.type
                    null -> SemanticType.Error
                }
        }

        /**
         * A literal value, not necessarily within legal bounds
         */
        sealed class LiteralExpression(sourceRange: SourceRange) : Expression(sourceRange) {
            /**
             * Integer value expression. The integer may be outside of legal bounds
             */
            class LiteralIntExpression(val value: String, val isParentized: Boolean, sourceRange: SourceRange) : LiteralExpression(sourceRange) {
                override val actualType: SemanticType
                    get() = SemanticType.Integer
            }

            /**
             * Boolean literal expression. Value has already been verified and is thus legal. Types have not been set
             * yet though.
             */
            class LiteralBoolExpression(val value: Boolean, sourceRange: SourceRange) : LiteralExpression(sourceRange) {
                override val actualType: SemanticType
                    get() = SemanticType.Boolean
            }

            /**
             * Null value expression. Cannot have an actual type.
             */
            class LiteralNullExpression(sourceRange: SourceRange) : LiteralExpression(sourceRange) {
                override val actualType: SemanticType
                    get() = SemanticType.Null
            }

            /**
             * This expression.
             */
            class LiteralThisExpression(sourceRange: SourceRange) : LiteralExpression(sourceRange) {
                lateinit var definition: ClassDefinition

                override val actualType: SemanticType
                    get() = SemanticType.Class(definition.node.name)
            }
        }

        /**
         * Object instantiation expression
         */
        class NewObjectExpression(val type: SemanticType.Class, sourceRange: SourceRange) : Expression(sourceRange) {
            override val actualType: SemanticType
                get() = type
        }

        /**
         * Array instantiation expression
         */
        class NewArrayExpression(
            val type: SemanticType.Array,
            val length: Expression,
            sourceRange: SourceRange
        ) : Expression(sourceRange) {
            override val actualType: SemanticType
                get() = type
        }

        /**
         * Expression with two operands
         */
        class BinaryOperation(
            val left: Expression,
            val right: Expression,
            val operation: AST.BinaryExpression.Operation,
            sourceRange: SourceRange
        ) : Expression(sourceRange) {
            override val actualType: SemanticType
                get() = when (operation) {
                    AST.BinaryExpression.Operation.EQUALS, AST.BinaryExpression.Operation.NOT_EQUALS,
                    AST.BinaryExpression.Operation.GREATER_EQUALS, AST.BinaryExpression.Operation.GREATER_THAN,
                    AST.BinaryExpression.Operation.LESS_EQUALS, AST.BinaryExpression.Operation.LESS_THAN,
                    AST.BinaryExpression.Operation.AND, AST.BinaryExpression.Operation.OR ->
                        SemanticType.Boolean
                    AST.BinaryExpression.Operation.MULTIPLICATION, AST.BinaryExpression.Operation.MODULO,
                    AST.BinaryExpression.Operation.ADDITION, AST.BinaryExpression.Operation.SUBTRACTION,
                    AST.BinaryExpression.Operation.DIVISION ->
                        SemanticType.Integer
                    AST.BinaryExpression.Operation.ASSIGNMENT ->
                        left.actualType
                }
        }

        /**
         * Expression with one pre- or postfix operand
         */
        class UnaryOperation(
            val inner: Expression,
            val operation: AST.UnaryExpression.Operation,
            sourceRange: SourceRange
        ) : Expression(
            sourceRange
        ) {
            override val actualType: SemanticType
                get() = when (operation) {
                    AST.UnaryExpression.Operation.NOT -> SemanticType.Boolean
                    AST.UnaryExpression.Operation.MINUS -> SemanticType.Integer
                }
        }

        /**
         * Method call and return value
         *
         * **Note:** If [type] is [Internal][MethodInvocationExpression.Type.Internal] then [target] may not be fully analyzed.
         *
         * @param target method call target instance, or `null` if implicitly `this`
         * @param method method name
         * @param arguments concrete argument expressions
         */
        class MethodInvocationExpression(
            val target: Expression?,
            val method: Identifier,
            val arguments: List<Expression>,
            sourceRange: SourceRange
        ) : Expression(sourceRange) {
            var type: Type? = null

            override val actualType: SemanticType
                get() = type?.returnType ?: SemanticType.Error

            sealed class Type {
                abstract val returnType: SemanticType

                /**
                 * Normal method declared in source code.
                 */
                class Normal(val definition: MethodDefinition) : Type() {
                    override val returnType: SemanticType
                        get() = definition.node.returnType
                }

                /**
                 * Internal method provided by the runtime library.
                 */
                class Internal(val name: String, val fullName: String, override val returnType: SemanticType, val parameters: List<SemanticType>) : Type()
            }
        }

        /**
         * Field Access on an Expression
         *
         * @param target field access target instance
         * @param field field symbol the class
         */
        class FieldAccessExpression(
            val target: Expression,
            val field: Identifier,
            sourceRange: SourceRange
        ) : Expression(sourceRange) {
            var definition: FieldDefinition? = null

            override val actualType: SemanticType
                get() = definition?.node?.type ?: SemanticType.Error
        }

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
        ) : Expression(sourceRange) {
            override val actualType: SemanticType
                get() = when (val type = this.target.actualType) {
                    is SemanticType.Array -> type.elementType
                    else -> SemanticType.Error
                }
        }
    }

    sealed class Statement(sourceRange: SourceRange) : AstNode(sourceRange) {
        /**
         * Declaration (and optional assignment) statement of a local variable in a method
         */
        class LocalVariableDeclaration(
            val name: Identifier,
            val type: SemanticType,
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
        class Block(val statements: List<Statement>, sourceRange: SourceRange) : Statement(sourceRange)
    }
}
