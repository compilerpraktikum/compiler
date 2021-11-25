package edu.kit.compiler.semantic.visitor

import edu.kit.compiler.lex.AnnotationType
import edu.kit.compiler.lex.SourceFile
import edu.kit.compiler.lex.SourceRange
import edu.kit.compiler.semantic.AstNode

class SystemUsageChecker(val sourceFile: SourceFile) : AbstractVisitor()  {

    var lastVisitedFieldAccessExpressionWasOverriddenSystem = false

    override fun visitFieldAccessExpression(fieldAccessExpression: AstNode.Expression.FieldAccessExpression) {
        // System.in...., System.out.....
        if (fieldAccessExpression.target is AstNode.Expression.IdentifierExpression
            && fieldAccessExpression.target.name.symbol.text == "System" && TODO("has no definition"))
            TODO("what to do here -> do at the end")
        super.visitFieldAccessExpression(fieldAccessExpression)
    }
}
