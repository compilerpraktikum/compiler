package edu.kit.compiler.semantic

/**
 * Result type of semantic analysis
 */
sealed class SemanticType {
    object Integer : SemanticType()

    object Boolean : SemanticType()

    object Null : SemanticType()

    object Void : SemanticType()

    data class Class(val name: AstNode.Identifier) : SemanticType() {
        var definition: ClassDefinition? = null
    }

    data class Array(val elementType: SemanticType) : SemanticType()

    /**
     * If this type is assigned as the semantic type of an [AstNode], the defined type is invalid and type checks should
     * silently fail (because an error message has already been generated)
     */
    object Error : SemanticType()
}

fun SemanticType.display(): String = when (this) {
    SemanticType.Integer -> "int"
    SemanticType.Boolean -> "boolean"
    SemanticType.Null -> "null"
    SemanticType.Void -> "void"
    is SemanticType.Class -> name.text
    is SemanticType.Array -> baseType.display() + "[]".repeat(dimension)
    SemanticType.Error -> "[ERROR]"
}

/**
 * Recursively retrieves the base type of an array
 */
val SemanticType.Array.baseType: SemanticType
    get() {
        var baseType = elementType
        while (baseType is SemanticType.Array) {
            baseType = baseType.elementType
        }
        return baseType
    }

/**
 * Determines an array's dimension
 */
val SemanticType.Array.dimension: Int
    get() {
        var dim = 1
        var type = elementType
        while (type is SemanticType.Array) {
            dim += 1
            type = type.elementType
        }
        return dim
    }
