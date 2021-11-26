package edu.kit.compiler.transform

import edu.kit.compiler.semantic.AstNode
import edu.kit.compiler.semantic.SemanticType
import edu.kit.compiler.semantic.visitor.AbstractVisitor

/**
 * A visitor implementation that constructs fields for classes in the firm graph.
 */
class FieldVisitor : AbstractVisitor() {
    override fun visitFieldDeclaration(fieldDeclaration: AstNode.ClassMember.FieldDeclaration) {
        val type = when (val type = fieldDeclaration.type) {
            SemanticType.Boolean -> FirmContext.boolType
            SemanticType.Integer -> FirmContext.intType
            SemanticType.Void -> FirmContext.voidType
            is SemanticType.Array -> TODO()
            is SemanticType.Class -> FirmContext.classTypes[type.name.symbol]!!
            SemanticType.Error -> throw AssertionError("no transformation for invalid ASTs")
        }

        FirmContext.constructField(type, fieldDeclaration.name.symbol, TODO("get class from declaration"))
    }
}
