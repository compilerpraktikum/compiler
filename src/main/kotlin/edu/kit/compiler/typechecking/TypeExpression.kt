package edu.kit.compiler.typechecking

import edu.kit.compiler.ast.AST
import edu.kit.compiler.ast.Type
import edu.kit.compiler.wrapper.IdentityExpression
import edu.kit.compiler.wrapper.IdentityType
import edu.kit.compiler.wrapper.LenientTypedExpression
import edu.kit.compiler.wrapper.Of
import edu.kit.compiler.wrapper.TypedExpression
import edu.kit.compiler.wrapper.into
import edu.kit.compiler.wrapper.wrappers.Compose
import edu.kit.compiler.wrapper.wrappers.Identity
import edu.kit.compiler.wrapper.wrappers.InducedType
import edu.kit.compiler.wrapper.wrappers.InducedTyped
import edu.kit.compiler.wrapper.wrappers.SynthesizedType
import edu.kit.compiler.wrapper.wrappers.SynthesizedTyped
import edu.kit.compiler.wrapper.wrappers.into
import edu.kit.compiler.wrapper.wrappers.synthesizedType
import edu.kit.compiler.wrapper.wrappers.unwrapId

fun typeExpression(
    expression: IdentityExpression,
    expectedType: InducedType
): Compose<InducedTyped<Of>, SynthesizedTyped<Of>, LenientTypedExpression> =
    when (expression) {
        is AST.ArrayAccessExpression -> {

            val typedTarget = typeExpression(expression.target.unwrapId().into(), expectedType)
            val typedIndex = typeExpression(expression.index.unwrapId().into(), InducedType(Type.Integer))

            AST.ArrayAccessExpression(typedTarget, typedIndex)
                .let { SynthesizedTyped(it, typedTarget.synthesizedType) }
                .let { InducedTyped(it, expectedType) }
                .let { Compose(it) }
        }
        is AST.BinaryExpression -> TODO()
        is AST.FieldAccessExpression -> TODO()
        is AST.IdentifierExpression -> TODO()
        is AST.LiteralExpression -> TODO()
        is AST.MethodInvocationExpression -> TODO()
        is AST.NewArrayExpression -> TODO()
        is AST.NewObjectExpression -> TODO()
        is AST.UnaryExpression -> TODO()
    }
