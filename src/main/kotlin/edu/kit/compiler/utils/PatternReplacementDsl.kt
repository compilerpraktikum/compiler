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

        override fun toString(): String = "Variable($value)"
    }

    data class Constant<T>(private var value: T) : ValueHolder<T>() {
        override fun get(): T = value

        override fun toString(): String = "Constant($value)"
    }
}

interface MatchPattern<Target> {
    fun matches(target: Target): Boolean
}

class Rule<Target, Replacement, Scope : ReplacementBuilderScope>(
    val name: String,
    private val variables: List<ValueHolder.Variable<*>>,
    private val pattern: MatchPattern<Target>,
    private val condition: (() -> Boolean)?,
    private val replacementFactory: Scope.() -> Replacement,
) {
    context(Scope)
    fun match(node: Target): Replacement? {
        variables.forEach { it.reset() }

        val matches = try {
            pattern.matches(node)
        } catch (e: Exception) {
            throw RuleMatchingException(name, e)
        }
        if (!matches) {
            return null
        }

        if (condition != null && !condition.invoke()) {
            return null
        }

        return this@Scope.replacementFactory()
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
        check(matchPattern == null) { "only 1 call to match() allowed" }
        matchPattern = pattern
    }

    fun condition(condition: () -> Boolean) {
        check(conditionFn == null) { "only 1 call to condition() allowed" }
        conditionFn = condition
    }

    fun replaceWith(replacement: Scope.() -> Replacement) {
        check(replacementFactory == null) { "only 1 call to replaceWith() allowed" }
        replacementFactory = replacement
    }

    fun build(): Rule<Target, Replacement, Scope> = Rule(name, variables, matchPattern!!, conditionFn, replacementFactory!!)
}

interface ReplacementBuilderScope

fun <Target, Replacement, Scope : ReplacementBuilderScope> rule(name: String = "anonymous rule", block: RuleBuilderScope<Target, Replacement, Scope>.() -> Unit): Rule<Target, Replacement, Scope> =
    RuleBuilderScope<Target, Replacement, Scope>(name).apply(block).build()
