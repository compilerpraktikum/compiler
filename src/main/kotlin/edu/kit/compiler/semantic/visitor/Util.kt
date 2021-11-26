
package edu.kit.compiler.semantic.visitor

import edu.kit.compiler.lex.AnnotationType
import edu.kit.compiler.lex.SourceFile
import edu.kit.compiler.lex.SourceRange

/**
 * Function name alias for [run], to make code more expressive. We can call [kotlin.collections.MutableMap.putIfAbsent]
 * to add symbols to a namespace and then use `?.onError` instead of `?.run` to handle errors (as putIfAbsent should
 * return `null`, if no such symbol exists yet).
 */
fun <T> T.onError(block: T.() -> Unit) = run(block)
/**
 * Annotate a given source file with an error if the given function evaluates to false.
 */
fun checkAndAnnotateSourceFileIfNot(sourceFile: SourceFile, sourceRange: SourceRange, errorMsg: String, function: () -> kotlin.Boolean) {
    if (!function()) {
        sourceFile.annotate(
            AnnotationType.ERROR,
            sourceRange,
            errorMsg
        )
    }
}
