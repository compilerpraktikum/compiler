package edu.kit.compiler.transform

import edu.kit.compiler.lexer.StringTable
import edu.kit.compiler.semantic.SemanticAST
import edu.kit.compiler.semantic.visitor.accept

fun TypeRegistry.defineBuiltIns(stringTable: StringTable) {
    val stringSymbol = stringTable.tryRegisterIdentifier("String")
    createClass(stringSymbol).apply {
        layoutFields()
        finishLayout()
    }
}

/**
 * Facade to AST transformation into the firm intermediate representation
 */
object Transformation {

    /**
     * Transform a [Semantic AST][SemanticAST] into a firm graph
     */
    fun transform(ast: SemanticAST, stringTable: StringTable) {
        FirmContext.init()

        ast.accept(ClassConstructionVisitor())
        FirmContext.typeRegistry.defineBuiltIns(stringTable)
        ast.accept(MethodConstructionVisitor())

        ast.accept(TransformationVisitor())
    }
}
