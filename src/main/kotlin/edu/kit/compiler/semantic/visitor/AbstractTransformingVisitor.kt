package edu.kit.compiler.semantic.visitor

import edu.kit.compiler.lex.SourceRange
import edu.kit.compiler.semantic.AstNode
import edu.kit.compiler.semantic.SemanticType

abstract class AbstractTransformingVisitor : AnyVisitor {

    override fun visitProgram(program: AstNode.Program): AstNode.Program {
        val classes = program.classes.map { it.accept(this) }
        val sourceRange = visitSourceRange(program.sourceRange)

        return AstNode.Program(classes, sourceRange)
    }

    override fun visitClassDeclaration(classDeclaration: AstNode.ClassDeclaration): AstNode.ClassDeclaration {
        val name = classDeclaration.name.accept(this)
        val members = classDeclaration.members.map { it.accept(this) }
        val sourceRange = visitSourceRange(classDeclaration.sourceRange)

        return AstNode.ClassDeclaration(name, members, sourceRange)
    }

    override fun visitFieldDeclaration(fieldDeclaration: AstNode.ClassMember.FieldDeclaration): AstNode.ClassMember.FieldDeclaration {
        val name = fieldDeclaration.name.accept(this)
        val type = fieldDeclaration.type.accept(this)
        val sourceRange = visitSourceRange(fieldDeclaration.sourceRange)

        return AstNode.ClassMember.FieldDeclaration(name, type, sourceRange)
    }

    override fun visitMethodDeclaration(methodDeclaration: AstNode.ClassMember.SubroutineDeclaration.MethodDeclaration): AstNode.ClassMember.SubroutineDeclaration.MethodDeclaration {
        val returnType = methodDeclaration.returnType.accept(this)
        val name = methodDeclaration.name.accept(this)
        val throwsException = methodDeclaration.throwsException?.accept(this)
        val parameters = methodDeclaration.parameters.map { it.accept(this) }
        val block = methodDeclaration.block.accept(this)
        val sourceRange = visitSourceRange(methodDeclaration.sourceRange)

        return AstNode.ClassMember.SubroutineDeclaration.MethodDeclaration(returnType, name, throwsException, block, parameters, sourceRange)
    }

    override fun visitMainMethodDeclaration(mainMethodDeclaration: AstNode.ClassMember.SubroutineDeclaration.MainMethodDeclaration): AstNode.ClassMember.SubroutineDeclaration.MainMethodDeclaration {
        val returnType = mainMethodDeclaration.returnType.accept(this)
        val name = mainMethodDeclaration.name.accept(this)
        val throwsException = mainMethodDeclaration.throwsException?.accept(this)
        val parameters = mainMethodDeclaration.parameters.map { it.accept(this) }
        val block = mainMethodDeclaration.block.accept(this)
        val sourceRange = visitSourceRange(mainMethodDeclaration.sourceRange)

        return AstNode.ClassMember.SubroutineDeclaration.MainMethodDeclaration(returnType, name, throwsException, block, parameters, sourceRange)
    }

    override fun visitParameter(parameter: AstNode.ClassMember.SubroutineDeclaration.Parameter): AstNode.ClassMember.SubroutineDeclaration.Parameter {
        val name = parameter.name.accept(this)
        val type = parameter.type.accept(this)
        val sourceRange = visitSourceRange(parameter.sourceRange)

        return AstNode.ClassMember.SubroutineDeclaration.Parameter(name, type, sourceRange)
    }

    override fun visitArrayAccessExpression(arrayAccessExpression: AstNode.Expression.ArrayAccessExpression): AstNode.Expression.ArrayAccessExpression {
        val target = arrayAccessExpression.target.accept(this)
        val index = arrayAccessExpression.index.accept(this)
        val sourceRange = visitSourceRange(arrayAccessExpression.sourceRange)

        return AstNode.Expression.ArrayAccessExpression(target, index, sourceRange)
    }

    override fun visitBinaryOperation(binaryOperation: AstNode.Expression.BinaryOperation): AstNode.Expression.BinaryOperation {
        val left = binaryOperation.left.accept(this)
        val right = binaryOperation.right.accept(this)
        val sourceRange = visitSourceRange(binaryOperation.sourceRange)

        return AstNode.Expression.BinaryOperation(left, right, binaryOperation.operation, sourceRange)
    }

    override fun visitFieldAccessExpression(fieldAccessExpression: AstNode.Expression.FieldAccessExpression): AstNode.Expression.FieldAccessExpression {
        val target = fieldAccessExpression.target.accept(this)
        val field = fieldAccessExpression.field.accept(this)
        val sourceRange = visitSourceRange(fieldAccessExpression.sourceRange)

        return AstNode.Expression.FieldAccessExpression(target, field, sourceRange)
    }

    override fun visitIdentifierExpression(identifierExpression: AstNode.Expression.IdentifierExpression): AstNode.Expression.IdentifierExpression {
        val name = identifierExpression.name.accept(this)
        val sourceRange = visitSourceRange(identifierExpression.sourceRange)

        return AstNode.Expression.IdentifierExpression(name, sourceRange)
    }

    override fun visitLiteralBoolExpression(literalBoolExpression: AstNode.Expression.LiteralExpression.LiteralBoolExpression): AstNode.Expression.LiteralExpression.LiteralBoolExpression =
        AstNode.Expression.LiteralExpression.LiteralBoolExpression(
            literalBoolExpression.value,
            visitSourceRange(literalBoolExpression.sourceRange)
        )

    override fun visitLiteralIntExpression(literalIntExpression: AstNode.Expression.LiteralExpression.LiteralIntExpression): AstNode.Expression.LiteralExpression.LiteralIntExpression =
        AstNode.Expression.LiteralExpression.LiteralIntExpression(
            literalIntExpression.value,
            visitSourceRange(literalIntExpression.sourceRange)
        )

    override fun visitLiteralNullExpression(literalNullExpression: AstNode.Expression.LiteralExpression.LiteralNullExpression): AstNode.Expression.LiteralExpression.LiteralNullExpression =
        AstNode.Expression.LiteralExpression.LiteralNullExpression(visitSourceRange(literalNullExpression.sourceRange))

    override fun visitLiteralThisExpression(literalThisExpression: AstNode.Expression.LiteralExpression.LiteralThisExpression): AstNode.Expression.LiteralExpression.LiteralThisExpression =
        AstNode.Expression.LiteralExpression.LiteralThisExpression(visitSourceRange(literalThisExpression.sourceRange))

    override fun visitMethodInvocationExpression(methodInvocationExpression: AstNode.Expression.MethodInvocationExpression): AstNode.Expression.MethodInvocationExpression {
        val target = methodInvocationExpression.target?.accept(this)
        val method = methodInvocationExpression.method.accept(this)
        val arguments = methodInvocationExpression.arguments.map { it.accept(this) }
        val sourceRange = visitSourceRange(methodInvocationExpression.sourceRange)

        return AstNode.Expression.MethodInvocationExpression(target, method, arguments, sourceRange)
    }

    override fun visitNewArrayExpression(newArrayExpression: AstNode.Expression.NewArrayExpression): AstNode.Expression.NewArrayExpression {
        val type = newArrayExpression.type.accept(this)
        val length = newArrayExpression.length.accept(this)
        val sourceRange = visitSourceRange(newArrayExpression.sourceRange)

        return AstNode.Expression.NewArrayExpression(type, length, sourceRange)
    }

    override fun visitNewObjectExpression(newObjectExpression: AstNode.Expression.NewObjectExpression): AstNode.Expression.NewObjectExpression {
        val clazz = newObjectExpression.clazz.accept(this)
        val sourceRange = visitSourceRange(newObjectExpression.sourceRange)

        return AstNode.Expression.NewObjectExpression(clazz, sourceRange)
    }

    override fun visitUnaryOperation(unaryOperation: AstNode.Expression.UnaryOperation): AstNode.Expression.UnaryOperation {
        val inner = unaryOperation.inner.accept(this)
        val sourceRange = visitSourceRange(unaryOperation.sourceRange)
        return AstNode.Expression.UnaryOperation(inner, unaryOperation.operation, sourceRange)
    }

    override fun visitBlock(block: AstNode.Statement.Block): AstNode.Statement.Block {
        val statements = block.statements.map { it.accept(this) }
        val sourceRange = visitSourceRange(block.sourceRange)

        return AstNode.Statement.Block(statements, sourceRange)
    }

    override fun visitExpressionStatement(expressionStatement: AstNode.Statement.ExpressionStatement): AstNode.Statement.ExpressionStatement =
        AstNode.Statement.ExpressionStatement(
            expressionStatement.expression.accept(this),
            visitSourceRange(expressionStatement.sourceRange)
        )

    override fun visitIfStatement(ifStatement: AstNode.Statement.IfStatement): AstNode.Statement.IfStatement {
        val cond = ifStatement.condition.accept(this)
        val thenCase = ifStatement.thenCase.accept(this)
        val elseCase = ifStatement.elseCase?.accept(this)
        return AstNode.Statement.IfStatement(cond, thenCase, elseCase, visitSourceRange(ifStatement.sourceRange))
    }

    override fun visitLocalVariableDeclaration(localVariableDeclaration: AstNode.Statement.LocalVariableDeclaration): AstNode.Statement.LocalVariableDeclaration {
        val name = localVariableDeclaration.name.accept(this)
        val type = localVariableDeclaration.type.accept(this)
        val init = localVariableDeclaration.initializer?.accept(this)
        val sourceRange = visitSourceRange(localVariableDeclaration.sourceRange)
        return AstNode.Statement.LocalVariableDeclaration(name, type, init, sourceRange)
    }

    override fun visitReturnStatement(returnStatement: AstNode.Statement.ReturnStatement): AstNode.Statement.ReturnStatement =
        AstNode.Statement.ReturnStatement(
            returnStatement.expression?.accept(this),
            visitSourceRange(returnStatement.sourceRange)
        )

    override fun visitWhileStatement(whileStatement: AstNode.Statement.WhileStatement): AstNode.Statement.WhileStatement =
        AstNode.Statement.WhileStatement(
            whileStatement.condition.accept(this),
            whileStatement.statement.accept(this),
            visitSourceRange(whileStatement.sourceRange)
        )

    override fun visitIntType() = SemanticType.Integer

    override fun visitBoolType() = SemanticType.Boolean

    override fun visitVoidType() = SemanticType.Void

    override fun visitArrayType(arrayType: SemanticType.Array): SemanticType.Array {
        val elementType = arrayType.elementType.accept(this)
        return SemanticType.Array(elementType)
    }

    override fun visitClassType(clazz: SemanticType.Class): SemanticType.Class {
        return clazz.copy(name = clazz.name.accept(this))
    }

    fun visitIdentifier(identifier: AstNode.Identifier): AstNode.Identifier = identifier

    override fun visitTypeError(semanticType: SemanticType.Error): SemanticType.Error = semanticType

    fun visitSourceRange(sourceRange: SourceRange): SourceRange = sourceRange
}

fun AstNode.Program.accept(visitor: AbstractTransformingVisitor): AstNode.Program = visitor.visitProgram(this)
fun AstNode.ClassDeclaration.accept(visitor: AbstractTransformingVisitor): AstNode.ClassDeclaration =
    visitor.visitClassDeclaration(this)

fun AstNode.ClassMember.SubroutineDeclaration.Parameter.accept(visitor: AbstractTransformingVisitor): AstNode.ClassMember.SubroutineDeclaration.Parameter =
    visitor.visitParameter(this)

fun AstNode.accept(visitor: AbstractTransformingVisitor): AstNode =
    when (this) {
        is AstNode.Program -> this.accept(visitor)
        is AstNode.ClassDeclaration -> this.accept(visitor)
        is AstNode.ClassMember -> this.accept(visitor)
        is AstNode.ClassMember.SubroutineDeclaration.Parameter -> this.accept(visitor)
        is AstNode.Expression -> this.accept(visitor)
        is AstNode.Statement -> this.accept(visitor)
        is AstNode.Identifier -> this.accept(visitor)
    }

fun AstNode.Identifier.accept(visitor: AbstractTransformingVisitor): AstNode.Identifier =
    visitor.visitIdentifier(this)

fun AstNode.ClassMember.accept(visitor: AbstractTransformingVisitor): AstNode.ClassMember =
    when (this) {
        is AstNode.ClassMember.FieldDeclaration -> visitor.visitFieldDeclaration(this)
        is AstNode.ClassMember.SubroutineDeclaration.MainMethodDeclaration -> visitor.visitMainMethodDeclaration(this)
        is AstNode.ClassMember.SubroutineDeclaration.MethodDeclaration -> visitor.visitMethodDeclaration(this)
    }

fun AstNode.Expression.accept(visitor: AbstractTransformingVisitor): AstNode.Expression =
    when (this) {
        is AstNode.Expression.ArrayAccessExpression -> visitor.visitArrayAccessExpression(this)
        is AstNode.Expression.BinaryOperation -> visitor.visitBinaryOperation(this)
        is AstNode.Expression.FieldAccessExpression -> visitor.visitFieldAccessExpression(this)
        is AstNode.Expression.IdentifierExpression -> visitor.visitIdentifierExpression(this)
        is AstNode.Expression.LiteralExpression -> this.accept(visitor)
        is AstNode.Expression.MethodInvocationExpression -> visitor.visitMethodInvocationExpression(this)
        is AstNode.Expression.NewArrayExpression -> visitor.visitNewArrayExpression(this)
        is AstNode.Expression.NewObjectExpression -> visitor.visitNewObjectExpression(this)
        is AstNode.Expression.UnaryOperation -> visitor.visitUnaryOperation(this)
    }

fun AstNode.Expression.LiteralExpression.accept(visitor: AbstractTransformingVisitor): AstNode.Expression.LiteralExpression =
    when (this) {
        is AstNode.Expression.LiteralExpression.LiteralBoolExpression -> visitor.visitLiteralBoolExpression(this)
        is AstNode.Expression.LiteralExpression.LiteralIntExpression -> visitor.visitLiteralIntExpression(this)
        is AstNode.Expression.LiteralExpression.LiteralNullExpression -> visitor.visitLiteralNullExpression(this)
        is AstNode.Expression.LiteralExpression.LiteralThisExpression -> visitor.visitLiteralThisExpression(this)
    }

fun AstNode.Statement.Block.accept(visitor: AbstractTransformingVisitor): AstNode.Statement.Block =
    visitor.visitBlock(this)

fun AstNode.Statement.accept(visitor: AbstractTransformingVisitor): AstNode.Statement =
    when (this) {
        is AstNode.Statement.Block -> this.accept(visitor)
        is AstNode.Statement.ExpressionStatement -> visitor.visitExpressionStatement(this)
        is AstNode.Statement.IfStatement -> visitor.visitIfStatement(this)
        is AstNode.Statement.LocalVariableDeclaration -> visitor.visitLocalVariableDeclaration(this)
        is AstNode.Statement.ReturnStatement -> visitor.visitReturnStatement(this)
        is AstNode.Statement.WhileStatement -> visitor.visitWhileStatement(this)
    }

fun SemanticType.Array.accept(visitor: AbstractTransformingVisitor): SemanticType.Array =
    visitor.visitArrayType(this)

fun SemanticType.accept(visitor: AbstractTransformingVisitor): SemanticType =
    when (this) {
        SemanticType.Integer -> visitor.visitIntType()
        SemanticType.Boolean -> visitor.visitBoolType()
        SemanticType.Void -> visitor.visitVoidType()
        is SemanticType.Array -> this.accept(visitor)
        is SemanticType.Class -> visitor.visitClassType(this)
        is SemanticType.Error -> visitor.visitTypeError(this)
    }
