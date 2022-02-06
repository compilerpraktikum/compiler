package edu.kit.compiler.semantic

import edu.kit.compiler.source.SourceRange

/**
 * Result type of semantic analysis
 */
sealed class SemanticType {
    object Integer : SemanticType()

    object Boolean : SemanticType()

    object Null : SemanticType()

    object Void : SemanticType()

    data class Class(val name: SemanticAST.Identifier) : SemanticType() {
        var definition: ClassDefinition? = null
    }

    /**
     * @param elementType the base type of the array (might be another array type)
     * @param sourceRange the array-type source range (same as the surrounding expression). Since array types must be
     * explicitly defined (i.e. as field/method/parameter/variable type or as the expression type in a new array expression),
     * and can never be inferred, this property is always initialized.
     */
    data class Array(val elementType: SemanticType, val sourceRange: SourceRange) : SemanticType()

    /**
     * If this type is assigned as the semantic type of [SemanticAST], the defined type is invalid and type checks should
     * silently fail (because an error message has already been generated)
     */
    object Error : SemanticType()
}

private fun SemanticType.displayInternal(): String = when (this) {
    SemanticType.Integer -> "int"
    SemanticType.Boolean -> "boolean"
    SemanticType.Null -> "null"
    SemanticType.Void -> "void"
    is SemanticType.Class -> name.text
    is SemanticType.Array -> baseType.displayInternal() + "[]".repeat(dimension)
    SemanticType.Error -> "[ERROR]"
}

fun SemanticType.display(withBackTicks: Boolean = true) = when (withBackTicks) {
    true -> "`${displayInternal()}`"
    false -> displayInternal()
}

/**
 * Recursively retrieves the base type of the array
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
