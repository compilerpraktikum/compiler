package edu.kit.compiler.error

import com.github.ajalt.mordant.rendering.TextColors
import com.github.ajalt.mordant.rendering.TextStyles.bold
import edu.kit.compiler.lex.AnnotationType
import edu.kit.compiler.lex.SourceFile
import java.io.PrintStream

private const val TAB_SIZE = 4

fun String.escapeTabs(tabSize: Int = 4): String = replace("\t", " ".repeat(tabSize))

private fun PrintStream.formatLine(text: String, note: String?, line: Int, column: Int, length: Int, color: TextColors) {
    require(length > 0)
    val lineAsString = line.toString()
    val numTabsBeforeColumn = text.asSequence().take(column - 1).count { it == '\t' }
    println("  " + TextColors.gray("$lineAsString| ") + text.escapeTabs(TAB_SIZE))
    val markerIndent = lineAsString.length + 2 + (column - 1) + numTabsBeforeColumn * (TAB_SIZE - 1)
    println("  " + " ".repeat(markerIndent) + (color)(bold("^".repeat(length)) + (if (note != null) " $note" else "")))
}

private fun formatAnnotation(sourceFile: SourceFile, annotation: SourceFile.Annotation) {
    val color = when (annotation.type) {
        AnnotationType.WARNING -> TextColors.yellow
        AnnotationType.ERROR -> TextColors.red
    }

    val range = annotation.range
    val position = range.start
    val end = range.end
    check(position.line == end.line) { "multi-line annotations are not yet supported" }

    System.err.apply {
        println(color(bold("[${annotation.type}]") + " ${annotation.message}"))
        println("  in ${sourceFile.path}:${position.display()}")
        val line = sourceFile.getLine(position.line)
        if (line.length < 200) {
            formatLine(line, annotation.note, position.line, position.column, range.length.coerceAtLeast(1), color)
        } else {
            val length = if (range.length > 190) 1 else range.length.coerceAtLeast(1)
            val lookaround = (190 - length) / 2
            val start = (position.column - 1 - lookaround).coerceAtLeast(0)
            val end = (position.column - 1 + length + lookaround).coerceAtMost(line.length)
            val linePart = line.substring(start, end)
            formatLine("... $linePart ...", annotation.note, position.line, position.column + 4 - start, length, color)
        }
    }
}

object AnnotationFormatter {
    val DEFAULT = { source: SourceFile, annotation: SourceFile.Annotation -> formatAnnotation(source, annotation) }
}
