package edu.kit.compiler.wrapper.wrappers

import edu.kit.compiler.wrapper.Kind
import edu.kit.compiler.wrapper.Of
import edu.kit.compiler.wrapper.Unwrappable
import edu.kit.compiler.wrapper.unwrap

@JvmInline
value class Compose<F, G, A>(val unCompose: Kind<F, Kind<G, A>>) : Kind<Compose<F, G, Of>, A>

fun <F, G, A> Kind<Compose<F, G, Of>, A>.into(): Compose<F, G, A> = this as Compose<F, G, A>
class UnwrappableCompose<F, G>(private inline val unwrapF: Unwrappable<F>, private inline val unwrapG: Unwrappable<G>) :
    Unwrappable<Compose<F, G, Of>> {
    override fun <A> unwrapValue(fa: Kind<Compose<F, G, Of>, A>): A =
        fa.into().unCompose.unwrap(unwrapF).unwrap(unwrapG)
}
