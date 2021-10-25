package edu.kit.compiler

import edu.kit.compiler.error.CompilerResult
import edu.kit.compiler.error.ERROR_FILE_SYSTEM
import edu.kit.compiler.lex.RingBuffer
import edu.kit.compiler.lex.StringTable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Main compiler pipeline. Stores state specific to one compilation unit and defines the strategy with which all
 * compiler phases are called.
 *
 * @param config compilation configuration parameters
 * @param inputPath input path. No sanity checks have been performed yet
 */
class Compiler(
    private val config: Config,
    private val inputPath: String
) {
    /**
     * A fixed-size threadPool for target parallelism
     */
    private lateinit var threadPool: ExecutorService

    /**
     * Run-specific instance of [StringTable] that is passed to the different phases. It will be filled by
     * side-effects, which allows lexicographic and syntactic analysis to be intertwined.
     */
    private val stringTable = StringTable()

    /**
     * Overall convenience method to fully invoke the compilation of one compile unit. All error logging has already
     * been done when this method finishes, so the returned exit code can be used to terminate the application
     * without further processing.
     *
     * @return returns 0 if the compilation completed successfully, or an appropriate exit code if an error occurred.
     */
    fun compile(): Int {
        // prepare a thread-pool for parallelization
        threadPool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors())

        val input = prepareCompilationUnit().unwrap {
            reportError(System.err)
            return ERROR_FILE_SYSTEM
        }

        // echo mode
        if (config.isEcho) {
            runBlocking {
                withContext(CoroutineScope(threadPool.asCoroutineDispatcher()).coroutineContext) {
                    var c = input.nextChar()
                    while (c != null) {
                        print(c)
                        c = input.nextChar()
                    }
                }
            }

            return 0
        }

        return 0
    }

    /**
     * Sanity-check the input parameter and then prepare a reader that can be used by the lexer to generate a token
     * stream
     */
    private fun prepareCompilationUnit(): CompilerResult<RingBuffer> {
        val inputFile = File(this.inputPath)

        try {
            if (!inputFile.exists()) {
                return CompilerResult.failure("File does not exist")
            }

            if (inputFile.isDirectory) {
                return CompilerResult.failure("Input path is not a file")
            }

            if (!inputFile.canRead()) {
                return CompilerResult.failure("Cannot read input file")
            }
        } catch (e: SecurityException) {
            return CompilerResult.failure("Access to input file denied: ${e.message}")
        }

        return CompilerResult.success(
            RingBuffer(FileInputStream(inputFile).channel)
        )
    }

    interface Config {
        val isEcho: Boolean
    }
}
