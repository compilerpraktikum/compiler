package edu.kit.compiler.ast

import edu.kit.compiler.ast.AST.Type
import edu.kit.compiler.lexer.Symbol
import edu.kit.compiler.source.SourceRange

abstract class AbstractAstVisitor {

    open fun visitProgram(program: Parsed<AST.Program>) =
        program.map { program ->
            program.classes.map { classDecl -> visitClassDeclaration(classDecl) }.let { AST.Program(it) }
        }
            .mapPosition { visitSourceRange(it) }

    open fun visitClassDeclaration(classDeclaration: Parsed<AST.ClassDeclaration>) =
        classDeclaration.map {
            val name = visitIdentifier(it.name)
            val member = it.member.map { member -> visitClassMember(member) }
            AST.ClassDeclaration(name, member)
        }.mapPosition { visitSourceRange(it) }

    open fun visitClassMember(classMember: Parsed<AST.ClassMember>): Parsed<AST.ClassMember> =
        classMember.casePack { it, packer ->
            when (it) {
                is AST.Field -> visitFieldDeclaration(packer.pack(it))
                is AST.MainMethod -> visitMainMethodDeclaration(packer.pack(it))
                is AST.Method -> visitMethodDeclaration(packer.pack(it))
            }
        }

    open fun visitSourceRange(sourceRange: SourceRange): SourceRange = sourceRange

    open fun visitFieldDeclaration(fieldDeclaration: Parsed<AST.Field>) =
        fieldDeclaration.map {
            val name = visitIdentifier(it.name)
            val type = visitType(it.type)

            AST.Field(name, type)
        }.mapPosition { visitSourceRange(it) }

    open fun visitIdentifier(name: Parsed<Symbol>): Parsed<Symbol> = name.map {
        it
    }.mapPosition { visitSourceRange(it) }

    open fun visitType(type: Parsed<Type>): Parsed<Type> = type.casePack { value, packer ->
        when (value) {
            is Type.Array -> visitArrayType(packer.pack(value))
            is Type.Boolean -> visitBoolType(packer.pack(value))
            is Type.Class -> visitClassType(packer.pack(value))
            is Type.Void -> visitVoidType(packer.pack(value))
            is Type.Integer -> visitIntType(packer.pack(value))
        }
    }

    open fun visitMethodDeclaration(methodDeclaration: Parsed<AST.Method>): Parsed<AST.Method> = methodDeclaration.map {
        val name = visitIdentifier(it.name)
        val returnType = visitType(it.returnType)
        val parameters = it.parameters.map { param -> visitParameter(param) }
        val block = visitBlock(it.block)
        val throwsException = it.throwsException?.let { exc -> visitIdentifier(exc) }

        AST.Method(name, returnType, parameters, block, throwsException)
    }.mapPosition { visitSourceRange(it) }

    open fun visitMainMethodDeclaration(mainMethodDeclaration: Parsed<AST.MainMethod>) = mainMethodDeclaration.map {
        val name = visitIdentifier(it.name)
        val returnType = visitType(it.returnType)
        val parameters = it.parameters.map { param -> visitParameter(param) }
        val block = visitBlock(it.block)
        val throwsException = it.throwsException?.let { exc -> visitIdentifier(exc) }

        AST.MainMethod(name, returnType, parameters, block, throwsException)
    }.mapPosition { visitSourceRange(it) }

    open fun visitParameter(parameter: Parsed<AST.Parameter>): Parsed<AST.Parameter> = parameter.map {
        val name = visitIdentifier(it.name)
        val type = visitType(it.type)

        AST.Parameter(name, type)
    }.mapPosition { visitSourceRange(it) }

    open fun visitArrayAccessExpression(arrayAccessExpression: Parsed<AST.ArrayAccessExpression>) =
        arrayAccessExpression.map {
            val target = visitExpression(it.target)
            val index = visitExpression(it.index)

            AST.ArrayAccessExpression(target, index)
        }.mapPosition { visitSourceRange(it) }

    open fun visitExpression(target: Parsed<AST.Expression>): Parsed<AST.Expression> =
        target.casePack { expression, packer ->
            when (expression) {
                is AST.ArrayAccessExpression -> visitArrayAccessExpression(packer.pack(expression))
                is AST.BinaryExpression -> visitBinaryOperation(packer.pack(expression))
                is AST.FieldAccessExpression -> visitFieldAccessExpression(packer.pack(expression))
                is AST.IdentifierExpression -> visitIdentifierExpression(packer.pack(expression))
                is AST.LiteralExpression -> visitLiteralExpression(packer.pack(expression))
                is AST.MethodInvocationExpression -> visitMethodInvocationExpression(packer.pack(expression))
                is AST.NewArrayExpression -> visitNewArrayExpression(packer.pack(expression))
                is AST.NewObjectExpression -> visitNewObjectExpression(packer.pack(expression))
                is AST.UnaryExpression -> visitUnaryOperation(packer.pack(expression))
            }
        }

    open fun visitBinaryOperation(binaryOperation: Parsed<AST.BinaryExpression>): Parsed<AST.BinaryExpression> =
        binaryOperation.map {
            val left = visitExpression(it.left)
            val right = visitExpression(it.right)
            AST.BinaryExpression(left, right, it.operation)
        }.mapPosition { visitSourceRange(it) }

    open fun visitFieldAccessExpression(fieldAccessExpression: Parsed<AST.FieldAccessExpression>): Parsed<AST.FieldAccessExpression> =
        fieldAccessExpression.map {
            val target = visitExpression(it.target)
            val index = visitIdentifier(it.field)
            AST.FieldAccessExpression(target, index)
        }.mapPosition { visitSourceRange(it) }

    open fun visitIdentifierExpression(identifierExpression: Parsed<AST.IdentifierExpression>): Parsed<AST.IdentifierExpression> =
        identifierExpression.map {
            val ident = visitIdentifier(it.name)
            AST.IdentifierExpression(ident)
        }.mapPosition { visitSourceRange(it) }

    open fun visitLiteralExpression(literalBoolExpression: Parsed<AST.LiteralExpression>): Parsed<AST.LiteralExpression> =
        literalBoolExpression.map {
            it
        }.mapPosition { visitSourceRange(it) }

    open fun visitMethodInvocationExpression(methodInvocationExpression: Parsed<AST.MethodInvocationExpression>): Parsed<AST.MethodInvocationExpression> =
        methodInvocationExpression.map {
            val target = it.target?.let { it1 -> visitExpression(it1) }
            val method = visitIdentifier(it.method)
            val arguments = it.arguments.map { arg -> visitExpression(arg) }

            AST.MethodInvocationExpression(target, method, arguments)
        }.mapPosition { visitSourceRange(it) }

    open fun visitNewArrayExpression(newArrayExpression: Parsed<AST.NewArrayExpression>): Parsed<AST.NewArrayExpression> =
        newArrayExpression.map {
            val type = visitArrayType(it.type)
            val length = visitExpression(it.length)

            AST.NewArrayExpression(type, length)
        }.mapPosition { visitSourceRange(it) }

    open fun visitNewObjectExpression(newObjectExpression: Parsed<AST.NewObjectExpression>): Parsed<AST.NewObjectExpression> =
        newObjectExpression.map {
            val clazz = visitIdentifier(it.type.name)

            AST.NewObjectExpression(Type.Class(clazz))
        }.mapPosition { visitSourceRange(it) }

    open fun visitUnaryOperation(unaryOperation: Parsed<AST.UnaryExpression>): Parsed<AST.UnaryExpression> =
        unaryOperation.map {
            val expression = visitExpression(it.expression)
            AST.UnaryExpression(expression, it.operation)
        }.mapPosition { visitSourceRange(it) }

    open fun visitBlock(block: Parsed<AST.Block>): Parsed<AST.Block> = block.map {
        val statements = it.statements.map { stmt -> visitBlockStatement(stmt) }

        AST.Block(
            statements,
            it.openingBraceRange.mapPosition { range -> visitSourceRange(range) },
            it.closingBraceRange.mapPosition { range -> visitSourceRange(range) }
        )
    }.mapPosition { visitSourceRange(it) }

    open fun visitBlockStatement(blockStatement: Parsed<AST.BlockStatement>): Parsed<AST.BlockStatement> =
        blockStatement.casePack { it, packer ->
            when (it) {
                is AST.LocalVariableDeclarationStatement -> visitLocalVariableDeclaration(packer.pack(it))
                is AST.Statement -> visitStatement(packer.pack(it))
            }
        }

    open fun visitStatement(statement: Parsed<AST.Statement>): Parsed<AST.Statement> =
        statement.casePack { it, packer ->
            when (it) {
                is AST.Block -> visitBlock(packer.pack(it))
                is AST.ExpressionStatement -> visitExpressionStatement(packer.pack(it))
                is AST.IfStatement -> visitIfStatement(packer.pack(it))
                is AST.ReturnStatement -> visitReturnStatement(packer.pack(it))
                is AST.WhileStatement -> visitWhileStatement(packer.pack(it))
            }
        }

    open fun visitExpressionStatement(expressionStatement: Parsed<AST.ExpressionStatement>): Parsed<AST.ExpressionStatement> =
        expressionStatement.map {
            AST.ExpressionStatement(visitExpression((it.expression)))
        }.mapPosition { visitSourceRange(it) }

    open fun visitIfStatement(ifStatement: Parsed<AST.IfStatement>): Parsed<AST.IfStatement> = ifStatement.map {
        val cond = visitExpression(it.condition)
        val thenStmt = visitStatement(it.trueStatement)
        val elseStmt = it.falseStatement?.let { stmt -> visitStatement(stmt) }

        AST.IfStatement(cond, thenStmt, elseStmt)
    }.mapPosition { visitSourceRange(it) }

    open fun visitLocalVariableDeclaration(localVariableDeclaration: Parsed<AST.LocalVariableDeclarationStatement>) =
        localVariableDeclaration.map {
            val name = visitIdentifier(it.name)
            val type = visitType(it.type)
            val initializer = it.initializer?.let { init -> visitExpression(init) }

            AST.LocalVariableDeclarationStatement(name, type, initializer)
        }.mapPosition { visitSourceRange(it) }

    open fun visitReturnStatement(returnStatement: Parsed<AST.ReturnStatement>): Parsed<AST.ReturnStatement> =
        returnStatement.map {
            AST.ReturnStatement(it.expression?.let { expr -> visitExpression(expr) })
        }.mapPosition { visitSourceRange(it) }

    open fun visitWhileStatement(whileStatement: Parsed<AST.WhileStatement>): Parsed<AST.WhileStatement> =
        whileStatement.map {
            val cond = visitExpression(it.condition)
            val statement = visitStatement(it.statement)
            AST.WhileStatement(cond, statement)
        }.mapPosition { visitSourceRange(it) }

    open fun visitIntType(int: Parsed<Type.Integer>) = int.mapPosition { visitSourceRange(it) }

    open fun visitBoolType(bool: Parsed<Type.Boolean>) = bool.mapPosition { visitSourceRange(it) }

    open fun visitVoidType(void: Parsed<Type.Void>) = void.mapPosition { visitSourceRange(it) }

    open fun visitArrayType(arrayType: Parsed<Type.Array>): Parsed<Type.Array> =
        arrayType.map {
            Type.Array(visitType(it.elementType))
        }.mapPosition { visitSourceRange(it) }

    open fun visitClassType(clazz: Parsed<Type.Class>): Parsed<Type.Class> = clazz.map {
        Type.Class(visitIdentifier(it.name))
    }.mapPosition { visitSourceRange(it) }
}
