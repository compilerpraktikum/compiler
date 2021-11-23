package edu.kit.compiler.semantic

import edu.kit.compiler.lex.Symbol

/**
 * Result type of semantic analysis
 */
sealed class SemanticType {
    object IntType : SemanticType()

    object BoolType : SemanticType()

    object VoidType : SemanticType()

    data class ComplexType(val className: Symbol, val classDeclaration: AstNode.ClassDeclaration) : SemanticType()

    data class ArrayType(val elementType: SemanticType) : SemanticType() {
        init {
            assert(elementType !is VoidType)
        }
    }

    /**
     * If this type is assigned as the semantic type of an [AstNode], the defined type is invalid and type checks should
     * silently fail (because an error message has already been generated)
     */
    object ErrorType : SemanticType()
}
