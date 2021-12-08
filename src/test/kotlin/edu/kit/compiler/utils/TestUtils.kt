package edu.kit.compiler.utils

import edu.kit.compiler.ast.AST
import edu.kit.compiler.lex.LexerMjTestSuite
import edu.kit.compiler.lex.Symbol
import edu.kit.compiler.parser.Parser
import edu.kit.compiler.semantic.AstNode
import edu.kit.compiler.semantic.visitor.PrettyPrintVisitor
import edu.kit.compiler.semantic.visitor.accept
import edu.kit.compiler.wrapper.wrappers.Parsed
import edu.kit.compiler.wrapper.wrappers.validate
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.nio.file.Paths
import java.util.stream.Stream
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
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
        is AST.LiteralExpression.Integer -> "value = $value"
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

object TestUtils {

    class TestFileArgument(val name: String, val path: Path) {
        // This is used for naming in the junit output
        override fun toString(): String = name
    }

    /** this is used to run multiple instances of the test:
     * see: https://blog.oio.de/2018/11/13/how-to-use-junit-5-methodsource-parameterized-tests-with-kotlin/
     *
     * @param subdirectory Name of the subdirectory in `test-cases`, from where this function should get the test files
     *
     * @return Stream of **relative** Paths (e.g. LongestPattern.mj) The name displayed in the test results and
     *         shouldn't be verbose
     */
    fun getTestSuiteFilesFor(subdirectory: String): Stream<TestFileArgument> {
        // https://stackoverflow.com/questions/320542/how-to-get-the-path-of-a-running-jar-file
        val testFolderAbsolutePath =
            File(LexerMjTestSuite::class.java.protectionDomain.codeSource.location.toURI()).getPath()
        val projectRootDirectory = Paths.get(testFolderAbsolutePath).parent.parent.parent.parent
        val path = projectRootDirectory.resolve("test-cases").resolve(subdirectory)
        return path.listDirectoryEntries("*.mj").map { TestFileArgument(path.relativize(it).name, it) }.stream()
    }

    @OptIn(ExperimentalStdlibApi::class)
    internal inline fun <reified T : AST> expectNode(input: String, expectedNode: Parsed<T>, runParser: Parser.() -> Parsed<T>) {
        val (lexer, sourceFile) = createLexer(input)
        val res = Parser(sourceFile, lexer.tokens()).runParser()
        assertEquals(expectedNode.debug(), res.debug())
    }

    @OptIn(ExperimentalStdlibApi::class)
    internal inline fun <reified T : AST> expectNode(input: String, expectedNode: List<Parsed<T>>, runParser: Parser.() -> List<Parsed<T>>) {
        val (lexer, sourceFile) = createLexer(input)
        val res = Parser(sourceFile, lexer.tokens()).runParser()
        assertEquals(expectedNode.debug(), res.debug())
    }

    @OptIn(ExperimentalStdlibApi::class)
    fun createAST(input: String): Parsed<AST.Program> {
        val (lexer, sourceFile) = createLexer(input)

        val ast = Parser(sourceFile, lexer.tokens()).parse()
        sourceFile.printAnnotations()
        return ast
    }

    fun assertIdemPotence(input: String) {
        println("====[ input ]====")
        println(input)

        val ast1 = createAST(input)
        val pretty1 = prettyPrint(ast1.validate()!!)
        println("====[ pretty1 ]====")
        println(pretty1)

        val ast2 = createAST(pretty1)
        val pretty2 = prettyPrint(ast2.validate()!!)
        println("====[ pretty2 ]====")
        println(pretty2)

        assertEquals(pretty1, pretty2)
    }

    fun prettyPrint(astRoot: AstNode.Program): String {
        val byteArrayOutputStream = ByteArrayOutputStream()
        val utf8: String = StandardCharsets.UTF_8.name()
        val printStream = PrintStream(byteArrayOutputStream, true, utf8)

        astRoot.accept(PrettyPrintVisitor(printStream))

        return byteArrayOutputStream.toString(utf8)
    }
}
