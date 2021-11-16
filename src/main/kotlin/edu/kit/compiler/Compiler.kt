package edu.kit.compiler

import edu.kit.compiler.ast.PrettyPrintVisitor
import edu.kit.compiler.ast.accept
import edu.kit.compiler.ast.validate
import edu.kit.compiler.error.AnnotationFormatter
import edu.kit.compiler.error.CompilerResult
import edu.kit.compiler.error.ExitCode
import edu.kit.compiler.lex.Lexer
import edu.kit.compiler.lex.SourceFile
import edu.kit.compiler.lex.StringTable
import edu.kit.compiler.parser.Parser
import java.io.IOException
import java.nio.charset.MalformedInputException
import java.nio.file.Path
import kotlin.io.path.inputStream

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
    @OptIn(ExperimentalStdlibApi::class)
    fun compile(): Int {
        if (config.mode == Mode.Echo) {
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

            when (config.mode) {
                Mode.Compile -> {
                    throw NotImplementedError("Compile mode not yet implemented.")
                }
                Mode.Echo -> { throw IllegalStateException("echo unhandled") }
                Mode.LexTest -> {
                    Lexer(
                        sourceFile,
                        stringTable,
                        printWarnings = false
                    ).tokens().lexTestRepr.forEach {
                        println(it)
                    }

                    sourceFile.printAnnotations(AnnotationFormatter.DEFAULT)
                    if (sourceFile.hasError) {
                        return ExitCode.ERROR_COMPILATION_FAILED
                    }
                }
                Mode.ParseTest -> {
                    val lexer = Lexer(
                        sourceFile,
                        stringTable,
                        printWarnings = false
                    )

                    val parser = Parser(sourceFile, lexer.tokens())

                    try {
                        parser.parse()
                    } catch (ex: NotImplementedError) {
                        // TODO remove once parser properly supports multiple errors and properly uses [SourceFile.annotate]
                        sourceFile.printAnnotations(AnnotationFormatter.DEFAULT)
                        System.err.println("[error] invalid program")
                        return ExitCode.ERROR_COMPILATION_FAILED
                    }

                    sourceFile.printAnnotations(AnnotationFormatter.DEFAULT)
                    if (sourceFile.hasError) {
                        return ExitCode.ERROR_COMPILATION_FAILED
                    }
                }
                Mode.PrettyPrintAst -> {
                    val tokens = Lexer(
                        sourceFile,
                        stringTable,
                        printWarnings = false
                    ).tokens()

                    try {
                        val program = Parser(sourceFile, tokens).parse().validate() ?: throw IllegalArgumentException("parsing failed")
                        program.accept(PrettyPrintVisitor(System.out))
                    } catch (ex: IllegalArgumentException) {
                        sourceFile.printAnnotations(AnnotationFormatter.DEFAULT)
                        System.err.println("[error] $ex")
                        return ExitCode.ERROR_COMPILATION_FAILED
                    } catch (ex: NotImplementedError) {
                        // TODO remove once parser properly supports multiple errors and properly uses [SourceFile.annotate]
                        sourceFile.printAnnotations(AnnotationFormatter.DEFAULT)
                        System.err.println("[error] invalid program")
                        return ExitCode.ERROR_COMPILATION_FAILED
                    }

                    sourceFile.printAnnotations(AnnotationFormatter.DEFAULT)
                    if (sourceFile.hasError) {
                        return ExitCode.ERROR_COMPILATION_FAILED
                    }
                }
            }
        } catch (e: Exception) {
            System.err.println("Internal error: ${e.message}")
            e.printStackTrace(System.err)
            return ExitCode.ERROR_INTERNAL
        }

        return ExitCode.SUCCESS
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

    enum class Mode {
        Compile, Echo, LexTest, ParseTest, PrettyPrintAst
    }

    interface Config {
        val sourceFile: Path
        val mode: Mode
    }
}
