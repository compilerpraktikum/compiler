package edu.kit.compiler.semantic.visitor

import edu.kit.compiler.lex.SourceFile
import edu.kit.compiler.semantic.AstNode

class StringUsageChecker(val sourceFile: SourceFile) : AbstractVisitor() {

    override fun visitNewObjectExpression(newObjectExpression: AstNode.Expression.NewObjectExpression) {
        errorIfTrue(sourceFile, newObjectExpression.sourceRange, "instantiation of built-in class `String` not allowed") { newObjectExpression.clazz.symbol.text == "String" }
    }
}

fun doStringUsageChecking(program: AstNode.Program, sourceFile: SourceFile) {
    program.accept(StringUsageChecker(sourceFile))
}
