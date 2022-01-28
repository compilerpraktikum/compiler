package edu.kit.compiler.utils

private class RuleMatchingException(name: String, exception: Exception) :
    Exception("Failed to match rule \"$name\": ${exception.message}", exception)

sealed class ValueHolder<T> {
    abstract fun get(): T

    data class Variable<T>(private var value: T? = null) : ValueHolder<T>() {
        fun set(v: T) {
            value = v
        }

        override fun get(): T = value ?: error("value not initialized")

        val hasValue: Boolean
            get() = value != null

        fun reset() {
            value = null
        }
    }

    data class Constant<T>(private var value: T? = null) : ValueHolder<T>() {
        override fun get(): T = value ?: error("value not initialized")
    }
}

interface MatchPattern<Target> {
    fun matches(target: Target): Boolean
}

class Rule<Target, Replacement, Scope : ReplacementBuilderScope>(
    private val name: String,
    private val variables: List<ValueHolder.Variable<*>>,
    private val pattern: MatchPattern<Target>,
    private val condition: (() -> Boolean)?,
    private val replacementFactory: Scope.() -> Replacement,
) {
    // TODO replace with `context(Scope)` when Kotlin 1.6.20 is released
    fun match(node: Target, scope: Scope): Replacement? {
        variables.forEach { it.reset() }

        val matches = try {
            pattern.matches(node)
        } catch (e: Exception) {
            throw RuleMatchingException(name, e)
        }
        if (!matches) {
            return null
        }

        if (condition != null && !condition!!()) {
            return null
        }

        return scope.replacementFactory()
    }
}

class RuleBuilderScope<Target, Replacement, Scope : ReplacementBuilderScope>(private val name: String) {
    private val variables = mutableListOf<ValueHolder.Variable<*>>()
    private var matchPattern: MatchPattern<Target>? = null
    private var conditionFn: (() -> Boolean)? = null
    private var replacementFactory: (Scope.() -> Replacement)? = null

    fun <T> variable() = ValueHolder.Variable<T>().also {
        variables.add(it)
    }

    fun <T> constant(value: T) = ValueHolder.Constant(value)

    fun match(pattern: MatchPattern<Target>) {
        matchPattern = pattern
    }

    fun condition(condition: () -> Boolean) {
        conditionFn = condition
    }

    fun replaceWith(replacement: Scope.() -> Replacement) {
        replacementFactory = replacement
    }

    fun build(): Rule<Target, Replacement, Scope> = Rule(name, variables, matchPattern!!, conditionFn, replacementFactory!!)
}

interface ReplacementBuilderScope {
    fun <Target, Replacement, Scope : ReplacementBuilderScope> Rule<Target, Replacement, Scope>.match(node: Target) =
        with(this@match) { match(node, this@ReplacementBuilderScope as Scope) }
}

fun <Target, Replacement, Scope : ReplacementBuilderScope> rule(name: String = "anonymous rule", block: RuleBuilderScope<Target, Replacement, Scope>.() -> Unit): Rule<Target, Replacement, Scope> =
    RuleBuilderScope<Target, Replacement, Scope>(name).apply(block).build()