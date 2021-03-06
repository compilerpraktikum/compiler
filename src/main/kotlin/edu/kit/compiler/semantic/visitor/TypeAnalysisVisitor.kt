package edu.kit.compiler.semantic.visitor

import edu.kit.compiler.ast.AST
import edu.kit.compiler.semantic.SemanticAST
import edu.kit.compiler.semantic.SemanticType
import edu.kit.compiler.semantic.baseType
import edu.kit.compiler.semantic.display
import edu.kit.compiler.source.SourceFile
import edu.kit.compiler.source.SourceRange

/**
 * Type analysis visitor. Run name analysis beforehand.
 *
 * @param sourceFile input token stream to annotate with errors
 */
class TypeAnalysisVisitor(private val sourceFile: SourceFile) : AbstractVisitor() {

    /**
     * Return type of currently visited method. Since the visitor is depth-first, only one method can be visited at a
     * time.
     */
    private lateinit var currentExpectedMethodReturnType: SemanticType

    /**
     * Suppress error messages while this is active.
     *
     * @see separateTypeCheckPass
     */
    private var suppressTypeErrors = false

    override fun visitParameter(parameter: SemanticAST.ClassMember.SubroutineDeclaration.Parameter) {
        errorIf(parameter.type is SemanticType.Void) {
            "parameter cannot have type `void`" at parameter.typeSourceRange
        }
        super.visitParameter(parameter)
    }

    override fun visitFieldDeclaration(fieldDeclaration: SemanticAST.ClassMember.FieldDeclaration) {
        errorIf(fieldDeclaration.type is SemanticType.Void) {
            "field cannot have type `void`" at fieldDeclaration.sourceRange
        }
        super.visitFieldDeclaration(fieldDeclaration)
    }

    override fun visitLocalVariableDeclaration(localVariableDeclaration: SemanticAST.Statement.LocalVariableDeclaration) {
        if (localVariableDeclaration.initializer != null) {
            localVariableDeclaration.initializer.expectedType = localVariableDeclaration.type
        }

        if (localVariableDeclaration.type is SemanticType.Void) {
            sourceFile.error {
                "variable cannot have type `void`" at localVariableDeclaration.sourceRange
            }
            // no need to check the initializer because it would need to have type `void` which doesn't make any
            // sense, creating useless follow-up errors
            return
        } else if (localVariableDeclaration.type is SemanticType.Array && localVariableDeclaration.type.baseType is SemanticType.Void) {
            localVariableDeclaration.type.accept(this)
            // do not descent into initializer, because this would just be a follow-up error
        } else {
            super.visitLocalVariableDeclaration(localVariableDeclaration)
        }
    }

    override fun visitArrayAccessExpression(arrayAccessExpression: SemanticAST.Expression.ArrayAccessExpression) {
        if (arrayAccessExpression.target.actualType !is SemanticType.Error) {
            errorIfNot(arrayAccessExpression.target.actualType is SemanticType.Array) {
                suppressTypeErrors = true
                "array access on non-array type ${arrayAccessExpression.target.actualType.display()}" at arrayAccessExpression.sourceRange
            }
        }

        arrayAccessExpression.index.expectedType = SemanticType.Integer
        arrayAccessExpression.target.expectedType =
            SemanticType.Array(arrayAccessExpression.expectedType, arrayAccessExpression.sourceRange)
        arrayAccessExpression.target.accept(this)

        separateTypeCheckPass {
            errorIfNot(arrayAccessExpression.index.actualType is SemanticType.Integer) {
                suppressTypeErrors = true
                "expected index expression of type `int`, but got ${arrayAccessExpression.index.actualType.display()}" at arrayAccessExpression.index.sourceRange
            }
            arrayAccessExpression.index.accept(this)
        }

        checkTypesCompatible(arrayAccessExpression)
    }

    override fun visitFieldAccessExpression(fieldAccessExpression: SemanticAST.Expression.FieldAccessExpression) {
        // prevent follow-up errors
        if (fieldAccessExpression.target.actualType is SemanticType.Error) {
            return
        }

        checkTypesCompatible(fieldAccessExpression)
    }

    override fun visitMethodDeclaration(methodDeclaration: SemanticAST.ClassMember.SubroutineDeclaration.MethodDeclaration) {
        currentExpectedMethodReturnType = methodDeclaration.returnType
        super.visitMethodDeclaration(methodDeclaration)
    }

    override fun visitMainMethodDeclaration(mainMethodDeclaration: SemanticAST.ClassMember.SubroutineDeclaration.MainMethodDeclaration) {
        currentExpectedMethodReturnType = mainMethodDeclaration.returnType
        super.visitMainMethodDeclaration(mainMethodDeclaration)
    }

    override fun visitStatement(statement: SemanticAST.Statement) {
        separateTypeCheckPass {
            super.visitStatement(statement)
        }
    }

    override fun visitReturnStatement(returnStatement: SemanticAST.Statement.ReturnStatement) {
        if (returnStatement.expression != null) {
            if (currentExpectedMethodReturnType is SemanticType.Void) {
                errorIf(true) {
                    "cannot return value from method with return type `void`" at returnStatement.sourceRange
                }
                return
            }

            returnStatement.expression.expectedType = currentExpectedMethodReturnType
            super.visitReturnStatement(returnStatement)
            checkTypesCompatible(returnStatement.expression)
        } else {
            errorIfNot(currentExpectedMethodReturnType is SemanticType.Void) {
                "method with return type ${currentExpectedMethodReturnType.display()} must return a value" at returnStatement.sourceRange
            }
        }
    }

    override fun visitExpressionStatement(expressionStatement: SemanticAST.Statement.ExpressionStatement) {
        fun SemanticAST.Expression.isMethodInvocationOrAssignment(): Boolean {
            return this is SemanticAST.Expression.MethodInvocationExpression ||
                (this is SemanticAST.Expression.BinaryOperation && operation == AST.BinaryExpression.Operation.ASSIGNMENT)
        }

        errorIfNot(expressionStatement.expression.isMethodInvocationOrAssignment()) {
            "statement has no side-effects" at expressionStatement.sourceRange
        }

        expressionStatement.expression.expectedType = expressionStatement.expression.actualType
        super.visitExpressionStatement(expressionStatement)
    }

    override fun visitBinaryOperation(binaryOperation: SemanticAST.Expression.BinaryOperation) {
        if (binaryOperation.operation == AST.BinaryExpression.Operation.ASSIGNMENT) {
            binaryOperation.left.isLeftHandAssignment = true
        }
        binaryOperation.right.expectedType = when (binaryOperation.operation) {
            AST.BinaryExpression.Operation.EQUALS, AST.BinaryExpression.Operation.NOT_EQUALS, AST.BinaryExpression.Operation.ASSIGNMENT ->
                binaryOperation.left.actualType
            AST.BinaryExpression.Operation.GREATER_EQUALS, AST.BinaryExpression.Operation.GREATER_THAN,
            AST.BinaryExpression.Operation.LESS_EQUALS, AST.BinaryExpression.Operation.LESS_THAN,
            AST.BinaryExpression.Operation.MULTIPLICATION, AST.BinaryExpression.Operation.MODULO,
            AST.BinaryExpression.Operation.ADDITION, AST.BinaryExpression.Operation.SUBTRACTION,
            AST.BinaryExpression.Operation.DIVISION ->
                SemanticType.Integer
            AST.BinaryExpression.Operation.AND, AST.BinaryExpression.Operation.OR ->
                SemanticType.Boolean
        }
        binaryOperation.left.expectedType = when (binaryOperation.operation) {
            AST.BinaryExpression.Operation.EQUALS, AST.BinaryExpression.Operation.NOT_EQUALS ->
                binaryOperation.left.actualType
            AST.BinaryExpression.Operation.ASSIGNMENT ->
                binaryOperation.expectedType
            AST.BinaryExpression.Operation.GREATER_EQUALS, AST.BinaryExpression.Operation.GREATER_THAN,
            AST.BinaryExpression.Operation.LESS_EQUALS, AST.BinaryExpression.Operation.LESS_THAN,
            AST.BinaryExpression.Operation.MULTIPLICATION, AST.BinaryExpression.Operation.MODULO,
            AST.BinaryExpression.Operation.ADDITION, AST.BinaryExpression.Operation.SUBTRACTION,
            AST.BinaryExpression.Operation.DIVISION ->
                SemanticType.Integer
            AST.BinaryExpression.Operation.AND, AST.BinaryExpression.Operation.OR ->
                SemanticType.Boolean
        }
        separateTypeCheckPass {
            binaryOperation.left.accept(this)
        }
        separateTypeCheckPass {
            binaryOperation.right.accept(this)
        }

        checkTypesCompatible(binaryOperation)
    }

    override fun visitUnaryOperation(unaryOperation: SemanticAST.Expression.UnaryOperation) {
        unaryOperation.inner.expectedType = when (unaryOperation.operation) {
            AST.UnaryExpression.Operation.MINUS -> SemanticType.Integer
            AST.UnaryExpression.Operation.NOT -> SemanticType.Boolean
        }

        checkTypesCompatible(unaryOperation)
        super.visitUnaryOperation(unaryOperation)
    }

    override fun visitIfStatement(ifStatement: SemanticAST.Statement.IfStatement) {
        ifStatement.condition.expectedType = SemanticType.Boolean
        super.visitIfStatement(ifStatement)
    }

    override fun visitWhileStatement(whileStatement: SemanticAST.Statement.WhileStatement) {
        whileStatement.condition.expectedType = SemanticType.Boolean
        super.visitWhileStatement(whileStatement)
    }

    override fun visitMethodInvocationExpression(methodInvocationExpression: SemanticAST.Expression.MethodInvocationExpression) {
        if (methodInvocationExpression.target != null) {
            methodInvocationExpression.target.expectedType = methodInvocationExpression.target.actualType
            methodInvocationExpression.target.accept(this)
        }

        var argumentsListLengthValid = true

        fun checkArguments(paramTypes: List<SemanticType>, methodName: String, range: SourceRange) {
            val args = methodInvocationExpression.arguments
            if (args.size != paramTypes.size) {
                errorIf(true) {
                    "method `$methodName` requires ${paramTypes.size} argument(s), but got ${args.size}" at range
                }
                argumentsListLengthValid = false
            }

            args.zip(paramTypes).forEach { (arg, paramType) -> arg.expectedType = paramType }
        }

        when (val methodType = methodInvocationExpression.type) {
            is SemanticAST.Expression.MethodInvocationExpression.Type.Normal -> checkArguments(
                methodType.definition.node.parameters.map { it.type },
                methodInvocationExpression.method.text,
                methodInvocationExpression.method.sourceRange.extend(methodInvocationExpression.sourceRange)
            )
            is SemanticAST.Expression.MethodInvocationExpression.Type.Internal -> checkArguments(
                methodType.parameters,
                methodType.fullName,
                methodInvocationExpression.sourceRange
            )
            null -> return
        }

        if (argumentsListLengthValid) {
            methodInvocationExpression.arguments.forEach {
                separateTypeCheckPass {
                    it.accept(this)
                }
            }
        }

        checkTypesCompatible(methodInvocationExpression)
    }

    override fun visitIdentifierExpression(identifierExpression: SemanticAST.Expression.IdentifierExpression) {
        checkTypesCompatible(identifierExpression)
    }

    override fun visitNewArrayExpression(newArrayExpression: SemanticAST.Expression.NewArrayExpression) {
        newArrayExpression.length.expectedType = SemanticType.Integer
        super.visitNewArrayExpression(newArrayExpression)

        checkTypesCompatible(newArrayExpression)
    }

    override fun visitArrayType(arrayType: SemanticType.Array) {
        errorIf(arrayType.elementType is SemanticType.Void) {
            "array of type `void` not allowed" at arrayType.sourceRange
        }
        super.visitArrayType(arrayType)
    }

    override fun visitNewObjectExpression(newObjectExpression: SemanticAST.Expression.NewObjectExpression) {
        checkTypesCompatible(newObjectExpression)
    }

    override fun visitLiteralBoolExpression(literalBoolExpression: SemanticAST.Expression.LiteralExpression.LiteralBoolExpression) {
        checkTypesCompatible(literalBoolExpression)
    }

    override fun visitLiteralIntExpression(literalIntExpression: SemanticAST.Expression.LiteralExpression.LiteralIntExpression) {
        checkTypesCompatible(literalIntExpression)
    }

    override fun visitLiteralNullExpression(literalNullExpression: SemanticAST.Expression.LiteralExpression.LiteralNullExpression) {
        checkTypesCompatible(literalNullExpression)
    }

    override fun visitLiteralThisExpression(literalThisExpression: SemanticAST.Expression.LiteralExpression.LiteralThisExpression) {
        checkTypesCompatible(literalThisExpression)
    }

    private fun checkTypesCompatible(expression: SemanticAST.Expression) {
        if (!suppressTypeErrors && expression.actualType != SemanticType.Error && expression.expectedType != SemanticType.Error) {
            errorIfNot(areTypesCompatible(expression.expectedType, expression.actualType)) {
                suppressTypeErrors = true
                "incompatible types: expected ${expression.expectedType.display()}, but got ${expression.actualType.display()}" at expression.sourceRange
            }
        } else {
            assert(sourceFile.hasError)
        }
    }

    private fun areTypesCompatible(expected: SemanticType, actual: SemanticType): Boolean {
        fun isNullCompatible(type: SemanticType) =
            type == SemanticType.Null || type is SemanticType.Class || type is SemanticType.Array

        if (expected == SemanticType.Null) {
            return isNullCompatible(actual)
        } else if (actual == SemanticType.Null) {
            return isNullCompatible(expected)
        }

        if (expected is SemanticType.Class) {
            return actual is SemanticType.Class && expected.name.symbol == actual.name.symbol
        } else if (expected is SemanticType.Array) {
            return actual is SemanticType.Array && areTypesCompatible(expected.elementType, actual.elementType)
        }

        return expected === actual
    }

    private fun errorIf(condition: Boolean, lazyAnnotation: () -> AnnotationBuilder) =
        sourceFile.errorIf(condition, lazyAnnotation)

    private fun errorIfNot(condition: Boolean, lazyAnnotation: () -> AnnotationBuilder) =
        sourceFile.errorIfNot(condition, lazyAnnotation)

    /**
     * Without error suppression the following code
     * ```java
     * int[] a;
     * a[0][0];
     * ```
     * will print the following errors
     *  - `a` was expected to be `int[][]` but was `int[]`
     *  - `a[0]` was expected to be `int[]` but was `int`
     *  - `a[0][0]` is an array access on a non-class type `int`
     *
     * This is undesirable because all the above errors share the same root cause. To prevent this,
     * subsequent errors after the first type error are suppressed by default. To explicitly opt-out of this
     * behaviour, one can wrap code in
     * ```kt
     * separateTypeCheckPass {
     *     // code
     * }
     * ```
     * which will completely decouple the nested checks from those outside. This can be used for example to
     * separate type checks for different arguments of a method call.
     */
    private fun separateTypeCheckPass(block: () -> Unit) {
        val prevValue = suppressTypeErrors
        suppressTypeErrors = false
        block()
        suppressTypeErrors = prevValue
    }
}
