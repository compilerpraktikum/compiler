package edu.kit.compiler.semantic

import edu.kit.compiler.ast.validate
import edu.kit.compiler.lexer.Lexer
import edu.kit.compiler.lexer.StringTable
import edu.kit.compiler.lexer.initializeKeywords
import edu.kit.compiler.parser.Parser
import edu.kit.compiler.utils.MjTestSuite

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
