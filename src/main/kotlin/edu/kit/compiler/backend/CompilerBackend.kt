package edu.kit.compiler.backend

import java.nio.file.Path

class CompilerBackend(
    private val compilationUnit: String,
    private val assemblyFile: Path,
    private val executableFile: Path,
    private val useMolki: Boolean,
) : Backend {
    override fun generate() {
        TODO("code generation")
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
