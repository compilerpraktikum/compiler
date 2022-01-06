package edu.kit.compiler.error

import com.github.ajalt.mordant.rendering.TextColors
import com.github.ajalt.mordant.rendering.TextStyle
import com.github.ajalt.mordant.rendering.TextStyles.bold
import edu.kit.compiler.source.AnnotationType
import edu.kit.compiler.source.SourceFile
import edu.kit.compiler.source.SourceRange
import java.io.PrintStream

private const val TAB_SIZE = 4
private const val LOOKAROUND_BEFORE = 2
private const val LOOKAROUND_AFTER = 1

private fun String.escapeTabs(tabSize: Int = TAB_SIZE): String = replace("\t", " ".repeat(tabSize))

private val String.indentLength
    get() = asSequence().takeWhile { it.isWhitespace() }.count()

private fun String.highlight(start: Int, end: Int, formatter: (String) -> String): String {
    require(start <= end)
    if (start == end) {
        return this
    } else {
        return substring(0, start) + formatter(substring(start, end)) + substring(end)
    }
}
private fun String.highlight(start: Int, end: Int, style: TextStyle) = highlight(start, end) { style(it) }

private fun PrintStream.formatLineWithHighlight(code: String, note: String?, codeLinePrefix: String, column: Int, length: Int, color: TextColors) {
    require(length >= 0)
    val numTabsBeforeColumn = code.asSequence().take(column).count { it == '\t' }

    if (note != null) {
        println(
            "    " +
                TextColors.gray(note)
        )
    }
    println(
        "    " +
            TextColors.gray("$codeLinePrefix| ") +
            code.highlight(
                column.coerceAtMost(code.length),
                (column + length).coerceAtMost(code.length), // end of range may be at the \n char which is NOT included in [code]
                color
            ).escapeTabs(TAB_SIZE)
    )
    println(
        "    " +
            " ".repeat(codeLinePrefix.length) + TextColors.gray("| ") +
            " ".repeat(column + numTabsBeforeColumn * (TAB_SIZE - 1)) + (color + bold)("^".repeat(length.coerceAtLeast(1)))
    )
}

private fun PrintStream.formatLine(code: String, codeLinePrefix: String) {
    println("    " + TextColors.gray("$codeLinePrefix| ") + code.escapeTabs(TAB_SIZE))
}

internal fun formatAnnotation(out: PrintStream, sourceFile: SourceFile, annotation: SourceFile.Annotation) {
    val color = when (annotation.type) {
        AnnotationType.WARNING -> TextColors.yellow
        AnnotationType.ERROR -> TextColors.red
    }

    val lengthLineNumber = maxOf(annotation.range.last.line, annotation.notes.maxOfOrNull { it.range.last.line } ?: 0).toString().length + LOOKAROUND_AFTER
    fun Int.formatLineNumber() = toString().padStart(lengthLineNumber, ' ')

    fun PrintStream.formatRange(range: SourceRange, note: String?, lookaroundBefore: Int = 0, lookaroundAfter: Int = 0) {
        val lookaroundStart = (range.start.line - lookaroundBefore).coerceAtLeast(1)
        val lookaroundEnd = (range.last.line + lookaroundAfter).coerceAtMost(sourceFile.numLines)

        for (line in lookaroundStart until range.start.line) {
            formatLine(
                sourceFile.getLine(line),
                line.formatLineNumber(),
            )
        }

        for (line in range.start.line..range.last.line) {
            val isFirstInRange = line == range.start.line
            val isLastInRange = line == range.last.line

            val code = sourceFile.getLine(line)

            val end = if (isLastInRange) {
                range.last.column
            } else {
                code.length
            }

            val start = if (isFirstInRange) {
                range.start.column - 1
            } else {
                code.indentLength.coerceAtMost(end)
            }

            formatLineWithHighlight(
                code,
                if (isFirstInRange) note else null,
                line.formatLineNumber(),
                start,
                end - start,
                color
            )
        }

        for (line in (range.last.line + 1)..lookaroundEnd) {
            val code = sourceFile.getLine(line)

            if (line == lookaroundEnd && code.isEmpty()) {
                break // an empty line at the end does not add any valuable info to the output
            }

            formatLine(
                sourceFile.getLine(line),
                line.formatLineNumber(),
            )
        }

        println()
    }

    out.apply {
        // header
        println(color(bold("[${annotation.type}]") + " ${annotation.message}"))
        println(TextColors.gray("    in ${sourceFile.path}:${annotation.range.start.display()}"))

        // main source snippet
        formatRange(annotation.range, null, LOOKAROUND_BEFORE, LOOKAROUND_AFTER)

        // notes
        annotation.notes.forEach { (range, note, prefix) ->
            formatRange(range, "$prefix: $note")
        }
    }
}

object AnnotationFormatter {
    val DEFAULT = { source: SourceFile, annotation: SourceFile.Annotation -> formatAnnotation(System.err, source, annotation) }
}
