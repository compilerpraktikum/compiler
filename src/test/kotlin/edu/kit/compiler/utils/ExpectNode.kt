package edu.kit.compiler.utils

import edu.kit.compiler.ast.AST
import edu.kit.compiler.lexer.Symbol
import edu.kit.compiler.parser.Parser
import edu.kit.compiler.wrapper.wrappers.Parsed
import kotlin.test.assertEquals

internal inline fun <reified T> Parsed<T>?.debug(): String {
    return when (this) {
        is Parsed.Valid -> {
            val nodeName = node!!::class.java.simpleName
            "Valid$nodeName(${node.toContentString()})"
        }
        is Parsed.Error -> {
            val nodeName = (node?.let { it::class } ?: T::class).java.simpleName
            "Invalid$nodeName(${node.toContentString()})"
        }
        null -> "/"
    }
}

internal inline fun <reified T> List<Parsed<T>>.debug() = "{ ${this.joinToString(separator = ", ") { it.debug() }} }"

private fun <T> T.toContentString() = when (this) {
    null -> ""
    is AST -> toChildString()
    is Symbol -> "Symbol(${this.text})"
    is String -> "\"$this\""
    else -> toString()
}

private fun AST.toChildString(): String = when (this) {
    is AST.Program -> "classes = ${classes.debug()}"
    is AST.ClassDeclaration -> "name = ${name.debug()}, member = ${member.debug()}"
    is AST.Field -> "name = ${name.debug()}, type = ${type.debug()}"
    is AST.Method -> "name = ${name.debug()}, return = ${returnType.debug()}, throws = ${throwsException.debug()}, parameters = ${parameters.debug()}, body = ${block.debug()}"
    is AST.MainMethod -> "name = ${name.debug()}, return = ${returnType.debug()}, throws = ${throwsException.debug()}, parameters = \${parameters.debug(), body = ${block.debug()}"
    is AST.Parameter -> "name = ${name.debug()}, type = ${type.debug()}"

    is AST.LocalVariableDeclarationStatement -> "name = ${name.debug()}, type = ${type.debug()}, init = ${initializer.debug()}"
    is AST.Block -> "statements = ${statements.debug()}"
    is AST.IfStatement -> "condition = ${condition.debug()}, true = ${trueStatement.debug()}, false = ${falseStatement.debug()}"
    is AST.WhileStatement -> "condition = ${condition.debug()}, body = ${statement.debug()}"
    is AST.ReturnStatement -> "expression = ${expression.debug()}"
    is AST.ExpressionStatement -> "expression = ${expression.debug()}"

    is AST.BinaryExpression -> "op = '${operation.repr}', left = ${left.debug()}, right = ${right.debug()}"
    is AST.UnaryExpression -> "op = '${operation.repr}', expression = ${expression.debug()}"
    is AST.MethodInvocationExpression -> "name = ${method.debug()}, target = ${target.debug()}, arguments = ${arguments.debug()}"
    is AST.FieldAccessExpression -> "name = ${field.debug()}, target = ${target.debug()}"
    is AST.ArrayAccessExpression -> "index = ${index.debug()}, target = ${target.debug()}"
    is AST.IdentifierExpression -> "name = ${name.debug()}"
    is AST.LiteralExpression -> when (this) {
        is AST.LiteralExpression.Integer -> "value = ${if (isNegated) "-" else ""}$value"
        is AST.LiteralExpression.Boolean -> "value = $value"
        is AST.LiteralExpression.Null -> "value = null"
        is AST.LiteralExpression.This -> "value = this"
    }
    is AST.NewObjectExpression -> "class = ${clazz.debug()}"
    is AST.NewArrayExpression -> "type = ${type.debug()}, length = ${length.debug()}"

    AST.Type.Void -> ""
    AST.Type.Integer -> ""
    AST.Type.Boolean -> ""
    is AST.Type.Class -> "name = ${name.debug()}"
    is AST.Type.Array -> "elementType = ${elementType.debug()}"
}

internal inline fun <reified T : AST> expectNode(input: String, expectedNode: Parsed<T>, runParser: Parser.() -> Parsed<T>) {
    val (lexer, sourceFile) = createLexer(input)
    val res = Parser(sourceFile, lexer.tokens()).runParser()
    assertEquals(expectedNode.debug(), res.debug())
}

internal inline fun <reified T : AST> expectNode(input: String, expectedNode: List<Parsed<T>>, runParser: Parser.() -> List<Parsed<T>>) {
    val (lexer, sourceFile) = createLexer(input)
    val res = Parser(sourceFile, lexer.tokens()).runParser()
    assertEquals(expectedNode.debug(), res.debug())
}
