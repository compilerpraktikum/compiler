package edu.kit.compiler

import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.UsageError
import com.github.ajalt.clikt.parameters.options.ArgsTransformer
import com.github.ajalt.clikt.parameters.options.OptionWithValues
import com.github.ajalt.clikt.parameters.options.RawOption
import com.github.ajalt.clikt.parameters.options.ValueTransformer

private fun mvar(choices: Iterable<String>): String {
    return choices.joinToString("|", prefix = "[", postfix = "]")
}
private fun errorMessage(context: Context, choice: String, choices: Map<String, *>): String {
    return context.localization.invalidChoice(choice, choices.keys.toList())
}

/**
 * Convert the option based on a fixed set of values. Allows to specify multiple values separated by [separator].
 *
 * **Example:**
 * ```
 * option().choices(mapOf("foo" to 1, "bar" to 2))
 * ```
 *
 * @param[separator] separator if multiple values are given (default: `,`)
 * @param[ignoreCase] if `true`, the option will accept values case-insensitive (default: `false`)
 *
 * @return a list of all chosen options
 */
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

/**
 * Converts an option that produces a list of values into a set of values.
 *
 * @param[allowDuplicates] if `false`, the option is rejected if any duplicates are found (default: `false`)
 */
fun <T : Any> OptionWithValues<List<T>, List<T>, T>.distinct(allowDuplicates: Boolean = false): OptionWithValues<Set<T>, Set<T>, T> {
    val distinctTransformer: ArgsTransformer<T, Set<T>> = {
        val values = transformEach(it)
        val set = values.toSet()
        if (!allowDuplicates && values.size != set.size) {
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
