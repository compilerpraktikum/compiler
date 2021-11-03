package edu.kit.compiler

import edu.kit.compiler.error.CompilerResult
import edu.kit.compiler.error.ExitCode
import edu.kit.compiler.lex.BufferedInputProvider
import edu.kit.compiler.lex.InputProvider
import edu.kit.compiler.lex.Lexer
import edu.kit.compiler.lex.StringTable
import edu.kit.compiler.lex.SyncInputProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Main compiler pipeline. Stores state specific to one compilation unit and defines the strategy with which all
 * compiler phases are called.
 *
 * @param config compilation configuration parameters
 */
class Compiler(private val config: Config) {
    /**
     * A fixed-size threadPool for target parallelism
     */
    private var threadPool: ExecutorService? = null

    /**
     * Run-specific instance of [StringTable] that is passed to the different phases. It will be filled by
     * side-effects, which allows lexicographic and syntactic analysis to be intertwined.
     */
    private val stringTable = StringTable().apply {
        initializeKeywords()
    }

    /**
     * Overall convenience method to fully invoke the compilation of one compile unit. All error logging has already
     * been done when this method finishes, so the returned exit code can be used to terminate the application
     * without further processing.
     *
     * @return returns 0 if the compilation completed successfully, or an appropriate exit code if an error occurred.
     */
    fun compile(): Int {
        try {
            // prepare a thread-pool for parallelization
            threadPool = when (config.parallelization) {
                0u -> Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors())
                1u -> Executors.newSingleThreadExecutor()
                else -> Executors.newFixedThreadPool(config.parallelization.toInt())
            }

            // open the input file
            val input = openCompilationUnit().unwrap {
                reportError(System.err)
                return ExitCode.ERROR_FILE_SYSTEM
            }

            return when (config.mode) {
                Mode.Compile -> {
                    throw NotImplementedError("Compile mode not yet implemented.")
                }
                Mode.Echo -> runBlocking {
                    withContext(CoroutineScope(threadPool!!.asCoroutineDispatcher()).coroutineContext) {
                        try {
                            var c = input.next()
                            while (c != InputProvider.END_OF_FILE) {
                                print(c)
                                c = input.next()
                            }
                            return@withContext ExitCode.SUCCESS
                        } catch (e: IOException) {
                            System.err.println("Unexpected IOException: ${e.message}")
                            return@withContext ExitCode.ERROR_FILE_SYSTEM
                        }
                    }
                }
                Mode.LexTest -> runBlocking {
                    var hasInvalidToken = false

                    Lexer(
                        config.sourceFile.absolutePath,
                        input,
                        stringTable,
                        printWarnings = false
                    ).tokens().onEach {
                        hasInvalidToken = hasInvalidToken || (it is Token.ErrorToken)
                    }.lexTestRepr.collect {
                        println(it)
                    }

                    if (hasInvalidToken) {
                        ExitCode.ERROR_INVALID_TOKEN
                    } else {
                        ExitCode.SUCCESS
                    }
                }
            }
        } finally {
            threadPool?.shutdown()
        }
    }

    /**
     * Sanity-check the input parameter and then prepare a reader that can be used by the lexer to generate a token
     * stream
     */
    private fun openCompilationUnit(): CompilerResult<InputProvider> {
        val sourceFile = config.sourceFile

        try {
            if (!sourceFile.exists()) {
                return CompilerResult.failure("File does not exist")
            }

            if (sourceFile.isDirectory) {
                return CompilerResult.failure("Input path is not a file")
            }

            if (!sourceFile.canRead()) {
                return CompilerResult.failure("Cannot read input file")
            }
        } catch (e: SecurityException) {
            return CompilerResult.failure("Access to input file denied: ${e.message}")
        }

        return CompilerResult.success(
            FileInputStream(sourceFile).let {
                if (config.async) {
                    BufferedInputProvider(it)
                } else {
                    SyncInputProvider(it)
                }
            }
        )
    }

    enum class Mode {
        Compile, Echo, LexTest,
    }

    interface Config {
        val sourceFile: File
        val mode: Mode
        val parallelization: UInt
        val async: Boolean
    }
}
