package edu.kit.compiler.parser

import edu.kit.compiler.lex.SourceFile
import edu.kit.compiler.utils.MjTestSuite
import edu.kit.compiler.utils.createAst
import edu.kit.compiler.utils.prettyPrint
import kotlin.test.assertEquals

internal class PrettyPrintMjTestSuite : MjTestSuite("syntax", "ast") {

    override fun TestContext.execute() {
        println("========[ input ]========")
        println(source.content)

        val program1 = createAst(source)
        checkStep(successful = program1 != null)
        val pretty1 = prettyPrint(program1)
        println("=======[ Pretty1 ]=======")
        println(pretty1)

        val program2 = createAst(SourceFile.from(source.path + ".pretty", pretty1))
        checkStep(successful = program2 != null)
        val pretty2 = prettyPrint(program2)
        println("=======[ Pretty2 ]=======")
        println(pretty2)
        println("=========================")
        println()

        assertEquals(pretty1, pretty2)
    }
}
