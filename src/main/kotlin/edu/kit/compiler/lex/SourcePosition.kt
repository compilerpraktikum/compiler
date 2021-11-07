package edu.kit.compiler.lex

data class SourcePosition(
    val line: Int,
    val column: Int,
) {

    fun display(): String = "$line:$column"
}

fun SourcePosition.nextLine() = SourcePosition(
    line + 1,
    0
)

fun SourcePosition.nextColumn() = SourcePosition(
    line,
    column + 1
)
