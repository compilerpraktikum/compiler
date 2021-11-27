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

fun doSearchForReturnStatement(program: AstNode.Program, sourceFile: SourceFile) {
    program.accept(ReturnStatementSearcher(sourceFile))
}

fun doAssignmentLHSChecking(program: AstNode.Program, sourceFile: SourceFile) {
    program.accept(AssignmentLHSChecker(sourceFile))
}

fun doConstantBoundariesCheck(program: AstNode.Program, sourceFile: SourceFile) {
    program.accept(ConstantBoundariesChecker(sourceFile))
}
