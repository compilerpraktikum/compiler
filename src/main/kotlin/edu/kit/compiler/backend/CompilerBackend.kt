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
) : Backend {
    override fun generate() {
        val graphs = Program.getGraphs()
        val functions = CodeGenFacade(graphs).generate()
        val assembly =
            functions.values.joinToString("\n\n") { functionBody ->
                functionBody.joinToString("\n") { it.toAssembler() }
            }
        assemblyFile.writeText(assembly)
        Linker().link(assemblyFile, executableFile)
    }
}

fun createCompilerBackend(
    compilationUnit: String,
    assemblyFile: Path,
    executableFile: Path,
) = CompilerBackend(compilationUnit, assemblyFile, executableFile, useMolki = false)

fun createCompilerBackendWithMolki(
    compilationUnit: String,
    assemblyFile: Path,
    executableFile: Path,
) = CompilerBackend(compilationUnit, assemblyFile, executableFile, useMolki = true)
