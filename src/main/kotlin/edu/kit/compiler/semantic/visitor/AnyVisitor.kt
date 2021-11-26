package edu.kit.compiler.semantic.visitor

import edu.kit.compiler.semantic.AstNode
import edu.kit.compiler.semantic.SemanticType

interface AnyVisitor {
    fun visitProgram(program: AstNode.Program): Any

    fun visitClassDeclaration(classDeclaration: AstNode.ClassDeclaration): Any

    fun visitFieldDeclaration(fieldDeclaration: AstNode.ClassMember.FieldDeclaration): Any

    fun visitMethodDeclaration(methodDeclaration: AstNode.ClassMember.SubroutineDeclaration.MethodDeclaration): Any

    fun visitMainMethodDeclaration(mainMethodDeclaration: AstNode.ClassMember.SubroutineDeclaration.MainMethodDeclaration): Any

    fun visitParameter(parameter: AstNode.ClassMember.SubroutineDeclaration.Parameter): Any

    fun visitArrayAccessExpression(arrayAccessExpression: AstNode.Expression.ArrayAccessExpression): Any

    fun visitBinaryOperation(binaryOperation: AstNode.Expression.BinaryOperation): Any

    fun visitFieldAccessExpression(fieldAccessExpression: AstNode.Expression.FieldAccessExpression): Any

    fun visitIdentifierExpression(identifierExpression: AstNode.Expression.IdentifierExpression): Any

    fun visitLiteralBoolExpression(literalBoolExpression: AstNode.Expression.LiteralExpression.LiteralBoolExpression): Any

    fun visitLiteralIntExpression(literalIntExpression: AstNode.Expression.LiteralExpression.LiteralIntExpression): Any

    fun visitLiteralNullExpression(literalNullExpression: AstNode.Expression.LiteralExpression.LiteralNullExpression): Any

    fun visitLiteralThisExpression(literalThisExpression: AstNode.Expression.LiteralExpression.LiteralThisExpression): Any

    fun visitMethodInvocationExpression(methodInvocationExpression: AstNode.Expression.MethodInvocationExpression): Any

    fun visitNewArrayExpression(newArrayExpression: AstNode.Expression.NewArrayExpression): Any

    fun visitNewObjectExpression(newObjectExpression: AstNode.Expression.NewObjectExpression): Any

    fun visitUnaryOperation(unaryOperation: AstNode.Expression.UnaryOperation): Any

    fun visitBlock(block: AstNode.Statement.Block): Any

    fun visitExpressionStatement(expressionStatement: AstNode.Statement.ExpressionStatement): Any

    fun visitIfStatement(ifStatement: AstNode.Statement.IfStatement): Any

    fun visitLocalVariableDeclaration(localVariableDeclaration: AstNode.Statement.LocalVariableDeclaration): Any

    fun visitReturnStatement(returnStatement: AstNode.Statement.ReturnStatement): Any

    fun visitWhileStatement(whileStatement: AstNode.Statement.WhileStatement): Any

    fun visitIntType(): Any

    fun visitBoolType(): Any

    fun visitVoidType(): Any

    fun visitArrayType(arrayType: SemanticType.Array): Any

    fun visitClassType(clazz: SemanticType.Class): Any
    fun visitTypeError(semanticType: SemanticType.Error): Any
}
