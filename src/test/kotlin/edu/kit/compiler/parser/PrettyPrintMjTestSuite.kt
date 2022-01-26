package edu.kit.compiler.parser

import edu.kit.compiler.ast.validate
import edu.kit.compiler.semantic.SemanticAST
import edu.kit.compiler.source.SourceFile
import edu.kit.compiler.utils.MjTestSuite
import edu.kit.compiler.utils.createParser
import edu.kit.compiler.utils.prettyPrint
import kotlin.test.assertEquals

fun tryCreateSemanticAst(sourceFile: SourceFile): SemanticAST.Program? {
    val (parser) = createParser(sourceFile)
    return parser.parse().validate()
}

internal class PrettyPrintMjTestSuite : MjTestSuite("syntax", "ast") {

    override fun TestContext.execute() {
        println("========[ input ]========")
        println(source.content)

        val program1 = tryCreateSemanticAst(source)
        checkStep(successful = program1 != null)
        val pretty1 = prettyPrint(program1)
        println("=======[ Pretty1 ]=======")
        println(pretty1)

        val program2 = tryCreateSemanticAst(SourceFile.from(source.path + ".pretty", pretty1))
        checkStep(successful = program2 != null)
        val pretty2 = prettyPrint(program2)
        println("=======[ Pretty2 ]=======")
        println(pretty2)
        println("=========================")
        println()

        assertEquals(pretty1, pretty2)
    }
}
