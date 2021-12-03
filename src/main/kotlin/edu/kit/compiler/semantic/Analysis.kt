package edu.kit.compiler.semantic

import edu.kit.compiler.lex.SourceFile
import edu.kit.compiler.lex.StringTable
import edu.kit.compiler.semantic.visitor.MainMethodCounter
import edu.kit.compiler.semantic.visitor.MainMethodVerifier
import edu.kit.compiler.semantic.visitor.accept
import edu.kit.compiler.semantic.visitor.doAssignmentLHSChecking
import edu.kit.compiler.semantic.visitor.doConstantBoundariesCheck
import edu.kit.compiler.semantic.visitor.doNameAnalysis
import edu.kit.compiler.semantic.visitor.doSearchForReturnStatement
import edu.kit.compiler.semantic.visitor.doTypeAnalysis

fun doMainMethodAnalysis(program: AstNode.Program, sourceFile: SourceFile) {
    program.accept(MainMethodVerifier(sourceFile))
    program.accept(MainMethodCounter(sourceFile))
}

fun doSemanticAnalysis(program: AstNode.Program, sourceFile: SourceFile, stringTable: StringTable) {
    doNameAnalysis(program, sourceFile, stringTable)
    doTypeAnalysis(program, sourceFile)
    doMainMethodAnalysis(program, sourceFile)
    doSearchForReturnStatement(program, sourceFile)
    doAssignmentLHSChecking(program, sourceFile)
    doConstantBoundariesCheck(program, sourceFile)
}
