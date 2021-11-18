package edu.kit.compiler.wrapper

import edu.kit.compiler.ast.AST
import edu.kit.compiler.ast.Type

// ---------------------------------------- Types ----------------------------------------//

/**
 * There are multiple concerns, that AST needs to solve:
 *
 * 1. Normal expressions as used by the semantic stage as input (see `Expression`)
 * 2. As an output of the Parser, which can contain invalid pieces (see `ExpressionWithErrors`)
 * 3. Typed Expressions, which should be annotated with their type (see `TypedExpression`)
 *
 * These type have subtle differences, which makes it hard to unify them into a single type.
 * But they all share the same structure, which would be beneficial to exploit.
 *
 * The problem with the basic recursive type (`Expression` in the example) is, that there is no way to customize
 * how the recursive case is handled: `ExpressionWithErrors` requires that recursive nesting is made optional.
 * `TypedExpression` requires, that every recursive call is annotated with the additional type information.
 *
 * The solution implemented here, is wrapping every recursive nested value with a generic type
 * (called `E` in the `WrappedExpression` example). The purpose of this generic type is making the concrete recursion
 * behaviour pluggable.
 * - For normal, valid, unannotated nodes, we use [Identity]
 * - For nodes, that contain invalid pieces, we use [Lenient]
 * - and for annotated nodes, we use [Annotated]
 *
 * The approach is adapted from [arrow core](https://arrow-kt.io/docs/0.10/patterns/glossary/#using-higher-kinds-with-typeclasses)
 * The syntax is a little different:
 * - What arrow writes as `Kind<ForOption, A>`, we would write as `Kind<Option<Of>, A>`
 * - instead of `ListKOf<A>` we write `Kind<List<Of>, A>` directly (omitting the type alias)
 * - `fun ListKOf<A>.fix()` we call would call `fun Kind<List<Of>, A>.into()` instead
 *
 * @sample DocsCodeSnippets.Expression
 * @sample DocsCodeSnippets.ExpressionWithErrors
 * @sample DocsCodeSnippets.TypedExpression
 * @sample DocsCodeSnippets.WrappedExpression.Expr
 *
 * @param F Wrapper you want to apply to the other parameter `A`
 * @param A Value to pass into `F`.
 */
interface Kind<out F, out A> {
    fun outOf(): Kind<F, A> = this
}

/**
 * Indicator of an anonymous argument of a partially applied Type.
 * For example: Expression<Of> represents an `Expression<E>` type, that is still missing its `E` argument
 *
 * Partially applied types like `Expression<Of>` can be further applied using the`.into()` methods on
 * `Kind<Expression<Of>, A>` for example.
 *
 * The Type is called `Of`, because the name makes the generic type signatures easier to read:
 * `Kind<Expression<Of>, A>` can be read as the type contains "Expressions of A"
 *
 * Different wrappers can be chained (see `exampleChaining`):
 * @sample DocsCodeSnippets.exampleChaining
 * The type `Lenient<AST.Expression<Of>>` means the contents of the type are either `Error`s or they are `Valid`
 * `Expression`s. The `Of` part of the type denotes, that we have not yet decided what type we want to nest inside the
 * Expression.
 *
 * The `exampleChainingRecursive` example shows how you can nest data inside the recursive type. In this case, Expression will contain
 * `Lenient` `Expression`s in every recursive case.
 * @sample DocsCodeSnippets.exampleChainingInner
 * @sample DocsCodeSnippets.exampleChainingRecursive
 */
class Of private constructor()

/**
 * This interface can be implemented for Wrappers [F], that
 * allow for extracting a contained value [A] from the wrapper.
 *
 * There is an extension function [unwrap], that can be used
 * extract the value inside a Wrapper.
 * See [WrappingExample.unwrappedValue] for an example
 *
 *
 * @sample WrappingExample
 */
interface Unwrappable<F> {
    fun <A> unwrapValue(fa: Kind<F, A>): A
}

object UnwrappableIdentity : Unwrappable<Identity<Of>> {
    override fun <A> unwrapValue(fa: Kind<Identity<Of>, A>) = fa.into().v
}
fun <A, F> Kind<F, A>.unwrap(wrapper: Unwrappable<F>): A = wrapper.unwrapValue(this)

// ---------------------------------------- Implementors ----------------------------------------//
/**
 * extension function to fully apply `Lenient<Of>` to `A` using `Kind<Lenient<Of>, A>`
 */
fun <A> Kind<Lenient<Of>, A>.into(): Lenient<A> = this as Lenient<A>

/**
 * extension function to fully apply `Type<Of>` to `A` using `Kind<Type<Of>, A>`
 */
fun <A> Kind<Type<Of>, A>.into(): Type<A> = this as Type<A>
fun <A> Kind<Type.Array.ArrayType<Of>, A>.into(): Type.Array.ArrayType<A> = this as Type.Array.ArrayType<A>
fun <A> Kind<AST.Parameter<Of>, A>.into(): AST.Parameter<A> = this as AST.Parameter<A>

/**
 * extension function to fully apply `Extension<Of>` to `A` using `Kind<Expression<Of>, A>`
 */
fun <A, O> Kind<AST.Expression<Of, O>, A>.into(): AST.Expression<A, O> = this as AST.Expression<A, O>
fun <E, S, O> Kind<AST.Statement<E, Of, O>, S>.into(): AST.Statement<E, S, O> = this as AST.Statement<E, S, O>
fun <E, S, O> Kind<AST.BlockStatement<E, Of, O>, S>.into(): AST.BlockStatement<E, S, O> = this as AST.BlockStatement<E, S, O>

// ---------------------------------------- Code Examples ----------------------------------------//

private object WrappingExample {
    val wrappedValue: Kind<Identity<Of>, AST.Expression<Of, Identity<Of>>> = TODO()
    val unwrappedValue: AST.Expression<Of, Identity<Of>> = wrappedValue.unwrap(UnwrappableIdentity)
}

private object DocsCodeSnippets {

    private val exampleChaining: Lenient<AST.Expression<Of, Nothing>> =
        Lenient.Valid(AST.LiteralExpression(2))
    private val exampleChainingInner: Lenient<AST.Expression<Lenient<Of>, Nothing>> =
        Lenient.Valid(AST.LiteralExpression(2))
    private val exampleChainingRecursive: Lenient<AST.Expression<Lenient<Of>, Nothing>> =
        Lenient.Valid(
            AST.BinaryExpression(
                Lenient.Error(null),
                exampleChainingInner,
                AST.BinaryExpression.Operation.ADDITION
            )
        )

    sealed class Expression() {
        data class Literal(val i: Int) : Expression()
        data class Add(val left: Expression, val right: Expression)
    }

    sealed class ExpressionWithErrors() {
        data class Lenient<T>(val v: T?, val error: String)

        data class Literal(val i: Int) : ExpressionWithErrors()
        data class Add(val left: Lenient<Expression>, val right: Lenient<Expression>) : ExpressionWithErrors()
    }

    sealed class TypedExpression(val type: Type) {
        sealed class Type() {
            object Int : Type()
            data class Array(val type: Type) : Type()
        }

        data class Literal(val i: Int) : TypedExpression(Type.Int)
        data class Add(val left: TypedExpression, val right: TypedExpression) : TypedExpression(TODO())
    }

    object WrappedExpression {

        sealed class Expr<out E> : Kind<Expr<Of>, E> {
            data class Add<out E>(val left: Kind<E, Kind<Expr<Of>, E>>, val right: Kind<E, Kind<Expr<Of>, E>>) :
                Expr<E>()

            data class Literal(val i: Int) : Expr<Nothing>()

            val leftExpr: Expr<Identity<Of>> = Add(Identity(Literal(2)), Identity(Literal(3)))
            val rightExpr: Expr<Identity<Of>> = Literal(3)
            val example: Expr<Identity<Of>> = Add(Identity(leftExpr), Identity(rightExpr))
        }
    }

    private val exampleExpressionOfIdentity: AST.Expression<Identity<Of>, Identity<Of>> = AST.BinaryExpression(
        Identity(AST.LiteralExpression(2)),
        Identity(AST.LiteralExpression(3)),
        AST.BinaryExpression.Operation.ADDITION
    )

    private object ConstFoldExample {
        fun constFold(i: AST.Expression<ConstValue<Int, Of>, Identity<Of>>): ConstValue<Int, AST.Expression<ConstValue<Int, Of>, Identity<Of>>> =
            when (i) {
                is AST.LiteralExpression<*> -> ConstValue(i.value as Int)
                is AST.UnaryExpression ->
                    when (i.operation) {
                        AST.UnaryExpression.Operation.NOT -> ConstValue(i.expression.into().c.inv())
                        AST.UnaryExpression.Operation.MINUS -> ConstValue(-i.expression.into().c)
                    }
                else -> TODO()
            }
    }
}
