package edu.kit.compiler.semantic.visitor

import edu.kit.compiler.lex.SourceFile
import edu.kit.compiler.semantic.AstNode
import edu.kit.compiler.semantic.SemanticType

/**
 * Search for definite returns in non-void methods. These can be either
 * <ul>
 *     <li> a top level return statement.
 *     <li> an if/else tree where each block/ branch has a definite return statement.
 * </ul>
 */
class ReturnStatementSearcher(val sourceFile: SourceFile) : AbstractVisitor() {

    private var foundTopLevelReturnStatement: Boolean = false
    private var foundNonTopLevelReturnStatement: Boolean = false
    private var recursionDepthCounter: Int = 1

    override fun visitMethodDeclaration(methodDeclaration: AstNode.ClassMember.SubroutineDeclaration.MethodDeclaration) {
        if (methodDeclaration.returnType == SemanticType.Void) return
        foundTopLevelReturnStatement = false

        methodDeclaration.block.statements.forEach { statement ->
            statement.accept(this)
            if (getAndResetFoundNonTopLevelReturnStatement()) {
                // only true if ifstatement and ifElse
                foundTopLevelReturnStatement = true
            }
        }

        sourceFile.errorIfNot(foundTopLevelReturnStatement) {
            "missing return statement in non-void function" at methodDeclaration.sourceRange
        }
        foundTopLevelReturnStatement = false
    }

    override fun visitWhileStatement(whileStatement: AstNode.Statement.WhileStatement) {
        // explicit no-op: we do assume loops to be skipped.
    }

    override fun visitIfStatement(ifStatement: AstNode.Statement.IfStatement) {
        if (ifStatement.elseCase == null) {
            return
        }

        var foundReturnInThenCase = descent {
            ifStatement.thenCase.accept(this)
            getAndResetFoundNonTopLevelReturnStatement()
        }

        var foundReturnInElseCase = descent {
            ifStatement.elseCase.accept(this)
            getAndResetFoundNonTopLevelReturnStatement()
        }

        if (foundReturnInThenCase && foundReturnInElseCase) {
            foundNonTopLevelReturnStatement = true
        }
    }

    private fun getAndResetFoundNonTopLevelReturnStatement(): Boolean {
        val tmp = foundNonTopLevelReturnStatement
        foundNonTopLevelReturnStatement = false
        return tmp
    }

    private fun descent(function: () -> Boolean): Boolean {
        recursionDepthCounter++
        val tmp = function()
        recursionDepthCounter--
        return tmp
    }

    override fun visitReturnStatement(returnStatement: AstNode.Statement.ReturnStatement) {
        if (recursionDepthCounter == 1) {
            // TOP-level return statement found ==> Done.
            foundTopLevelReturnStatement = true
        } else {
            foundNonTopLevelReturnStatement = true
        }
        super.visitReturnStatement(returnStatement)
    }
}

fun doSearchForReturnStatement(program: AstNode.Program, sourceFile: SourceFile) {
    program.accept(ReturnStatementSearcher(sourceFile))
}
