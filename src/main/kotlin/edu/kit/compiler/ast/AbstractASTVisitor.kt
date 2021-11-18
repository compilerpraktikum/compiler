package edu.kit.compiler.ast

import edu.kit.compiler.wrapper.Unwrappable
import edu.kit.compiler.wrapper.into
import edu.kit.compiler.wrapper.unwrap
import edu.kit.compiler.wrapper.wrappers.into

abstract class AbstractASTVisitor<ExprW, StmtW, DeclW, ClassW, OtherW>(
    private val unwrapExpr: Unwrappable<ExprW>,
    private val unwrapStmt: Unwrappable<StmtW>,
    private val unwrapDecl: Unwrappable<DeclW>,
    private val unwrapClass: Unwrappable<ClassW>,
    private val unwrapOther: Unwrappable<OtherW>
) :
    ASTVisitor<ExprW, StmtW, DeclW, ClassW, OtherW> {
    override fun visit(program: AST.Program<ExprW, StmtW, DeclW, ClassW, OtherW>) {
        program.classes.forEach { it.unwrap(unwrapClass).accept(this) }
    }

    override fun visit(classDeclaration: AST.ClassDeclaration<ExprW, StmtW, DeclW, OtherW>) {
        classDeclaration.member.forEach { it.unwrap(unwrapDecl).accept(this) }
    }

    override fun visit(field: AST.Field<OtherW>) {
        field.type.unwrap(unwrapOther).into().accept(this)
    }

    override fun visit(method: AST.Method<ExprW, StmtW, OtherW>) {
        method.returnType.unwrap(unwrapOther).into().accept(this)
        method.parameters.forEach { it.unwrap(unwrapOther).into().accept(this) }
        method.block.unwrap(unwrapStmt).accept(this)
    }

    override fun visit(mainMethod: AST.MainMethod<ExprW, StmtW, OtherW>) {
        mainMethod.returnType.unwrap(unwrapOther).into().accept(this)
        mainMethod.parameters.forEach { it.unwrap(unwrapOther).into().accept(this) }
        mainMethod.block.unwrap(unwrapStmt).accept(this)
    }

    override fun visit(parameter: AST.Parameter<OtherW>) {
        parameter.type.unwrap(unwrapOther).into().accept(this)
    }

    override fun visit(localVariableDeclarationStatement: AST.LocalVariableDeclarationStatement<ExprW, OtherW>) {
        localVariableDeclarationStatement.type.unwrap(unwrapOther).into().accept(this)
        localVariableDeclarationStatement.initializer?.also { it.unwrap(unwrapExpr) }
    }

    override fun visit(block: AST.Block<ExprW, StmtW, OtherW>) {
        block.statements.forEach { it.unwrap(unwrapStmt).into() }
    }

    override fun visit(ifStatement: AST.IfStatement<ExprW, StmtW, OtherW>) {
        ifStatement.condition.unwrap(unwrapExpr).into().accept(this)
        ifStatement.trueStatement.unwrap(unwrapStmt).into().accept(this)
        ifStatement.falseStatement?.also { it.unwrap(unwrapStmt).into().accept(this) }
    }

    override fun visit(whileStatement: AST.WhileStatement<ExprW, StmtW, OtherW>) {
        whileStatement.condition.unwrap(unwrapExpr).into().accept(this)
        whileStatement.statement.unwrap(unwrapStmt).into().accept(this)
    }

    override fun visit(returnStatement: AST.ReturnStatement<ExprW, OtherW>) {
        returnStatement.expression?.also { it.unwrap(unwrapExpr).into().accept(this) }
    }

    override fun visit(binaryExpression: AST.BinaryExpression<ExprW, OtherW>) {
        binaryExpression.left.unwrap(unwrapExpr).into().accept(this)
        binaryExpression.right.unwrap(unwrapExpr).into().accept(this)
        binaryExpression.operation.accept(this)
    }

    override fun visit(unaryExpression: AST.UnaryExpression<ExprW, OtherW>) {
        unaryExpression.expression.unwrap(unwrapExpr).into().accept(this)
        unaryExpression.operation.accept(this)
    }

    override fun visit(methodInvocationExpression: AST.MethodInvocationExpression<ExprW, OtherW>) {
        methodInvocationExpression.target?.also { it.unwrap(unwrapExpr).into().accept(this) }
        methodInvocationExpression.arguments.forEach { it.unwrap(unwrapExpr).into().accept(this) }
    }

    override fun visit(fieldAccessExpression: AST.FieldAccessExpression<ExprW, OtherW>) {
        fieldAccessExpression.target.unwrap(unwrapExpr).into().accept(this)
    }

    override fun visit(arrayAccessExpression: AST.ArrayAccessExpression<ExprW, OtherW>) {
        arrayAccessExpression.index.unwrap(unwrapExpr).into().accept(this)
        arrayAccessExpression.target.unwrap(unwrapExpr).into().accept(this)
    }

    override fun visit(identifierExpression: AST.IdentifierExpression) {
        // Nothing to do
    }

    override fun <T> visit(literalExpression: AST.LiteralExpression<T>) {
        // Nothing to do
    }

    override fun visit(newObjectExpression: AST.NewObjectExpression) {
        // Nothing to do
    }

    override fun visit(newArrayExpression: AST.NewArrayExpression<ExprW, OtherW>) {
        newArrayExpression.type.unwrap(unwrapOther).into().accept(this)
        newArrayExpression.length.unwrap(unwrapExpr).into().accept(this)
    }

    override fun visit(voidType: Type.Void) {
        // Nothing to do
    }

    override fun visit(integerType: Type.Integer) {
        // Nothing to do
    }

    override fun visit(booleanType: Type.Boolean) {
        // Nothing to do
    }

    override fun visit(arrayType: Type.Array<OtherW>) {
        arrayType.arrayType.accept(this)
    }

    override fun visit(classType: Type.Class) {
        // Nothing to do
    }

    override fun visit(operation: AST.BinaryExpression.Operation) {
        // Nothing to do
    }

    override fun visit(expressionStatement: AST.ExpressionStatement<ExprW, OtherW>) {
        expressionStatement.expression.unwrap(unwrapExpr).into().accept(this)
    }
}

fun <E, S, D, C, O> AST.Program<E, S, D, C, O>.accept(visitor: AbstractASTVisitor<E, S, D, C, O>) = visitor.visit(this)

fun <E, S, D, C, O> AST.BinaryExpression.Operation.accept(visitor: AbstractASTVisitor<E, S, D, C, O>) =
    visitor.visit(this)

fun <E, S, D, C, O> AST.UnaryExpression.Operation.accept(visitor: AbstractASTVisitor<E, S, D, C, O>) =
    visitor.visit(this)
