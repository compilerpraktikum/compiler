package edu.kit.compiler.backend

import edu.kit.compiler.backend.codegen.FirmGraphToCodeGenTreeGraphGenerator
import edu.kit.compiler.initializeKeywords
import edu.kit.compiler.lex.Lexer
import edu.kit.compiler.lex.SourceFile
import edu.kit.compiler.lex.StringTable
import edu.kit.compiler.optimization.doConstantPropagationAndFolding
import edu.kit.compiler.parser.Parser
import edu.kit.compiler.semantic.doSemanticAnalysis
import edu.kit.compiler.transform.Transformation
import edu.kit.compiler.wrapper.wrappers.validate
import firm.Dump
import firm.Program
import firm.Util
import org.junit.jupiter.api.Test

class CodeGenTest {

    private fun dumpGraphs(phase: String) {
        Program.getGraphs().forEach { Dump.dumpGraph(it, phase) }
    }

    fun setupGraph(code: String) {
        val sourceFile = SourceFile.from("/path/to/file", code)
        val stringTable = StringTable(StringTable::initializeKeywords)
        val lexer = Lexer(sourceFile, stringTable)
        val parser = Parser(sourceFile, lexer.tokens())
        val program = parser.parse().also {
            sourceFile.printAnnotations()
        }.validate()!!
        doSemanticAnalysis(program, sourceFile, stringTable)
        Transformation.transform(program)
        dumpGraphs("after-construction")
        Util.lowerSels()
        dumpGraphs("after-lowering")
        Program.getGraphs().forEach {
            val generator = FirmGraphToCodeGenTreeGraphGenerator(it)
            val blocks = generator.buildTrees()
        }
    }

    @Test
    fun testClausesNPE() {
        setupGraph("class clazz { public static void main(String[] args) {test();} public int test() {return 23+3; } }")
    }

    @Test
    fun testBasic() {
        setupGraph(
            """
            class Test {
                public static void main(String[] args) {System.out.println(1);}

                public int test() { int b = 1; int c = 1 + b; return c;}
            }
            """.trimIndent()
        )
    }
}
