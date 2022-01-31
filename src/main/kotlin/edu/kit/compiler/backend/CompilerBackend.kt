package edu.kit.compiler.backend

import edu.kit.compiler.backend.codegen.CodeGenFacade
import firm.Program
import java.nio.file.Path

class CompilerBackend(
    private val compilationUnit: String,
    private val assemblyFile: Path,
    private val executableFile: Path,
    private val useMolki: Boolean,
    private val dumpCodeGenIR: Boolean = false,
    private val dumpMolki: Boolean = false,
) : Backend {
    override fun generate() {
        val graphs = Program.getGraphs()
        val codeGenFacade = CodeGenFacade(graphs, dumpCodeGenIR = dumpCodeGenIR, dumpMolkIR = dumpMolki)
        val functions = codeGenFacade.generate()
        println(assemblyFile)
        val assembly = codeGenFacade.generateAssemblyFile(assemblyFile)
        Linker().link(assemblyFile, executableFile)
    }
}
