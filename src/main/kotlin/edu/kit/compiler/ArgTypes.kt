package edu.kit.compiler

import kotlinx.cli.ArgType

// workaround because ParsingException is internal and the only way to use the built-in error handling
// -> https://github.com/Kotlin/kotlinx-cli/issues/71
private fun throwParsingException(msg: String): Nothing {
    throw Class.forName("kotlinx.cli.ParsingException").getConstructor(kotlin.String::class.java)
            .newInstance(msg) as Exception
}

object ArgTypes {

    object UInt : ArgType<kotlin.UInt>(true) {
        override val description: kotlin.String
            get() = "{ UInt }"
    
        override fun convert(value: kotlin.String, name: kotlin.String): kotlin.UInt {
            return value.toUIntOrNull()
                    ?: throwParsingException("Option $name is expected to be an unsigned number. $value is provided.")
        }
    }
    
}
