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
        parameter.type = constructSemanticType(parameter.type, TODO("get class declaration, check before"))
    }

    override fun visitLocalVariableDeclaration(localVariableDeclaration: AstNode.Statement.LocalVariableDeclaration) {
        // type check on children
        super.visitLocalVariableDeclaration(localVariableDeclaration)

        // L-Values check!
        errorIfFalse(localVariableDeclaration.sourceRange, "No \"void\" variables.") { localVariableDeclaration.type is SemanticType.Void }
        errorIfFalse(localVariableDeclaration.sourceRange, "No \"String\" instantiation.") {
            localVariableDeclaration.type is SemanticType.Class && localVariableDeclaration.type.name.symbol.text == "String"
        }

        // type check
        errorIfFalse(localVariableDeclaration.sourceRange, "Initializer and declared type don't match") {
            localVariableDeclaration.initializer?.actualType != localVariableDeclaration.type
        }
        TODO("impl!")
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
        // If everything's correct, the arrayAccessExpression's type is the elementtype
        arrayAccessExpression.actualType = (arrayAccessExpression.target.actualType as SemanticType.Array).elementType
    }

    override fun visitFieldAccessExpression(fieldAccessExpression: AstNode.Expression.FieldAccessExpression) {
        super.visitFieldAccessExpression(fieldAccessExpression)
        errorIfFalse(fieldAccessExpression.sourceRange, "Field access can only apply to targets of complex type.") {
            fieldAccessExpression.target.actualType is SemanticType.Class
        }
        when (fieldAccessExpression.target.actualType) {
            is SemanticType.Array -> TODO("Error")
        }
        // TODO identifier ==> Definition from current namespace (wait for AstNode change)!
//        fieldAccessExpression.actualType = fieldAccessExpression.field
    }

    override fun visitMethodDeclaration(methodDeclaration: AstNode.ClassMember.SubroutineDeclaration.MethodDeclaration) {
        currentExpectedMethodReturnType = methodDeclaration.returnType
        super.visitMethodDeclaration(methodDeclaration)
    }

    override fun visitMainMethodDeclaration(mainMethodDeclaration: AstNode.ClassMember.SubroutineDeclaration.MainMethodDeclaration) {
        currentExpectedMethodReturnType = mainMethodDeclaration.returnType
        super.visitMainMethodDeclaration(mainMethodDeclaration)
    }

    override fun visitClassDeclaration(classDeclaration: AstNode.ClassDeclaration) {
        // TODO maybe check for name = String, if namensanalyse doesnt do that
        super.visitClassDeclaration(classDeclaration)
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
        super.visitExpressionStatement(expressionStatement)
    }

    override fun visitBinaryOperation(binaryOperation: AstNode.Expression.BinaryOperation) {
        binaryOperation.left.accept(this)

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

        binaryOperation.right.accept(this)

        binaryOperation.actualType = when (binaryOperation.operation) {
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
                binaryOperation.left.expectedType
        }
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
        // TODO uncomment if rebased on name analysis stuff
//        methodInvocationExpression.arguments.zip(methodInvocationExpression.definition.node.parameters).forEach { pair -> pair.first.expectedType = pair.second.type }

        super.visitMethodInvocationExpression(methodInvocationExpression)
        // TODO what is with this? Do this when "this" typing is clear.
        // TODO if target == null ?
//        checkAndMessageIfNot(methodInvocationExpression.sourceRange,"") {
//            methodInvocationExpression.target is AstNode.Expression.IdentifierExpression ||
//                methodInvocationExpression.target is TODO("THIS_EXPRESSION as a literal")
//        }

        methodInvocationExpression.actualType = TODO("$methodInvocationExpression.definition.node.returnType")
        checkActualTypeEqualsExpectedType(methodInvocationExpression)
    }

    override fun visitIdentifierExpression(identifierExpression: AstNode.Expression.IdentifierExpression) {
        TODO("check if setting ${identifierExpression.actualType} is needed. We need namespace stuff for that.")
        checkActualTypeEqualsExpectedType(identifierExpression)
    }

    override fun visitNewArrayExpression(newArrayExpression: AstNode.Expression.NewArrayExpression) {
        newArrayExpression.length.expectedType = SemanticType.Integer
        super.visitNewArrayExpression(newArrayExpression)

        newArrayExpression.actualType = constructSemanticType(newArrayExpression.type)
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
        newObjectExpression.actualType = TODO("get from namespace stuff (${newObjectExpression.clazz}), must be some ComplexType")
        checkActualTypeEqualsExpectedType(newObjectExpression)
    }

    override fun visitLiteralBoolExpression(literalBoolExpression: AstNode.Expression.LiteralExpression.LiteralBoolExpression) {
        literalBoolExpression.actualType = SemanticType.Boolean
        checkActualTypeEqualsExpectedType(literalBoolExpression)
    }

    override fun visitLiteralIntExpression(literalIntExpression: AstNode.Expression.LiteralExpression.LiteralIntExpression) {
        // can only be positive
        TODO("check ${literalIntExpression.value} not negative, not greater 2^(31)-1")

        literalIntExpression.actualType = SemanticType.Integer
        checkActualTypeEqualsExpectedType(literalIntExpression)
    }

    override fun visitLiteralNullExpression(literalNullExpression: AstNode.Expression.LiteralExpression.LiteralNullExpression) {
        literalNullExpression.actualType = TODO("null should be a type or whatever")
        checkActualTypeEqualsExpectedType(literalNullExpression)
    }

    override fun visitUnaryOperation(unaryOperation: AstNode.Expression.UnaryOperation) {
        unaryOperation.inner.expectedType = when (unaryOperation.operation) {
            AST.UnaryExpression.Operation.MINUS -> SemanticType.Integer
            AST.UnaryExpression.Operation.NOT -> SemanticType.Boolean
        }

        super.visitUnaryOperation(unaryOperation)
        unaryOperation.actualType = unaryOperation.inner.expectedType
        checkActualTypeEqualsExpectedType(unaryOperation)
    }

    private fun checkActualTypeEqualsExpectedType(expression: AstNode.Expression) {
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
