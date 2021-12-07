package edu.kit.compiler

import com.github.ajalt.clikt.core.BadParameterValue
import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.UsageError
import com.github.ajalt.clikt.parameters.arguments.RawArgument
import com.github.ajalt.clikt.parameters.arguments.convert
import com.github.ajalt.clikt.parameters.options.ArgsTransformer
import com.github.ajalt.clikt.parameters.options.OptionWithValues
import com.github.ajalt.clikt.parameters.options.RawOption
import com.github.ajalt.clikt.parameters.options.ValueTransformer
import com.github.ajalt.clikt.parameters.options.convert

// TODO remove if not needed

internal fun valueToUInt(it: String): UInt {
    return it.toUIntOrNull() ?: throw BadParameterValue("$it is not a valid unsigned integer")
}

/** Convert the argument values to an `UInt` */
fun RawArgument.uint() = convert { valueToUInt(it) }

/** Convert the option values to an `UInt` */
fun RawOption.uint() = convert({ "UINT" }) { valueToUInt(it) }

private fun mvar(choices: Iterable<String>): String {
    return choices.joinToString("|", prefix = "[", postfix = "]")
}
private fun errorMessage(context: Context, choice: String, choices: Map<String, *>): String {
    return context.localization.invalidChoice(choice, choices.keys.toList())
}

fun <T : Any> RawOption.choices(
    choices: Map<String, T>,
    metavar: String = mvar(choices.keys),
    separator: String = ",",
    ignoreCase: Boolean = false,
): OptionWithValues<List<T>, List<T>, T> {
    val c = if (ignoreCase) choices.mapKeys { it.key.lowercase() } else choices
    val valueTransformer: ValueTransformer<T> = {
        try {
            val value = transformValue(it)
            c[if (ignoreCase) value.lowercase() else value] ?: fail(errorMessage(context, value, choices))
        } catch (err: UsageError) {
            err.paramName = name
            throw err
        } catch (err: Exception) {
            fail(err.message ?: "")
        }
    }
    return copy(
        transformValue = valueTransformer,
        transformEach = { it },
        transformAll = { it.lastOrNull() ?: emptyList() },
        validator = {},
        nvalues = 1,
        valueSplit = Regex.fromLiteral(separator),
        metavarWithDefault = metavarWithDefault.copy(default = { metavar })
    )
}

fun <T : Any> RawOption.choices(
    vararg choices: Pair<String, T>,
    metavar: String = mvar(choices.asSequence().map { it.first }.asIterable()),
    separator: String = ",",
    ignoreCase: Boolean = false,
): OptionWithValues<List<T>, List<T>, T> {
    return choices(choices.toMap(), metavar, separator, ignoreCase)
}

fun RawOption.choices(
    vararg choices: String,
    metavar: String = mvar(choices.asIterable()),
    separator: String = ",",
    ignoreCase: Boolean = false,
): OptionWithValues<List<String>, List<String>, String> {
    return choices(choices.associateBy { it }, metavar, separator, ignoreCase)
}

private fun <T : Any> List<T>.getDuplicates(): Set<T> {
    val alreadySeen = mutableSetOf<T>()
    val duplicates = mutableSetOf<T>()
    forEach {
        if (alreadySeen.contains(it)) {
            duplicates.add(it)
        }
        alreadySeen.add(it)
    }
    return duplicates
}

fun <T : Any> OptionWithValues<List<T>, List<T>, T>.distinct(): OptionWithValues<Set<T>, Set<T>, T> {
    val distinctTransformer: ArgsTransformer<T, Set<T>> = {
        val values = transformEach(it)
        val set = values.toSet()
        if (values.size != set.size) {
            val duplicates = values.getDuplicates()
            throw CliktError("Error: Duplicated ${if (duplicates.size > 1) "entries" else "entry"} for \"$name\": " + duplicates.joinToString(", "))
        }
        set
    }
    return this.copy(
        transformValue = transformValue,
        transformEach = distinctTransformer,
        transformAll = { it.lastOrNull() ?: emptySet() },
        validator = {},
    )
}
