package edu.kit.compiler

import edu.kit.compiler.ast.validate
import edu.kit.compiler.backend.Backend
import edu.kit.compiler.backend.FirmBackend
import edu.kit.compiler.backend.createCompilerBackend
import edu.kit.compiler.backend.createCompilerBackendWithMolki
import edu.kit.compiler.error.CompilerResult
import edu.kit.compiler.error.ExitCode
import edu.kit.compiler.lexer.Lexer
import edu.kit.compiler.lexer.StringTable
import edu.kit.compiler.optimization.Optimization
import edu.kit.compiler.parser.Parser
import edu.kit.compiler.semantic.doSemanticAnalysis
import edu.kit.compiler.semantic.visitor.PrettyPrintVisitor
import edu.kit.compiler.semantic.visitor.accept
import edu.kit.compiler.source.SourceFile
import edu.kit.compiler.transform.Transformation
import firm.Dump.dumpGraph
import firm.Program
import firm.Util
import java.io.IOException
import java.nio.charset.MalformedInputException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.inputStream
import kotlin.io.path.nameWithoutExtension

class CompilationException(
    message: String? = null
) : Exception(message)

private fun SourceFile.assertHasErrors() {
    if (!hasError) {
        throw IllegalStateException("source file should have error(s)")
    }
}

private fun Path.replaceExtension(newExtension: String): String = nameWithoutExtension + newExtension

/**
 * Main compiler pipeline. Stores state specific to one compilation unit and defines the strategy with which all
 * compiler phases are called.
 *
 * @param config compilation configuration parameters
 */
class Compiler(private val config: Config) {

    /**
     * Run-specific instance of [StringTable] that is passed to the different phases. It will be filled by
     * side-effects, which allows lexicographic and syntactic analysis to be intertwined.
     */
    private val stringTable = StringTable(StringTable::initializeKeywords)

    /**
     * Overall convenience method to fully invoke the compilation of one compile unit. All error logging has already
     * been done when this method finishes, so the returned exit code can be used to terminate the application
     * without further processing.
     *
     * @return returns 0 if the compilation completed successfully, or an appropriate exit code if an error occurred.
     */
    fun compile(): Int {
        val mode = config.mode
        if (mode == Mode.Echo) {
            // needs to be handled separately because SourceFile does only support valid input encodings and
            // echo needs to support binary files (for whatever reason...)
            return handleEcho()
        }

        try {
            // open the input file
            val sourceFile = openCompilationUnit().unwrap {
                reportError()
                return ExitCode.ERROR_FILE_SYSTEM
            }

            @Suppress("KotlinConstantConditions")
            when (mode) {
                Mode.Echo -> { throw IllegalStateException("echo unhandled") }
                Mode.LexTest -> {
                    val lexer = Lexer(
                        sourceFile,
                        stringTable,
                    )

                    lexer.tokens().lexTestRepr.forEach {
                        println(it)
                    }
                }
                Mode.ParseTest -> {
                    val lexer = Lexer(
                        sourceFile,
                        stringTable,
                    )
                    val parser = Parser(sourceFile, lexer.tokens())

                    parser.parse()
                }
                Mode.PrettyPrintAst -> {
                    val lexer = Lexer(
                        sourceFile,
                        stringTable,
                    )
                    val parser = Parser(sourceFile, lexer.tokens())

                    run {
                        val program = parser.parse().validate() ?: return@run sourceFile.assertHasErrors()
                        program.accept(PrettyPrintVisitor(System.out))
                    }
                }
                Mode.SemanticCheck -> {
                    val lexer = Lexer(
                        sourceFile,
                        stringTable,
                    )
                    val parser = Parser(sourceFile, lexer.tokens())

                    run {
                        val program = parser.parse().validate() ?: return@run sourceFile.assertHasErrors()
                        doSemanticAnalysis(program, sourceFile, stringTable)
                    }
                }
                Mode.CompileFirm, Mode.CompileMolki, Mode.Compile -> {
                    val lexer = Lexer(
                        sourceFile,
                        stringTable,
                    )
                    val parser = Parser(sourceFile, lexer.tokens())

                    run {
                        val program = parser.parse().validate() ?: return@run sourceFile.assertHasErrors()
                        doSemanticAnalysis(program, sourceFile, stringTable)
                        if (sourceFile.hasError) {
                            return@run
                        }

                        Transformation.transform(program)
                        dumpGraphsIfEnabled(Dump.MethodGraphsAfterConstruction, "after-construction")
                        Util.lowerSels()
                        dumpGraphsIfEnabled(Dump.MethodGraphsAfterLowering, "after-lowering")

                        Optimization.constantPropagationAndFolding()
                        dumpGraphsIfEnabled(Dump.MethodGraphsAfterOptimization, "after-optimization")

                        runBackEnd(
                            when (mode) {
                                Mode.CompileFirm -> ::FirmBackend
                                Mode.CompileMolki -> ::createCompilerBackendWithMolki
                                Mode.Compile -> ::createCompilerBackend
                                else -> { throw IllegalStateException("unknown mode") }
                            }
                        )
                    }
                }
            }

            sourceFile.printAnnotations()
            return if (sourceFile.hasError) {
                ExitCode.ERROR_COMPILATION_FAILED
            } else {
                ExitCode.SUCCESS
            }
        } catch (e: CompilationException) {
            System.err.println("error: ${e.message}")
            return ExitCode.ERROR_COMPILATION_FAILED
        } catch (e: Exception) {
            System.err.println("internal error: ${e.message}")
            e.printStackTrace(System.err)
            return ExitCode.ERROR_INTERNAL
        }
    }

    private fun dumpGraphsIfEnabled(flag: Dump, phase: String) {
        if (config.dump.contains(flag)) {
            Program.getGraphs().forEach { dumpGraph(it, phase) }
        }
    }

    /**
     * Sanity-check the input parameter and then prepare a reader that can be used by the lexer to generate a token
     * stream
     */
    private fun openCompilationUnit(): CompilerResult<SourceFile> {
        val sourceFile = config.sourceFile

        return try {
            CompilerResult.success(
                SourceFile.from(sourceFile)
            )
        } catch (e: SecurityException) {
            return CompilerResult.failure("error: access to source file denied: ${e.message}")
        } catch (e: IOException) {
            return CompilerResult.failure("error: failed to read source file: ${e.message}")
        } catch (e: OutOfMemoryError) {
            return CompilerResult.failure("error: files larger than 2GB are not supported")
        } catch (e: MalformedInputException) {
            return CompilerResult.failure("error: invalid source file encoding: ${e.message}")
        }
    }

    private fun runBackEnd(factory: (String, Path, Path) -> Backend) {
        val sourceFileName = config.sourceFile.fileName

        val assemblyFile = if (config.dump.contains(Dump.AssemblyFile)) {
            Paths.get(sourceFileName.replaceExtension(".asm"))
        } else {
            Files.createTempFile("$sourceFileName-", ".asm").apply {
                toFile().deleteOnExit()
            }
        }

        val executableFile = config.outputFile ?: Paths.get(sourceFileName.replaceExtension(""))
        if (executableFile.toAbsolutePath().normalize() == config.sourceFile.toAbsolutePath().normalize()) {
            throw CompilationException("output file would overwrite source file. please specify a different output file using `--out`.")
        }

        val backend = factory(sourceFileName.toString(), assemblyFile, executableFile)
        backend.generate()
    }

    private fun handleEcho(): Int {
        val sourceFile = config.sourceFile

        try {
            sourceFile.inputStream().transferTo(System.out)
            return ExitCode.SUCCESS
        } catch (e: SecurityException) {
            System.err.println("error: access to source file denied: ${e.message}")
        } catch (e: IOException) {
            System.err.println("error: failed to read source file: ${e.message}")
        }

        return ExitCode.ERROR_FILE_SYSTEM
    }

    enum class Mode(val cliFlag: String) {
        Echo("echo"),
        LexTest("lextest"),
        ParseTest("parsetest"),
        PrettyPrintAst("print-ast"),
        SemanticCheck("check"),
        CompileFirm("compile-firm"),
        CompileMolki("compile-molki"),
        Compile("compile"),
    }

    enum class Dump(val cliFlag: String) {
        AssemblyFile("asm"),
        MethodGraphsAfterConstruction("graph:construction"),
        MethodGraphsAfterLowering("graph:lowering"),
        MethodGraphsAfterOptimization("graph:optimization");

        override fun toString(): String = cliFlag
    }

    interface Config {
        val mode: Mode
        val sourceFile: Path
        val outputFile: Path?
        val dump: Set<Dump>
    }
}
