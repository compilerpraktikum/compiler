package edu.kit.compiler.semantic

import edu.kit.compiler.lex.Symbol

/**
 * Result type of semantic analysis
 */
sealed class SemanticType {
    object IntType : SemanticType()

    object BoolType : SemanticType()

    object VoidType : SemanticType()

    data class ComplexType(val className: Symbol) : SemanticType() {
        lateinit var classNamespace: Namespace.ClassNamespace
    }

    data class ArrayType(val elementType: SemanticType) : SemanticType() {
        init {
            assert(elementType !is VoidType)
        }
    }
}
