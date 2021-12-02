package edu.kit.compiler.transform

import edu.kit.compiler.semantic.AstNode
import edu.kit.compiler.semantic.visitor.accept

/**
 * Facade to AST transformation into the firm intermediate representation
 */
object Transformation {

    /**
     * Transform a [Semantic AST][AstNode] into a firm graph
     */
    fun transform(ast: AstNode) {
        FirmContext.init()

        ast.accept(TypeConstructionVisitor())
        ast.accept(TransformationVisitor())
    }
}
