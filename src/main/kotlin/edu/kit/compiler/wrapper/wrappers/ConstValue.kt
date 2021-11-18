package edu.kit.compiler.wrapper.wrappers

import edu.kit.compiler.wrapper.Kind
import edu.kit.compiler.wrapper.Of

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
