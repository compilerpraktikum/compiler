package edu.kit.compiler.backend.assembly

import edu.kit.compiler.backend.register.PlatformInstruction
import java.io.File
import java.nio.file.Path

class AssemblyGenerator(output: Path) : AutoCloseable {

    private enum class Segment(val switch: String) {
        TEXT(".text")
    }

    private val outputFile = output.toFile().also(File::createNewFile)
    private val outputWriter = outputFile.outputStream().bufferedWriter()

    /**
     * Current segment
     */
    private var currentSegment: Segment = Segment.TEXT
        set(value) {
            if (field != value) {
                appendLine(value.switch)
                field = value
            }
        }

    init {
        // enter text mode initially
        appendLine(currentSegment.switch)
    }

    fun generateFunction(functionName: String, functionBody: List<PlatformInstruction>) {
        // make sure we are in correct segment
        currentSegment = Segment.TEXT

        // some black sorcery
        appendLine(".p2align 4,,15")
        appendLine(".globl $functionName")
        appendLine(".type $functionName, @function")

        // append function body
        appendLine("$functionName:")
        functionBody.map(PlatformInstruction::toAssembler).forEach(::appendLine)

        // calculate function size
        appendLine(".size $functionName, .-$functionName")
    }

    /**
     * Append a line to the assembly file
     */
    private fun appendLine(line: String) {
        outputWriter.write(line)
        outputWriter.newLine()
    }

    override fun close() {
        outputWriter.close()
    }
}
