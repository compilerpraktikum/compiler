package edu.kit.compiler.error

import com.github.ajalt.mordant.rendering.TextColors
import com.github.ajalt.mordant.rendering.TextStyles.bold
import edu.kit.compiler.lex.AnnotationType
import edu.kit.compiler.lex.SourceFile

private fun formatAnnotation(sourceFile: SourceFile, annotation: SourceFile.Annotation) {
    val (out, color) = when (annotation.type) {
        AnnotationType.WARNING -> Pair(System.out, TextColors.yellow)
        AnnotationType.ERROR -> Pair(System.err, TextColors.red)
    }

    out.apply {
        println(color("[${annotation.type}] ${annotation.message}"))
        println("  in ${sourceFile.path}:${annotation.position.display()}")
        val line = sourceFile.getLine(annotation.position.line)
        if (line.length < 200) { // TODO handle very long lines
            val lineAsString = annotation.position.line.toString()
            println("    $lineAsString| $line")
            println("    " + " ".repeat(lineAsString.length + 2 + annotation.position.column - 1) + (color + bold)("^"))
            println()
        }
    }
}

object AnnotationFormatter {
    val DEFAULT = { source: SourceFile, annotation: SourceFile.Annotation -> formatAnnotation(source, annotation) }
}
