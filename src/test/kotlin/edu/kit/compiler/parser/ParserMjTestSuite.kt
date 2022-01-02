package edu.kit.compiler.parser

import edu.kit.compiler.initializeKeywords
import edu.kit.compiler.lexer.Lexer
import edu.kit.compiler.lexer.StringTable
import edu.kit.compiler.utils.MjTestSuite
import edu.kit.compiler.wrapper.wrappers.validate

internal class ParserMjTestSuite : MjTestSuite("syntax", "parser") {

    override fun TestContext.execute() {
        val stringTable = StringTable(StringTable::initializeKeywords)
        val lexer = Lexer(source, stringTable)
        val parser = Parser(source, lexer.tokens())

        val program = parser.parse().validate()
        checkResult(successful = program != null)
    }
}
