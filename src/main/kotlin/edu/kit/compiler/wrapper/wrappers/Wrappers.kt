package edu.kit.compiler.wrapper.wrappers

import edu.kit.compiler.lex.SourceRange
import edu.kit.compiler.wrapper.Functor
import edu.kit.compiler.wrapper.Kind
import edu.kit.compiler.wrapper.Of
import edu.kit.compiler.wrapper.Unwrappable
import edu.kit.compiler.wrapper.unwrap

/**
 * Wrapper type, that adds a type annotation to the contained value
 */
data class Annotated<Ann, Val>(val value: Val, val annotation: Ann) : Kind<Annotated<Ann, Of>, Val>

inline fun <A, Ann1, Ann2> Kind<Annotated<Ann1, Of>, A>.mapAnnotation(f: (Ann1) -> Ann2): Annotated<Ann2, A> =
    Annotated(this.into().value, f(annotation))

inline fun <A, B, Ann> Kind<Annotated<Ann, Of>, A>.mapValue(f: (A) -> B): Annotated<Ann, B> =
    this.into().run { Annotated(f(value), annotation) }

inline fun <Ann, Node> Kind<Annotated<Ann, Of>, Node>.into(): Annotated<Ann, Node> = this as Annotated<Ann, Node>

@JvmInline
value class UnwrappableAnnotated<Ann>(val empty: Unit? = null) : Unwrappable<Annotated<Ann, Of>> {
    override fun <A> unwrapValue(fa: Kind<Annotated<Ann, Of>, A>) = fa.into().value
}

inline fun <Ann, Node> Annotated<Ann, Node>.unwrapAnnotated() = this.unwrap(UnwrappableAnnotated())

@JvmInline
value class FunctorAnnotated<Ann>(val empty: Unit? = null) : Functor<Annotated<Ann, Of>> {
    override fun <A, B> functorMap(f: (A) -> B, fa: Kind<Annotated<Ann, Of>, A>): Kind<Annotated<Ann, Of>, B> =
        fa.mapValue(f)
}

val <Node, Ann> Kind<Annotated<Ann, Of>, Node>.annotation: Ann get() = this.into().annotation
val <Node, Ann> Kind<Annotated<Ann, Of>, Node>.annotationValue: Node get() = this.into().value

// Positioned
typealias Positioned<Node> = Annotated<SourceRange, Node>

inline fun <Node> Node.positioned(range: SourceRange): Positioned<Node> = Annotated(this, range)

val PositionedUnwrapper = UnwrappableAnnotated<SourceRange>()
