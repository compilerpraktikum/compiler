package edu.kit.compiler.backend

import edu.kit.compiler.ast.validate
import edu.kit.compiler.backend.codegen.FirmToCodeGenTranslator
import edu.kit.compiler.initializeKeywords
import edu.kit.compiler.lexer.Lexer
import edu.kit.compiler.lexer.StringTable
import edu.kit.compiler.source.SourceFile
import edu.kit.compiler.parser.Parser
import edu.kit.compiler.semantic.doSemanticAnalysis
import edu.kit.compiler.transform.Transformation
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
            val generator = FirmToCodeGenTranslator(it)
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

    @Test
    fun testCall() {
        setupGraph(
            """
                class Test {
                    public static void main(String[] args) {}
                    public int foo(int x) { return 2+x; }
                    public int bar(int y, int z) { return foo(3+y)+; }
                }
            """.trimIndent()
        )
    }
}
