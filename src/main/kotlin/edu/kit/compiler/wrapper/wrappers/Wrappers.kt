package edu.kit.compiler.wrapper.wrappers

import edu.kit.compiler.ast.Functor
import edu.kit.compiler.lex.SourceRange
import edu.kit.compiler.typechecking.into
import edu.kit.compiler.wrapper.Kind
import edu.kit.compiler.wrapper.Of
import edu.kit.compiler.wrapper.Unwrappable

/**
 * Wrapper type, that adds a type annotation to the contained value
 */
data class Annotated<Ann, Node>(val node: Node, val ann: Ann) : Kind<Annotated<Ann, Of>, Node> {
    inline fun <B> mapAnnotation(f: (Ann) -> B) = Annotated(node, f(ann))
}

fun <Ann, Node> Kind<Annotated<Ann, Of>, Node>.into(): Annotated<Ann, Node> = this as Annotated<Ann, Node>typealias Positioned<Node> = Annotated<SourceRange, Node>

fun <Node> Node.positioned(range: SourceRange): Positioned<Node> = Annotated(this, range)

@JvmInline
value class UnwrappableAnnotated<Ann>(val empty: Unit? = null) : Unwrappable<Annotated<Ann, Of>> {
    override fun <A> unwrappableExtract(fa: Kind<Annotated<Ann, Of>, A>) = fa.into().node
}

@JvmInline
value class AnnotatedFunctor<Ann>(val empty: Unit? = null) : Functor<Annotated<Ann, Of>> {
    override fun <A, B> functorMap(f: (A) -> B, fa: Kind<Annotated<Ann, Of>, A>): Kind<Annotated<Ann, Of>, B> = fa.into().mapAnnotated(f)
}

inline fun <Ann, A, B> Annotated<Ann, A>.mapAnnotated(f: (A) -> B): Annotated<Ann, B> = Annotated(f(node), ann)
