package edu.kit.compiler.wrapper.wrappers

import edu.kit.compiler.wrapper.Functor
import edu.kit.compiler.wrapper.Kind
import edu.kit.compiler.wrapper.Of
import edu.kit.compiler.wrapper.Unwrappable
import edu.kit.compiler.wrapper.fmap
import edu.kit.compiler.wrapper.unwrap

@JvmInline
value class Compose<out F, out G, out A>(val unCompose: Kind<F, Kind<G, A>>) : Kind<Compose<F, G, Of>, A>

inline fun <F, G, A> Kind<F, Kind<G, A>>.compose() = Compose(this)

inline fun <F, G, A> Kind<Compose<F, G, Of>, A>.into(): Compose<F, G, A> = this as Compose<F, G, A>

// -------------------------- Instances ----------------------------//
class UnwrappableCompose<F, G>(private inline val unwrapF: Unwrappable<F>, private inline val unwrapG: Unwrappable<G>) :
    Unwrappable<Compose<F, G, Of>> {
    override fun <A> unwrapValue(fa: Kind<Compose<F, G, Of>, A>): A =
        fa.into().unCompose.unwrap(unwrapF).unwrap(unwrapG)
}

class FunctorCompose<F, G>(private inline val functorF: Functor<F>, private inline val functorG: Functor<G>) :
    Functor<Compose<F, G, Of>> {
    override fun <A, B> functorMap(f: (A) -> B, fga: Kind<Compose<F, G, Of>, A>): Kind<Compose<F, G, Of>, B> =
        fga.into()
            .unCompose
            .fmap(functorF) { ga ->
                ga.fmap(functorG) { a -> f(a) }
            }.compose()
}
