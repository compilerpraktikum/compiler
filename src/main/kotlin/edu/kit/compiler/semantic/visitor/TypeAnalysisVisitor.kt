package edu.kit.compiler.semantic.visitor

import edu.kit.compiler.ast.AST
import edu.kit.compiler.lex.SourceFile
import edu.kit.compiler.lex.SourcePosition
import edu.kit.compiler.lex.SourceRange
import edu.kit.compiler.semantic.AstNode
import edu.kit.compiler.semantic.ParsedType
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
        checkAndMessageIfNot(parameter.sourceRange, "No void typed parameters.") { parameter.type !is ParsedType.VoidType }
        parameter.semanticType = constructSemanticType(parameter.type, TODO("get class declaration, check before"))
    }

    /**
     * L-Values:
     */
    override fun visitLocalVariableDeclaration(localVariableDeclaration: AstNode.Statement.LocalVariableDeclaration) {
        // type check on children
        super.visitLocalVariableDeclaration(localVariableDeclaration)

        // L-Values check!
        checkAndMessageIfNot(localVariableDeclaration.sourceRange, "No \"void\" variables.") { localVariableDeclaration.type is ParsedType.VoidType }
        checkAndMessageIfNot(localVariableDeclaration.sourceRange, "No \"String\" instantiation.") {
            localVariableDeclaration.type is ParsedType.ComplexType && localVariableDeclaration.type.name.symbol.text == "String"
        }

        // type check
        checkAndMessageIfNot(localVariableDeclaration.sourceRange, "Initializer and declared type don't match") {
            (localVariableDeclaration.initializer?.actualSemanticType ?: SemanticType.ErrorType) != localVariableDeclaration.type
        }
        TODO("impl!")
    }

    override fun visitArrayAccessExpression(arrayAccessExpression: AstNode.Expression.ArrayAccessExpression) {
        super.visitArrayAccessExpression(arrayAccessExpression)
        // left-to-right: check target, then check index.

        checkAndMessageIfNot(arrayAccessExpression.sourceRange, "Array access target is no array.") {
            arrayAccessExpression.target.actualSemanticType is SemanticType.ArrayType
        }
        checkAndMessageIfNot(arrayAccessExpression.sourceRange, "Only \"Int\"-typed array indices.") {
            arrayAccessExpression.index.actualSemanticType is SemanticType.IntType
        }
        // If everything's correct, the arrayAccessExpression's type is the elementtype
        arrayAccessExpression.actualSemanticType = (arrayAccessExpression.target.actualSemanticType as SemanticType.ArrayType).elementType
    }

    override fun visitFieldAccessExpression(fieldAccessExpression: AstNode.Expression.FieldAccessExpression) {
        super.visitFieldAccessExpression(fieldAccessExpression)
        checkAndMessageIfNot(fieldAccessExpression.sourceRange, "Field access can only apply to targets of complex type.") {
            fieldAccessExpression.target.actualSemanticType is SemanticType.ComplexType
        }
        when (fieldAccessExpression.target.actualSemanticType) {
            is SemanticType.ArrayType -> TODO("Error")
        }
        // TODO identifier ==> Definition from current namespace (wait for AstNode change)!
//        fieldAccessExpression.actualSemanticType = fieldAccessExpression.field
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
        checkAndMessageIfNot(returnStatement.sourceRange, "No return values on methods with return type \"void\".") {
            !(currentExpectedMethodReturnType is SemanticType.VoidType && returnStatement.expression != null)
        }
        if (returnStatement.expression != null) {
            returnStatement.expression.expectedSemanticType = currentExpectedMethodReturnType
            super.visitReturnStatement(returnStatement)
            checkActualTypeEqualsExpectedType(returnStatement.expression)
        }
    }

    override fun visitExpressionStatement(expressionStatement: AstNode.Statement.ExpressionStatement) {
        checkAndMessageIfNot(expressionStatement.sourceRange, "Only method invocations and assignments are allowed as statements.") {
            expressionStatement.expression is AstNode.Expression.MethodInvocationExpression ||
                (expressionStatement.expression is AstNode.Expression.BinaryOperation && expressionStatement.expression.operation == AST.BinaryExpression.Operation.ASSIGNMENT)
        }
        super.visitExpressionStatement(expressionStatement)
    }

    override fun visitBinaryOperation(binaryOperation: AstNode.Expression.BinaryOperation) {

        binaryOperation.left.accept(this)

        binaryOperation.right.expectedSemanticType = when (binaryOperation.operation) {
            AST.BinaryExpression.Operation.EQUALS, AST.BinaryExpression.Operation.NOT_EQUALS, AST.BinaryExpression.Operation.ASSIGNMENT ->
                binaryOperation.left.actualSemanticType
            AST.BinaryExpression.Operation.GREATER_EQUALS, AST.BinaryExpression.Operation.GREATER_THAN,
            AST.BinaryExpression.Operation.LESS_EQUALS, AST.BinaryExpression.Operation.LESS_THAN,
            AST.BinaryExpression.Operation.MULTIPLICATION, AST.BinaryExpression.Operation.MODULO,
            AST.BinaryExpression.Operation.ADDITION, AST.BinaryExpression.Operation.SUBTRACTION,
            AST.BinaryExpression.Operation.DIVISION ->
                SemanticType.IntType
            AST.BinaryExpression.Operation.AND, AST.BinaryExpression.Operation.OR ->
                SemanticType.BoolType
        }
        binaryOperation.left.expectedSemanticType = when (binaryOperation.operation) {
            AST.BinaryExpression.Operation.EQUALS, AST.BinaryExpression.Operation.NOT_EQUALS, AST.BinaryExpression.Operation.ASSIGNMENT ->
                binaryOperation.left.expectedSemanticType // TODO
            AST.BinaryExpression.Operation.GREATER_EQUALS, AST.BinaryExpression.Operation.GREATER_THAN,
            AST.BinaryExpression.Operation.LESS_EQUALS, AST.BinaryExpression.Operation.LESS_THAN,
            AST.BinaryExpression.Operation.MULTIPLICATION, AST.BinaryExpression.Operation.MODULO,
            AST.BinaryExpression.Operation.ADDITION, AST.BinaryExpression.Operation.SUBTRACTION,
            AST.BinaryExpression.Operation.DIVISION ->
                SemanticType.IntType
            AST.BinaryExpression.Operation.AND, AST.BinaryExpression.Operation.OR ->
                SemanticType.BoolType
        }

        binaryOperation.right.accept(this)

        binaryOperation.actualSemanticType = when (binaryOperation.operation) {
            AST.BinaryExpression.Operation.EQUALS, AST.BinaryExpression.Operation.NOT_EQUALS,
            AST.BinaryExpression.Operation.GREATER_EQUALS, AST.BinaryExpression.Operation.GREATER_THAN,
            AST.BinaryExpression.Operation.LESS_EQUALS, AST.BinaryExpression.Operation.LESS_THAN,
            AST.BinaryExpression.Operation.AND, AST.BinaryExpression.Operation.OR ->
                SemanticType.BoolType
            AST.BinaryExpression.Operation.MULTIPLICATION, AST.BinaryExpression.Operation.MODULO,
            AST.BinaryExpression.Operation.ADDITION, AST.BinaryExpression.Operation.SUBTRACTION,
            AST.BinaryExpression.Operation.DIVISION ->
                SemanticType.IntType
            AST.BinaryExpression.Operation.ASSIGNMENT ->
                binaryOperation.left.expectedSemanticType
        }
        checkActualTypeEqualsExpectedType(binaryOperation)
    }

    override fun visitIfStatement(ifStatement: AstNode.Statement.IfStatement) {
        ifStatement.condition.expectedSemanticType = SemanticType.BoolType
        super.visitIfStatement(ifStatement)
    }

    override fun visitWhileStatement(whileStatement: AstNode.Statement.WhileStatement) {
        whileStatement.condition.expectedSemanticType = SemanticType.BoolType
        super.visitWhileStatement(whileStatement)
    }

    override fun visitMethodInvocationExpression(methodInvocationExpression: AstNode.Expression.MethodInvocationExpression) {
        TODO("Check for each parameter in ${methodInvocationExpression.method}.parameters if they're matching with ${methodInvocationExpression.arguments}")

//        methodInvocationExpression.arguments.zip(TODO("${methodInvocationExpression.method}.parameters")) .forEach {
//
//        }

        super.visitMethodInvocationExpression(methodInvocationExpression)
        // TODO what is with this? Do this when "this" typing is clear.
        // TODO if target == null ?
//        checkAndMessageIfNot(methodInvocationExpression.sourceRange,"") {
//            methodInvocationExpression.target is AstNode.Expression.IdentifierExpression ||
//                methodInvocationExpression.target is TODO("THIS_EXPRESSION as a literal")
//        }

        methodInvocationExpression.actualSemanticType = TODO("${methodInvocationExpression.method}.returnType")
        checkActualTypeEqualsExpectedType(methodInvocationExpression)
    }

    override fun visitIdentifierExpression(identifierExpression: AstNode.Expression.IdentifierExpression) {
        TODO("check if setting ${identifierExpression.actualSemanticType} is needed. We need namespace stuff for that.")
        checkActualTypeEqualsExpectedType(identifierExpression)
    }

    override fun visitNewArrayExpression(newArrayExpression: AstNode.Expression.NewArrayExpression) {
        newArrayExpression.length.expectedSemanticType = SemanticType.IntType
        super.visitNewArrayExpression(newArrayExpression)

        newArrayExpression.actualSemanticType = constructSemanticType(newArrayExpression.type)
        checkActualTypeEqualsExpectedType(newArrayExpression)
    }

    override fun visitArrayType(arrayType: ParsedType.ArrayType) {
        // TODO fix non-existing sourceRange
        checkAndMessageIfNot(createSourceRangeDummy(), "No void typed arrays.") {
            !(arrayType.elementType !is ParsedType.ArrayType && arrayType.elementType is ParsedType.VoidType)
        }
        super.visitArrayType(arrayType)
    }

    override fun visitNewObjectExpression(newObjectExpression: AstNode.Expression.NewObjectExpression) {
        super.visitNewObjectExpression(newObjectExpression)
        newObjectExpression.actualSemanticType = TODO("get from namespace stuff (${newObjectExpression.clazz}), must be some ComplexType")
        checkActualTypeEqualsExpectedType(newObjectExpression)
    }

    override fun visitLiteralBoolExpression(literalBoolExpression: AstNode.Expression.LiteralExpression.LiteralBoolExpression) {
        literalBoolExpression.actualSemanticType = SemanticType.BoolType
        checkActualTypeEqualsExpectedType(literalBoolExpression)
    }

    override fun visitLiteralIntExpression(literalIntExpression: AstNode.Expression.LiteralExpression.LiteralIntExpression) {
        // can only be positive
        TODO("check ${literalIntExpression.value} not negative, not greater 2^(31)-1")

        literalIntExpression.actualSemanticType = SemanticType.IntType
        checkActualTypeEqualsExpectedType(literalIntExpression)
    }

    override fun visitLiteralNullExpression(literalNullExpression: AstNode.Expression.LiteralExpression.LiteralNullExpression) {
        literalNullExpression.actualSemanticType = TODO("null should be a type or whatever")
        checkActualTypeEqualsExpectedType(literalNullExpression)
    }

    override fun visitUnaryOperation(unaryOperation: AstNode.Expression.UnaryOperation) {
        unaryOperation.inner.expectedSemanticType = when (unaryOperation.operation) {
            AST.UnaryExpression.Operation.MINUS -> SemanticType.IntType
            AST.UnaryExpression.Operation.NOT -> SemanticType.BoolType
        }

        super.visitUnaryOperation(unaryOperation)
        unaryOperation.actualSemanticType = unaryOperation.inner.expectedSemanticType
        checkActualTypeEqualsExpectedType(unaryOperation)
    }

    private fun checkActualTypeEqualsExpectedType(expression: AstNode.Expression) {
        checkAndMessageIfNot(expression.sourceRange, "Expected type ${expression.expectedSemanticType}, got ${expression.actualSemanticType}") {
            expression.actualSemanticType == expression.expectedSemanticType
        }
    }

    private fun checkAndMessageIfNot(sourceRange: SourceRange, errorMsg: String, function: () -> kotlin.Boolean) {
        checkAndAnnotateSourceFileIfNot(sourceFile, sourceRange, errorMsg, function)
        // TODO more?
    }

    // TODO this should not be necessary!
    private fun createSourceRangeDummy() = SourceRange(SourcePosition(sourceFile, 0), 0)
}
