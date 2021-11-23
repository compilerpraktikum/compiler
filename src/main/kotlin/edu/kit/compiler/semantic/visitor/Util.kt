package edu.kit.compiler.semantic.visitor

import edu.kit.compiler.semantic.AstNode
import edu.kit.compiler.semantic.ParsedType
import edu.kit.compiler.semantic.SemanticType

/**
 * Function name alias for [run], to make code more expressive. We can call [kotlin.collections.MutableMap.putIfAbsent]
 * to add symbols to a namespace and then use `?.onError` instead of `?.run` to handle errors (as putIfAbsent should
 * return `null`, if no such symbol exists yet).
 */
fun <T> T.onError(block: T.() -> Unit) = run(block)

/**
 * Construct a [SemanticType] from a [ParsedType] and a [AstNode.ClassDeclaration] if required.
 */
fun constructSemanticType(parsedType: ParsedType, typeDeclaration: AstNode.ClassDeclaration? = null): SemanticType {
    return when (parsedType) {
        ParsedType.BoolType -> SemanticType.BoolType
        ParsedType.IntType -> SemanticType.IntType
        ParsedType.VoidType -> SemanticType.VoidType
        is ParsedType.ArrayType -> SemanticType.ArrayType(
            constructSemanticType(
                parsedType.elementType,
                typeDeclaration
            )
        )
        is ParsedType.ComplexType -> SemanticType.ComplexType(typeDeclaration!!.name, typeDeclaration)
    }
}

/**
 * Recursively retrieves the base type of an array
 */
val ParsedType.baseType: ParsedType
    get() = when (this) {
        is ParsedType.ArrayType -> this.elementType.baseType
        else -> this
    }

/**
 * Determines an array's dimension
 */
val ParsedType.dimension: Int
    get() {
        var dim = 0
        var type = this
        while (type is ParsedType.ArrayType) {
            dim += 1
            type = type.elementType
        }
        return dim
    }
