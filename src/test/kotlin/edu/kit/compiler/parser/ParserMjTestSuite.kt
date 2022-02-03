package edu.kit.compiler.parser

import edu.kit.compiler.ast.validate
import edu.kit.compiler.utils.MjTestSuite
import edu.kit.compiler.utils.createParser

internal class ParserMjTestSuite : MjTestSuite("syntax", "parser") {

    override fun TestContext.execute() {
        val (parser) = createParser(source)

        val program = parser.parse().validate()
        checkResult(successful = program != null)
    }
}
