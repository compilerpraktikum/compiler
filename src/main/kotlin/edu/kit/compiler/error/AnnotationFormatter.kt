package edu.kit.compiler.error

import com.github.ajalt.mordant.rendering.TextColors
import com.github.ajalt.mordant.rendering.TextStyles.bold
import edu.kit.compiler.lex.AnnotationType
import edu.kit.compiler.lex.SourceFile
import java.io.PrintStream

private const val TAB_SIZE = 4

fun String.escapeTabs(tabSize: Int = 4): String = replace("\t", " ".repeat(tabSize))

private fun PrintStream.formatLine(text: String, line: Int, column: Int, color: TextColors) {
    val lineAsString = line.toString()
    val numTabsBeforeColumn = text.asSequence().take(column - 1).count { it == '\t' }
    println("    " + TextColors.gray("$lineAsString| ") + text.escapeTabs(TAB_SIZE))
    println("    " + " ".repeat(lineAsString.length + 2 + (column - 1) + numTabsBeforeColumn * (TAB_SIZE - 1)) + (color + bold)("^"))
}

private fun formatAnnotation(sourceFile: SourceFile, annotation: SourceFile.Annotation) {
    val (out, color) = when (annotation.type) {
        AnnotationType.WARNING -> Pair(System.err, TextColors.yellow)
        AnnotationType.ERROR -> Pair(System.err, TextColors.red)
    }

    out.apply {
        println(color("[${annotation.type}] ${annotation.message}"))
        println("  in ${sourceFile.path}:${annotation.position.display()}")
        val line = sourceFile.getLine(annotation.position.line)
        if (line.length < 200) {
            formatLine(line, annotation.position.line, annotation.position.column, color)
        } else {
            val start = (annotation.position.column - 1 - 90).coerceAtLeast(0)
            val end = (annotation.position.column - 1 + 91).coerceAtMost(line.length)
            val linePart = line.substring(start, end).toString()
            formatLine("... $linePart ...", annotation.position.line, annotation.position.column + 4 - start, color)
        }
    }
}

object AnnotationFormatter {
    val DEFAULT = { source: SourceFile, annotation: SourceFile.Annotation -> formatAnnotation(source, annotation) }
}
