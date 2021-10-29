package edu.kit.compiler.lex

class SourcePosition(
    val path: String,
    val line: Int,
    val column: Int,
) {
    
    override fun toString(): String = "$path:$line:$column"
    
}

fun SourcePosition.nextLine() = SourcePosition(
    path,
    line + 1,
    0
)

fun SourcePosition.nextColumn() = SourcePosition(
    path,
    line,
    column + 1
)
