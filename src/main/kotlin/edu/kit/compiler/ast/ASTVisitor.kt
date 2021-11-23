package edu.kit.compiler.ast

interface ASTVisitor {

    fun visit(program: AST.Program)

    fun visit(classDeclaration: AST.ClassDeclaration)

    fun visit(field: AST.Field)

    fun visit(method: AST.Method)

    fun visit(mainMethod: AST.MainMethod)

    fun visit(parameter: AST.Parameter)

    fun visit(localVariableDeclarationStatement: AST.LocalVariableDeclarationStatement)

    fun visit(block: AST.Block)

    fun visit(ifStatement: AST.IfStatement)

    fun visit(whileStatement: AST.WhileStatement)

    fun visit(returnStatement: AST.ReturnStatement)

    fun visit(binaryExpression: AST.BinaryExpression)

    fun visit(unaryExpression: AST.UnaryExpression)

    fun visit(methodInvocationExpression: AST.MethodInvocationExpression)

    fun visit(fieldAccessExpression: AST.FieldAccessExpression)

    fun visit(arrayAccessExpression: AST.ArrayAccessExpression)

    fun visit(identifierExpression: AST.IdentifierExpression)

    fun <T> visit(literalExpression: AST.LiteralExpression<T>)

    fun visit(newObjectExpression: AST.NewObjectExpression)

    fun visit(newArrayExpression: AST.NewArrayExpression)

    fun visit(voidType: Type.Void)

    fun visit(integerType: Type.Integer)

    fun visit(booleanType: Type.Boolean)

    fun visit(arrayType: Type.Array)

    fun visit(arrayType: Type.Array.ArrayType)

    fun visit(classType: Type.Class)

    fun visit(operation: AST.BinaryExpression.Operation)

    fun visit(operation: AST.UnaryExpression.Operation)

    fun visit(expressionStatement: AST.ExpressionStatement)
}

fun AST.Expression.accept(visitor: ASTVisitor) = when (this) {
    is AST.ArrayAccessExpression -> visitor.visit(this)
    is AST.BinaryExpression -> visitor.visit(this)
    is AST.FieldAccessExpression -> visitor.visit(this)
    is AST.IdentifierExpression -> visitor.visit(this)
    is AST.LiteralExpression<*> -> visitor.visit(this)
    is AST.MethodInvocationExpression -> visitor.visit(this)
    is AST.NewArrayExpression -> visitor.visit(this)
    is AST.NewObjectExpression -> visitor.visit(this)
    is AST.UnaryExpression -> visitor.visit(this)
}

@JvmName("acceptExpression")
fun AST.Expression.accept(visitor: ASTVisitor) =
    this.accept(visitor)

fun AST.Statement.accept(visitor: ASTVisitor) = when (this) {
    is AST.Block -> visitor.visit(this)
    is AST.ExpressionStatement -> visitor.visit(this)
    is AST.IfStatement -> visitor.visit(this)
    is AST.ReturnStatement -> visitor.visit(this)
    is AST.WhileStatement -> visitor.visit(this)
}

fun AST.BlockStatement.accept(visitor: ASTVisitor) = when (this) {
    is AST.LocalVariableDeclarationStatement -> visitor.visit(this)
    is AST.StmtWrapper -> this.statement.accept(visitor)
}

fun AST.Parameter.accept(visitor: ASTVisitor) = visitor.visit(this)

fun Type.accept(visitor: ASTVisitor) = when (this) {
    is Type.Array -> visitor.visit(this)
    is Type.Boolean -> visitor.visit(this)
    is Type.Class -> visitor.visit(this)
    is Type.Integer -> visitor.visit(this)
    is Type.Void -> visitor.visit(this)
}

fun Type.Array.ArrayType.accept(visitor: ASTVisitor) = visitor.visit(this)

fun AST.ClassDeclaration.accept(visitor: ASTVisitor) =
    visitor.visit(this)

fun AST.ClassMember.accept(visitor: ASTVisitor) = when (this) {
    is AST.Field -> visitor.visit(this)
    is AST.MainMethod -> visitor.visit(this)
    is AST.Method -> visitor.visit(this)
}

fun AST.Program.accept(visitor: ASTVisitor) = visitor.visit(this)

fun AST.BinaryExpression.Operation.accept(visitor: ASTVisitor) =
    visitor.visit(this)

fun AST.UnaryExpression.Operation.accept(visitor: ASTVisitor) =
    visitor.visit(this)
