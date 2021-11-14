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

private val exampleChaining: Lenient<AST.Expression<Of>> =
    Lenient.Valid(AST.LiteralExpression(2))
private val exampleChainingInner: Lenient<AST.Expression<Lenient<Of>>> =
    Lenient.Valid(AST.LiteralExpression(2))
private val exampleChainingRecursive: Lenient<AST.Expression<Lenient<Of>>> =
    Lenient.Valid(
        AST.BinaryExpression(
            Lenient.Error("expected expression"),
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
    data class Error(val message: String) : Lenient<Nothing>()
    data class Valid<out A>(val c: A) : Lenient<A>()

    fun getAsValid() = when (this) {
        is Error -> null
        is Valid -> this.c
    }

    fun <B> map(m: (A) -> B): Lenient<B> = when (this) {
        is Error -> this
        is Valid -> Valid(m(this.c))
    }
}

inline fun <A> Lenient<A>.unwrapOr(handle: () -> A): A = when (this) {
    is Lenient.Error -> handle()
    is Lenient.Valid -> this.c
}

inline fun <A> A.wrapValid(): Lenient.Valid<A> = Lenient.Valid(this)

/**
 * extension function to fully apply `Lenient<Of>` to `A` using `Kind<Lenient<Of>, A>`
 */
fun <A> Kind<Lenient<Of>, A>.into(): Lenient<A> = this as Lenient<A>

/**
 * extension function to fully apply `Extension<Of>` to `A` using `Kind<Expression<Of>, A>`
 */
fun <A> Kind<AST.Expression<Of>, A>.into(): AST.Expression<A> = this as AST.Expression<A>

fun <S, E> Kind<AST.Statement<Of, E>, S>.into(): AST.Statement<S, E> = this as AST.Statement<S, E>
fun <S, E> Kind<AST.BlockStatement<Of, E>, S>.into(): AST.BlockStatement<S, E> = this as AST.BlockStatement<S, E>

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

data class TypeAnnotated<A>(val type: Type, val v: A) : Kind<TypeAnnotated<Of>, A>

fun <A> Kind<TypeAnnotated<Of>, A>.into(): TypeAnnotated<A> = this as TypeAnnotated<A>

interface NaturalTransformation<F, G> {
    fun <A> run(fa: Kind<F, A>): Kind<G, A>
}

interface Functor<F> {
    fun <A, B> fmap(f: (A) -> B, fa: Kind<F, A>): Kind<F, B>
}

interface Functor1<T> {
    fun <F, G : Functor<G>> map1(nt: NaturalTransformation<F, G>, tf: Kind<T, F>): Kind<T, G>
}

fun <E, S, D, C, T> AST.Program<E, S, D, C>.map(f: (Kind<C, AST.ClassDeclaration<E, S, D>>) -> Kind<T, AST.ClassDeclaration<E, S, D>>): AST.Program<E, S, D, T> =
    AST.Program(this.classes.map { f(it) })

fun toValidAst(ast: AST.Program<Lenient<Of>, Lenient<Of>, Lenient<Of>, Lenient<Of>>): AST.Program<Identity<Of>, Identity<Of>, Identity<Of>, Identity<Of>>? {
    val classes = ast.classes.map { it.into() }.map {
        when (it) {
            is Lenient.Valid -> toValidClassDeclaration(it.c) ?: return null
            is Lenient.Error -> return null
        }
    }.map { Identity(it) }
    return AST.Program(classes)
}

fun toValidClassDeclaration(decl: AST.ClassDeclaration<Lenient<Of>, Lenient<Of>, Lenient<Of>>): AST.ClassDeclaration<Identity<Of>, Identity<Of>, Identity<Of>>? {
    val member = decl.member
        .map { it.into() }
        .map {
            when (it) {
                is Lenient.Valid -> toValidMember(it.c) ?: return null
                is Lenient.Error -> return null
            }
        }
        .map { Identity(it) }
    return AST.ClassDeclaration(decl.name, member)
}

fun toValidMember(member: AST.ClassMember<Lenient<Of>, Lenient<Of>>): AST.ClassMember<Identity<Of>, Identity<Of>>? {
    return when (member) {
        is AST.Field -> member
        is AST.MainMethod -> AST.MainMethod(
            member.name,
            member.returnType,
            member.parameters,
            Identity(toValidBlock(member.block.into().getAsValid() ?: return null) ?: return null),
            member.throwsException
        )
        is AST.Method -> AST.Method(
            member.name,
            member.returnType,
            member.parameters,
            Identity(toValidBlock(member.block.into().getAsValid() ?: return null) ?: return null),
            member.throwsException
        )
    }
}

fun toValidBlock(block: AST.Block<Lenient<Of>, Lenient<Of>>): AST.Block<Identity<Of>, Identity<Of>>? {
    val statements = block.statements.map { it.into() }.map { it.getAsValid() ?: return null }
        .map { toValidBlockStatement(it.into()) ?: return null }.map { Identity(it) }
    return AST.Block(statements)
}

fun toValidStatement(stmt: AST.Statement<Lenient<Of>, Lenient<Of>>): AST.Statement<Identity<Of>, Identity<Of>>? {
    return when (stmt) {
        is AST.Block -> toValidBlock(stmt)
        is AST.ExpressionStatement -> AST.ExpressionStatement(
            Identity(
                toValidExpression((stmt.expression.into().getAsValid() ?: return null).into()) ?: return null
            )
        )
        is AST.IfStatement ->
            AST.IfStatement(
                Identity(
                    toValidExpression(stmt.condition.into().unwrapOr { return null }.into()) ?: return null
                ),
                Identity(
                    (toValidStatement(stmt.trueStatement.into().unwrapOr { return null }.into()) ?: return null)
                ),
                if (stmt.falseStatement != null) {
                    Identity(
                        toValidStatement(stmt.falseStatement.into().unwrapOr { return null }.into()) ?: return null
                    )
                } else {
                    null
                }

            )
        is AST.ReturnStatement ->
            AST.ReturnStatement(
                if (stmt.expression == null) {
                    null
                } else {
                    Identity(
                        toValidExpression((stmt.expression.into().getAsValid() ?: return null).into()) ?: return null
                    )
                }
            )
        is AST.WhileStatement -> AST.WhileStatement(
            Identity(
                toValidExpression(stmt.condition.into().unwrapOr { return null }.into()) ?: return null
            ),
            Identity(
                toValidStatement(stmt.statement.into().unwrapOr { return null }.into()) ?: return null
            )
        )
    }
}

fun toValidBlockStatement(blockStmt: AST.BlockStatement<Lenient<Of>, Lenient<Of>>): AST.BlockStatement<Identity<Of>, Identity<Of>>? {
    return when (blockStmt) {
        is AST.LocalVariableDeclarationStatement -> AST.LocalVariableDeclarationStatement(
            blockStmt.name, blockStmt.type,
            if (blockStmt.initializer == null) {
                null
            } else {
                Identity(
                    toValidExpression((blockStmt.initializer.into().getAsValid() ?: return null).into()) ?: return null
                )
            }
        )
        is AST.StmtWrapper -> AST.StmtWrapper(toValidStatement(blockStmt.statement) ?: return null)
    }
}

fun toValidExpression(expression: AST.Expression<Lenient<Of>>): AST.Expression<Identity<Of>>? {
    return when (expression) {
        is AST.ArrayAccessExpression ->
            AST.ArrayAccessExpression(
                Identity(
                    toValidExpression(
                        expression.target.into().unwrapOr { return null }.into()
                    ) ?: return null
                ),
                Identity(
                    toValidExpression(
                        expression.index.into().unwrapOr { return null }.into()
                    ) ?: return null
                )
            )
        is AST.BinaryExpression ->
            AST.BinaryExpression(
                Identity(
                    toValidExpression(
                        expression.left.into().unwrapOr { return null }.into()
                    ) ?: return null
                ),
                Identity(
                    toValidExpression(
                        expression.right.into().unwrapOr { return null }.into()
                    ) ?: return null
                ),
                expression.operation
            )
        is AST.FieldAccessExpression ->
            AST.FieldAccessExpression(
                Identity(
                    toValidExpression(
                        expression.target.into().unwrapOr { return null }.into()
                    ) ?: return null
                ),
                expression.field
            )
        is AST.IdentifierExpression -> expression
        is AST.LiteralExpression<*> -> expression
        is AST.MethodInvocationExpression -> AST.MethodInvocationExpression(
            if (expression.target != null) {
                Identity(
                    toValidExpression(
                        expression.target.into().unwrapOr { return null }.into()
                    ) ?: return null
                )
            } else {
                null
            },
            expression.method,
            expression.arguments
                .map { it.into().unwrapOr { return null }.into() }
                .map { toValidExpression(it) ?: return null }
                .map { Identity(it) }
        )

        is AST.NewArrayExpression -> AST.NewArrayExpression(
            expression.type,
            Identity(
                toValidExpression(
                    expression.length.into().unwrapOr { return null }.into()
                ) ?: return null
            ),
            expression.basisType
        )
        is AST.NewObjectExpression -> expression
        is AST.UnaryExpression -> AST.UnaryExpression(
            Identity(
                toValidExpression(
                    expression.expression.into().unwrapOr { return null }.into()
                ) ?: return null
            ),
            expression.operation
        )
    }
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

    private val exampleExpressionOfIdentity: AST.Expression<Identity<Of>> = AST.BinaryExpression(
        Identity(AST.LiteralExpression(2)),
        Identity(AST.LiteralExpression(3)),
        AST.BinaryExpression.Operation.ADDITION
    )

    private object ConstFoldExample {
        fun constFold(i: AST.Expression<ConstValue<Int, Of>>): ConstValue<Int, AST.Expression<ConstValue<Int, Of>>> =
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
