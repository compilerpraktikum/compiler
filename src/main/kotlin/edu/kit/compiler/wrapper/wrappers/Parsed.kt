package edu.kit.compiler.wrapper.wrappers

import edu.kit.compiler.ast.AST
import edu.kit.compiler.ast.Type
import edu.kit.compiler.lex.SourceRange
import edu.kit.compiler.semantic.AstNode
import edu.kit.compiler.semantic.ParsedType
import kotlin.math.exp

/**
 * An AST-Node wrapper, that indicates, that the contained AST-Node may or may not be valid.
 *
 * - The `Error` variant denotes, that the AST-Node itself is invalid
 * - The `Valid(c: A)` variant denotes, that the AST-Node itself is valid, but `A` might contain invalid nodes.
 */
sealed class Parsed<out A>(open val position: SourceRange) {
    /**
     * Error kind, may contain a node
     *
     * @see Parsed
     */
    data class Error<out A>(override val position: SourceRange, val node: A?) : Parsed<A>(position)

    /**
     * Valid kind, contains the node
     *
     * @see Parsed
     */
    data class Valid<out A>(override val position: SourceRange, val node: A) : Parsed<A>(position)

    fun getAsValid() = when (this) {
        is Error -> null
        is Valid -> this.node
    }

    val sourceRange: SourceRange
        get() = when (this) {
            is Error -> position
            is Valid -> position
        }

    inline fun <B> map(m: (A) -> B): Parsed<B> = when (this) {
        is Error -> Error(this.position, this.node?.let(m))
        is Valid -> Valid(this.position, m(this.node))
    }

    inline fun mapPosition(m: (SourceRange) -> SourceRange): Parsed<A> = when (this) {
        is Error -> Error(m(position), node)
        is Valid -> Valid(m(position), node)
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
    is Parsed.Valid -> Parsed.Error(this.position, this.node)
}

/**
 * Wrap an element in a [Parsed.Error] instance
 */
fun <A> A?.wrapErroneous(position: SourceRange): Parsed.Error<A> = Parsed.Error(position, this)

fun Parsed<AST.Program>.validate(): AstNode.Program? =
    unwrapOr { return null }.let { program ->
        program.classes
            .map { it.validate() ?: return null }
            .let {
                AstNode.Program(it, sourceRange)
            }
    }

fun Parsed<AST.ClassDeclaration>.validate(): AstNode.ClassDeclaration? = unwrapOr { return null }.let {
    val members = it.member.map {
        it.validate() ?: return null
    }
    AstNode.ClassDeclaration(it.name, members, this.sourceRange)
}

fun Parsed<AST.ClassMember>.validate(): AstNode.ClassMember? = unwrapOr { return null }.let { classMember ->
    when (classMember) {
        is AST.Field -> AstNode.ClassMember.FieldDeclaration(
            classMember.name,
            classMember.type.validate() ?: return null,
            this.sourceRange
        )
        is AST.MainMethod -> AstNode.ClassMember.SubroutineDeclaration.MainMethodDeclaration(
            classMember.returnType.validate() ?: return null,
            classMember.name,
            classMember.throwsException,
            classMember.block.validate() ?: return null,
            classMember.parameters.map { it.validate() ?: return null },
            sourceRange
        )

        is AST.Method ->
            AstNode.ClassMember.SubroutineDeclaration.MethodDeclaration(
                classMember.returnType.validate() ?: return null,
                classMember.name,
                classMember.throwsException,
                classMember.block.validate() ?: return null,
                classMember.parameters.map { it.validate() ?: return null }, sourceRange
            )
    }
}

private fun Parsed<AST.Parameter>.validate(): AstNode.ClassMember.SubroutineDeclaration.Parameter? =
    unwrapOr { return null }.let { parameter ->
        AstNode.ClassMember.SubroutineDeclaration.Parameter(
            parameter.name,
            parameter.type.validate() ?: return null,
            sourceRange
        )
    }

private fun AST.Block.validate(range: SourceRange): AstNode.Statement.Block? =
    statements.map {
        it.validate() ?: return null
    }.let {
        AstNode.Statement.Block(it, range)
    }

private fun Parsed<AST.Block>.validate(): AstNode.Statement.Block? = unwrapOr { return null }.let { block ->
    block.validate(sourceRange)
}

class Asd<A> {
    inner class Wasd(val asds: List<A>)
}

@JvmName("validateASTBlockStatement")
private fun Parsed<AST.BlockStatement>.validate(): AstNode.Statement? = unwrapOr { return null }.let { blockStatement ->
    when (blockStatement) {
        is AST.LocalVariableDeclarationStatement -> AstNode.Statement.LocalVariableDeclaration(
            blockStatement.name,
            blockStatement.type.validate() ?: return null,
            blockStatement.initializer?.run { validate() ?: return null },
            sourceRange
        )
        is AST.StmtWrapper ->
            blockStatement.statement.validate(sourceRange)
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
            falseStatement?.run { validate() ?: return null },
            sourceRange
        )
        is AST.ReturnStatement -> AstNode.Statement.ReturnStatement(expression?.run {
            validate() ?: return null
        }, sourceRange)
        is AST.WhileStatement ->
            AstNode.Statement.WhileStatement(
                condition.validate() ?: return null,
                statement.validate() ?: return null,
                sourceRange
            )
    }
}

private fun Parsed<AST.Statement>.validate(): AstNode.Statement? =
    unwrapOr { return null }.validate(this.sourceRange)

private fun Parsed<AST.Expression>.validate(): AstNode.Expression? = unwrapOr { return null }.let { expression ->
    when (expression) {
        is AST.ArrayAccessExpression ->
            AstNode.Expression.ArrayAccessExpression(
                expression.target.validate() ?: return null,
                expression.index.validate() ?: return null, sourceRange
            )
        is AST.BinaryExpression ->
            AstNode.Expression.BinaryOperation(
                expression.left.validate() ?: return null,
                expression.right.validate() ?: return null,
                expression.operation,
                sourceRange
            )
        is AST.FieldAccessExpression ->
            AstNode.Expression.FieldAccessExpression(
                expression.target.validate() ?: return null,
                expression.field,
                sourceRange
            )
        is AST.IdentifierExpression ->
            AstNode.Expression.IdentifierExpression(expression.name, sourceRange)
        is AST.LiteralExpression<*> ->
            when (expression.value) {
                "null" -> AstNode.Expression.LiteralExpression.LiteralNullExpression(sourceRange)
                "this" -> TODO("fix 'this' is not a literal")
                is String -> AstNode.Expression.LiteralExpression.LiteralIntExpression(expression.value, sourceRange)
                is Boolean -> AstNode.Expression.LiteralExpression.LiteralBoolExpression(expression.value, sourceRange)
                else -> null
            }
        is AST.MethodInvocationExpression ->
            AstNode.Expression.MethodInvocationExpression(
                expression.target?.validate() ?: return null,
                expression.method,
                expression.arguments.map {
                    it.validate() ?: return null
                }, sourceRange
            )
        is AST.NewArrayExpression ->
            AstNode.Expression.NewArrayExpression(
                expression.type.validate() ?: return null,
                expression.length.validate() ?: return null,
                sourceRange
            )
        is AST.NewObjectExpression ->
            AstNode.Expression.NewObjectExpression(expression.clazz, sourceRange)
        is AST.UnaryExpression ->
            AstNode.Expression.UnaryOperation(
                expression.expression.validate() ?: return null,
                expression.operation,
                sourceRange
            )
    }
}

fun Parsed<Type.Array.ArrayType>.validate(): ParsedType.ArrayType? = unwrapOr { return null }.let {
    ParsedType.ArrayType(it.elementType.validate() ?: return null)
}

fun Parsed<Type>.validate(): ParsedType? = unwrapOr { return null }.let { type ->
    when (type) {
        is Type.Array -> ParsedType.ArrayType(type.arrayType.elementType.validate() ?: return null)
        is Type.Boolean -> ParsedType.BoolType
        is Type.Class -> ParsedType.ComplexType(type.name)
        is Type.Integer -> ParsedType.IntType
        is Type.Void -> ParsedType.VoidType
    }
}
