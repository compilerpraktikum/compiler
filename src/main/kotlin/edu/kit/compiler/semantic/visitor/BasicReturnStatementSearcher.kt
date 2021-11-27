package edu.kit.compiler.semantic.visitor

import edu.kit.compiler.lex.SourceFile
import edu.kit.compiler.lex.SourceRange
import edu.kit.compiler.semantic.AstNode
import edu.kit.compiler.semantic.SemanticType
import java.util.Stack

class BasicReturnStatementSearcher(val sourceFile: SourceFile) : AbstractVisitor() {

    var foundAReturnStatement: Boolean = false
    var weAreAtTopLevel: Boolean = true
    var recursionDepthCounter: Int = 1 // TODO maybe use this, since multiple return statements in one if block pollute the stack.

    private var foundAReturnStatementInIfStack = Stack<Boolean>().apply {
        push(false)
    }

    override fun visitMethodDeclaration(methodDeclaration: AstNode.ClassMember.SubroutineDeclaration.MethodDeclaration) {
        foundAReturnStatement = false

        methodDeclaration.block.statements.forEach { statement ->
            weAreAtTopLevel = true
            statement.accept(this)
            // TODO maybe invariant stack size == 1
            if (recursionDepthCounter + 1 == foundAReturnStatementInIfStack.size) {
                if (foundAReturnStatementInIfStack.pop()) {
                    foundAReturnStatement = true
                }
            }
            resetStackAndCounter()
        }

        checkAndMessageIfNot(methodDeclaration.sourceRange, "Missing return statement.") {
            methodDeclaration.returnType == SemanticType.Void || foundAReturnStatement
        }
        foundAReturnStatement = false
    }

    private fun resetStackAndCounter() {
        recursionDepthCounter = 1
        foundAReturnStatementInIfStack = Stack<Boolean>().apply {
            push(false)
        }
    }

    override fun visitWhileStatement(whileStatement: AstNode.Statement.WhileStatement) {
        // explicit no-op: we do assume loops to be skipped.
    }

    override fun visitMainMethodDeclaration(mainMethodDeclaration: AstNode.ClassMember.SubroutineDeclaration.MainMethodDeclaration) {
//        foundAReturnStatement = false
//        super.visitMainMethodDeclaration(mainMethodDeclaration)
//
//        checkAndMessageIfNot(mainMethodDeclaration.sourceRange, "Missing return statement.") { mainMethodDeclaration.returnType == SemanticType.Void || foundAReturnStatement }
//        foundAReturnStatement = false
    }

    override fun visitIfStatement(ifStatement: AstNode.Statement.IfStatement) {
        // 0 -? enter if "1" -> if finds return "2" -> after if when 2 = 3 wenn 1 = 4 -> enter else -? findet return dann 5
        weAreAtTopLevel = false
        if (ifStatement.elseCase == null) {
//            foundAReturnStatementInIfStack.push(false)
            return
        }
        recursionDepthCounter++
        println(" ".repeat(recursionDepthCounter * 2) + "IFSTATEMENT depth=$recursionDepthCounter  stackSize=${foundAReturnStatementInIfStack.size}")
//        i = 1
        ifStatement.thenCase.accept(this) // wenn return da i = 2
        // / liste position ist rekursiontiefe
        var foundReturnInThenCase = false
        println(" ".repeat(recursionDepthCounter * 2) + "sizecheckTHEN:   depth=$recursionDepthCounter, stackSize=${foundAReturnStatementInIfStack.size}")
        if (2 == foundAReturnStatementInIfStack.size) {
//        if (recursionDepthCounter == foundAReturnStatementInIfStack.size + (recursionDepthCounter - 2)) {
            foundReturnInThenCase = foundAReturnStatementInIfStack.pop()
            println(" ".repeat(recursionDepthCounter * 2) + "THEN:  $foundReturnInThenCase")
        }

        ifStatement.elseCase.accept(this)

        var foundReturnInElseCase = false
        println(" ".repeat(recursionDepthCounter * 2) + "sizecheckELSE:   depth=$recursionDepthCounter, stackSize=${foundAReturnStatementInIfStack.size}")
        if (2 == foundAReturnStatementInIfStack.size) {
            foundReturnInElseCase = foundAReturnStatementInIfStack.pop()
            println(" ".repeat(recursionDepthCounter * 2) + "Else:  $foundReturnInElseCase")
        }

//        foundAReturnStatementInIfStack.push(foundReturnInThenCase && foundReturnInElseCase)
        if (foundReturnInThenCase && foundReturnInElseCase) foundAReturnStatementInIfStack.push(true)

        recursionDepthCounter--
    }

    override fun visitReturnStatement(returnStatement: AstNode.Statement.ReturnStatement) {
        if (weAreAtTopLevel) {
            foundAReturnStatement = true
        } else {
            foundAReturnStatementInIfStack.push(true)
        }
        super.visitReturnStatement(returnStatement)
    }

    private fun checkAndMessageIfNot(sourceRange: SourceRange, errorMsg: String, function: () -> kotlin.Boolean) {
        checkAndAnnotateSourceFileIfNot(sourceFile, sourceRange, errorMsg, function)
        // TODO more?
    }
}
