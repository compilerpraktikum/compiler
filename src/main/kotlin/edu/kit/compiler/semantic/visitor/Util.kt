package edu.kit.compiler.semantic.visitor

/**
 * Function name alias for [run], to make code more expressive. We can call [kotlin.collections.MutableMap.putIfAbsent]
 * to add symbols to a namespace and then use `?.onError` instead of `?.run` to handle errors (as putIfAbsent should
 * return `null`, if no such symbol exists yet).
 */
fun <T> T.onError(block: T.() -> Unit) = run(block)
