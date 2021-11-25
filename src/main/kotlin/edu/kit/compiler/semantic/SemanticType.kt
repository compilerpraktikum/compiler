package edu.kit.compiler.semantic

/**
 * Result type of semantic analysis
 */
sealed class SemanticType {
    object IntType : SemanticType()

    object BoolType : SemanticType()

    object VoidType : SemanticType()

    data class ComplexType(val name: AstNode.Identifier) : SemanticType()

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

/**
 * Recursively retrieves the base type of an array
 */
val SemanticType.baseType: SemanticType
    get() = when (this) {
        is SemanticType.ArrayType -> this.elementType.baseType
        else -> this
    }

/**
 * Determines an array's dimension
 */
val SemanticType.dimension: Int
    get() {
        var dim = 0
        var type = this
        while (type is SemanticType.ArrayType) {
            dim += 1
            type = type.elementType
        }
        return dim
    }
