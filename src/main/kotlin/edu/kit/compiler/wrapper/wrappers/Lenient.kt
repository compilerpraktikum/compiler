package edu.kit.compiler.wrapper.wrappers

import edu.kit.compiler.ast.AST

/**
 * An AST-Node wrapper, that indicates, that the contained AST-Node may or may not be valid.
 *
 * - The `Error` variant denotes, that the AST-Node itself is invalid
 * - The `Valid(c: A)` variant denotes, that the AST-Node itself is valid, but `A` might contain invalid nodes.
 */
sealed class Lenient<out A> {
    /**
     * Error kind, may contain a node
     *
     * @see Lenient
     */
    data class Error<out A>(val node: A?) : Lenient<A>()

    /**
     * Valid kind, contains the node
     *
     * @see Lenient
     */
    data class Valid<out A>(val node: A) : Lenient<A>()

    fun getAsValid() = when (this) {
        is Error -> null
        is Valid -> this.node
    }

    fun <B> map(m: (A) -> B): Lenient<B> = when (this) {
        is Error -> Error(this.node?.let(m))
        is Valid -> Valid(m(this.node))
    }
}

inline fun <A> Lenient<A>.unwrapOr(handle: () -> A): A = when (this) {
    is Lenient.Error -> handle()
    is Lenient.Valid -> this.node
}

fun <A> A.wrapValid(): Lenient.Valid<A> = Lenient.Valid(this)

/**
 * Convert a [Lenient.Valid] to [Lenient.Error] (or keep [Lenient.Error])
 */
fun <A> Lenient<A>.markErroneous(): Lenient.Error<A> = when (this) {
    is Lenient.Error -> this
    is Lenient.Valid -> Lenient.Error(this.node)
}

/**
 * Wrap an element in a [Lenient.Error] instance
 */
fun <A> A.wrapErroneous(): Lenient.Error<A> = Lenient.Error(this)

fun Lenient<AST.Program>.validate(): AST.Program? {
    TODO()
}
