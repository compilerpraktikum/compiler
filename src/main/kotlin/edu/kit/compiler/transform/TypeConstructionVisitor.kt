package edu.kit.compiler.transform

import edu.kit.compiler.lex.Symbol
import edu.kit.compiler.semantic.AstNode
import edu.kit.compiler.semantic.SemanticType
import edu.kit.compiler.semantic.visitor.AbstractVisitor

/**
 * A visitor implementation that constructs class types for the firm graph.
 */
class TypeConstructionVisitor : AbstractVisitor() {
    var currentClassSymbol: Symbol? = null

    override fun visitClassDeclaration(classDeclaration: AstNode.ClassDeclaration) {
        currentClassSymbol = classDeclaration.name.symbol
        val classType = FirmContext.typeRegistry.createClass(classDeclaration.name.symbol)
        super.visitClassDeclaration(classDeclaration)
        classType.layoutFields()
        classType.finishLayout()
    }

    override fun visitFieldDeclaration(fieldDeclaration: AstNode.ClassMember.FieldDeclaration) {
        FirmContext.typeRegistry.createField(
            currentClassSymbol!!,
            fieldDeclaration.name.symbol,
            fieldDeclaration.type,
        )
        // do not descend
    }

    override fun visitMethodDeclaration(methodDeclaration: AstNode.ClassMember.SubroutineDeclaration.MethodDeclaration) {
        FirmContext.typeRegistry.createMethod(
            currentClassSymbol!!,
            methodDeclaration.name.symbol,
            methodDeclaration.returnType,
            methodDeclaration.parameters.map { it.type },
            false
        )
        // do not descend
    }

    override fun visitMainMethodDeclaration(mainMethodDeclaration: AstNode.ClassMember.SubroutineDeclaration.MainMethodDeclaration) {
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
