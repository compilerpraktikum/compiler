package edu.kit.compiler.semantic.visitor

import edu.kit.compiler.lex.SourceFile
import edu.kit.compiler.lex.SourceRange
import edu.kit.compiler.semantic.AstNode

class StringUsageChecker(val sourceFile: SourceFile) : AbstractVisitor() {
    override fun visitClassDeclaration(classDeclaration: AstNode.ClassDeclaration) {
        checkAndMessageIfNot(
            SourceRange(classDeclaration.sourceRange.start, 1),
            "You cannot define a class named \"String\", since it is  pre-defined."
        ) {
            classDeclaration.name.symbol.text != "String"
        }
        super.visitClassDeclaration(classDeclaration)
    }

    override fun visitNewObjectExpression(newObjectExpression: AstNode.Expression.NewObjectExpression) {
        checkAndMessageIfNot(newObjectExpression.sourceRange, "No Instantiations of String.") { newObjectExpression.clazz.symbol.text != "String" }
    }

// TODO string access?
//
//    override fun visitFieldAccessExpression(fieldAccessExpression: AstNode.Expression.FieldAccessExpression) {
//        checkAndMessageIfNot("") {
//            !(fieldAccessExpression.target is AstNode.Expression.IdentifierExpression && fieldAccessExpression.target.name.symbol.text == "String")
//        }
//
//        super.visitFieldAccessExpression(fieldAccessExpression)
//    }
//
//    override fun visitMethodInvocationExpression(methodInvocationExpression: AstNode.Expression.MethodInvocationExpression) {
//        checkAndMessageIfNot("") {
//            !(methodInvocationExpression.target is AstNode.Expression.IdentifierExpression && methodInvocationExpression.target.name.symbol.text == "String")
//        }
//        super.visitMethodInvocationExpression(methodInvocationExpression)
//    }
//

    private fun checkAndMessageIfNot(sourceRange: SourceRange, errorMsg: String, function: () -> kotlin.Boolean) {
        checkAndAnnotateSourceFileIfNot(sourceFile, sourceRange, errorMsg, function)
        // TODO more?
    }
}
