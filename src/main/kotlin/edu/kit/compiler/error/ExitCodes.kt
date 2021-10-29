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
     * Erroneous command line arguments were given, preventing compilation
     */
    const val ERROR_ARGUMENTS = 2
    
    /**
     * Tokenizing failed, because of an unexpected token, preventing compilation
     */
    const val ERROR_INVALID_TOKEN = 3
}
