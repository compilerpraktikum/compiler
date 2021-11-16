package edu.kit.compiler.lex

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
    fun annotate(type: AnnotationType, position: SourcePosition, message: String)
}

private val Char.isInvisible
    get() = (this == '\r')

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
            val content = Files.readString(path, StandardCharsets.US_ASCII)
            return SourceFile(path.absolutePathString(), StringInputProvider(content))
        }

        fun from(path: String, content: String) = SourceFile(path, StringInputProvider(content))
    }

    var currentPosition = SourcePosition(1, 0)
        private set

    /**
     * Map: line number -> start of that line in [input]
     */
    private val lineStarts = ArrayList<Int>(input.limit / 80).apply {
        add(0) // line 0 starts at position 0
    }

    override fun next(): Char {
        val char = input.next()

        if (char.isNewLine) {
            lineStarts.add(input.cursor)
        }

        currentPosition = when {
            char.isNewLine -> currentPosition.nextLine()
            char.isInvisible -> currentPosition
            else -> currentPosition.nextColumn()
        }

        return char
    }

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
            var current = start + 1
            while (current < input.limit && !input.get(current).isNewLine) {
                current += 1
            }
            current
        }

        return buildString(end - start - 1) {
            for (i in start until end) {
                val char = input.get(i)
                if (!char.isInvisible) {
                    append(char)
                }
            }
        }
    }

    var hasError = false // TODO replace usages with validate @csicar
        private set
    private val annotations: MutableMap<Int, ArrayList<Annotation>> = TreeMap() // line -> annotations

    override fun annotate(type: AnnotationType, position: SourcePosition, message: String) {
        annotations.computeIfAbsent(position.line) { ArrayList() }.add(Annotation(type, position, message))
        if (type == AnnotationType.ERROR) {
            hasError = true
        }
    }

    fun printAnnotations(formatter: (SourceFile, Annotation) -> Unit) {
        annotations.asSequence().flatMap { it.value }.forEach {
            formatter(this, it)
        }
    }

    /**
     * Get a sequence of annotations. This is *only* used for testing the parser recovery
     */
    fun getAnnotations(): Sequence<Annotation> {
        return annotations.asSequence().flatMap(Map.Entry<Int, ArrayList<Annotation>>::value)
    }

    data class Annotation(
        val type: AnnotationType,
        val position: SourcePosition,
        val message: String,
    )
}
