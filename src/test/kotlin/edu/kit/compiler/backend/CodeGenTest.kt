package edu.kit.compiler.backend

import com.tylerthrailkill.helpers.prettyprint.pp
import edu.kit.compiler.Compiler
import edu.kit.compiler.ast.validate
import edu.kit.compiler.backend.codegen.BinaryOpType
import edu.kit.compiler.backend.codegen.CodeGenFacade
import edu.kit.compiler.backend.codegen.CodeGenIR
import edu.kit.compiler.backend.codegen.InstructionSelector
import edu.kit.compiler.backend.codegen.VirtualRegisterTable
import edu.kit.compiler.backend.molkir.Instruction
import edu.kit.compiler.backend.molkir.MolkIR
import edu.kit.compiler.backend.molkir.Register
import edu.kit.compiler.backend.molkir.Width
import edu.kit.compiler.lexer.Lexer
import edu.kit.compiler.lexer.StringTable
import edu.kit.compiler.lexer.initializeKeywords
import edu.kit.compiler.optimization.doOptimization
import edu.kit.compiler.parser.Parser
import edu.kit.compiler.semantic.doSemanticAnalysis
import edu.kit.compiler.source.SourceFile
import edu.kit.compiler.transform.Transformation
import firm.Dump
import firm.Graph
import firm.Program
import firm.Util
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.Ignore
import kotlin.test.assertEquals

class CodeGenTest {

    private fun dumpGraphs(phase: String) {
        Program.getGraphs().forEach { Dump.dumpGraph(it, phase) }
    }

    private fun generateGraph(
        code: String,
        registerTable: VirtualRegisterTable = VirtualRegisterTable()
    ): MutableIterable<Graph> {
        val sourceFile = SourceFile.from("/path/to/file", code)
        val stringTable = StringTable(StringTable::initializeKeywords)
        val lexer = Lexer(sourceFile, stringTable)
        val parser = Parser(sourceFile, lexer.tokens())
        val program = parser.parse().also {
            sourceFile.printAnnotations()
        }.validate()!!
        doSemanticAnalysis(program, sourceFile, stringTable)
        Transformation.transform(program, stringTable)
        dumpGraphs("after-construction")
        Util.lowerSels()
        dumpGraphs("after-lowering")
        doOptimization(Compiler.OptimizationLevel.Base, false)
        return Program.getGraphs()!!
    }

    private fun setupGraph(
        code: String,
        registerTable: VirtualRegisterTable = VirtualRegisterTable()
    ): CodeGenFacade {
        val graphs = generateGraph(code, registerTable)
        val codegen = CodeGenFacade(graphs, Compiler.OptimizationLevel.Base, dumpCodeGenIR = true, dumpMolkIR = true)
        codegen.generateCodeGenIR()
        return codegen
    }

    @Test
    @Ignore
    fun integrationTest() {
        val facade = setupGraph(
            """
                class Reader {
                    public static void main(String[] args) {
                        int[] numbers = new int[1];
                        numbers[0] = 2;
                        System.out.println(numbers[0]);
                    }
                }
            """.trimIndent()
        )
        facade.generateMolkiIr()
        facade.generateBlockLayout()
        facade.generatePlatformCode()
        val assemblyFile = File.createTempFile("out", ".asm").toPath()
        val executableFile = File.createTempFile("executable", ".out").toPath()
        facade.generateAssemblyFile(assemblyFile)
        Linker().link(assemblyFile, executableFile)
    }

    private fun transformToMolki(
        registerTable: VirtualRegisterTable = VirtualRegisterTable(),
        block: (VirtualRegisterTable) -> CodeGenIR
    ): List<Instruction> {
        val instructions = InstructionSelector(Compiler.OptimizationLevel.Base).transform(block(registerTable), registerTable)
        println("molki output:")
        instructions.forEach { it.toMolki().pp() }
        return instructions
    }

    private fun assertMolkiEquals(actual: List<MolkIR>, expected: List<String>) {
        assertEquals(expected, actual.filter { it !is Instruction.Comment }.map { it.toMolki() })
    }

    @Test
    fun testReplacementSystemAdd() {
        val res = transformToMolki {
            CodeGenIR.BinaryOp(BinaryOpType.ADD, CodeGenIR.Const("2", Width.QUAD), CodeGenIR.Const("3", Width.QUAD))
        }
        assertMolkiEquals(
            res,
            listOf(
                "addq [ $2 | $3 ] -> %@2"
            )
        )
    }

    @Test
    fun testNestedAdd() {

        val res = transformToMolki {
            CodeGenIR.BinaryOp(
                BinaryOpType.ADD,
                CodeGenIR.Const("22", Width.QUAD),
                CodeGenIR.BinaryOp(
                    BinaryOpType.ADD,
                    CodeGenIR.Const("33", Width.QUAD),
                    CodeGenIR.Const("44", Width.QUAD)
                )s
            )
        }
        assertMolkiEquals(
            res,
            listOf(
                "addq [ $33 | $44 ] -> %@3",
                "addq [ $22 | %@3 ] -> %@4",
            )
        )
    }

    @Test
    fun testSeq() {
        val res = transformToMolki {
            CodeGenIR.Seq(
                first = CodeGenIR.Const("0", Width.DOUBLE),
                second = CodeGenIR.Const("0", Width.WORD)
            )
        }
        assertMolkiEquals(
            res,
            listOf(
                "movl $0, %@1d",
                "movw $0, %@0w"
            )
        )
    }

    @Test
    fun testReplacementReturnReg() {
        val res = transformToMolki {
            CodeGenIR.Seq(
                first = CodeGenIR.Return(CodeGenIR.RegisterRef(it.newRegister(Width.QUAD))),
                second = CodeGenIR.Const("0", Width.WORD)
            )
        }
        assertMolkiEquals(
            res,
            listOf(
                "movq %@0, %@r0",
                "movw $0, %@1w"
            )
        )
    }

    @Test
    fun testReplacementReturnConst() {
        val res = transformToMolki {
            CodeGenIR.Return(CodeGenIR.Const("0", Width.WORD))
        }
        assertMolkiEquals(
            res,
            listOf(
                "movw $0, %@r0w",
            )
        )
    }

    @Test
    fun testReplacementReturnSum() {
        val res = transformToMolki {
            CodeGenIR.Return(
                CodeGenIR.BinaryOp(
                    BinaryOpType.ADD,
                    left = CodeGenIR.Const("7", Width.WORD),
                    right = CodeGenIR.RegisterRef(it.newRegister(Width.WORD))
                )
            )
        }
        assertMolkiEquals(
            res,
            listOf(
                "addw [ $7 | %@0w ] -> %@2w",
                "movw %@2w, %@r0w"
            )
        )
    }

    @Test
    fun testReplacementSystem() {
        val res = transformToMolki {
            CodeGenIR.BinaryOp(
                BinaryOpType.ADD,
                CodeGenIR.Const("2", Width.QUAD),
                CodeGenIR.RegisterRef(
                    Register(
                        id = it.newRegister(Width.QUAD).id,
                        width = Width.QUAD
                    )
                )
            )
        }
        assertMolkiEquals(
            res,
            listOf(
                "addq [ $2 | %@0 ] -> %@2",
            )
        )
    }

    @Test
    @Ignore
    fun testWithPlatform() {
        val graph = generateGraph("class Test { public static void main(String[] args) {} }")
        val codeGenFacade = CodeGenFacade(graph, Compiler.OptimizationLevel.Base, dumpCodeGenIR = true, dumpMolkIR = true)
        val platformCodes = codeGenFacade.generate()
        platformCodes.forEach { (graph, instructions) ->
            println("graph: ${graph.entity.ldName}")
            codeGenFacade.blocksWithLayout[graph]!!.flatten().map { it.toMolki() }.pp()
            println(instructions.joinToString("\n   ") { it.toAssembler() })
        }
    }
}
