package edu.kit.compiler.wrapper

import edu.kit.compiler.lex.SourceRange

/**
 * Wrapper (aka Functor1), that passes the contents directly along.
 *
 * For example: `Expression<Identity<Of>>` would be an expression directly containing expressions
 * @sample DocsCodeSnippets.exampleExpressionOfIdentity
 */
data class Identity<A>(val v: A) : Kind<Identity<Of>, A>

fun <A> Kind<Identity<Of>, A>.into(): Identity<A> = this as Identity<A>

/**
 * Wrapper type, that ignores its contents (aka. a Constant Functor over `A`)
 * and stores a value `T` instead
 *
 * This can be useful for evaluating an expression. `constFold` does one reduction step:
 * It takes an Expression, that only contains values and returns a calculated value for the whole expression
 * @sample DocsCodeSnippets.ConstFoldExample
 */
data class ConstValue<T, A>(val c: T) : Kind<ConstValue<T, Of>, A>

/**
 * Fully applies the Const Type type. TODO: maybe remove `T` generic and replace with predefined `JavaValue`?
 */
fun <A, T> Kind<ConstValue<T, Of>, A>.into(): ConstValue<T, A> = this as ConstValue<T, A>

/**
 * Wrapper type, that adds a type annotation to the contained value
 */
data class Annotated<Ann, Node>(val node: Node, val ann: Ann) : Kind<Annotated<Ann, Of>, Node> {
    inline fun <B> mapAnnotation(f: (Ann) -> B) = Annotated(node, f(ann))
}

fun <Ann, Node> Kind<Annotated<Ann, Of>, Node>.into(): Annotated<Ann, Node> = this as Annotated<Ann, Node>

typealias Positioned<Node> = Annotated<SourceRange, Node>

fun <Node> Node.position(range: SourceRange): Positioned<Node> = Annotated(this, range)

/**
 * Wrapper stack that the parser outputs.
 * It consists of [Lenient] AST Node `A`, that have a
 * [SourceRange] associated with them (using [Positioned])
 */
typealias Parsed<A> = Positioned<Lenient<A>>
