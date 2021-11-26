package edu.kit.compiler.transform

import edu.kit.compiler.semantic.AstNode
import edu.kit.compiler.semantic.visitor.AbstractVisitor

/**
 * A visitor implementation that constructs class types for the firm graph.
 */
class ClassTypeVisitor : AbstractVisitor() {
    override fun visitClassDeclaration(classDeclaration: AstNode.ClassDeclaration) {
        FirmContext.constructClassType(classDeclaration.name.symbol)
    }
}
