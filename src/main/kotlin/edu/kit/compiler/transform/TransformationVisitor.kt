package edu.kit.compiler.transform

import edu.kit.compiler.semantic.SemanticAST
import edu.kit.compiler.semantic.SemanticAST.ClassMember.SubroutineDeclaration.MethodDeclaration
import edu.kit.compiler.semantic.visitor.AbstractVisitor
import edu.kit.compiler.semantic.visitor.accept

/**
 * A visitor implementation that constructs the firm graph of the [AST][SemanticAST].
 */
class TransformationVisitor : AbstractVisitor() {
    override fun visitClassDeclaration(classDeclaration: SemanticAST.ClassDeclaration) {
        classDeclaration.accept(TransformationClassVisitor(classDeclaration))
    }
}

/**
 * Visit a single class and construct all its members in the firm graph
 */
private class TransformationClassVisitor(private val surroundingClass: SemanticAST.ClassDeclaration) : AbstractVisitor() {
    override fun visitMethodDeclaration(methodDeclaration: MethodDeclaration) {
        methodDeclaration.accept(TransformationMethodVisitor(surroundingClass))
    }

    override fun visitMainMethodDeclaration(mainMethodDeclaration: SemanticAST.ClassMember.SubroutineDeclaration.MainMethodDeclaration) {
        mainMethodDeclaration.accept(TransformationMethodVisitor(surroundingClass))
    }
}

/**
 * Visit a single method and construct all its code in the firm graph
 *
 * @param surroundingClass the surrounding class declaration (which is used as an implicite parameter to methods)
 */
class TransformationMethodVisitor(private val surroundingClass: SemanticAST.ClassDeclaration) : AbstractVisitor() {

    /**
     * Number of local variables (including parameters and this-ptr) of the method
     */
    var numberOfVariables = 0

    /**
     * Indices of local variables of firm
     */
    lateinit var localVariableDeclarations: Map<SemanticAST.Statement.LocalVariableDeclaration, Int>

    /**
     * Indices of parameters in local variables of firm
     */
    lateinit var parameterDeclarations: Map<SemanticAST.ClassMember.SubroutineDeclaration.Parameter, Int>

    /**
     * Method that is visited by this visitor
     */
    private lateinit var generatedMethod: SemanticAST.ClassMember.SubroutineDeclaration

    override fun visitBlock(block: SemanticAST.Statement.Block) {
        block.statements.forEach {
            it.accept(this)

            // skip dead code to prevent generating multiple returns
            if (it is SemanticAST.Statement.ReturnStatement) {
                return
            }
        }
    }

    override fun visitMethodDeclaration(methodDeclaration: MethodDeclaration) {
        val variableCounter = LocalVariableCounter(1)
        methodDeclaration.accept(variableCounter)

        numberOfVariables = variableCounter.numberOfVariables
        localVariableDeclarations = variableCounter.definitionMapping
        parameterDeclarations = variableCounter.parameterMapping
        generatedMethod = methodDeclaration

        FirmContext.constructMethod(
            FirmContext.typeRegistry.getMethod(
                surroundingClass.name.symbol,
                methodDeclaration.name.symbol
            ),
            methodDeclaration,
            numberOfVariables
        ) {
            super.visitMethodDeclaration(methodDeclaration)
        }
    }

    override fun visitMainMethodDeclaration(mainMethodDeclaration: SemanticAST.ClassMember.SubroutineDeclaration.MainMethodDeclaration) {
        val variableCounter = LocalVariableCounter(0)
        mainMethodDeclaration.accept(variableCounter)

        numberOfVariables = variableCounter.numberOfVariables
        localVariableDeclarations = variableCounter.definitionMapping
        parameterDeclarations = emptyMap()
        generatedMethod = mainMethodDeclaration

        FirmContext.constructMainMethod(
            FirmContext.typeRegistry.getMethod(
                surroundingClass.name.symbol,
                mainMethodDeclaration.name.symbol
            ),
            numberOfVariables
        ) {
            super.visitMainMethodDeclaration(mainMethodDeclaration)
        }
    }

    override fun visitBinaryOperation(binaryOperation: SemanticAST.Expression.BinaryOperation) {
        FirmContext.binaryExpression(binaryOperation, generatedMethod, this)
    }

    override fun visitUnaryOperation(unaryOperation: SemanticAST.Expression.UnaryOperation) {
        super.visitUnaryOperation(unaryOperation)
        FirmContext.unaryExpression(unaryOperation.operation)
    }

    override fun visitLiteralBoolExpression(literalBoolExpression: SemanticAST.Expression.LiteralExpression.LiteralBoolExpression) {
        FirmContext.literalBool(literalBoolExpression.value)
    }

    override fun visitLiteralIntExpression(literalIntExpression: SemanticAST.Expression.LiteralExpression.LiteralIntExpression) {
        FirmContext.literalInt(literalIntExpression.parsedValue)
    }

    override fun visitLiteralThisExpression(literalThisExpression: SemanticAST.Expression.LiteralExpression.LiteralThisExpression) {
        // safe cast, because semantic analysis guarantees that `this` cannot be used in main-method
        FirmContext.loadThis(generatedMethod as MethodDeclaration)
    }

    override fun visitLiteralNullExpression(literalNullExpression: SemanticAST.Expression.LiteralExpression.LiteralNullExpression) {
        FirmContext.loadNull()
    }

    override fun visitFieldAccessExpression(fieldAccessExpression: SemanticAST.Expression.FieldAccessExpression) {
        super.visitFieldAccessExpression(fieldAccessExpression)
        FirmContext.fieldReadAccess(fieldAccessExpression)
    }

    override fun visitIfStatement(ifStatement: SemanticAST.Statement.IfStatement) {
        FirmContext.ifStatement(ifStatement.elseCase != null, ifStatement, this)
    }

    override fun visitWhileStatement(whileStatement: SemanticAST.Statement.WhileStatement) {
        FirmContext.whileStatement(whileStatement, this)
    }

    override fun visitMethodInvocationExpression(methodInvocationExpression: SemanticAST.Expression.MethodInvocationExpression) {
        methodInvocationExpression.arguments.forEach { it.accept(this) }
        if (methodInvocationExpression.type is SemanticAST.Expression.MethodInvocationExpression.Type.Normal) {
            methodInvocationExpression.target?.accept(this)
        }

        FirmContext.methodInvocation(methodInvocationExpression, generatedMethod)
    }

    override fun visitIdentifierExpression(identifierExpression: SemanticAST.Expression.IdentifierExpression) {
        super.visitIdentifierExpression(identifierExpression)
        FirmContext.identifierReadAccess(identifierExpression, generatedMethod, this)
    }

    override fun visitLocalVariableDeclaration(localVariableDeclaration: SemanticAST.Statement.LocalVariableDeclaration) {
        super.visitLocalVariableDeclaration(localVariableDeclaration)
        FirmContext.localVariableDeclaration(localVariableDeclaration, this)
    }

    override fun visitArrayAccessExpression(arrayAccessExpression: SemanticAST.Expression.ArrayAccessExpression) {
        super.visitArrayAccessExpression(arrayAccessExpression)
        FirmContext.arrayReadAccess(arrayAccessExpression)
    }

    override fun visitReturnStatement(returnStatement: SemanticAST.Statement.ReturnStatement) {
        if (generatedMethod is SemanticAST.ClassMember.SubroutineDeclaration.MainMethodDeclaration) {
            FirmContext.specialMainReturnStatement()
        } else {
            if (returnStatement.expression != null) {
                super.visitReturnStatement(returnStatement)
                FirmContext.returnStatement(true)
            } else {
                FirmContext.returnStatement(false)
            }
        }
    }

    override fun visitNewArrayExpression(newArrayExpression: SemanticAST.Expression.NewArrayExpression) {
        super.visitNewArrayExpression(newArrayExpression)
        FirmContext.newArrayAllocation(newArrayExpression)
    }

    override fun visitNewObjectExpression(newObjectExpression: SemanticAST.Expression.NewObjectExpression) {
        super.visitNewObjectExpression(newObjectExpression)
        FirmContext.newObjectAllocation(newObjectExpression)
    }

    override fun visitExpressionStatement(expressionStatement: SemanticAST.Statement.ExpressionStatement) {
        super.visitExpressionStatement(expressionStatement)
        FirmContext.expressionStatement()
    }
}
