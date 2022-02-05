package edu.kit.compiler.backend

import edu.kit.compiler.CompilationException
import edu.kit.compiler.utils.Logger
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.absolutePathString

object MolkiAssembler {
    fun assemble(molkiFile: Path, assemblyFile: Path) {
        val processBuilder = ProcessBuilder(
            "molki.py",
            "-o", assemblyFile.absolutePathString(),
            "assemble",
            molkiFile.absolutePathString()
        ).inheritIO()

        Logger.debug { "Generating assembly file using molki ..." }
        try {
            val process = processBuilder.start()
            val exitCode = process.waitFor()
            if (exitCode != 0) {
                throw CompilationException("molki returned non-zero exit code $exitCode $molkiFile")
            }
            val actualAssemblyFile = assemblyFile.absolutePathString() + ".s"
            Files.move(Path.of(actualAssemblyFile), assemblyFile, StandardCopyOption.REPLACE_EXISTING)
        } catch (e: IOException) {
            throw CompilationException("failed generate assembly file: ${e.message}. Is molki.py not in \$PATH?")
        }
    }
}
