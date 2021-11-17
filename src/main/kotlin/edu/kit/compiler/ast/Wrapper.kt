package edu.kit.compiler.ast

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
 * - and for annotated nodes, we use [TypeAnnotated]
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
 * @sample exampleChaining
 * The type `Lenient<AST.Expression<Of>>` means the contents of the type are either `Error`s or they are `Valid`
 * `Expression`s. The `Of` part of the type denotes, that we have not yet decided what type we want to nest inside the
 * Expression.
 *
 * The `exampleChainingRecursive` example shows how you can nest data inside the recursive type. In this case, Expression will contain
 * `Lenient` `Expression`s in every recursive case.
 * @sample exampleChainingInner
 * @sample exampleChainingRecursive
 */
class Of private constructor()

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
fun <E, S, O> Kind<AST.BlockStatement<E, Of, O>, S>.into(): AST.BlockStatement<E, S, O> =
    this as AST.BlockStatement<E, S, O>

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
 * Wrapper (aka Functor1), that passes the contents directly along.
 *
 * For example: `Expression<Identity<Of>>` would be an expression directly containing expressions
 * @sample DocsCodeSnippets.exampleExpressionOfIdentity
 */
data class Identity<A>(val v: A) : Kind<Identity<Of>, A>

fun <A> Kind<Identity<Of>, A>.into(): Identity<A> = this as Identity<A>

data class TypeAnnotated<Ann, TypeW>(val type: Type<TypeW>, val v: Ann) : Kind<TypeAnnotated<Of, TypeW>, Ann>

fun <A, TypeW> Kind<TypeAnnotated<Of, TypeW>, A>.into(): TypeAnnotated<A, TypeW> = this as TypeAnnotated<A, TypeW>

/**
 *
 */
data class Annotated<Annotation, Node>(val annotation: Annotation, val node: Node)

interface NaturalTransformation<F, G> {
    fun <A> run(fa: Kind<F, A>): Kind<G, A>
}

interface Functor<F> {
    fun <A, B> fmap(fa: Kind<F, A>, f: (A) -> B): Kind<F, B>
}

interface Functor1<T> {
    fun <F, G : Functor<G>> Kind<T, F>.map1(nt: NaturalTransformation<F, G>): Kind<T, G>
}

/**
 * `mapClassW` converts from one `ClassWrapper` generic to another.
 * The function is marked `inline` to allow early returns from the lambda expression
 */
inline fun <ExprW1, ExprW2, StmtW1, StmtW2, MethW1, MethW2, ClassW1, ClassW2, OtherW1, OtherW2> AST.Program<ExprW1, StmtW1, MethW1, ClassW1, OtherW1>.mapClassW(
    f: (Kind<ClassW1, AST.ClassDeclaration<ExprW1, StmtW1, MethW1, OtherW1>>)
    -> Kind<ClassW2, AST.ClassDeclaration<ExprW2, StmtW2, MethW2, OtherW2>>
): AST.Program<ExprW2, StmtW2, MethW2, ClassW2, OtherW2> =
    AST.Program(this.classes.map(f))

inline fun <ExprW1, ExprW2, StmtW1, StmtW2, MethW1, MethW2, OtherW1, OtherW2> AST.ClassDeclaration<ExprW1, StmtW1, MethW1, OtherW1>.mapMethodW(
    f: (Kind<MethW1, AST.ClassMember<ExprW1, StmtW1, OtherW1>>)
    -> Kind<MethW2, AST.ClassMember<ExprW2, StmtW2, OtherW2>>
): AST.ClassDeclaration<ExprW2, StmtW2, MethW2, OtherW2> =
    AST.ClassDeclaration(name, member.map(f))

inline fun <OtherW1, OtherW2> Type<OtherW1>.mapOtherW(
    f: (Kind<OtherW1, Kind<Type<Of>, OtherW1>>) -> Kind<OtherW2, Kind<Type<Of>, OtherW2>>
): Type<OtherW2> =
    when (this) {
        is Type.Array -> Type.Array(Type.Array.ArrayType(f(arrayType.elementType)))
        is Type.Boolean -> this
        is Type.Class -> this
        is Type.Integer -> this
        is Type.Void -> this
    }

inline fun <OtherW1, OtherW2> AST.Parameter<OtherW1>.mapOtherW(
    f: (Kind<OtherW1, Kind<Type<Of>, OtherW1>>) -> Kind<OtherW2, Kind<Type<Of>, OtherW2>>
): AST.Parameter<OtherW2> =
    AST.Parameter(name, f(type))

inline fun <ExprW1, ExprW2, StmtW1, StmtW2, OtherW1, OtherW2> AST.Statement<ExprW1, StmtW1, OtherW1>.mapStmt(
    mapBlockStatement: (Kind<StmtW1, Kind<AST.BlockStatement<ExprW1, Of, OtherW1>, StmtW1>>) -> Kind<StmtW2, Kind<AST.BlockStatement<ExprW2, Of, OtherW2>, StmtW2>>,
    mapStatement: (Kind<StmtW1, Kind<AST.Statement<ExprW1, Of, OtherW1>, StmtW1>>) -> Kind<StmtW2, Kind<AST.Statement<ExprW2, Of, OtherW2>, StmtW2>>,
    mapExpression: (Kind<ExprW1, Kind<AST.Expression<Of, OtherW1>, ExprW1>>) -> Kind<ExprW2, Kind<AST.Expression<Of, OtherW2>, ExprW2>>,
):
    AST.Statement<ExprW2, StmtW2, OtherW2> = when (this) {
    is AST.Block -> AST.Block(
        this.statements.map { mapBlockStatement(it) }
    ).into()
    is AST.ExpressionStatement ->
        AST.ExpressionStatement(
            mapExpression(expression)
        )
    is AST.IfStatement ->
        AST.IfStatement(
            mapExpression(condition),
            mapStatement(trueStatement),
            falseStatement?.let { mapStatement(it) }
        )
    is AST.ReturnStatement ->
        AST.ReturnStatement(
            expression?.let { justExpr ->
                mapExpression(justExpr)
            }
        )

    is AST.WhileStatement ->
        AST.WhileStatement(
            mapExpression(condition),
            mapStatement(statement),
        )
}

inline fun <ExprW1, ExprW2, StmtW1, StmtW2, OtherW1, OtherW2> AST.BlockStatement<ExprW1, StmtW1, OtherW1>.mapBlockStmt(
    mapStatement: (AST.Statement<ExprW1, StmtW1, OtherW1>) -> AST.Statement<ExprW2, StmtW2, OtherW2>,
    mapExpression: (Kind<ExprW1, Kind<AST.Expression<Of, OtherW1>, ExprW1>>) -> Kind<ExprW2, Kind<AST.Expression<Of, OtherW2>, ExprW2>>,
    mapType: (Kind<OtherW1, Kind<Type<Of>, OtherW1>>) -> Kind<OtherW2, Kind<Type<Of>, OtherW2>>
):
    AST.BlockStatement<ExprW2, StmtW2, OtherW2> =
    when (this) {
        is AST.LocalVariableDeclarationStatement ->
            AST.LocalVariableDeclarationStatement(name, mapType(type), initializer?.let { mapExpression(it) })
        is AST.StmtWrapper ->
            AST.StmtWrapper(mapStatement(statement))
    }

inline fun <ExprW1, ExprW2, OtherW1, OtherW2> AST.Expression<ExprW1, OtherW1>.mapExprW(
    f: (Kind<ExprW1, Kind<AST.Expression<Of, OtherW1>, ExprW1>>) -> Kind<ExprW2, Kind<AST.Expression<Of, OtherW2>, ExprW2>>,
    g: (Kind<OtherW1, Kind<Type.Array.ArrayType<Of>, OtherW1>>) -> Kind<OtherW2, Kind<Type.Array.ArrayType<Of>, OtherW2>>
): AST.Expression<ExprW2, OtherW2> =
    when (this) {
        is AST.ArrayAccessExpression ->
            AST.ArrayAccessExpression(
                f(target),
                f(index)
            )
        is AST.BinaryExpression ->
            AST.BinaryExpression(
                f(left),
                f(right),
                operation
            )
        is AST.FieldAccessExpression ->
            AST.FieldAccessExpression(f(target), field)
        is AST.IdentifierExpression -> this
        is AST.LiteralExpression<*> -> this
        is AST.MethodInvocationExpression ->
            AST.MethodInvocationExpression(
                target?.let { target -> f(target) },
                method,
                arguments.map { arg -> f(arg) }
            )
        is AST.NewArrayExpression ->
            AST.NewArrayExpression(
                g(type),
                f(length)
            )
        is AST.NewObjectExpression -> this
        is AST.UnaryExpression ->
            AST.UnaryExpression(f(expression), operation)
    }

typealias LenientProgram = AST.Program<Lenient<Of>, Lenient<Of>, Lenient<Of>, Lenient<Of>, Lenient<Of>>
typealias ProgramOfIdentity = AST.Program<Identity<Of>, Identity<Of>, Identity<Of>, Identity<Of>, Identity<Of>>

fun Lenient<LenientProgram>.validate(): ProgramOfIdentity? = this.unwrapOr { return null }.validate()

fun LenientProgram.validate(): ProgramOfIdentity? =
    this.mapClassW { Identity(it.into().unwrapOr { return null }.validate() ?: return null) }

typealias ClassDeclarationOfLenient = AST.ClassDeclaration<Lenient<Of>, Lenient<Of>, Lenient<Of>, Lenient<Of>>
typealias ClassDeclarationOfIdentity = AST.ClassDeclaration<Identity<Of>, Identity<Of>, Identity<Of>, Identity<Of>>

fun ClassDeclarationOfLenient.validate(): ClassDeclarationOfIdentity? =
    this.mapMethodW { Identity(it.into().unwrapOr { return null }.validate() ?: return null) }

fun Type<Lenient<Of>>.validate(): Type<Identity<Of>>? =
    this.mapOtherW { Identity(it.into().unwrapOr { return null }.into().validate() ?: return null) }

fun AST.Parameter<Lenient<Of>>.validate(): AST.Parameter<Identity<Of>>? =
    this.mapOtherW { Identity(it.into().unwrapOr { return null }.into().validate() ?: return null) }

// fun toValidClassDeclaration(decl: AST.ClassDeclaration<Lenient<Of>, Lenient<Of>, Lenient<Of>, Lenient<Of>>): AST.ClassDeclaration<Identity<Of>, Identity<Of>, Identity<Of>, Identity<Of>>? {
//    val member = decl.member
//        .map { it.into() }
//        .map {
//            when (it) {
//                is Lenient.Valid -> toValidMember(it.node) ?: return null
//                is Lenient.Error -> return null
//            }
//        }
//        .map { Identity(it) }
//    return AST.ClassDeclaration(decl.name, member)
// }

typealias ClassMemberOfLenient = AST.ClassMember<Lenient<Of>, Lenient<Of>, Lenient<Of>>
typealias ClassMemberOfIdentity = AST.ClassMember<Identity<Of>, Identity<Of>, Identity<Of>>

fun ClassMemberOfLenient.validate(): ClassMemberOfIdentity? {
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

typealias BlockOfLenient = AST.Block<Lenient<Of>, Lenient<Of>, Lenient<Of>>
typealias BlockOfIdentity = AST.Block<Identity<Of>, Identity<Of>, Identity<Of>>

fun AST.Block<Lenient<Of>, Lenient<Of>, Lenient<Of>>.validateBlock(): AST.Block<Identity<Of>, Identity<Of>, Identity<Of>>? {
    val statements = this.statements.map { it.into().unwrapOr { return null }.into().validate() ?: return null }
        .map { Identity(it) }

    return AST.Block(statements)
}

typealias StatementOfLenient = AST.Statement<Lenient<Of>, Lenient<Of>, Lenient<Of>>
typealias StatementOfIdentity = AST.Statement<Identity<Of>, Identity<Of>, Identity<Of>>

fun StatementOfLenient.validate(): StatementOfIdentity? {
    return this.mapStmt(
        { blockStatement -> Identity(blockStatement.into().unwrapOr { return null }.into().validate() ?: return null) },
        { statement -> Identity(statement.into().unwrapOr { return null }.into().validate() ?: return null) },
        { expression -> Identity(expression.into().unwrapOr { return null }.into().validate() ?: return null) }
    )
}

typealias BlockStatementOfLenient = AST.BlockStatement<Lenient<Of>, Lenient<Of>, Lenient<Of>>
typealias BlockStatementOfIdentity = AST.BlockStatement<Identity<Of>, Identity<Of>, Identity<Of>>

fun BlockStatementOfLenient.validate(): BlockStatementOfIdentity? {
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

private object DocsCodeSnippets {
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

private object WrappingExample {
    val wrappedValue: Kind<Identity<Of>, AST.Expression<Of, Identity<Of>>> = TODO()
    val unwrappedValue: AST.Expression<Of, Identity<Of>> = wrappedValue.unwrap(UnwrappableIdentity)
}
