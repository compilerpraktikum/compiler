package edu.kit.compiler.semantic.visitor

import edu.kit.compiler.lex.SourceFile
import edu.kit.compiler.semantic.AstNode
import edu.kit.compiler.semantic.Namespace
import edu.kit.compiler.semantic.ParsedType

/**
 * Name analysis for subroutines.
 *
 * @param sourceFile input token stream to annotate with errors
 */
class SubroutineNameAnalysis(private val sourceFile: SourceFile) : AbstractVisitor() {
    override fun visitClassDeclaration(classDeclaration: AstNode.ClassDeclaration) {
        val classAnalysis = LocalClassNameAnalysis(classDeclaration.classNamespace, sourceFile)
        classDeclaration.accept(classAnalysis)
    }
}

/**
 * Subroutine name analysis for single classes. Contains the [classNamespace] as a scope for its members.
 *
 * @param classNamespace scope of surrounding class
 * @param sourceFile input token stream to annotate with errors
 */
private class LocalClassNameAnalysis(
    private val classNamespace: Namespace.ClassNamespace,
    private val sourceFile: SourceFile
) : AbstractVisitor() {
    override fun visitMethodDeclaration(methodDeclaration: AstNode.ClassMember.SubroutineDeclaration.MethodDeclaration) {
        subroutineAnalysis(methodDeclaration)
    }

    override fun visitMainMethodDeclaration(mainMethodDeclaration: AstNode.ClassMember.SubroutineDeclaration.MainMethodDeclaration) {
        subroutineAnalysis(mainMethodDeclaration)
    }

    private fun subroutineAnalysis(subroutineDeclaration: AstNode.ClassMember.SubroutineDeclaration) {
        val methodNamespace = Namespace.MethodNamespace()
        val localAnalysis = LocalNameAnalysis(classNamespace, methodNamespace, sourceFile)
        subroutineDeclaration.accept(localAnalysis)
        subroutineDeclaration.methodNamespace = methodNamespace
    }
}

/**
 * Second stage of the name analysis that checks names used in methods and local blocks
 *
 * @param classNamespace scope of surrounding class
 * @param localNamespace scope of current block
 * @param sourceFile The input stream to be annotated with error messages
 */
private class LocalNameAnalysis(
    private val classNamespace: Namespace.ClassNamespace,
    private val localNamespace: Namespace.LocalNamespace,
    private val sourceFile: SourceFile
) : AbstractVisitor() {
    override fun visitParameter(parameter: AstNode.ClassMember.SubroutineDeclaration.Parameter) {
        // todo check name

        super.visitParameter(parameter)
    }

    override fun visitArrayAccessExpression(arrayAccessExpression: AstNode.Expression.ArrayAccessExpression) {
        // todo check target

        super.visitArrayAccessExpression(arrayAccessExpression)
    }

    override fun visitFieldAccessExpression(fieldAccessExpression: AstNode.Expression.FieldAccessExpression) {
        // todo check target

        super.visitFieldAccessExpression(fieldAccessExpression)
    }

    override fun visitIdentifierExpression(identifierExpression: AstNode.Expression.IdentifierExpression) {
        // todo check ident

        super.visitIdentifierExpression(identifierExpression)
    }

    override fun visitMethodInvocationExpression(methodInvocationExpression: AstNode.Expression.MethodInvocationExpression) {
        // todo check target

        super.visitMethodInvocationExpression(methodInvocationExpression)
    }

    override fun visitBlock(block: AstNode.Statement.Block) {
        // todo new local scope, new visitor
    }

    override fun visitLocalVariableDeclaration(localVariableDeclaration: AstNode.Statement.LocalVariableDeclaration) {
        // todo add to local scope

        super.visitLocalVariableDeclaration(localVariableDeclaration)
    }

    override fun visitComplexType(complexType: ParsedType.ComplexType) {
        // todo add class declaration to type
    }
}
