package edu.kit.compiler.exec

import edu.kit.compiler.Compiler
import edu.kit.compiler.ast.validate
import edu.kit.compiler.error.ExitCode
import edu.kit.compiler.semantic.doSemanticAnalysis
import edu.kit.compiler.utils.MjTestSuite
import edu.kit.compiler.utils.createParser
import edu.kit.compiler.utils.normalizeLineEndings
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.io.path.deleteExisting
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.nameWithoutExtension
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private fun ByteArray.display() = joinToString(separator = ", ", prefix = "[", postfix = "]")

private fun InputStream.normalizeLineEndings(): InputStream = readAllBytes()
    .toString(Charsets.US_ASCII)
    .normalizeLineEndings()
    .toByteArray(Charsets.US_ASCII)
    .inputStream()

@EnabledOnOs(OS.LINUX) // current version of jFirm / libfirm works only on linux
internal class ExecMjTestSuite : MjTestSuite("exec") {
    private lateinit var runnerDir: Path
    private lateinit var ioThreadPool: ExecutorService

    @BeforeAll
    fun setup() {
        val projectRoot = Paths.get("")
        runnerDir = projectRoot.resolve("test-run")
        Files.createDirectories(runnerDir)
        Files.newDirectoryStream(runnerDir).forEach {
            it.deleteExisting()
        }

        ioThreadPool = Executors.newFixedThreadPool(2) // 1 for input, 1 for output
    }

    override fun TestContext.execute() {
        val (parser, _, stringTable) = createParser(source)
        val program = parser.parse().validate()
        checkStep(!source.hasError && program != null)

        doSemanticAnalysis(program, source, stringTable)
        checkStep(!source.hasError)

        val executableFile = runnerDir.resolve(testCase.path.fileName.nameWithoutExtension)
        val successful = runCompileProcess(testCase.path, executableFile)
        checkStep(successful)

        val testCaseDir = testCase.path.parent
        val fileName = testCase.path.fileName.toString()

        fun checkExecution(inputFile: Path?, outputFileBase: String) {
            val result = executeProgram(executableFile, inputFile = inputFile)
            assertTrue("expected success, but got ${result.display()}") {
                result is ExecutionResult.Success
            }
            result as ExecutionResult.Success

            val expectedOutputFile = testCaseDir.resolve("$outputFileBase.out")
            if (!expectedOutputFile.exists()) {
                return
            }

            val expected = Files.readAllBytes(expectedOutputFile).toString(Charsets.US_ASCII).trim().normalizeLineEndings()
            val actual = result.output.toString(Charsets.US_ASCII).trim()

            if (expected != actual) {
                fail(
                    """
                    error: output does not match
                    expected: <$expected>
                      actual: <$actual>
                    expected bytes: ${expected.toByteArray(Charsets.US_ASCII).display()}
                      actual bytes: ${actual.toByteArray(Charsets.US_ASCII).display()}
                    """.trimIndent()
                )
            }
        }

        when (val mode = Regex(".*\\.([^.]+)\\.(java|mj)").matchEntire(fileName)?.let { it.groupValues[1] }) {
            "inf" -> {
                val result = executeProgram(executableFile, timeout = 10.seconds)
                assertTrue("expected timeout, but got $result") {
                    result is ExecutionResult.Failure && result.type == ExecutionResult.Failure.Type.Timeout
                }
            }
            "input" -> {
                val testName = fileName.removeSuffix(".input.java").removeSuffix(".input.mj")
                testCaseDir.listDirectoryEntries("$testName.*.inputc").forEach { inputFile ->
                    println("[input] ${inputFile.fileName}")
                    checkExecution(
                        inputFile,
                        inputFile.fileName.toString(),
                    )
                }
            }
            null -> {
                checkExecution(
                    null,
                    fileName
                )
            }
            else -> throw Exception("unknown mode \"$mode\"")
        }
    }

    private fun runCompileProcess(input: Path, output: Path): Boolean {
        val jarFile = Paths.get("out", "libs", "compiler-all.jar")
        val process = ProcessBuilder(
            "java",
            "-jar", jarFile.toAbsolutePath().toString(),
            "--" + Compiler.Mode.CompileFirm.cliFlag,
            "--out", output.toAbsolutePath().toString(),
            input.toAbsolutePath().toString()
        ).start()

        process.inputStream.transferTo(System.out)
        process.errorStream.transferTo(System.err)

        val result = process.waitFor()
        return result == ExitCode.SUCCESS
    }

    private sealed class ExecutionResult {
        abstract fun display(): String

        class Success(val output: ByteArray) : ExecutionResult() {
            override fun display(): String = "Success"
        }
        class Failure(val type: Type) : ExecutionResult() {
            enum class Type {
                ExitCode,
                Timeout,
            }

            override fun display(): String = "Failure($type)"
        }
    }

    private fun executeProgram(executableFile: Path, inputFile: Path? = null, timeout: Duration = 30.seconds): ExecutionResult {
        val process = ProcessBuilder(executableFile.toAbsolutePath().toString()).start()

        val inputFuture = inputFile?.let {
            CompletableFuture.runAsync({
                try {
                    inputFile.inputStream().normalizeLineEndings().transferTo(process.outputStream)
                    process.outputStream.close()
                } catch (e: IOException) {
                    if (e.message == "Broken pipe" || e.message == "Stream closed") {
                        // stream closed before end of input -> fine as long as the output matches
                    } else {
                        throw e
                    }
                }
            }, ioThreadPool)
        }

        var output: ByteArray? = null
        val outputFuture = CompletableFuture.runAsync({
            output = process.inputStream.readAllBytes()
        }, ioThreadPool)

        val exitedBeforeTimeout = process.waitFor(timeout.inWholeSeconds, TimeUnit.SECONDS)
        if (!exitedBeforeTimeout) {
            process.destroyForcibly() // also closes input/output streams and thereby terminates the threads
            inputFuture?.join()
            outputFuture.join()
            return ExecutionResult.Failure(ExecutionResult.Failure.Type.Timeout)
        }

        inputFuture?.join()
        outputFuture.join()

        val exitCode = process.exitValue()
        if (exitCode != 0) {
            return ExecutionResult.Failure(ExecutionResult.Failure.Type.ExitCode)
        }

        return ExecutionResult.Success(output!!)
    }
}
