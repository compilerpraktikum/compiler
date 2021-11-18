package edu.kit.compiler.wrapper.wrappers

import edu.kit.compiler.wrapper.Kind
import edu.kit.compiler.wrapper.Of
import edu.kit.compiler.wrapper.Unwrappable

/**
 * Wrapper (aka Functor1), that passes the contents directly along.
 *
 * For example: `Expression<Identity<Of>>` would be an expression directly containing expressions
 * @sample DocsCodeSnippets.exampleExpressionOfIdentity
 */
data class Identity<A>(val v: A) : Kind<Identity<Of>, A>

fun <A> Kind<Identity<Of>, A>.into(): Identity<A> = this as Identity<A>

object UnwrappableIdentity : Unwrappable<Identity<Of>> {
    override fun <A> unwrapValue(fa: Kind<Identity<Of>, A>) = fa.into().v
}
