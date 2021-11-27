package edu.kit.compiler.semantic

import edu.kit.compiler.lex.SourceFile
import edu.kit.compiler.lex.StringTable
import edu.kit.compiler.semantic.visitor.doMainMethodAnalysis
import edu.kit.compiler.semantic.visitor.doStringUsageChecking
import edu.kit.compiler.semantic.visitor.doTypeAnalysis

fun doSemanticAnalysis(program: AstNode.Program, sourceFile: SourceFile, stringTable: StringTable) {
    doNameAnalysis(program, sourceFile, stringTable)
    doTypeAnalysis(program, sourceFile)
    doMainMethodAnalysis(program, sourceFile)
    doStringUsageChecking(program, sourceFile) // macht nix kaputt, aber evtl duplicate von namensanalyse?
}
