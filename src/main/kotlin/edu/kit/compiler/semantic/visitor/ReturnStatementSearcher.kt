package edu.kit.compiler.semantic.visitor

import edu.kit.compiler.lex.SourceFile
import edu.kit.compiler.semantic.AstNode
import edu.kit.compiler.semantic.SemanticType

/**
 * Search for definite returns in non-void methods. These can be either
 * - a top level return statement.
 * - an if/else tree where each block/ branch has a definite return statement.
 */
class ReturnStatementSearcher(val sourceFile: SourceFile) : AbstractVisitor() {

    private var foundReturn: Boolean = false

    override fun visitMethodDeclaration(methodDeclaration: AstNode.ClassMember.SubroutineDeclaration.MethodDeclaration) {
        if (methodDeclaration.returnType == SemanticType.Void) {
            return
        }

        foundReturn = false

        methodDeclaration.block.accept(this)

        sourceFile.errorIfNot(foundReturn) {
            "missing return statement in non-void function" at methodDeclaration.name.sourceRange // TODO better source range
        }
    }

    override fun visitBlock(block: AstNode.Statement.Block) {
        block.statements.forEach { statement ->
            statement.accept(this)
            if (foundReturn) {
                return
            }
        }
    }

    override fun visitMainMethodDeclaration(mainMethodDeclaration: AstNode.ClassMember.SubroutineDeclaration.MainMethodDeclaration) {
        // do not descend: has return type void
    }

    override fun visitExpression(expression: AstNode.Expression) {
        // do not descend: expressions cannot return
    }

    override fun visitStatement(statement: AstNode.Statement) {
        checkNoReturnYet()
        super.visitStatement(statement)
    }

    override fun visitWhileStatement(whileStatement: AstNode.Statement.WhileStatement) {
        // skip because the condition might never be true
    }

    override fun visitIfStatement(ifStatement: AstNode.Statement.IfStatement) {
        if (ifStatement.elseCase == null) {
            return
        }

        if (containsReturn(ifStatement.thenCase) && containsReturn(ifStatement.elseCase)) {
            foundReturn = true
        }
    }

    private fun containsReturn(stmt: AstNode.Statement): Boolean {
        checkNoReturnYet()
        stmt.accept(this)
        val foundReturnInStatement = foundReturn
        foundReturn = false
        return foundReturnInStatement
    }

    override fun visitReturnStatement(returnStatement: AstNode.Statement.ReturnStatement) {
        foundReturn = true
    }

    private fun checkNoReturnYet() {
        check(!foundReturn) // iteration should stop once first return is encountered
    }
}
