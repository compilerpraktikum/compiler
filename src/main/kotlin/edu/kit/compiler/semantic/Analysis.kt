package edu.kit.compiler.semantic

import edu.kit.compiler.lex.StringTable
import edu.kit.compiler.semantic.visitor.AssignmentLHSChecker
import edu.kit.compiler.semantic.visitor.ConstantBoundariesChecker
import edu.kit.compiler.semantic.visitor.MainMethodCounter
import edu.kit.compiler.semantic.visitor.MainMethodVerifier
import edu.kit.compiler.semantic.visitor.NameResolver
import edu.kit.compiler.semantic.visitor.NamespacePopulator
import edu.kit.compiler.semantic.visitor.ReturnStatementSearcher
import edu.kit.compiler.semantic.visitor.TypeAnalysisVisitor
import edu.kit.compiler.semantic.visitor.accept
import edu.kit.compiler.source.SourceFile

/**
 * Semantic analysis pipeline
 *
 * @param program the semantic [AST][AstNode] from a parser instance
 * @param sourceFile input abstraction where errors can be reported
 * @param stringTable [StringTable] for name analysis
 */
fun doSemanticAnalysis(program: AstNode.Program, sourceFile: SourceFile, stringTable: StringTable) {
    doNameAnalysis(program, sourceFile, stringTable)
    program.accept(TypeAnalysisVisitor(sourceFile))
    program.accept(MainMethodVerifier(sourceFile))
    program.accept(MainMethodCounter(sourceFile))
    program.accept(ReturnStatementSearcher(sourceFile))
    program.accept(AssignmentLHSChecker(sourceFile))
    program.accept(ConstantBoundariesChecker(sourceFile))
}

/**
 * Name analysis pipeline
 */
fun doNameAnalysis(program: AstNode.Program, sourceFile: SourceFile, stringTable: StringTable) {
    val global = GlobalNamespace().apply {
        defineBuiltIns(sourceFile, stringTable)
    }
    program.accept(NamespacePopulator(global, sourceFile))
    program.accept(NameResolver(global, sourceFile, stringTable.tryRegisterIdentifier("System")))
}
