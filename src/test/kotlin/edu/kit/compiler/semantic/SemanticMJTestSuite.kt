package edu.kit.compiler.semantic

import edu.kit.compiler.ast.validate
import edu.kit.compiler.utils.MjTestSuite
import edu.kit.compiler.utils.createParser

internal class SemanticMJTestSuite : MjTestSuite("semantic") {

    override fun TestContext.execute() {
        val (parser, _, stringTable) = createParser(source)

        val program = parser.parse().validate()
        checkStep(successful = program != null)

        doSemanticAnalysis(program, source, stringTable)
        checkResult(successful = !source.hasError)
    }
}
