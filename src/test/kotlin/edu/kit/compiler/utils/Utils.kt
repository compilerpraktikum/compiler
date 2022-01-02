package edu.kit.compiler.utils

import edu.kit.compiler.ast.validate
import edu.kit.compiler.initializeKeywords
import edu.kit.compiler.lexer.Lexer
import edu.kit.compiler.lexer.StringTable
import edu.kit.compiler.lexer.Symbol
import edu.kit.compiler.parser.Parser
import edu.kit.compiler.parser.anchorSetOf
import edu.kit.compiler.semantic.AstNode
import edu.kit.compiler.semantic.visitor.PrettyPrintVisitor
import edu.kit.compiler.semantic.visitor.accept
import edu.kit.compiler.source.SourceFile
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.test.assertEquals
import kotlin.test.fail

internal val emptyAnchorSet = anchorSetOf().intoUnion()

fun String.toSymbol() = Symbol(this, isKeyword = false)

fun createLexer(input: String, fileName: String = "/path/to/file"): Triple<Lexer, SourceFile, StringTable> {
    val stringTable = StringTable(StringTable::initializeKeywords)
    val sourceFile = SourceFile.from(fileName, input)
    return Triple(Lexer(sourceFile, stringTable), sourceFile, stringTable)
}

fun getTestCasePathName(index: Int): String {
    val stackFrame = Exception().stackTrace[index] ?: return "unknown"
    return "/tests/${stackFrame.fileName}/${stackFrame.methodName}"
}

fun createLexer(input: String, stackIndex: Int) = createLexer(input, getTestCasePathName(stackIndex))

inline fun <T> withParser(sourceFile: SourceFile, f: Parser.() -> T): T {
    val stringTable = StringTable(StringTable::initializeKeywords)
    val lexer = Lexer(sourceFile, stringTable)
    val parser = Parser(sourceFile, lexer.tokens())
    return parser.f().also {
        sourceFile.printAnnotations()
    }
}

inline fun <T> withParser(input: String, f: Parser.() -> T): T =
    withParser(SourceFile.from("/path/to/file", input), f)

fun createAst(source: SourceFile): AstNode.Program? = withParser(source) { parse().validate() }

fun createAST(input: String): AstNode.Program? {
    val sourceFile = SourceFile.from("/path/to/file", input)
    return createAst(sourceFile)
}

fun assertIdempotence(input: String) {
    println("========[ input ]========")
    println(input)

    val ast1 = createAST(input) ?: fail("failed to parse input")
    val pretty1 = prettyPrint(ast1)
    println("=======[ Pretty1 ]=======")
    println(pretty1)

    val ast2 = createAST(pretty1) ?: fail("failed to parse pretty printed output")
    val pretty2 = prettyPrint(ast2)
    println("=======[ Pretty2 ]=======")
    println(pretty2)
    println("=========================")
    println()

    assertEquals(pretty1, pretty2)
}

fun prettyPrint(program: AstNode.Program): String {
    val out = ByteArrayOutputStream()
    val printStream = PrintStream(out, false, Charsets.UTF_8)

    program.accept(PrettyPrintVisitor(printStream))

    printStream.flush()
    return out.toString(Charsets.UTF_8)
}
