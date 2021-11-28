
package edu.kit.compiler.semantic.visitor

import edu.kit.compiler.lex.AnnotationType
import edu.kit.compiler.lex.SourceFile
import edu.kit.compiler.lex.SourceRange

/**
 * Annotate a given source file with an error if the given function evaluates to false.
 */
fun errorIfFalse(sourceFile: SourceFile, sourceRange: SourceRange, errorMsg: String, function: () -> Boolean) {
    if (!function()) {
        sourceFile.annotate(
            AnnotationType.ERROR,
            sourceRange,
            errorMsg
        )
    }
}

fun errorIfTrue(sourceFile: SourceFile, sourceRange: SourceRange, errorMsg: String, function: () -> Boolean) {
    errorIfFalse(sourceFile, sourceRange, errorMsg) { !function() }
}
