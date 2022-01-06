package edu.kit.compiler.source

data class SourcePosition(
    val file: SourceFile,
    val offset: Int,
) {
    private val lineAndColumn: Pair<Int, Int> by lazy { file.calculateLineAndColumn(offset) }
    val line: Int
        get() = lineAndColumn.first
    val column: Int
        get() = lineAndColumn.second

    fun display(): String = "$line:$column"
}

private fun SourcePosition.advance(amount: Int) = SourcePosition(file, offset + amount)

data class SourceRange(
    val start: SourcePosition,
    val length: Int
) {
    init {
        require(length >= 0) { "length must be >= 0" }
    }

    // store lazy value instead of on demand computation (get() = ...) so multiple calls of range.end.line do not cause
    // multiple calls to SourceFile.calculateLineAndColumn()
    val last: SourcePosition by lazy { start.advance(length - 1) }

    fun extend(other: SourceRange): SourceRange {
        require(start.file == other.start.file) { "can only extend source ranges of the same source file" }
        require(start.offset <= other.last.offset) { "ranges must be in correct order" }
        val combinedLength = (other.start.offset + other.length) - start.offset
        return SourceRange(start, combinedLength)
    }
}

fun SourcePosition.extend(length: Int) = SourceRange(this, length)
