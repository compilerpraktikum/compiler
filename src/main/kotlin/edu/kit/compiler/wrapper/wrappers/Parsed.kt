package edu.kit.compiler.wrapper.wrappers

import edu.kit.compiler.ast.AST
import edu.kit.compiler.ast.Type
import edu.kit.compiler.lex.SourceRange
import edu.kit.compiler.lex.Symbol
import edu.kit.compiler.semantic.AstNode
import edu.kit.compiler.semantic.SemanticType

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

    fun getAsValid() = when (this) {
        is Error -> null
        is Valid -> this.node
    }

    val isValid: Boolean get() = when (this) {
        is Error -> false
        is Valid -> true
    }

    inline fun <B> map(m: (A) -> B): Parsed<B> = when (this) {
        is Error -> Error(this.range, this.node?.let(m))
        is Valid -> Valid(this.range, m(this.node))
    }

    /**
     * Replaces the [Error.node] and [Valid.node] values with the given [newNode]
     * If [Error.node] is null, the value is *still* replaces by [newNode]
     */
    fun <B> replaceNode(newNode: B): Parsed<B> = when (this) {
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
}

inline fun <A> Parsed<A>.unwrapOr(handle: () -> A): A = when (this) {
    is Parsed.Error -> handle()
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

private fun Symbol.toIdentifier(sourceRange: SourceRange): AstNode.Identifier = AstNode.Identifier(this, sourceRange)

private fun Parsed<Symbol>.validate(): AstNode.Identifier? =
    this.unwrapOr { return null }.toIdentifier(this.range)

fun Parsed<AST.Program>.validate(): AstNode.Program? =
    unwrapOr { return null }.let { program ->
        program.classes
            .map { it.validate() ?: return null }
            .let {
                AstNode.Program(it, this.range)
            }
    }

fun Parsed<AST.ClassDeclaration>.validate(): AstNode.ClassDeclaration? = unwrapOr { return null }.let {
    val members = it.member.map { member ->
        member.validate() ?: return null
    }
    AstNode.ClassDeclaration(it.name.validate() ?: return null, members, this.range)
}

fun Parsed<AST.ClassMember>.validate(): AstNode.ClassMember? = unwrapOr { return null }.let { classMember ->
    when (classMember) {
        is AST.Field -> AstNode.ClassMember.FieldDeclaration(
            classMember.name.validate() ?: return null,
            classMember.type.validate() ?: return null,
            this.range
        )
        is AST.MainMethod -> AstNode.ClassMember.SubroutineDeclaration.MainMethodDeclaration(
            classMember.returnType.validate() ?: return null,
            classMember.name.validate() ?: return null,
            classMember.throwsException?.let { it.validate() ?: return null },
            classMember.block.validate() ?: return null,
            classMember.parameters.map { it.validate() ?: return null },
            this.range
        )

        is AST.Method ->
            AstNode.ClassMember.SubroutineDeclaration.MethodDeclaration(
                classMember.returnType.validate() ?: return null,
                classMember.name.validate() ?: return null,
                classMember.throwsException?.let { it.validate() ?: return null },
                classMember.block.validate() ?: return null,
                classMember.parameters.map { it.validate() ?: return null }, this.range
            )
    }
}

private fun Parsed<AST.Parameter>.validate(): AstNode.ClassMember.SubroutineDeclaration.Parameter? =
    unwrapOr { return null }.let { parameter ->
        AstNode.ClassMember.SubroutineDeclaration.Parameter(
            parameter.name.validate() ?: return null,
            parameter.type.validate() ?: return null,
            this.range
        )
    }

private fun AST.Block.validate(range: SourceRange): AstNode.Statement.Block? =
    statements.map {
        it.validate() ?: return null
    }.let {
        AstNode.Statement.Block(it, range)
    }

private fun Parsed<AST.Block>.validate(): AstNode.Statement.Block? = unwrapOr { return null }.validate(this.range)

@JvmName("validateASTBlockStatement")
private fun Parsed<AST.BlockStatement>.validate(): AstNode.Statement? = unwrapOr { return null }.let { blockStatement ->
    when (blockStatement) {
        is AST.LocalVariableDeclarationStatement -> AstNode.Statement.LocalVariableDeclaration(
            blockStatement.name.validate() ?: return null,
            blockStatement.type.validate() ?: return null,
            blockStatement.initializer?.let { it.validate() ?: return null },
            this.range
        )
        is AST.StmtWrapper ->
            blockStatement.statement.validate(this.range)
    }
}

private fun AST.Statement.validate(sourceRange: SourceRange): AstNode.Statement? {
    return when (this) {
        is AST.Block -> validate(sourceRange)
        is AST.ExpressionStatement -> AstNode.Statement.ExpressionStatement(
            expression.validate() ?: return null, sourceRange
        )
        is AST.IfStatement -> AstNode.Statement.IfStatement(
            condition.validate() ?: return null,
            trueStatement.validate() ?: return null,
            falseStatement?.let { it.validate() ?: return null },
            sourceRange
        )
        is AST.ReturnStatement -> AstNode.Statement.ReturnStatement(
            expression?.let {
                it.validate() ?: return null
            },
            sourceRange
        )
        is AST.WhileStatement ->
            AstNode.Statement.WhileStatement(
                condition.validate() ?: return null,
                statement.validate() ?: return null,
                sourceRange
            )
    }
}

private fun Parsed<AST.Statement>.validate(): AstNode.Statement? =
    unwrapOr { return null }.validate(this.range)

private fun Parsed<AST.Expression>.validate(): AstNode.Expression? = unwrapOr { return null }.let { expression ->
    when (expression) {
        is AST.ArrayAccessExpression ->
            AstNode.Expression.ArrayAccessExpression(
                expression.target.validate() ?: return null,
                expression.index.validate() ?: return null, this.range
            )
        is AST.BinaryExpression ->
            AstNode.Expression.BinaryOperation(
                expression.left.validate() ?: return null,
                expression.right.validate() ?: return null,
                expression.operation,
                this.range
            )
        is AST.FieldAccessExpression ->
            AstNode.Expression.FieldAccessExpression(
                expression.target.validate() ?: return null,
                expression.field.validate() ?: return null,
                this.range
            )
        is AST.IdentifierExpression ->
            AstNode.Expression.IdentifierExpression(expression.name.validate() ?: return null, this.range)
        is AST.LiteralExpression<*> ->
            when (expression.value) {
                "null" -> AstNode.Expression.LiteralExpression.LiteralNullExpression(this.range)
                "this" -> TODO("fix 'this' is not a literal")
                is String -> AstNode.Expression.LiteralExpression.LiteralIntExpression(
                    expression.value,
                    this.range
                )
                is Boolean -> AstNode.Expression.LiteralExpression.LiteralBoolExpression(
                    expression.value,
                    this.range
                )
                else -> null
            }
        is AST.MethodInvocationExpression ->
            AstNode.Expression.MethodInvocationExpression(
                expression.target?.let { it.validate() ?: return null },
                expression.method.validate() ?: return null,
                expression.arguments.map {
                    it.validate() ?: return null
                },
                this.range
            )
        is AST.NewArrayExpression ->
            AstNode.Expression.NewArrayExpression(
                expression.type.validate() ?: return null,
                expression.length.validate() ?: return null,
                this.range
            )
        is AST.NewObjectExpression ->
            AstNode.Expression.NewObjectExpression(expression.clazz.validate() ?: return null, this.range)
        is AST.UnaryExpression ->
            AstNode.Expression.UnaryOperation(
                expression.expression.validate() ?: return null,
                expression.operation,
                this.range
            )
    }
}

fun Parsed<Type.Array.ArrayType>.validate(): SemanticType.Array? = unwrapOr { return null }.let {
    SemanticType.Array(it.elementType.validate() ?: return null)
}

fun Parsed<Type>.validate(): SemanticType? = unwrapOr { return null }.let { type ->
    when (type) {
        is Type.Array -> SemanticType.Array(type.arrayType.elementType.validate() ?: return null)
        is Type.Boolean -> SemanticType.Boolean
        is Type.Class -> SemanticType.Class(type.name.validate() ?: return null)
        is Type.Integer -> SemanticType.Integer
        is Type.Void -> SemanticType.Void
    }
}
