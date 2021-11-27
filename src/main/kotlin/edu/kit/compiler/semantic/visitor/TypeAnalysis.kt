package edu.kit.compiler.semantic.visitor

import edu.kit.compiler.lex.SourceFile
import edu.kit.compiler.semantic.AstNode

fun doTypeAnalysis(program: AstNode.Program, sourceFile: SourceFile) {
    program.accept(TypeAnalysisVisitor(sourceFile))
}

fun doMainMethodAnalysis(program: AstNode.Program, sourceFile: SourceFile) {
    program.accept(MainMethodVerifier(sourceFile))
    program.accept(MainMethodCounter(sourceFile))
}

fun doStringUsageChecking(program: AstNode.Program, sourceFile: SourceFile) {
    program.accept(StringUsageChecker(sourceFile))
}
