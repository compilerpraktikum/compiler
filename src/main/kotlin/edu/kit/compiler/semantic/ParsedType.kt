package edu.kit.compiler.semantic

import edu.kit.compiler.lex.Symbol

/**
 * Types returned by the parser. No typechecking has been applied, they only serve to denote types of variables and methods.
 */
sealed class ParsedType {
    object IntType : ParsedType()

    object BoolType : ParsedType()

    object VoidType : ParsedType()

    data class ComplexType(val symbol: Symbol) : ParsedType()

    data class ArrayType(val elementType: ParsedType) : ParsedType()
}
