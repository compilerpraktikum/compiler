package edu.kit.compiler.backend

import edu.kit.compiler.backend.codegen.CodeGenFacade
import edu.kit.compiler.backend.codegen.CodeGenIR
import firm.Program
import java.nio.file.Path
import kotlin.io.path.writeText

class CompilerBackend(
    private val compilationUnit: String,
    private val assemblyFile: Path,
    private val executableFile: Path,
    private val useMolki: Boolean,
    private val dumpMolki: Boolean = false,
    private val dumpCodeGenIR: Boolean = false,
) : Backend {
    override fun generate() {
        val graphs = Program.getGraphs()
        val codeGenFacade = CodeGenFacade(graphs)
        val functions = codeGenFacade.generate()
        println(assemblyFile)
        val assembly = codeGenFacade.generateAssemblyFile(assemblyFile)
        Linker().link(assemblyFile, executableFile)
    }
}
