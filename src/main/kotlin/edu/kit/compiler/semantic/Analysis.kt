package edu.kit.compiler.semantic

import edu.kit.compiler.lex.SourceFile

fun doSemanticAnalysis(program: AstNode.Program, sourceFile: SourceFile) {
    doNameAnalysis(program, sourceFile)
}
