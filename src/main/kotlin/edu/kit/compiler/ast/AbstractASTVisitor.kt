package edu.kit.compiler.ast

import edu.kit.compiler.wrapper.Unwrappable
import edu.kit.compiler.wrapper.into
import edu.kit.compiler.wrapper.unwrap

abstract class AbstractASTVisitor<ExprW, StmtW, DeclW, ClassW, OtherW>(
    private val unwrapExpr: Unwrappable<ExprW>,
    private val unwrapStmt: Unwrappable<StmtW>,
    private val unwrapDecl: Unwrappable<DeclW>,
    private val unwrapClass: Unwrappable<ClassW>,
    private val unwrapOther: Unwrappable<OtherW>
) : ASTVisitor<ExprW, StmtW, DeclW, ClassW, OtherW> {

    // AST.Program
    protected fun AST.Program<ExprW, StmtW, DeclW, ClassW, OtherW>.descendClasses() {
        classes.forEach { it.unwrap(unwrapClass).accept(this@AbstractASTVisitor) }
    }

    override fun visit(program: AST.Program<ExprW, StmtW, DeclW, ClassW, OtherW>) {
        program.descendClasses()
    }

    // AST.ClassDeclaration
    protected fun AST.ClassDeclaration<ExprW, StmtW, DeclW, OtherW>.descendMembers() {
        member.forEach { it.unwrap(unwrapDecl).accept(this@AbstractASTVisitor) }
    }

    override fun visit(classDeclaration: AST.ClassDeclaration<ExprW, StmtW, DeclW, OtherW>) {
        classDeclaration.descendMembers()
    }

    // AST.Field
    protected fun AST.Field<OtherW>.descendType() {
        type.unwrap(unwrapOther).into().accept(this@AbstractASTVisitor)
    }

    override fun visit(field: AST.Field<OtherW>) {
        field.descendType()
    }

    // AST.Method
    protected fun AST.Method<ExprW, StmtW, OtherW>.descendReturnType() {
        returnType.unwrap(unwrapOther).into().accept(this@AbstractASTVisitor)
    }

    protected fun AST.Method<ExprW, StmtW, OtherW>.descendParameters() {
        parameters.forEach { it.unwrap(unwrapOther).into().accept(this@AbstractASTVisitor) }
    }

    protected fun AST.Method<ExprW, StmtW, OtherW>.descendBlock() {
        block.unwrap(unwrapStmt).accept(this@AbstractASTVisitor)
    }

    override fun visit(method: AST.Method<ExprW, StmtW, OtherW>) {
        method.descendReturnType()
        method.descendParameters()
        method.descendBlock()
    }

    // AST.MainMethod
    protected fun AST.MainMethod<ExprW, StmtW, OtherW>.descendReturnType() {
        returnType.unwrap(unwrapOther).into().accept(this@AbstractASTVisitor)
    }

    protected fun AST.MainMethod<ExprW, StmtW, OtherW>.descendParameters() {
        parameters.forEach { it.unwrap(unwrapOther).into().accept(this@AbstractASTVisitor) }
    }

    protected fun AST.MainMethod<ExprW, StmtW, OtherW>.descendBlock() {
        block.unwrap(unwrapStmt).accept(this@AbstractASTVisitor)
    }

    override fun visit(mainMethod: AST.MainMethod<ExprW, StmtW, OtherW>) {
        mainMethod.descendReturnType()
        mainMethod.descendParameters()
        mainMethod.descendBlock()
    }

    // AST.Parameter
    protected fun AST.Parameter<OtherW>.descendType() {
        type.unwrap(unwrapOther).into().accept(this@AbstractASTVisitor)
    }

    override fun visit(parameter: AST.Parameter<OtherW>) {
        parameter.descendType()
    }

    // AST.LocalVariableDeclarationStatement
    protected fun AST.LocalVariableDeclarationStatement<ExprW, OtherW>.descendType() {
        type.unwrap(unwrapOther).into().accept(this@AbstractASTVisitor)
    }

    protected fun AST.LocalVariableDeclarationStatement<ExprW, OtherW>.descendInitializer() {
        initializer?.also { it.unwrap(unwrapExpr).into().accept(this@AbstractASTVisitor) }
    }

    override fun visit(localVariableDeclarationStatement: AST.LocalVariableDeclarationStatement<ExprW, OtherW>) {
        localVariableDeclarationStatement.descendType()
        localVariableDeclarationStatement.descendInitializer()
    }

    // AST.Block
    protected fun AST.Block<ExprW, StmtW, OtherW>.descendStatements() {
        statements.forEach { it.unwrap(unwrapStmt).into().accept(this@AbstractASTVisitor) }
    }

    override fun visit(block: AST.Block<ExprW, StmtW, OtherW>) {
        block.descendStatements()
    }

    // AST.IfStatement
    protected fun AST.IfStatement<ExprW, StmtW, OtherW>.descendCondition() {
        condition.unwrap(unwrapExpr).into().accept(this@AbstractASTVisitor)
    }

    protected fun AST.IfStatement<ExprW, StmtW, OtherW>.descendTrueStatement() {
        trueStatement.unwrap(unwrapStmt).into().accept(this@AbstractASTVisitor)
    }

    fun AST.IfStatement<ExprW, StmtW, OtherW>.descendFalseStatement() {
        falseStatement?.also { it.unwrap(unwrapStmt).into().accept(this@AbstractASTVisitor) }
    }

    override fun visit(ifStatement: AST.IfStatement<ExprW, StmtW, OtherW>) {
        ifStatement.descendCondition()
        ifStatement.descendTrueStatement()
        ifStatement.descendFalseStatement()
    }

    // AST.WhileStatement
    protected fun AST.WhileStatement<ExprW, StmtW, OtherW>.descendCondition() {
        condition.unwrap(unwrapExpr).into().accept(this@AbstractASTVisitor)
    }

    protected fun AST.WhileStatement<ExprW, StmtW, OtherW>.descendStatement() {
        statement.unwrap(unwrapStmt).into().accept(this@AbstractASTVisitor)
    }

    override fun visit(whileStatement: AST.WhileStatement<ExprW, StmtW, OtherW>) {
        whileStatement.descendCondition()
        whileStatement.descendStatement()
    }

    // AST.ReturnStatement
    protected fun AST.ReturnStatement<ExprW, OtherW>.descendExpression() {
        expression?.also { it.unwrap(unwrapExpr).into().accept(this@AbstractASTVisitor) }
    }

    override fun visit(returnStatement: AST.ReturnStatement<ExprW, OtherW>) {
        returnStatement.descendExpression()
    }

    // AST.BinaryExpression
    protected fun AST.BinaryExpression<ExprW, OtherW>.descendLeft() {
        left.unwrap(unwrapExpr).into().accept(this@AbstractASTVisitor)
    }

    protected fun AST.BinaryExpression<ExprW, OtherW>.descendRight() {
        right.unwrap(unwrapExpr).into().accept(this@AbstractASTVisitor)
    }

    protected fun AST.BinaryExpression<ExprW, OtherW>.descendOperation() {
        operation.accept(this@AbstractASTVisitor)
    }

    override fun visit(binaryExpression: AST.BinaryExpression<ExprW, OtherW>) {
        binaryExpression.descendLeft()
        binaryExpression.descendOperation()
        binaryExpression.descendRight()
    }

    // AST.UnaryExpression
    protected fun AST.UnaryExpression<ExprW, OtherW>.descendExpression() {
        expression.unwrap(unwrapExpr).into().accept(this@AbstractASTVisitor)
    }

    protected fun AST.UnaryExpression<ExprW, OtherW>.descendOperation() {
        operation.accept(this@AbstractASTVisitor)
    }

    override fun visit(unaryExpression: AST.UnaryExpression<ExprW, OtherW>) {
        unaryExpression.descendOperation()
        unaryExpression.descendExpression()
    }

    // AST.MethodInvocationExpression
    protected fun AST.MethodInvocationExpression<ExprW, OtherW>.descendTarget() {
        target?.also { it.unwrap(unwrapExpr).into().accept(this@AbstractASTVisitor) }
    }

    protected fun AST.MethodInvocationExpression<ExprW, OtherW>.descendArguments() {
        arguments.forEach { it.unwrap(unwrapExpr).into().accept(this@AbstractASTVisitor) }
    }

    override fun visit(methodInvocationExpression: AST.MethodInvocationExpression<ExprW, OtherW>) {
        methodInvocationExpression.descendTarget()
        methodInvocationExpression.descendArguments()
    }

    // AST.FieldAccessExpression
    protected fun AST.FieldAccessExpression<ExprW, OtherW>.descendTarget() {
        target.unwrap(unwrapExpr).into().accept(this@AbstractASTVisitor)
    }

    override fun visit(fieldAccessExpression: AST.FieldAccessExpression<ExprW, OtherW>) {
        fieldAccessExpression.descendTarget()
    }

    // AST.ArrayAccessExpression
    protected fun AST.ArrayAccessExpression<ExprW, OtherW>.descendTarget() {
        target.unwrap(unwrapExpr).into().accept(this@AbstractASTVisitor)
    }

    protected fun AST.ArrayAccessExpression<ExprW, OtherW>.descendIndexExpression() {
        index.unwrap(unwrapExpr).into().accept(this@AbstractASTVisitor)
    }

    override fun visit(arrayAccessExpression: AST.ArrayAccessExpression<ExprW, OtherW>) {
        arrayAccessExpression.descendTarget()
        arrayAccessExpression.descendIndexExpression()
    }

    // AST.IdentifierExpression
    override fun visit(identifierExpression: AST.IdentifierExpression) {
        // Nothing to do
    }

    // AST.LiteralExpression
    override fun <T> visit(literalExpression: AST.LiteralExpression<T>) {
        // Nothing to do
    }

    // AST.NewObjectExpression
    override fun visit(newObjectExpression: AST.NewObjectExpression) {
        // Nothing to do
    }

    // AST.NewArrayExpression
    protected fun AST.NewArrayExpression<ExprW, OtherW>.descendType() {
        type.unwrap(unwrapOther).into().accept(this@AbstractASTVisitor)
    }

    protected fun AST.NewArrayExpression<ExprW, OtherW>.descendLength() {
        length.unwrap(unwrapExpr).into().accept(this@AbstractASTVisitor)
    }

    override fun visit(newArrayExpression: AST.NewArrayExpression<ExprW, OtherW>) {
        newArrayExpression.descendType()
        newArrayExpression.descendLength()
    }

    // Type.Void
    override fun visit(voidType: Type.Void) {
        // Nothing to do
    }

    // Type.Integer
    override fun visit(integerType: Type.Integer) {
        // Nothing to do
    }

    // Type.Boolean
    override fun visit(booleanType: Type.Boolean) {
        // Nothing to do
    }

    // Type.Array
    protected fun Type.Array<OtherW>.descendType() {
        arrayType.descendElementType()
        // arrayType.accept(this@AbstractASTVisitor)
    }

    override fun visit(arrayType: Type.Array<OtherW>) {
        arrayType.descendType()
    }

    // Type.Array.ArrayType
    protected fun Type.Array.ArrayType<OtherW>.descendElementType() {
        elementType.unwrap(unwrapOther).into().accept(this@AbstractASTVisitor)
    }

    override fun visit(arrayType: Type.Array.ArrayType<OtherW>) {
        arrayType.descendElementType()
    }

    // Type.Class
    override fun visit(classType: Type.Class) {
        // Nothing to do
    }

    // AST.BinaryExpression.Operation
    override fun visit(operation: AST.BinaryExpression.Operation) {
        // Nothing to do
    }

    // AST.UnaryExpression.Operation
    override fun visit(operation: AST.UnaryExpression.Operation) {
        // Nothing to do
    }

    // AST.ExpressionStatement
    protected fun AST.ExpressionStatement<ExprW, OtherW>.descendExpression() {
        expression.unwrap(unwrapExpr).into().accept(this@AbstractASTVisitor)
    }

    override fun visit(expressionStatement: AST.ExpressionStatement<ExprW, OtherW>) {
        expressionStatement.descendExpression()
    }
}

fun <E, S, D, C, O> AST.Program<E, S, D, C, O>.accept(visitor: AbstractASTVisitor<E, S, D, C, O>) = visitor.visit(this)

fun <E, S, D, C, O> AST.BinaryExpression.Operation.accept(visitor: AbstractASTVisitor<E, S, D, C, O>) =
    visitor.visit(this)

fun <E, S, D, C, O> AST.UnaryExpression.Operation.accept(visitor: AbstractASTVisitor<E, S, D, C, O>) =
    visitor.visit(this)
