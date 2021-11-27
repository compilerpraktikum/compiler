package edu.kit.compiler.semantic.visitor

import edu.kit.compiler.lex.SourceFile
import edu.kit.compiler.lex.SourceRange
import edu.kit.compiler.semantic.AstNode
import edu.kit.compiler.semantic.SemanticType

class BasicReturnStatementSearcher(val sourceFile: SourceFile) : AbstractVisitor() {

    var foundAReturnStatement: Boolean = false

    override fun visitMethodDeclaration(methodDeclaration: AstNode.ClassMember.SubroutineDeclaration.MethodDeclaration) {
        foundAReturnStatement = false
        super.visitMethodDeclaration(methodDeclaration)
        checkAndMessageIfNot(methodDeclaration.sourceRange, "Missing return statement.") { methodDeclaration.returnType == SemanticType.Void || foundAReturnStatement }
        foundAReturnStatement = false
    }

    override fun visitMainMethodDeclaration(mainMethodDeclaration: AstNode.ClassMember.SubroutineDeclaration.MainMethodDeclaration) {
        foundAReturnStatement = false
        super.visitMainMethodDeclaration(mainMethodDeclaration)

        checkAndMessageIfNot(mainMethodDeclaration.sourceRange, "Missing return statement.") { mainMethodDeclaration.returnType == SemanticType.Void || foundAReturnStatement }
        foundAReturnStatement = false
    }

    override fun visitReturnStatement(returnStatement: AstNode.Statement.ReturnStatement) {
        foundAReturnStatement = true
        super.visitReturnStatement(returnStatement)
    }

    private fun checkAndMessageIfNot(sourceRange: SourceRange, errorMsg: String, function: () -> kotlin.Boolean) {
        checkAndAnnotateSourceFileIfNot(sourceFile, sourceRange, errorMsg, function)
        // TODO more?
    }
}
