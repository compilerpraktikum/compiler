package edu.kit.compiler.semantic

import edu.kit.compiler.initializeKeywords
import edu.kit.compiler.lexer.Lexer
import edu.kit.compiler.lexer.StringTable
import edu.kit.compiler.parser.Parser
import edu.kit.compiler.utils.MjTestSuite
import edu.kit.compiler.wrapper.wrappers.validate

internal class SemanticMJTestSuite : MjTestSuite("semantic") {

    override fun TestContext.execute() {
        val stringTable = StringTable(StringTable::initializeKeywords)
        val lexer = Lexer(source, stringTable)
        val parser = Parser(source, lexer.tokens())

        val program = parser.parse().validate()
        checkStep(successful = program != null)

        doSemanticAnalysis(program, source, stringTable)
        checkResult(successful = !source.hasError)
    }
}
