package edu.kit.compiler.error

object ExitCode {
    /**
     * No error occurred
     */
    const val SUCCESS = 0

    /**
     * An error in the file system occurred and prevented compilation
     */
    const val ERROR_FILE_SYSTEM = 1

    /**
     * Error during compilation. For details see output on stderr.
     */
    const val ERROR_COMPILATION_FAILED = 2

    /**
     * Unspecified internal error.
     */
    const val ERROR_INTERNAL = 127
}
