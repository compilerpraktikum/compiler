package edu.kit.compiler.semantic

import edu.kit.compiler.lex.SourceFile
import edu.kit.compiler.lex.StringTable

fun doSemanticAnalysis(program: AstNode.Program, sourceFile: SourceFile, stringTable: StringTable) {
    doNameAnalysis(program, sourceFile, stringTable)
}
