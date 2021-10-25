package edu.kit.compiler

import com.github.ajalt.clikt.core.BadParameterValue
import com.github.ajalt.clikt.parameters.arguments.RawArgument
import com.github.ajalt.clikt.parameters.arguments.convert
import com.github.ajalt.clikt.parameters.options.RawOption
import com.github.ajalt.clikt.parameters.options.convert

internal fun valueToUInt(it: String): UInt {
    return it.toUIntOrNull() ?: throw BadParameterValue("$it is not a valid unsigned integer")
}

/** Convert the argument values to an `UInt` */
fun RawArgument.uint() = convert { valueToUInt(it) }

/** Convert the option values to an `UInt` */
fun RawOption.uint() = convert({ "UINT" }) { valueToUInt(it) }
