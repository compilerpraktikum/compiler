package edu.kit.compiler.ast

import edu.kit.compiler.lexer.Symbol
import edu.kit.compiler.semantic.SemanticAST
import edu.kit.compiler.semantic.SemanticType
import edu.kit.compiler.source.SourceRange

/**
 * An AST-Node wrapper, that indicates, that the contained AST-Node may or may not be valid.
 *
 * - The `Error` variant denotes, that the AST-Node itself is invalid
 * - The `Valid(c: A)` variant denotes, that the AST-Node itself is valid, but `A` might contain invalid nodes.
 */
sealed class Parsed<out A>(open val range: SourceRange) {
    /**
     * Error kind, may contain a node
     *
     * @see Parsed
     */
    data class Error<out A>(override val range: SourceRange, val node: A? = null) : Parsed<A>(range)

    /**
     * Valid kind, contains the node
     *
     * @see Parsed
     */
    data class Valid<out A>(override val range: SourceRange, val node: A) : Parsed<A>(range)

    fun getAsValid(): A? = when (this) {
        is Error -> null
        is Valid -> this.node
    }

    val isValid: Boolean
        get() = when (this) {
            is Error -> false
            is Valid -> true
        }

    fun getNodeOrNull(): A? = when (this) {
        is Error -> node
        is Valid -> node
    }

    inline fun <B> map(m: (A) -> B): Parsed<B> = when (this) {
        is Error -> Error(this.range, this.node?.let(m))
        is Valid -> Valid(this.range, m(this.node))
    }

    /**
     * Replaces the [Error.node] and [Valid.node] values with the given [newNode]
     * If [Error.node] is null, the value is *still* replaces by [newNode]
     */
    private fun <B> replaceNode(newNode: B): Parsed<B> = when (this) {
        is Error -> Error(this.range, newNode)
        is Valid -> Valid(this.range, newNode)
    }

    /**
     * Replaces the [Error.node] and [Valid.node] values with the value return by [newNode]
     * If [Error.node] is null, the value is *still* replaces by [newNode]
     */
    fun <B> replaceNode(newNode: () -> B): Parsed<B> = replaceNode(newNode())

    inline fun <B> cases(onValid: (A) -> B, onError: (A?) -> B): Parsed<B> = when (this) {
        is Error -> Error(this.range, onError(this.node))
        is Valid -> Valid(this.range, onValid(this.node))
    }

    inline fun mapPosition(m: (SourceRange) -> SourceRange): Parsed<A> = when (this) {
        is Error -> Error(m(range), node)
        is Valid -> Valid(m(range), node)
    }

    interface Packer {
        fun <B> pack(c: B): Parsed<B>
    }

    inline fun <B> casePack(f: (A, Packer) -> Parsed<B>): Parsed<B> = when (this) {
        is Valid -> f(
            this.node,
            object : Packer {
                override fun <B> pack(c: B): Parsed<B> = Valid(this@Parsed.range, c)
            }
        )
        is Error ->
            if (this.node == null) {
                Error(this.range, this.node)
            } else {
                f(
                    this.node,
                    object : Packer {
                        override fun <B> pack(c: B): Parsed<B> = Error(this@Parsed.range, c)
                    }
                )
            }
    }
}

inline fun <A> Parsed<A>.unwrapOr(handle: () -> A): A = when (this) {
    is Parsed.Error -> this.node ?: handle()
    is Parsed.Valid -> this.node
}

fun <A> A.wrapValid(position: SourceRange): Parsed.Valid<A> = Parsed.Valid(position, this)

/**
 * Convert a [Parsed.Valid] to [Parsed.Error] (or keep [Parsed.Error])
 */
fun <A> Parsed<A>.markErroneous(): Parsed.Error<A> = when (this) {
    is Parsed.Error -> this
    is Parsed.Valid -> Parsed.Error(this.range, this.node)
}

/**
 * Wrap an element in a [Parsed.Error] instance
 */
fun <A> A?.wrapErroneous(position: SourceRange): Parsed.Error<A> = Parsed.Error(position, this)

/**
 * Wrap an element in a [Parsed] based on the value of [valid].
 * @return [Parsed.Valid] if [valid] was `true`, [Parsed.Error] otherwise
 */
fun <A> A.wrapConditionally(valid: Boolean, range: SourceRange): Parsed<A> = when (valid) {
    true -> wrapValid(range)
    false -> wrapErroneous(range)
}

private fun Symbol.toIdentifier(sourceRange: SourceRange): SemanticAST.Identifier = SemanticAST.Identifier(this, sourceRange)

private fun Parsed<Symbol>.validate(): SemanticAST.Identifier? =
    this.unwrapOr { return null }.toIdentifier(this.range)

fun Parsed<AST.Program>.validate(): SemanticAST.Program? =
    unwrapOr { return null }.let { program ->
        program.classes
            .map { it.validate() ?: return null }
            .let {
                SemanticAST.Program(it, this.range)
            }
    }

fun Parsed<AST.ClassDeclaration>.validate(): SemanticAST.ClassDeclaration? = unwrapOr { return null }.let {
    val members = it.member.map { member ->
        member.validate() ?: return null
    }
    SemanticAST.ClassDeclaration(it.name.validate() ?: return null, members, this.range)
}

fun Parsed<AST.ClassMember>.validate(): SemanticAST.ClassMember? = unwrapOr { return null }.let { classMember ->
    when (classMember) {
        is AST.Field -> SemanticAST.ClassMember.FieldDeclaration(
            classMember.name.validate() ?: return null,
            classMember.type.validate() ?: return null,
            this.range
        )
        is AST.MainMethod -> SemanticAST.ClassMember.SubroutineDeclaration.MainMethodDeclaration(
            classMember.returnType.validate() ?: return null,
            classMember.name.validate() ?: return null,
            classMember.throwsException?.let { it.validate() ?: return null },
            classMember.block.validate() ?: return null,
            classMember.parameters.map { it.validate() ?: return null },
            this.range,
            classMember.returnType.range,
            classMember.block.getAsValid()!!.openingBraceRange.range,
            classMember.block.getAsValid()!!.closingBraceRange.range
        )

        is AST.Method ->
            SemanticAST.ClassMember.SubroutineDeclaration.MethodDeclaration(
                classMember.returnType.validate() ?: return null,
                classMember.name.validate() ?: return null,
                classMember.throwsException?.let { it.validate() ?: return null },
                classMember.block.validate() ?: return null,
                classMember.parameters.map { it.validate() ?: return null },
                this.range,
                classMember.returnType.range,
                classMember.block.getAsValid()!!.openingBraceRange.range,
                classMember.block.getAsValid()!!.closingBraceRange.range
            )
    }
}

private fun Parsed<AST.Parameter>.validate(): SemanticAST.ClassMember.SubroutineDeclaration.Parameter? =
    unwrapOr { return null }.let { parameter ->
        SemanticAST.ClassMember.SubroutineDeclaration.Parameter(
            parameter.name.validate() ?: return null,
            parameter.type.validate() ?: return null,
            parameter.type.range,
            this.range
        )
    }

private fun AST.Block.validate(range: SourceRange): SemanticAST.Statement.Block? =
    statements.map {
        it.validate() ?: return null
    }.let {
        SemanticAST.Statement.Block(it, range)
    }

private fun Parsed<AST.Block>.validate(): SemanticAST.Statement.Block? = unwrapOr { return null }.validate(this.range)

@JvmName("validateASTBlockStatement")
private fun Parsed<AST.BlockStatement>.validate(): SemanticAST.Statement? = unwrapOr { return null }.let { blockStatement ->
    when (blockStatement) {
        is AST.LocalVariableDeclarationStatement -> SemanticAST.Statement.LocalVariableDeclaration(
            blockStatement.name.validate() ?: return null,
            blockStatement.type.validate() ?: return null,
            blockStatement.initializer?.let { it.validate() ?: return null },
            this.range
        )
        is AST.Statement ->
            blockStatement.validate(this.range)
    }
}

private fun AST.Statement.validate(sourceRange: SourceRange): SemanticAST.Statement? {
    return when (this) {
        is AST.Block -> validate(sourceRange)
        is AST.ExpressionStatement -> SemanticAST.Statement.ExpressionStatement(
            expression.validate() ?: return null, sourceRange
        )
        is AST.IfStatement -> SemanticAST.Statement.IfStatement(
            condition.validate() ?: return null,
            trueStatement.validate() ?: return null,
            falseStatement?.let { it.validate() ?: return null },
            sourceRange
        )
        is AST.ReturnStatement -> SemanticAST.Statement.ReturnStatement(
            expression?.let {
                it.validate() ?: return null
            },
            sourceRange
        )
        is AST.WhileStatement ->
            SemanticAST.Statement.WhileStatement(
                condition.validate() ?: return null,
                statement.validate() ?: return null,
                sourceRange
            )
    }
}

private fun Parsed<AST.Statement>.validate(): SemanticAST.Statement? =
    unwrapOr { return null }.validate(this.range)

private fun Parsed<AST.Expression>.validate(): SemanticAST.Expression? = unwrapOr { return null }.let { expression ->
    when (expression) {
        is AST.ArrayAccessExpression ->
            SemanticAST.Expression.ArrayAccessExpression(
                expression.target.validate() ?: return null,
                expression.index.validate() ?: return null, this.range
            )
        is AST.BinaryExpression ->
            SemanticAST.Expression.BinaryOperation(
                expression.left.validate() ?: return null,
                expression.right.validate() ?: return null,
                expression.operation,
                this.range
            )
        is AST.FieldAccessExpression ->
            SemanticAST.Expression.FieldAccessExpression(
                expression.target.validate() ?: return null,
                expression.field.validate() ?: return null,
                this.range
            )
        is AST.IdentifierExpression ->
            SemanticAST.Expression.IdentifierExpression(expression.name.validate() ?: return null, this.range)
        is AST.LiteralExpression ->
            when (expression) {
                is AST.LiteralExpression.Integer -> SemanticAST.Expression.LiteralExpression.LiteralIntExpression(expression.value, expression.isNegated, this.range)
                is AST.LiteralExpression.Boolean -> SemanticAST.Expression.LiteralExpression.LiteralBoolExpression(expression.value, this.range)
                is AST.LiteralExpression.Null -> SemanticAST.Expression.LiteralExpression.LiteralNullExpression(this.range)
                is AST.LiteralExpression.This -> SemanticAST.Expression.LiteralExpression.LiteralThisExpression(this.range)
            }
        is AST.MethodInvocationExpression ->
            SemanticAST.Expression.MethodInvocationExpression(
                expression.target?.let { it.validate() ?: return null },
                expression.method.validate() ?: return null,
                expression.arguments.map {
                    it.validate() ?: return null
                },
                this.range
            )
        is AST.NewArrayExpression ->
            SemanticAST.Expression.NewArrayExpression(
                expression.type.validate() ?: return null,
                expression.length.validate() ?: return null,
                this.range
            )
        is AST.NewObjectExpression ->
            SemanticAST.Expression.NewObjectExpression(
                SemanticType.Class(expression.type.name.validate() ?: return null),
                this.range
            )
        is AST.UnaryExpression ->
            SemanticAST.Expression.UnaryOperation(
                expression.expression.validate() ?: return null,
                expression.operation,
                this.range
            )
    }
}

fun Parsed<AST.Type.Array>.validate(): SemanticType.Array? = unwrapOr { return null }.let {
    SemanticType.Array(it.elementType.validate() ?: return null, it.elementType.range)
}

fun Parsed<AST.Type>.validate(): SemanticType? = unwrapOr { return null }.let { type ->
    when (type) {
        is AST.Type.Array -> SemanticType.Array(type.elementType.validate() ?: return null, type.elementType.range)
        is AST.Type.Boolean -> SemanticType.Boolean
        is AST.Type.Class -> SemanticType.Class(type.name.validate() ?: return null)
        is AST.Type.Integer -> SemanticType.Integer
        is AST.Type.Void -> SemanticType.Void
    }
}
