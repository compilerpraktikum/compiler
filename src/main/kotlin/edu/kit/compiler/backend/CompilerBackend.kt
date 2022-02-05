package edu.kit.compiler.backend

import edu.kit.compiler.Compiler
import edu.kit.compiler.backend.codegen.CodeGenFacade
import edu.kit.compiler.utils.Logger
import firm.Program
import java.io.File
import java.nio.file.Path

class CompilerBackend(
    private val compilationUnit: String,
    private val assemblyFile: Path,
    private val executableFile: Path,
    private val useMolki: Boolean,
    private val optimizationLevel: Compiler.OptimizationLevel,
    private val dumpCodeGenIR: Boolean = false,
    private val dumpMolki: Boolean = false,
) : Backend {
    override fun generate() {
        val graphs = Program.getGraphs()
        val codeGenFacade = CodeGenFacade(graphs, optimizationLevel = optimizationLevel, dumpCodeGenIR = dumpCodeGenIR, dumpMolkIR = dumpMolki)
        codeGenFacade.generate()
        Logger.debug { "Assembly file: $assemblyFile" }
        if (useMolki) {
            Logger.warning { "The generated molki code is not entirely compatible with molki.py. Expect errors." }
            val molkiFile = File.createTempFile("out", ".molki")
            Logger.debug { "Molki file: $molkiFile" }
            codeGenFacade.generateMolkiFile(molkiFile)
            MolkiAssembler.assemble(molkiFile.toPath(), assemblyFile)
        } else {
            codeGenFacade.generateAssemblyFile(assemblyFile)
        }
        Linker().link(assemblyFile, executableFile)
    }
}
