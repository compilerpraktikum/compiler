package edu.kit.compiler.source

import edu.kit.compiler.error.AnnotationFormatter
import edu.kit.compiler.utils.normalizeLineEndings
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.TreeMap
import kotlin.io.path.absolutePathString

enum class AnnotationType(private val str: String) {
    WARNING("warning"),
    ERROR("error");

    override fun toString(): String = str
}

interface AnnotatableFile {
    fun annotate(annotation: SourceFile.Annotation)

    fun annotate(type: AnnotationType, range: SourceRange, message: String, notes: List<SourceNote> = emptyList()) {
        annotate(SourceFile.Annotation(type, range, message, notes))
    }

    fun annotate(type: AnnotationType, position: SourcePosition, message: String, notes: List<SourceNote> = emptyList()) {
        annotate(type, position.extend(1), message, notes)
    }
}

private val Char.isNewLine
    get() = (this == '\n')

class SourceFile
private constructor(
    val path: String,
    private val input: StringInputProvider,
) : InputProvider by input, AnnotatableFile {

    companion object {
        /**
         * @throws[java.io.IOException]
         */
        fun from(path: Path): SourceFile {
            val content = Files.readString(path, StandardCharsets.US_ASCII).normalizeLineEndings()
            return SourceFile(path.toRealPath().absolutePathString(), StringInputProvider(content))
        }

        fun from(path: String, content: String) = SourceFile(path, StringInputProvider(content.normalizeLineEndings()))
    }

    val content: String
        get() = input.content

    val currentPosition: SourcePosition
        get() = SourcePosition(this, input.cursor)

    /**
     * Map: line number -> start of that line in [input]
     */
    private val lineStarts = ArrayList<Int>(input.limit / 80).apply {
        add(0) // line 0 starts at position 0
    }

    override fun next(): Char {
        val char = input.next()

        if (char.isNewLine) {
            lineStarts.add(input.cursor + 1)
        }

        return char
    }

    /**
     * Retrieve the source content in the given line.
     * @return the source content excluding the newline character before and after the line
     */
    fun getLine(line: Int): String {
        require(line > 0) { "line must be > 0" }
        val lineIndex = line - 1
        check(lineIndex < lineStarts.size) { "can only obtain lines that have already been processed via next()" }

        val start = lineStarts[lineIndex]

        // position of newline char at the end of the line
        val end = if (lineIndex + 1 < lineStarts.size) {
            lineStarts[lineIndex + 1] - 1
        } else {
            // search next newline
            var current = start
            while (current < input.limit && !input.get(current).isNewLine) {
                current += 1
            }
            current
        }

        return input.substring(start, end)
    }

    val numLines: Int
        get() = lineStarts.size

    fun calculateLineAndColumn(offset: Int): Pair<Int, Int> {
        require(offset >= 0) { "offset must be >= 0" }
        val index = lineStarts.binarySearch(offset)
        return if (index >= 0) {
            Pair(index + 1, 1)
        } else {
            // reconstruct the insertion index (-index - 1), start of line offset is smaller than [offset] so take the element before insertion index
            val startIndex = -index - 2
            Pair(startIndex + 1, offset - lineStarts[startIndex] + 1)
        }
    }

    var hasError = false
        private set

    private val annotations: MutableMap<Int, ArrayList<Annotation>> = TreeMap() // line -> annotations

    override fun annotate(annotation: Annotation) {
        annotations.computeIfAbsent(annotation.range.start.line) { ArrayList() }.add(annotation)

        if (annotation.type == AnnotationType.ERROR) {
            hasError = true
        }
    }

    fun printAnnotations(formatter: (SourceFile, Annotation) -> Unit = AnnotationFormatter.DEFAULT) {
        getAnnotations().forEach {
            formatter(this, it)
        }
    }

    /**
     * Get a sequence of annotations. This is *only* used for testing the parser recovery
     */
    fun getAnnotations(): Sequence<Annotation> {
        return annotations.asSequence().flatMap { it.value }
    }

    data class Annotation(
        val type: AnnotationType,
        val range: SourceRange,
        val message: String,
        val notes: List<Note>,
    ) {
        data class Note(
            val range: SourceRange,
            val note: String,
            val prefix: String = "note",
        )
    }
}

typealias SourceNote = SourceFile.Annotation.Note
