package edu.kit.compiler.transform

import edu.kit.compiler.lexer.Symbol
import edu.kit.compiler.semantic.SemanticAST
import edu.kit.compiler.semantic.SemanticType
import edu.kit.compiler.semantic.visitor.AbstractVisitor

/**
 * A visitor implementation that constructs class types for the firm graph.
 */
class ClassConstructionVisitor : AbstractVisitor() {
    override fun visitClassDeclaration(classDeclaration: SemanticAST.ClassDeclaration) {
        FirmContext.typeRegistry.createClass(classDeclaration.name.symbol)
    }

    override fun visitFieldDeclaration(fieldDeclaration: SemanticAST.ClassMember.FieldDeclaration) {
        // do not descend
    }

    override fun visitMethodDeclaration(methodDeclaration: SemanticAST.ClassMember.SubroutineDeclaration.MethodDeclaration) {
        // do not descend
    }

    override fun visitMainMethodDeclaration(mainMethodDeclaration: SemanticAST.ClassMember.SubroutineDeclaration.MainMethodDeclaration) {
        // do not descend
    }
}

/**
 * A visitor implementation that constructs method types for the firm graph.
 */
class MethodConstructionVisitor : AbstractVisitor() {
    var currentClassSymbol: Symbol? = null

    override fun visitClassDeclaration(classDeclaration: SemanticAST.ClassDeclaration) {
        currentClassSymbol = classDeclaration.name.symbol
        val classType = FirmContext.typeRegistry.getClassType(classDeclaration.name.symbol)
        super.visitClassDeclaration(classDeclaration)
        classType.layoutFields()
        classType.finishLayout()
    }

    override fun visitFieldDeclaration(fieldDeclaration: SemanticAST.ClassMember.FieldDeclaration) {
        FirmContext.typeRegistry.createField(
            currentClassSymbol!!,
            fieldDeclaration.name.symbol,
            fieldDeclaration.type,
        )
        // do not descend
    }

    override fun visitMethodDeclaration(methodDeclaration: SemanticAST.ClassMember.SubroutineDeclaration.MethodDeclaration) {
        FirmContext.typeRegistry.createMethod(
            currentClassSymbol!!,
            methodDeclaration.name.symbol,
            methodDeclaration.returnType,
            methodDeclaration.parameters.map { it.type },
            false
        )
        // do not descend
    }

    override fun visitMainMethodDeclaration(mainMethodDeclaration: SemanticAST.ClassMember.SubroutineDeclaration.MainMethodDeclaration) {
        // overwrite types to match a c main method (`int main() {}`)
        FirmContext.typeRegistry.createMethod(
            currentClassSymbol!!,
            mainMethodDeclaration.name.symbol,
            SemanticType.Integer,
            emptyList(),
            true
        )
        // do not descend
    }
}
