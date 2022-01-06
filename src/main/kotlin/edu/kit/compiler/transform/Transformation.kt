package edu.kit.compiler.transform

import edu.kit.compiler.semantic.SemanticAST
import edu.kit.compiler.semantic.visitor.accept

/**
 * Facade to AST transformation into the firm intermediate representation
 */
object Transformation {

    /**
     * Transform a [Semantic AST][SemanticAST] into a firm graph
     */
    fun transform(ast: SemanticAST) {
        FirmContext.init()

        ast.accept(ClassConstructionVisitor())
        ast.accept(MethodConstructionVisitor())
        ast.accept(TransformationVisitor())
    }
}
