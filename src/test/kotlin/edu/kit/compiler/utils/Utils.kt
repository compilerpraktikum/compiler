package edu.kit.compiler.utils

import edu.kit.compiler.ast.validate
import edu.kit.compiler.initializeKeywords
import edu.kit.compiler.lexer.Lexer
import edu.kit.compiler.lexer.StringTable
import edu.kit.compiler.lexer.Symbol
import edu.kit.compiler.parser.Parser
import edu.kit.compiler.parser.anchorSetOf
import edu.kit.compiler.semantic.SemanticAST
import edu.kit.compiler.semantic.visitor.PrettyPrintVisitor
import edu.kit.compiler.semantic.visitor.accept
import edu.kit.compiler.source.SourceFile
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.test.assertEquals
import kotlin.test.fail

internal val emptyAnchorSet = anchorSetOf().intoUnion()

fun String.toSymbol() = Symbol(this, isKeyword = false)

fun generateTestCasePath(): String {
    val stackFrame = Exception().stackTrace.asSequence()
        .takeWhile { it.moduleName == null }
        .lastOrNull() ?: error("unexpected stack trace")
    return "/tests/${stackFrame.fileName}/${stackFrame.methodName}"
}

fun createLexer(sourceFile: SourceFile): Triple<Lexer, SourceFile, StringTable> {
    val stringTable = StringTable(StringTable::initializeKeywords)
    return Triple(Lexer(sourceFile, stringTable), sourceFile, stringTable)
}

fun createLexer(input: String) =
    createLexer(SourceFile.from(generateTestCasePath(), input))

fun createParser(sourceFile: SourceFile): Triple<Parser, SourceFile, StringTable> {
    val (lexer, _, stringTable) = createLexer(sourceFile)
    return Triple(Parser(sourceFile, lexer.tokens()), sourceFile, stringTable)
}

fun createParser(input: String) =
    createParser(SourceFile.from(generateTestCasePath(), input))

fun createSemanticAST(sourceFile: SourceFile, messageOnFail: String = "failed to parse input"): Triple<SemanticAST.Program, SourceFile, StringTable> {
    val (parser, _, stringTable) = createParser(sourceFile)
    val ast = parser.parse().validate() ?: run {
        sourceFile.printAnnotations()
        fail(messageOnFail)
    }
    return Triple(ast, sourceFile, stringTable)
}
fun createSemanticAST(input: String, messageOnFail: String = "failed to parse input") =
    createSemanticAST(SourceFile.from(generateTestCasePath(), input), messageOnFail = messageOnFail)

fun assertIdempotence(input: String) {
    println("========[ input ]========")
    println(input)

    val (ast1) = createSemanticAST(input)
    val pretty1 = prettyPrint(ast1)
    println("=======[ Pretty1 ]=======")
    println(pretty1)

    val (ast2) = createSemanticAST(pretty1, messageOnFail = "failed to parse pretty printed output")
    val pretty2 = prettyPrint(ast2)
    println("=======[ Pretty2 ]=======")
    println(pretty2)
    println("=========================")
    println()

    assertEquals(pretty1, pretty2)
}

fun prettyPrint(program: SemanticAST.Program): String {
    val out = ByteArrayOutputStream()
    val printStream = PrintStream(out, false, Charsets.UTF_8)

    program.accept(PrettyPrintVisitor(printStream))

    printStream.flush()
    return out.toString(Charsets.UTF_8)
}
