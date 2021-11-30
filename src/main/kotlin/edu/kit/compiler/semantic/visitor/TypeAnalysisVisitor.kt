package edu.kit.compiler.semantic.visitor

import edu.kit.compiler.ast.AST
import edu.kit.compiler.lex.SourceFile
import edu.kit.compiler.lex.SourcePosition
import edu.kit.compiler.lex.SourceRange
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
        errorIfFalse(parameter.sourceRange, "parameter cannot have type `void`") { parameter.type !is SemanticType.Void }
        super.visitParameter(parameter)
    }

    override fun visitFieldDeclaration(fieldDeclaration: AstNode.ClassMember.FieldDeclaration) {
        errorIfTrue(fieldDeclaration.sourceRange, "field cannot have type `void`") { fieldDeclaration.type is SemanticType.Void }
        super.visitFieldDeclaration(fieldDeclaration)
    }

    override fun visitLocalVariableDeclaration(localVariableDeclaration: AstNode.Statement.LocalVariableDeclaration) {
        if (localVariableDeclaration.initializer != null) {
            localVariableDeclaration.initializer.expectedType = localVariableDeclaration.type
        }
        // type check on children
        super.visitLocalVariableDeclaration(localVariableDeclaration)

        // L-Values check!
        errorIfTrue(localVariableDeclaration.sourceRange, "variable cannot have type `void`") { localVariableDeclaration.type is SemanticType.Void }

        // type check
        errorIfFalse(localVariableDeclaration.sourceRange, "Initializer and declared type don't match, expected ${localVariableDeclaration.type.display()}, got ${localVariableDeclaration.initializer?.actualType?.display() ?: ""}") {
            if (localVariableDeclaration.initializer != null) {
                compareSemanticTypes(localVariableDeclaration.type, localVariableDeclaration.initializer.actualType)
            } else true
        }
    }

    override fun visitArrayAccessExpression(arrayAccessExpression: AstNode.Expression.ArrayAccessExpression) {

        arrayAccessExpression.index.expectedType = SemanticType.Integer
        arrayAccessExpression.target.expectedType = SemanticType.Array(arrayAccessExpression.expectedType)
        super.visitArrayAccessExpression(arrayAccessExpression)
        // left-to-right: check target, then check index.

        errorIfFalse(arrayAccessExpression.sourceRange, "array access on non-array type ${arrayAccessExpression.target.actualType.display()}") {
            arrayAccessExpression.target.actualType is SemanticType.Array
        }
        errorIfFalse(arrayAccessExpression.sourceRange, "expected index expression of type `int`, got type ${arrayAccessExpression.index.actualType.display()}") {
            arrayAccessExpression.index.actualType is SemanticType.Integer
        }
        checkActualTypeEqualsExpectedType(arrayAccessExpression)
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
        if (returnStatement.expression != null) {
            errorIfTrue(returnStatement.sourceRange, "cannot return value from method with return type `void`") {
                currentExpectedMethodReturnType is SemanticType.Void
            }
            returnStatement.expression.expectedType = currentExpectedMethodReturnType
            super.visitReturnStatement(returnStatement)
            checkActualTypeEqualsExpectedType(returnStatement.expression)
        } else {
            errorIfFalse(returnStatement.sourceRange, "method with return type ${currentExpectedMethodReturnType.display()} must return a value") {
                currentExpectedMethodReturnType is SemanticType.Void
            }
        }
    }

    override fun visitExpressionStatement(expressionStatement: AstNode.Statement.ExpressionStatement) {
        errorIfFalse(expressionStatement.sourceRange, "statement has no side-effects") {
            expressionStatement.expression is AstNode.Expression.MethodInvocationExpression ||
                (expressionStatement.expression is AstNode.Expression.BinaryOperation && expressionStatement.expression.operation == AST.BinaryExpression.Operation.ASSIGNMENT)
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
                errorIfTrue(methodInvocationExpression.sourceRange, "method `${methodInvocationExpression.method.text}` requires ${paramTypes.size} arguments, but got ${args.size}") { true }
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
        // TODO fix non-existing sourceRangeƒ
        errorIfTrue(createSourceRangeDummy(), "array of type `void` not allowed") {
            arrayType.elementType is SemanticType.Void
        }
        super.visitArrayType(arrayType)
    }

    override fun visitNewObjectExpression(newObjectExpression: AstNode.Expression.NewObjectExpression) {
        super.visitNewObjectExpression(newObjectExpression)
        checkActualTypeEqualsExpectedType(newObjectExpression)
    }

    override fun visitLiteralBoolExpression(literalBoolExpression: AstNode.Expression.LiteralExpression.LiteralBoolExpression) {
        super.visitLiteralBoolExpression(literalBoolExpression)
        checkActualTypeEqualsExpectedType(literalBoolExpression)
    }

    override fun visitLiteralIntExpression(literalIntExpression: AstNode.Expression.LiteralExpression.LiteralIntExpression) {
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
        errorIfFalse(expression.sourceRange, "expected type ${expression.expectedType.display()}, but got ${expression.actualType.display()}") {
            compareSemanticTypes(expression.expectedType, expression.actualType)
        }
    }

    /**
     * Not commutative because of null allowance.
     */
    private fun compareSemanticTypes(type0: SemanticType, type1: SemanticType): Boolean =
        // TODO überlegen ob vlt doch equals methode überschreiben?
        if (type0 is SemanticType.Class && type1 is SemanticType.Class) {
            type0.name.symbol.text == type1.name.symbol.text
        } else if (type0 is SemanticType.Array && type1 is SemanticType.Array) {
            compareSemanticTypes(type0.elementType, type1.elementType)
        } else if (type0 is SemanticType.Class && type1 is SemanticType.Null || type1 is SemanticType.Class && type0 is SemanticType.Null) {
            true
        } else if (type0 is SemanticType.Array && type1 is SemanticType.Null || type1 is SemanticType.Array && type0 is SemanticType.Null) {
            true
        } else {
            type0 == type1
        }

    private fun errorIfFalse(sourceRange: SourceRange, errorMsg: String, function: () -> kotlin.Boolean) {
        errorIfFalse(sourceFile, sourceRange, errorMsg, function)
    }

    private fun errorIfTrue(sourceRange: SourceRange, errorMsg: String, function: () -> kotlin.Boolean) {
        errorIfTrue(sourceFile, sourceRange, errorMsg, function)
    }

    // TODO this should not be necessary!
    private fun createSourceRangeDummy() = SourceRange(SourcePosition(sourceFile, 1), 0)
}

fun doTypeAnalysis(program: AstNode.Program, sourceFile: SourceFile) {
    program.accept(TypeAnalysisVisitor(sourceFile))
}
