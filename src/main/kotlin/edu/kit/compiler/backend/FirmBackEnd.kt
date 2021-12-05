package edu.kit.compiler.backend

import firm.Backend
import java.nio.file.Path

class FirmBackEnd(
    private val compilationUnit: String,
    private val assemblyFile: Path,
    private val executableFile: Path,
) : CompilerBackEnd {
    override fun generate() {
        Backend.createAssembler(assemblyFile.toAbsolutePath().toString(), compilationUnit)
        Linker().link(assemblyFile, executableFile)
    }
}
