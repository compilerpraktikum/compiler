package edu.kit.compiler.transform

import edu.kit.compiler.semantic.AstNode
import edu.kit.compiler.semantic.AstNode.ClassMember.SubroutineDeclaration.MethodDeclaration
import edu.kit.compiler.semantic.visitor.AbstractVisitor
import edu.kit.compiler.semantic.visitor.accept
import firm.Dump
import firm.Graph

/**
 * A visitor implementation that constructs the firm graph of the [AST][AstNode].
 */
class TransformationVisitor(private val dumpMethodGraphs: Boolean) : AbstractVisitor() {
    override fun visitClassDeclaration(classDeclaration: AstNode.ClassDeclaration) {
        classDeclaration.accept(TransformationClassVisitor(classDeclaration, dumpMethodGraphs))
    }
}

/**
 * Visit a single class and construct all its members in the firm graph
 */
private class TransformationClassVisitor(private val surroundingClass: AstNode.ClassDeclaration, private val dumpMethodGraphs: Boolean) : AbstractVisitor() {
    override fun visitMethodDeclaration(methodDeclaration: MethodDeclaration) {
        methodDeclaration.accept(TransformationMethodVisitor(surroundingClass, dumpMethodGraphs))
    }

    override fun visitMainMethodDeclaration(mainMethodDeclaration: AstNode.ClassMember.SubroutineDeclaration.MainMethodDeclaration) {
        mainMethodDeclaration.accept(TransformationMethodVisitor(surroundingClass, dumpMethodGraphs))
    }
}

/**
 * Visit a single method and construct all its code in the firm graph
 *
 * @param surroundingClass the surrounding class declaration (which is used as an implicite parameter to methods)
 */
class TransformationMethodVisitor(private val surroundingClass: AstNode.ClassDeclaration, private val dumpGraph: Boolean) : AbstractVisitor() {

    /**
     * Number of local variables (including parameters and this-ptr) of the method
     */
    var numberOfVariables = 0

    /**
     * Indices of local variables of firm
     */
    lateinit var localVariableDeclarations: Map<AstNode.Statement.LocalVariableDeclaration, Int>

    /**
     * Indices of parameters in local variables of firm
     */
    lateinit var parameterDeclarations: Map<AstNode.ClassMember.SubroutineDeclaration.Parameter, Int>

    /**
     * Method that is visited by this visitor
     */
    private lateinit var generatedMethod: AstNode.ClassMember.SubroutineDeclaration

    override fun visitBlock(block: AstNode.Statement.Block) {
        block.statements.forEach {
            it.accept(this)

            // skip dead code to prevent generating multiple returns
            if (it is AstNode.Statement.ReturnStatement) {
                return
            }
        }
    }

    private fun dumpGraphIfEnabled(graph: Graph) {
        if (dumpGraph) {
            Dump.dumpGraph(graph, "construction")
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
        }.also {
            dumpGraphIfEnabled(it)
        }
    }

    override fun visitMainMethodDeclaration(mainMethodDeclaration: AstNode.ClassMember.SubroutineDeclaration.MainMethodDeclaration) {
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
        }.also {
            dumpGraphIfEnabled(it)
        }
    }

    override fun visitBinaryOperation(binaryOperation: AstNode.Expression.BinaryOperation) {
        FirmContext.binaryExpression(binaryOperation, generatedMethod, this)
    }

    override fun visitUnaryOperation(unaryOperation: AstNode.Expression.UnaryOperation) {
        super.visitUnaryOperation(unaryOperation)
        FirmContext.unaryExpression(unaryOperation.operation)
    }

    override fun visitLiteralBoolExpression(literalBoolExpression: AstNode.Expression.LiteralExpression.LiteralBoolExpression) {
        FirmContext.literalBool(literalBoolExpression.value)
    }

    override fun visitLiteralIntExpression(literalIntExpression: AstNode.Expression.LiteralExpression.LiteralIntExpression) {
        FirmContext.literalInt(literalIntExpression.parsedValue)
    }

    override fun visitLiteralThisExpression(literalThisExpression: AstNode.Expression.LiteralExpression.LiteralThisExpression) {
        // safe cast, because semantic analysis guarantees that `this` cannot be used in main-method
        FirmContext.loadThis(generatedMethod as MethodDeclaration)
    }

    override fun visitLiteralNullExpression(literalNullExpression: AstNode.Expression.LiteralExpression.LiteralNullExpression) {
        FirmContext.loadNull()
    }

    override fun visitFieldAccessExpression(fieldAccessExpression: AstNode.Expression.FieldAccessExpression) {
        super.visitFieldAccessExpression(fieldAccessExpression)
        FirmContext.fieldReadAccess(fieldAccessExpression)
    }

    override fun visitIfStatement(ifStatement: AstNode.Statement.IfStatement) {
        FirmContext.ifStatement(ifStatement.elseCase != null, ifStatement, this)
    }

    override fun visitWhileStatement(whileStatement: AstNode.Statement.WhileStatement) {
        FirmContext.whileStatement(whileStatement, this)
    }

    override fun visitMethodInvocationExpression(methodInvocationExpression: AstNode.Expression.MethodInvocationExpression) {
        methodInvocationExpression.arguments.forEach { it.accept(this) }
        if (methodInvocationExpression.type is AstNode.Expression.MethodInvocationExpression.Type.Normal) {
            methodInvocationExpression.target?.accept(this)
        }

        FirmContext.methodInvocation(methodInvocationExpression, generatedMethod)
    }

    override fun visitIdentifierExpression(identifierExpression: AstNode.Expression.IdentifierExpression) {
        super.visitIdentifierExpression(identifierExpression)
        FirmContext.identifierReadAccess(identifierExpression, generatedMethod, this)
    }

    override fun visitLocalVariableDeclaration(localVariableDeclaration: AstNode.Statement.LocalVariableDeclaration) {
        super.visitLocalVariableDeclaration(localVariableDeclaration)
        FirmContext.localVariableDeclaration(localVariableDeclaration, this)
    }

    override fun visitArrayAccessExpression(arrayAccessExpression: AstNode.Expression.ArrayAccessExpression) {
        super.visitArrayAccessExpression(arrayAccessExpression)
        FirmContext.arrayReadAccess(arrayAccessExpression)
    }

    override fun visitReturnStatement(returnStatement: AstNode.Statement.ReturnStatement) {
        if (generatedMethod is AstNode.ClassMember.SubroutineDeclaration.MainMethodDeclaration) {
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

    override fun visitNewArrayExpression(newArrayExpression: AstNode.Expression.NewArrayExpression) {
        super.visitNewArrayExpression(newArrayExpression)
        FirmContext.newArrayAllocation(newArrayExpression)
    }

    override fun visitNewObjectExpression(newObjectExpression: AstNode.Expression.NewObjectExpression) {
        super.visitNewObjectExpression(newObjectExpression)
        FirmContext.newObjectAllocation(newObjectExpression)
    }

    override fun visitExpressionStatement(expressionStatement: AstNode.Statement.ExpressionStatement) {
        super.visitExpressionStatement(expressionStatement)
        FirmContext.expressionStatement()
    }
}
