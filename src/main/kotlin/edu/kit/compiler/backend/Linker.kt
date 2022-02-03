package edu.kit.compiler.backend

import edu.kit.compiler.CompilationException
import edu.kit.compiler.utils.Logger
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

class Linker {
    fun link(assemblyFile: Path, executableFile: Path) {
        val runtimeLibFile = extractRuntimeLibraryFile()

        val processBuilder = ProcessBuilder(
            "gcc",
            "-g", // debug symbols
            "-x", "assembler", assemblyFile.toAbsolutePath().toString(),
            "-x", "c", runtimeLibFile.toAbsolutePath().toString(),
            "-o", executableFile.toAbsolutePath().toString()
        ).inheritIO()

        Logger.debug { "Linking executable using gcc ..." }
        try {
            val process = processBuilder.start()
            val exitCode = process.waitFor()
            if (exitCode != 0) {
                throw CompilationException("c compiler returned non-zero exit code")
            }
        } catch (e: IOException) {
            throw CompilationException("failed to generate executable: ${e.message}")
        }
    }

    private fun extractRuntimeLibraryFile(): Path {
        val runtime = javaClass.getResourceAsStream("/Runtime.c") ?: throw CompilationException("could not find runtime library")
        try {
            runtime.use {
                val tempFile = Files.createTempFile("compiler-runtime-lib-", ".c").apply {
                    toFile().deleteOnExit()
                }
                Files.copy(runtime, tempFile, StandardCopyOption.REPLACE_EXISTING)
                return tempFile
            }
        } catch (e: IOException) {
            throw CompilationException("failed to extract runtime library")
        }
    }
}
