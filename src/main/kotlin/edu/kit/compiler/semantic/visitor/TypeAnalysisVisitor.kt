package edu.kit.compiler.semantic.visitor

import edu.kit.compiler.ast.AST
import edu.kit.compiler.lex.SourceFile
import edu.kit.compiler.lex.SourcePosition
import edu.kit.compiler.lex.SourceRange
import edu.kit.compiler.semantic.AstNode
import edu.kit.compiler.semantic.SemanticType

/**
 * Type analysis. Run name analysis beforehand.
 *
 *
 * @param sourceFile input token stream to annotate with errors
 */
class TypeAnalysisVisitor(private val sourceFile: SourceFile) : AbstractVisitor() {

    lateinit var currentExpectedMethodReturnType: SemanticType

    override fun visitParameter(parameter: AstNode.ClassMember.SubroutineDeclaration.Parameter) {
        errorIfFalse(parameter.sourceRange, "No void typed parameters.") { parameter.type !is SemanticType.Void }
        super.visitParameter(parameter)
    }

    override fun visitLocalVariableDeclaration(localVariableDeclaration: AstNode.Statement.LocalVariableDeclaration) {
        if (localVariableDeclaration.initializer != null) {
            localVariableDeclaration.initializer.expectedType = localVariableDeclaration.type
        }
        // type check on children
        super.visitLocalVariableDeclaration(localVariableDeclaration)

        // L-Values check!
        errorIfTrue(localVariableDeclaration.sourceRange, "No \"void\" variables.") { localVariableDeclaration.type is SemanticType.Void }
        errorIfTrue(localVariableDeclaration.sourceRange, "No \"String\" instantiation.") {
            // TODO this needs tobe checked elsewhere
            localVariableDeclaration.type is SemanticType.Class && localVariableDeclaration.type.name.symbol.text == "String" && localVariableDeclaration.initializer != null
        }

        // type check
        errorIfFalse(localVariableDeclaration.sourceRange, "Initializer and declared type don't match") {
            localVariableDeclaration.initializer?.actualType != localVariableDeclaration.type
        }
    }

    override fun visitArrayAccessExpression(arrayAccessExpression: AstNode.Expression.ArrayAccessExpression) {
        super.visitArrayAccessExpression(arrayAccessExpression)
        // left-to-right: check target, then check index.

        errorIfFalse(arrayAccessExpression.sourceRange, "Array access target is no array.") {
            arrayAccessExpression.target.actualType is SemanticType.Array
        }
        errorIfFalse(arrayAccessExpression.sourceRange, "Only \"Int\"-typed array indices.") {
            arrayAccessExpression.index.actualType is SemanticType.Integer
        }
    }

    override fun visitFieldAccessExpression(fieldAccessExpression: AstNode.Expression.FieldAccessExpression) {
        // test.testvar = 1; test => target
        // ExpressionStatement -> BinOP ASSIGN - > left => Fieldaccess && right => Int
        // ExpressionStatement.actualType = ?
//        fieldAccessExpression
//        super.visitFieldAcces.Expression(fieldAccessExpression)
        errorIfFalse(fieldAccessExpression.sourceRange, "Field access can only apply to targets of complex type.") {
            fieldAccessExpression.target.actualType is SemanticType.Class
        }
        errorIfTrue(fieldAccessExpression.sourceRange, "Arrays have no fields.") { fieldAccessExpression.target.actualType is SemanticType.Array }
        checkActualTypeEqualsExpectedType(fieldAccessExpression)
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
        errorIfTrue(returnStatement.sourceRange, "No return values on methods with return type \"void\".") {
            currentExpectedMethodReturnType is SemanticType.Void && returnStatement.expression != null
        }
        if (returnStatement.expression != null) {
            returnStatement.expression.expectedType = currentExpectedMethodReturnType
            super.visitReturnStatement(returnStatement)
            checkActualTypeEqualsExpectedType(returnStatement.expression)
        }
    }

    override fun visitExpressionStatement(expressionStatement: AstNode.Statement.ExpressionStatement) {
        errorIfFalse(expressionStatement.sourceRange, "Only method invocations and assignments are allowed as statements.") {
            expressionStatement.expression is AstNode.Expression.MethodInvocationExpression ||
                (expressionStatement.expression is AstNode.Expression.BinaryOperation && expressionStatement.expression.operation == AST.BinaryExpression.Operation.ASSIGNMENT)
        }
        expressionStatement.expression.expectedType = expressionStatement.expression.actualType
        super.visitExpressionStatement(expressionStatement)
    }

    override fun visitBinaryOperation(binaryOperation: AstNode.Expression.BinaryOperation) {

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
            AST.BinaryExpression.Operation.EQUALS, AST.BinaryExpression.Operation.NOT_EQUALS, AST.BinaryExpression.Operation.ASSIGNMENT ->
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

        checkActualTypeEqualsExpectedType(binaryOperation)
    }

    override fun visitIfStatement(ifStatement: AstNode.Statement.IfStatement) {
        ifStatement.condition.expectedType = SemanticType.Boolean
        super.visitIfStatement(ifStatement)
    }

    override fun visitWhileStatement(whileStatement: AstNode.Statement.WhileStatement) {
        whileStatement.condition.expectedType = SemanticType.Boolean
        super.visitWhileStatement(whileStatement)
    }

    override fun visitMethodInvocationExpression(methodInvocationExpression: AstNode.Expression.MethodInvocationExpression) {
        // methodInvocationExpression.definition.node is of Type MethodDeclaration, pair.second is of type Parameter
        // expect that argument's types match the parameter's types in the definition.
        methodInvocationExpression.arguments
            .zip(methodInvocationExpression.definition!!.node.parameters)
            .forEach { pair -> pair.first.expectedType = pair.second.type }

        if (methodInvocationExpression.target != null) {
            // no expectations.
            methodInvocationExpression.target.expectedType = methodInvocationExpression.target.actualType
        }
        methodInvocationExpression.expectedType = methodInvocationExpression.actualType
        super.visitMethodInvocationExpression(methodInvocationExpression)
        // may not be the best message...
        errorIfFalse(methodInvocationExpression.sourceRange, "You can only invoke methods on identifiers or \"this\"") {
            methodInvocationExpression.target is AstNode.Expression.IdentifierExpression ||
                methodInvocationExpression.target is AstNode.Expression.LiteralExpression.LiteralThisExpression || methodInvocationExpression.target == null
        }

        checkActualTypeEqualsExpectedType(methodInvocationExpression)
    }

    override fun visitIdentifierExpression(identifierExpression: AstNode.Expression.IdentifierExpression) {
        checkActualTypeEqualsExpectedType(identifierExpression)
        super.visitIdentifierExpression(identifierExpression) // NOOP
    }

    override fun visitNewArrayExpression(newArrayExpression: AstNode.Expression.NewArrayExpression) {
        newArrayExpression.length.expectedType = SemanticType.Integer
        super.visitNewArrayExpression(newArrayExpression)

        checkActualTypeEqualsExpectedType(newArrayExpression)
    }

    override fun visitArrayType(arrayType: SemanticType.Array) {
        // TODO fix non-existing sourceRange
        errorIfTrue(createSourceRangeDummy(), "No void typed arrays.") {
            arrayType.elementType is SemanticType.Void
        }
        super.visitArrayType(arrayType)
    }

    override fun visitNewObjectExpression(newObjectExpression: AstNode.Expression.NewObjectExpression) {
        super.visitNewObjectExpression(newObjectExpression)
        checkActualTypeEqualsExpectedType(newObjectExpression)
    }

    override fun visitLiteralBoolExpression(literalBoolExpression: AstNode.Expression.LiteralExpression.LiteralBoolExpression) {
        literalBoolExpression.expectedType = SemanticType.Boolean
        super.visitLiteralBoolExpression(literalBoolExpression)
        checkActualTypeEqualsExpectedType(literalBoolExpression)
    }

    override fun visitLiteralIntExpression(literalIntExpression: AstNode.Expression.LiteralExpression.LiteralIntExpression) {
        // can only be positive
        // TODO check ${literalIntExpression.value} not negative, not greater 2^(31)-1 (in other visitor)

        checkActualTypeEqualsExpectedType(literalIntExpression)
    }

    override fun visitLiteralNullExpression(literalNullExpression: AstNode.Expression.LiteralExpression.LiteralNullExpression) {
        checkActualTypeEqualsExpectedType(literalNullExpression)
    }

    override fun visitUnaryOperation(unaryOperation: AstNode.Expression.UnaryOperation) {
        unaryOperation.inner.expectedType = when (unaryOperation.operation) {
            AST.UnaryExpression.Operation.MINUS -> SemanticType.Integer
            AST.UnaryExpression.Operation.NOT -> SemanticType.Boolean
        }

        super.visitUnaryOperation(unaryOperation)
        checkActualTypeEqualsExpectedType(unaryOperation)
    }

    private fun checkActualTypeEqualsExpectedType(expression: AstNode.Expression) {
        // trick17 TODO remove after debugging.
        try {
            expression.expectedType
        } catch (uninitializedPropertyAccessException: UninitializedPropertyAccessException) {
            errorIfTrue(expression.sourceRange, "BUG Expression has no expected type!") { true }
            // TODO ON/OFF-comment "return" to know where itt fails
//            return
        }

        errorIfFalse(expression.sourceRange, "Expected type ${expression.expectedType}, got ${expression.actualType}") {
            expression.actualType == expression.expectedType
        }
    }

    private fun errorIfFalse(sourceRange: SourceRange, errorMsg: String, function: () -> kotlin.Boolean) {
        checkAndAnnotateSourceFileIfNot(sourceFile, sourceRange, errorMsg, function)
        // TODO more?
    }

    private fun errorIfTrue(sourceRange: SourceRange, errorMsg: String, function: () -> kotlin.Boolean) {
        checkAndAnnotateSourceFileIfNot(sourceFile, sourceRange, errorMsg) { !function() }
        // TODO more?
    }

    // TODO this should not be necessary!
    private fun createSourceRangeDummy() = SourceRange(SourcePosition(sourceFile, 0), 0)
}
