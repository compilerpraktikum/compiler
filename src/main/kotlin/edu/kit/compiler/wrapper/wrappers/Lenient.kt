package edu.kit.compiler.wrapper.wrappers

import edu.kit.compiler.ast.AST
import edu.kit.compiler.ast.Type
import edu.kit.compiler.wrapper.Functor
import edu.kit.compiler.wrapper.IdentityBlockStatement
import edu.kit.compiler.wrapper.IdentityClassDeclaration
import edu.kit.compiler.wrapper.IdentityClassMember
import edu.kit.compiler.wrapper.IdentityProgram
import edu.kit.compiler.wrapper.IdentityStatement
import edu.kit.compiler.wrapper.Kind
import edu.kit.compiler.wrapper.LenientBlockStatement
import edu.kit.compiler.wrapper.LenientClassDeclaration
import edu.kit.compiler.wrapper.LenientClassMember
import edu.kit.compiler.wrapper.LenientProgram
import edu.kit.compiler.wrapper.LenientStatement
import edu.kit.compiler.wrapper.Of
import edu.kit.compiler.wrapper.into

/**
 * An AST-Node wrapper, that indicates, that the contained AST-Node may or may not be valid.
 *
 * - The `Error` variant denotes, that the AST-Node itself is invalid
 * - The `Valid(c: A)` variant denotes, that the AST-Node itself is valid, but `A` might contain invalid nodes.
 */
sealed class Lenient<out A> : Kind<Lenient<Of>, A> {
    /**
     * Error kind, may contain a node
     *
     * @see Lenient
     */
    data class Error<out A>(val node: A?) : Lenient<A>()

    /**
     * Valid kind, contains the node
     *
     * @see Lenient
     */
    data class Valid<out A>(val node: A) : Lenient<A>()

    fun getAsValid() = when (this) {
        is Error -> null
        is Valid -> this.node
    }

    fun <B> map(m: (A) -> B): Lenient<B> = when (this) {
        is Error -> Error(this.node?.let(m))
        is Valid -> Valid(m(this.node))
    }
}

object FunctorLenient : Functor<Lenient<Of>> {
    override fun <A, B> functorMap(f: (A) -> B, fa: Kind<Lenient<Of>, A>): Kind<Lenient<Of>, B> =
        fa.into().map(f)
}

inline fun <A> Lenient<A>.unwrapOr(handle: () -> A): A = when (this) {
    is Lenient.Error -> handle()
    is Lenient.Valid -> this.node
}

fun <A> A.wrapValid(): Lenient.Valid<A> = Lenient.Valid(this)

/**
 * Convert a [Lenient.Valid] to [Lenient.Error] (or keep [Lenient.Error])
 */
fun <A> Lenient<A>.markErroneous(): Lenient.Error<A> = when (this) {
    is Lenient.Error -> this
    is Lenient.Valid -> Lenient.Error(this.node)
}

/**
 * Wrap an element in a [Lenient.Error] instance
 */
fun <A> A.wrapErroneous(): Lenient.Error<A> = Lenient.Error(this)

fun Lenient<LenientProgram>.validate(): IdentityProgram? = this.unwrapOr { return null }.validate()
fun LenientProgram.validate(): IdentityProgram? =
    this.mapClassW { Identity(it.into().unwrapOr { return null }.validate() ?: return null) }

fun LenientClassDeclaration.validate(): IdentityClassDeclaration? =
    this.mapMethodW { Identity(it.into().unwrapOr { return null }.validate() ?: return null) }

fun Type<Lenient<Of>>.validate(): Type<Identity<Of>>? =
    this.mapOtherW { Identity(it.into().unwrapOr { return null }.into().validate() ?: return null) }

fun AST.Parameter<Lenient<Of>>.validate(): AST.Parameter<Identity<Of>>? =
    this.mapOtherW { Identity(it.into().unwrapOr { return null }.into().validate() ?: return null) }

fun LenientClassMember.validate(): IdentityClassMember? {
    return when (this) {
        is AST.Field<Lenient<Of>> -> AST.Field(
            name,
            Identity(type.into().unwrapOr { return null }.into().validate() ?: return null)
        )
        is AST.MainMethod -> AST.MainMethod(
            name,
            Identity((returnType.into().unwrapOr { return null }.into().validate() ?: return null)),
            parameters.map { it.into().unwrapOr { return null }.into().validate() ?: return null }
                .map { Identity(it) },
            Identity((block.into().getAsValid() ?: return null).validateBlock() ?: return null),
            throwsException
        )
        is AST.Method -> AST.Method(
            name,
            Identity((returnType.into().unwrapOr { return null }.into().validate() ?: return null)),
            parameters.map { it.into().unwrapOr { return null }.into().validate() ?: return null }
                .map { Identity(it) },
            Identity((block.into().getAsValid() ?: return null).validateBlock() ?: return null),
            throwsException
        )
    }
}

fun AST.Block<Lenient<Of>, Lenient<Of>, Lenient<Of>>.validateBlock(): AST.Block<Identity<Of>, Identity<Of>, Identity<Of>>? {
    val statements = this.statements.map { it.into().unwrapOr { return null }.into().validate() ?: return null }
        .map { Identity(it) }

    return AST.Block(statements)
}

fun LenientStatement.validate(): IdentityStatement? {
    return this.mapStmt(
        { blockStatement -> Identity(blockStatement.into().unwrapOr { return null }.into().validate() ?: return null) },
        { statement -> Identity(statement.into().unwrapOr { return null }.into().validate() ?: return null) },
        { expression -> Identity(expression.into().unwrapOr { return null }.into().validate() ?: return null) }
    )
}

fun LenientBlockStatement.validate(): IdentityBlockStatement? {
    return this.mapBlockStmt(
        { statement -> statement.into().validate() ?: return null },
        { expression -> Identity(expression.into().unwrapOr { return null }.into().validate() ?: return null) },
        { type -> Identity(type.into().unwrapOr { return null }.into().validate() ?: return null) }
    )
}

private val exampleExpression =
    AST.UnaryExpression(Lenient.Valid(AST.LiteralExpression(2)), AST.UnaryExpression.Operation.NOT)

fun AST.Expression<Lenient<Of>, Lenient<Of>>.validate(): AST.Expression<Identity<Of>, Identity<Of>>? {
    return this.mapExprW({ expr ->
        Identity(expr.into().unwrapOr { return null }.into().validate() ?: return null)
    }, { other ->
        Identity(other.into().unwrapOr { return null }.into().validate() ?: return null)
    })
}

private fun Type.Array.ArrayType<Lenient<Of>>.validate(): Type.Array.ArrayType<Identity<Of>>? {
    return Type.Array.ArrayType(
        Identity(
            this.elementType.into().unwrapOr { return null }.into().validate() ?: return null
        )
    )
}
