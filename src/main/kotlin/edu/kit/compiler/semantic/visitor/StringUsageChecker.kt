package edu.kit.compiler.semantic.visitor

import edu.kit.compiler.lex.SourceFile
import edu.kit.compiler.semantic.AstNode

class StringUsageChecker(val sourceFile: SourceFile) : AbstractVisitor() {

    override fun visitNewObjectExpression(newObjectExpression: AstNode.Expression.NewObjectExpression) {
        sourceFile.errorIf(newObjectExpression.clazz.symbol.text == "String") {
            "instantiation of built-in class `String` not allowed" at newObjectExpression.sourceRange
        }
    }
}

fun doStringUsageChecking(program: AstNode.Program, sourceFile: SourceFile) {
    program.accept(StringUsageChecker(sourceFile))
}
