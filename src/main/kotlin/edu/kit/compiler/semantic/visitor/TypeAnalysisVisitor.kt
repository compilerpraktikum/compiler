package edu.kit.compiler.semantic.visitor

import edu.kit.compiler.ast.AST
import edu.kit.compiler.lex.SourceFile
import edu.kit.compiler.lex.SourcePosition
import edu.kit.compiler.lex.extend
import edu.kit.compiler.semantic.AstNode
import edu.kit.compiler.semantic.SemanticType
import edu.kit.compiler.semantic.display

/**
 * Type analysis. Run name analysis beforehand.
 *
 *
 * @param sourceFile input token stream to annotate with errors
 */
class TypeAnalysisVisitor(private val sourceFile: SourceFile) : AbstractVisitor() {

    lateinit var currentExpectedMethodReturnType: SemanticType

    override fun visitParameter(parameter: AstNode.ClassMember.SubroutineDeclaration.Parameter) {
        errorIf(parameter.type is SemanticType.Void) {
            "parameter cannot have type `void`" at parameter.sourceRange
        }
        super.visitParameter(parameter)
    }

    override fun visitFieldDeclaration(fieldDeclaration: AstNode.ClassMember.FieldDeclaration) {
        errorIf(fieldDeclaration.type is SemanticType.Void) {
            "field cannot have type `void`" at fieldDeclaration.sourceRange
        }
        super.visitFieldDeclaration(fieldDeclaration)
    }

    override fun visitLocalVariableDeclaration(localVariableDeclaration: AstNode.Statement.LocalVariableDeclaration) {
        if (localVariableDeclaration.initializer != null) {
            localVariableDeclaration.initializer.expectedType = localVariableDeclaration.type
        }

        super.visitLocalVariableDeclaration(localVariableDeclaration)

        errorIf(localVariableDeclaration.type is SemanticType.Void) {
            "variable cannot have type `void`" at localVariableDeclaration.sourceRange
        }

        if (localVariableDeclaration.initializer != null) {
            errorIfNot(areTypesCompatible(localVariableDeclaration.type, localVariableDeclaration.initializer.actualType)) {
                "incompatible type of initializer: expected ${localVariableDeclaration.type.display()}, but got ${localVariableDeclaration.initializer.actualType.display()}" at localVariableDeclaration.sourceRange
            }
        }
    }

    override fun visitArrayAccessExpression(arrayAccessExpression: AstNode.Expression.ArrayAccessExpression) {

        arrayAccessExpression.index.expectedType = SemanticType.Integer
        arrayAccessExpression.target.expectedType = SemanticType.Array(arrayAccessExpression.expectedType)
        super.visitArrayAccessExpression(arrayAccessExpression)
        // left-to-right: check target, then check index.

        errorIfNot(arrayAccessExpression.target.actualType is SemanticType.Array) {
            "array access on non-array type ${arrayAccessExpression.target.actualType.display()}" at arrayAccessExpression.sourceRange
        }

        errorIfNot(arrayAccessExpression.index.actualType is SemanticType.Integer) {
            "expected index expression of type `int`, but got ${arrayAccessExpression.index.actualType.display()}" at arrayAccessExpression.sourceRange
        }

        checkTypesCompatible(arrayAccessExpression)
    }

    override fun visitFieldAccessExpression(fieldAccessExpression: AstNode.Expression.FieldAccessExpression) {
        // expected is set TO INT
        // test.testvar <= testvar is Int?
        // test.testvar = 1; test => target
        // ExpressionStatement -> BinOP ASSIGN - > left => Fieldaccess && right => Int
        // ExpressionStatement.actualType = ?
//        fieldAccessExpression
//        super.visitFieldAcces.Expression(fieldAccessExpression)
        if (fieldAccessExpression.target.actualType is SemanticType.Error) {
            return
        }
        checkTypesCompatible(fieldAccessExpression)
    }

    override fun visitMethodDeclaration(methodDeclaration: AstNode.ClassMember.SubroutineDeclaration.MethodDeclaration) {
        currentExpectedMethodReturnType = methodDeclaration.returnType
        super.visitMethodDeclaration(methodDeclaration)
    }

    override fun visitMainMethodDeclaration(mainMethodDeclaration: AstNode.ClassMember.SubroutineDeclaration.MainMethodDeclaration) {
        currentExpectedMethodReturnType = mainMethodDeclaration.returnType
        super.visitMainMethodDeclaration(mainMethodDeclaration)
    }

    override fun visitReturnStatement(returnStatement: AstNode.Statement.ReturnStatement) {
        if (returnStatement.expression != null) {
            errorIf(currentExpectedMethodReturnType is SemanticType.Void) {
                "cannot return value from method with return type `void`" at returnStatement.sourceRange
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

    override fun visitExpressionStatement(expressionStatement: AstNode.Statement.ExpressionStatement) {
        fun AstNode.Expression.isMethodInvocationOrAssignment(): Boolean {
            return this is AstNode.Expression.MethodInvocationExpression ||
                (this is AstNode.Expression.BinaryOperation && operation == AST.BinaryExpression.Operation.ASSIGNMENT)
        }

        errorIfNot(expressionStatement.expression.isMethodInvocationOrAssignment()) {
            "statement has no side-effects" at expressionStatement.sourceRange
        }

        expressionStatement.expression.expectedType = expressionStatement.expression.actualType
        super.visitExpressionStatement(expressionStatement)
    }

    override fun visitBinaryOperation(binaryOperation: AstNode.Expression.BinaryOperation) {
        // binop.expectedTyp is set
        // exMPLE ASSIGN test.testvar = 1 <- Int

        // right.expected = Int
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
        binaryOperation.left.accept(this)
        binaryOperation.right.accept(this)

        checkTypesCompatible(binaryOperation)
    }

    override fun visitIfStatement(ifStatement: AstNode.Statement.IfStatement) {
        ifStatement.condition.expectedType = SemanticType.Boolean
        super.visitIfStatement(ifStatement)
    }

    override fun visitWhileStatement(whileStatement: AstNode.Statement.WhileStatement) {
        whileStatement.condition.expectedType = SemanticType.Boolean
        super.visitWhileStatement(whileStatement)
    }

    override fun visitClassDeclaration(classDeclaration: AstNode.ClassDeclaration) {
        super.visitClassDeclaration(classDeclaration)
    }

    override fun visitMethodInvocationExpression(methodInvocationExpression: AstNode.Expression.MethodInvocationExpression) {
        // methodInvocationExpression.definition.node is of Type MethodDeclaration, pair.second is of type Parameter
        // expect that argument's types match the parameter's types in the definition.
        var argumentsListLengthValid = true

        fun checkArguments(paramTypes: List<SemanticType>) {
            val args = methodInvocationExpression.arguments
            if (args.size != paramTypes.size) {
                errorIf(true) {
                    "method `${methodInvocationExpression.method.text}` requires ${paramTypes.size} arguments, but got ${args.size}" at methodInvocationExpression.sourceRange
                }
                argumentsListLengthValid = false
            }

            args.zip(paramTypes).forEach { (arg, paramType) -> arg.expectedType = paramType }
        }

        when (val methodType = methodInvocationExpression.type) {
            is AstNode.Expression.MethodInvocationExpression.Type.Normal -> checkArguments(methodType.definition.node.parameters.map { it.type })
            is AstNode.Expression.MethodInvocationExpression.Type.Internal -> checkArguments(methodType.parameters)
            null -> return
        }

        if (methodInvocationExpression.target != null) {
            // no expectations.
            methodInvocationExpression.target.expectedType = methodInvocationExpression.target.actualType
        }

        methodInvocationExpression.expectedType = methodInvocationExpression.actualType

        if (argumentsListLengthValid) {
            super.visitMethodInvocationExpression(methodInvocationExpression)
        }

        // may not be the best message...

        if (methodInvocationExpression.target?.actualType is SemanticType.Error) {
            return
        }
        // TODO if langeweile, evtl wieder einbauen
//        errorIfFalse(methodInvocationExpression.sourceRange, "You can only invoke methods on identifiers, newObjectExpressions, methodinvokations, or \"this\"") {
//            methodInvocationExpression.target is AstNode.Expression.MethodInvocationExpression ||
//            methodInvocationExpression.target is AstNode.Expression.IdentifierExpression ||
//                methodInvocationExpression.target is AstNode.Expression.NewObjectExpression ||
//                methodInvocationExpression.target is AstNode.Expression.LiteralExpression.LiteralThisExpression || methodInvocationExpression.target == null
//        }

        checkTypesCompatible(methodInvocationExpression)
    }

    override fun visitIdentifierExpression(identifierExpression: AstNode.Expression.IdentifierExpression) {
        checkTypesCompatible(identifierExpression)
        super.visitIdentifierExpression(identifierExpression) // NOOP
    }

    override fun visitNewArrayExpression(newArrayExpression: AstNode.Expression.NewArrayExpression) {
        newArrayExpression.length.expectedType = SemanticType.Integer
        super.visitNewArrayExpression(newArrayExpression)

        checkTypesCompatible(newArrayExpression)
    }

    override fun visitArrayType(arrayType: SemanticType.Array) {
        // TODO fix non-existing sourceRangeƒ
        errorIf(arrayType.elementType is SemanticType.Void) {
            "array of type `void` not allowed" at createSourceRangeDummy()
        }
        super.visitArrayType(arrayType)
    }

    override fun visitNewObjectExpression(newObjectExpression: AstNode.Expression.NewObjectExpression) {
        super.visitNewObjectExpression(newObjectExpression)
        checkTypesCompatible(newObjectExpression)
    }

    override fun visitLiteralBoolExpression(literalBoolExpression: AstNode.Expression.LiteralExpression.LiteralBoolExpression) {
        super.visitLiteralBoolExpression(literalBoolExpression)
        checkTypesCompatible(literalBoolExpression)
    }

    override fun visitLiteralIntExpression(literalIntExpression: AstNode.Expression.LiteralExpression.LiteralIntExpression) {
        checkTypesCompatible(literalIntExpression)
    }

    override fun visitLiteralNullExpression(literalNullExpression: AstNode.Expression.LiteralExpression.LiteralNullExpression) {
        checkTypesCompatible(literalNullExpression)
    }

    override fun visitUnaryOperation(unaryOperation: AstNode.Expression.UnaryOperation) {
        unaryOperation.inner.expectedType = when (unaryOperation.operation) {
            AST.UnaryExpression.Operation.MINUS -> SemanticType.Integer
            AST.UnaryExpression.Operation.NOT -> SemanticType.Boolean
        }

        super.visitUnaryOperation(unaryOperation)
        checkTypesCompatible(unaryOperation)
    }

    private fun checkTypesCompatible(expression: AstNode.Expression) {
        errorIfNot(areTypesCompatible(expression.expectedType, expression.actualType)) {
            "incompatible types: expected ${expression.expectedType.display()}, but got ${expression.actualType.display()}" at expression.sourceRange
        }
    }

    private fun areTypesCompatible(expected: SemanticType, actual: SemanticType): Boolean {
        fun isNullCompatible(type: SemanticType) = type == SemanticType.Null || type is SemanticType.Class || type is SemanticType.Array

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

    private fun errorIf(condition: Boolean, lazyAnnotation: () -> AnnotationBuilder) = sourceFile.errorIf(condition, lazyAnnotation)
    private fun errorIfNot(condition: Boolean, lazyAnnotation: () -> AnnotationBuilder) = sourceFile.errorIfNot(condition, lazyAnnotation)

    // TODO this should not be necessary!
    private fun createSourceRangeDummy() = SourcePosition(sourceFile, 1).extend(1)
}

fun doTypeAnalysis(program: AstNode.Program, sourceFile: SourceFile) {
    program.accept(TypeAnalysisVisitor(sourceFile))
}
