package edu.kit.compiler.typechecking

import edu.kit.compiler.ast.AST
import edu.kit.compiler.ast.AllFunctors
import edu.kit.compiler.ast.DefaultVisitors
import edu.kit.compiler.ast.Functor
import edu.kit.compiler.ast.Type
import edu.kit.compiler.lex.SourceFile
import edu.kit.compiler.lex.SourcePosition
import edu.kit.compiler.lex.SourceRange
import edu.kit.compiler.wrapper.Kind
import edu.kit.compiler.wrapper.Of
import edu.kit.compiler.wrapper.into
import edu.kit.compiler.wrapper.wrappers.AnnotatedFunctor
import edu.kit.compiler.wrapper.wrappers.Positioned

open class SemanticNode<out A>(val position: SourceRange, val node: A) : Kind<SemanticNode<Of>, A> {
    fun upgradeTyped(type: SemanticNode<Type<SemanticNode<Of>>>) = TypedNode(position, type, node)
}

fun <A> Kind<SemanticNode<Of>, A>.into() = this as SemanticNode<A>
fun <A> SemanticNode<A>.toKind(): Kind<SemanticNode<Of>, A> = this

open class TypedNode<out A>(position: SourceRange, val type: SemanticNode<Type<SemanticNode<Of>>>, node: A) :
    SemanticNode<A>(position, node)

fun <A> Kind<TypedNode<Of>, A>.into() = this as TypedNode<A>
fun <A> TypedNode<A>.toKind(): Kind<TypedNode<Of>, A> = this as Kind<TypedNode<Of>, A>

open class PartialType<out A>(position: SourceRange, val inner: A?) : SemanticNode<A?>(position, inner)

fun <A> Kind<PartialType<Of>, A>.into() = this as PartialType<A>
fun <A> PartialType<A>.toKind(): Kind<PartialType<Of>, A> = this as Kind<PartialType<Of>, A>

inline fun <A, B> PartialType<A>.mapPartialType(f: (A) -> B): PartialType<B> = PartialType(position, inner?.let(f))

class CompareNode<out A>(
    position: SourceRange,
    val actualType: PartialType<Type<PartialType<Of>>>?,
    val expectedType: PartialType<Type<PartialType<Of>>>?,
    node: A
) : SemanticNode<A>(position, node) {
    fun withExpectedType(expected: PartialType<Type<PartialType<Of>>>) =
        CompareNode(position, actualType, expected, node)
}

fun <A> Kind<CompareNode<Of>, A>.into() = this as CompareNode<A>
fun <A> CompareNode<A>.toKind(): Kind<CompareNode<Of>, A> = this as Kind<CompareNode<Of>, A>

val position = SourceRange(SourcePosition(SourceFile.from("asd", "asd"), 0), 20)
val semanticNode: SemanticNode<Int> = SemanticNode(position, 1)
val semanticNodeas: Kind<SemanticNode<Of>, Int> = semanticNode

// fun <A, F, N: Kind<F, A>> N.outOff() : Kind<F, A> = this.outOf()
class SemanticNodeFunctor() : Functor<SemanticNode<Of>> {
    override fun <A, B> functorMap(f: (A) -> B, fa: Kind<SemanticNode<Of>, A>): Kind<SemanticNode<Of>, B> =
        fa.into().mapSemanticNode(f)
}

inline fun <A, B> SemanticNode<A>.mapSemanticNode(f: (A) -> B): SemanticNode<B> = SemanticNode(position, f(node))

val functors = AllFunctors<SemanticNode<Of>, Positioned<Of>, Positioned<Of>, Positioned<Of>, Positioned<Of>>(
    SemanticNodeFunctor(),
    AnnotatedFunctor(),
    AnnotatedFunctor(),
    AnnotatedFunctor(),
    AnnotatedFunctor()
)

class TypeExpression() :
    DefaultVisitors.ExpressionOnlyVisitor<
        // inputs
        SemanticNode<Of>, Positioned<Of>, Positioned<Of>, Positioned<Of>, Positioned<Of>,
        // expression output:
        CompareNode<Of>
        >(functors) {
    override fun stepExpression(expression: Kind<SemanticNode<Of>, AST.Expression<CompareNode<Of>, Positioned<Of>>>): Kind<CompareNode<Of>, AST.Expression<CompareNode<Of>, Positioned<Of>>> {
        val exprRange = expression.into().position
        return when (val expr = expression.into().node) {
            is AST.ArrayAccessExpression -> {
                val target = expr.target.into()
                val expectTarget = target.withExpectedType(
                    PartialType(
                        exprRange,
                        Type.arrayOf(PartialType(exprRange, null).toKind())
                    )
                )

                val index = expr.index.into()
                val expectedIndex = index.withExpectedType(
                    PartialType(index.position, Type.Integer)
                )
                val node = AST.ArrayAccessExpression(expectTarget.toKind(), expectedIndex.toKind())
                val wholeType: PartialType<Type<PartialType<Of>>>? = when (val targetType = target.actualType?.node) {
                    is Type.Array -> targetType.arrayType.elementType.into().mapPartialType { it.into() }
                    else -> null
                }
                CompareNode(
                    exprRange,
                    wholeType,
                    null,
                    node
                ).toKind()
            }

            is AST.BinaryExpression -> TODO()
            is AST.FieldAccessExpression -> TODO()
            is AST.IdentifierExpression -> TODO()
            is AST.LiteralBoolean ->
                CompareNode(exprRange, PartialType(exprRange, Type.Boolean), null, expr).toKind()
            is AST.LiteralInt ->
                CompareNode(exprRange, PartialType(exprRange, Type.Integer), null, expr).toKind()
            AST.LiteralNull -> TODO()
            AST.LiteralThis -> TODO()
            is AST.MethodInvocationExpression -> TODO()
            is AST.NewArrayExpression -> TODO()
            is AST.NewObjectExpression -> TODO()
            is AST.UnaryExpression ->
                when (expr.operation) {
                    AST.UnaryExpression.Operation.NOT -> {
                        val innerExpression = expr.expression.into().withExpectedType(
                            PartialType(expr.expression.into().position, Type.Boolean)
                        )
                        val node = AST.UnaryExpression(innerExpression.toKind(), expr.operation)
                        CompareNode(
                            exprRange,
                            PartialType(exprRange, Type.Boolean),
                            null,
                            node
                        ).toKind()
                    }
                    AST.UnaryExpression.Operation.MINUS -> {
                        val innerExpression = expr.expression.into().withExpectedType(
                            PartialType(expr.expression.into().position, Type.Integer)
                        )
                        val node = AST.UnaryExpression(innerExpression.toKind(), expr.operation)
                        CompareNode(
                            exprRange,
                            PartialType(exprRange, Type.Integer),
                            null,
                            node
                        ).toKind()
                    }
                }
        }
    }
}
//
// fun typeExpression(
//    expression: IdentityExpression,
// ): Compose<InducedTyped<Of>, SynthesizedTyped<Of>, LenientTypedExpression> =
//    when (expression) {
//        is AST.ArrayAccessExpression -> {
//
//            val typedTarget = typeExpression(expression.target.unwrapId().into())
//            val typedIndex = typeExpression(expression.index.unwrapId().into())
//
//            AST.ArrayAccessExpression(typedTarget, typedIndex)
//                .let { SynthesizedTyped(it, typedTarget.synthesizedType) }
//                .let { InducedTyped(it, expectedType) }
//                .let { Compose(it) }
//        }
//        is AST.BinaryExpression -> TODO()
//        is AST.FieldAccessExpression -> TODO()
//        is AST.IdentifierExpression -> TODO()
//        is AST.LiteralExpression -> TODO()
//        is AST.MethodInvocationExpression -> TODO()
//        is AST.NewArrayExpression -> TODO()
//        is AST.NewObjectExpression -> TODO()
//        is AST.UnaryExpression -> TODO()
//    }
