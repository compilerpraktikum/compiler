package edu.kit.compiler.backend

import java.nio.file.Path

class FirmBackend(
    private val compilationUnit: String,
    private val assemblyFile: Path,
    private val executableFile: Path,
) : Backend {
    override fun generate() {
        fixJavaCompatibility()
        firm.Backend.createAssembler(assemblyFile.toAbsolutePath().toString(), compilationUnit)
        Linker().link(assemblyFile, executableFile)
    }
}
