package edu.kit.compiler.semantic.visitor

import edu.kit.compiler.lex.AnnotationType
import edu.kit.compiler.lex.SourceFile
import edu.kit.compiler.lex.SourceRange
import edu.kit.compiler.semantic.AstNode
import edu.kit.compiler.semantic.SemanticType

class StringUsageChecker(val sourceFile: SourceFile) : AbstractVisitor()  {

    override fun visitClassDeclaration(classDeclaration: AstNode.ClassDeclaration) {
        if (classDeclaration.name.symbol.text == "String")
            sourceFile.annotate(
                AnnotationType.ERROR,
                SourceRange(classDeclaration.sourceRange.start, 1),
                "You cannot define a class named \"String\", since it is  pre-defined."
            )
        super.visitClassDeclaration(classDeclaration)
    }


    override fun visitNewObjectExpression(newObjectExpression: AstNode.Expression.NewObjectExpression) {
        checkAndMessageIfNot("No Instantiations of String.") { newObjectExpression.clazz.symbol.text != "String" }
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


    private fun checkAndMessageIfNot(errorMsg: String, function: () -> kotlin.Boolean) {
        if (!function()) {
            TODO("Print Error: $errorMsg and throw sth")
        }
    }
}
