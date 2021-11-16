package edu.kit.compiler.ast

import edu.kit.compiler.Token
import edu.kit.compiler.ast.AST.wrapBlockStatement
import edu.kit.compiler.lex.StringTable
import edu.kit.compiler.lex.Symbol

sealed class Type<out TypeWrapper>() : Kind<Type<Of>, TypeWrapper> {

    companion object {
        fun <TypeWrapper> arrayOf(elementType: Kind<TypeWrapper, Kind<Type<Of>, TypeWrapper>>) =
            Array(Array.ArrayType(elementType))
    }

    object Void : Type<Nothing>()

    object Integer : Type<Nothing>()

    object Boolean : Type<Nothing>()

    data class Array<out TypeWrapper>(
        val arrayType: ArrayType<TypeWrapper>
    ) : Type<TypeWrapper>() {
        @JvmInline
        value class ArrayType<out TypeWrapper>(
            val elementType: Kind<TypeWrapper, Kind<Type<Of>, TypeWrapper>>
        ) : Kind<ArrayType<Of>, TypeWrapper> {
            fun wrapArray() = Array(this)
        }
    }

    data class Class(
        val name: Symbol
    ) : Type<Nothing>()
}

public val Type<Identity<Of>>.baseType: Type<Nothing>
    get() = when (this) {
        is Type.Array -> this.arrayType.elementType.into().v.into().baseType
        is Type.Void -> this
        is Type.Integer -> this
        is Type.Class -> this
        is Type.Boolean -> this
    }

/**
 * Sealed AST-Node class structure
 */
object AST {

    /************************************************
     ** Class
     ************************************************/

    /**
     * @param ExpressionWrapper Wrapper for Expression Nodes
     * @param StatementWrapper Wrapper for Statements and Block Statement Nodes
     * @param MethodWrapper Wrapper for Method and Field Nodes
     * @param ClassWrapper Wrapper for Classes
     * @param OtherNodeWrapper Wrapper for the rest other Nodes, could fail in parsing
     */
    data class Program<ExpressionWrapper, StatementWrapper, MethodWrapper, ClassWrapper, OtherNodeWrapper>(
        val classes: List<Kind<ClassWrapper, ClassDeclaration<ExpressionWrapper, StatementWrapper, MethodWrapper, OtherNodeWrapper>>>,
    )

    data class ClassDeclaration<ExpressionWrapper, StatementWrapper, MethodWrapper, OtherNodeWrapper>(
        val name: Symbol,
        val member: List<Kind<MethodWrapper, ClassMember<ExpressionWrapper, StatementWrapper, OtherNodeWrapper>>>,
    )

    sealed class ClassMember<out ExpressionWrapper, out StatementWrapper, out OtherNodeWrapper> {
        val memberName: Symbol
            get() = when (this) {
                is Field -> name
                is MainMethod -> name
                is Method -> name
            }
    }

    data class Field<out OtherNodeWrapper>(
        val name: Symbol,
        val type: Kind<OtherNodeWrapper, Kind<Type<Of>, OtherNodeWrapper>>,
    ) : ClassMember<Nothing, Nothing, OtherNodeWrapper>()

    data class Method<out E, out S, out O>(
        val name: Symbol,
        val returnType: Kind<O, Kind<Type<Of>, O>>,
        val parameters: List<Kind<O, Kind<Parameter<Of>, O>>>,
        val block: Kind<S, Block<E, S, O>>,
        val throwsException: Symbol? = null,
    ) : ClassMember<E, S, O>()

    data class MainMethod<out E, out S, O>(
        // we need not only block but the rest too, for in semantical analysis we need to check exact match on
        // "public static void main(String[] $SOMEIDENTIFIER)"
        val name: Symbol,
        val returnType: Kind<O, Kind<Type<Of>, O>>,
        val parameters: List<Kind<O, Kind<Parameter<Of>, O>>>,
        val block: Kind<S, Block<E, S, O>>,
        val throwsException: Symbol? = null,
    ) : ClassMember<E, S, O>()

    data class Parameter<out OtherNodeWrapper>(
        val name: Symbol,
        val type: Kind<OtherNodeWrapper, Kind<Type<Of>, OtherNodeWrapper>>,
    ) : Kind<Parameter<Of>, OtherNodeWrapper>

    /************************************************
     ** Statement
     ************************************************/

    sealed class BlockStatement<out S, out E, out O> : Kind<BlockStatement<Of, E, O>, S>

    data class LocalVariableDeclarationStatement<E, O>(
        val name: Symbol,
        val type: Kind<O, Kind<Type<Of>, O>>,
        val initializer: Kind<E, Kind<Expression<Of, O>, E>>?,
    ) : BlockStatement<Nothing, E, O>()

    data class StmtWrapper<S, E, O>(val statement: Statement<S, E, O>) : BlockStatement<S, E, O>()

    sealed class Statement<out S, out E, out O> : Kind<Statement<Of, E, O>, S>

    fun <S, E, O> Statement<S, E, O>.wrapBlockStatement(): StmtWrapper<S, E, O> = StmtWrapper(this)

    val emptyStatement = Block<Nothing, Nothing, Nothing>(listOf())

    data class Block<out S, out E, out O>(
        val statements: List<Kind<S, Kind<BlockStatement<Of, E, O>, S>>>,
    ) : Statement<S, E, O>()

    data class IfStatement<out S, out E, out O>(
        val condition: Kind<E, Kind<Expression<Of, O>, E>>,
        val trueStatement: Kind<S, Kind<Statement<Of, E, O>, S>>,
        val falseStatement: Kind<S, Kind<Statement<Of, E, O>, S>>?
    ) : Statement<S, E, O>()

    data class WhileStatement<out S, out E, out O>(
        val condition: Kind<E, Kind<Expression<Of, O>, E>>,
        val statement: Kind<S, Kind<Statement<Of, E, O>, S>>,
    ) : Statement<S, E, O>()

    data class ReturnStatement<out E, out O>(
        val expression: Kind<E, Kind<Expression<Of, O>, E>>?,
    ) : Statement<Nothing, E, O>()

    data class ExpressionStatement<out E, out O>(
        val expression: Kind<E, Kind<Expression<Of, O>, E>>,
    ) : Statement<Nothing, E, O>()

    /************************************************
     ** Expression
     ************************************************/

    sealed class Expression<out E, out O> : Kind<Expression<Of, O>, E>

    data class BinaryExpression<E, O>(
        val left: Kind<E, Kind<Expression<Of, O>, E>>,
        val right: Kind<E, Kind<Expression<Of, O>, E>>,
        val operation: Operation
    ) : Expression<E, O>() {
        enum class Operation(
            val precedence: Int,
            val associativity: Associativity,
            val repr: String
        ) {
            ASSIGNMENT(1, Associativity.RIGHT, "="),

            // logical
            OR(2, Associativity.LEFT, "||"),

            AND(3, Associativity.LEFT, "&&"),

            // relational
            EQUALS(4, Associativity.LEFT, "=="),
            NOT_EQUALS(4, Associativity.LEFT, "!="),

            LESS_THAN(5, Associativity.LEFT, "<"),
            GREATER_THAN(5, Associativity.LEFT, ">"),
            LESS_EQUALS(5, Associativity.LEFT, "<="),
            GREATER_EQUALS(5, Associativity.LEFT, ">="),

            // arithmetical
            ADDITION(6, Associativity.LEFT, "+"),
            SUBTRACTION(6, Associativity.LEFT, "-"),

            MULTIPLICATION(7, Associativity.LEFT, "*"),
            DIVISION(7, Associativity.LEFT, "/"),
            MODULO(7, Associativity.LEFT, "%");

            enum class Associativity {
                LEFT,
                RIGHT,
            }
        }
    }

    data class UnaryExpression<out E, out O>(
        val expression: Kind<E, Kind<Expression<Of, O>, E>>,
        val operation: Operation
    ) : Expression<E, O>() {
        enum class Operation(
            val repr: String
        ) {
            NOT("!"),
            MINUS("-"),
        }
    }

    data class MethodInvocationExpression<E, O>(
        val target: Kind<E, Kind<Expression<Of, O>, E>>?,
        val method: Symbol,
        val arguments: List<Kind<E, Kind<Expression<Of, O>, E>>>
    ) : Expression<E, O>()

    data class FieldAccessExpression<E, O>(
        val target: Kind<E, Kind<Expression<Of, O>, E>>,
        val field: Symbol,
    ) : Expression<E, O>()

    data class ArrayAccessExpression<E, O>(
        val target: Kind<E, Kind<Expression<Of, O>, E>>,
        val index: Kind<E, Kind<Expression<Of, O>, E>>,
    ) : Expression<E, O>()

    /************************************************
     ** Primary expression
     ************************************************/

    data class IdentifierExpression(
        val name: Symbol,
    ) : Expression<Nothing, Nothing>()

    data class LiteralExpression<T>(
        val value: T,
    ) : Expression<Nothing, Nothing>()

    data class NewObjectExpression(
        val clazz: Symbol,
    ) : Expression<Nothing, Nothing>()

    data class NewArrayExpression<E, out O>(
        val type: Kind<O, Kind<Type.Array.ArrayType<Of>, O>>,
        val length: Kind<E, Kind<Expression<Of, O>, E>>,
    ) : Expression<E, O>()
}

fun Token.Operator.Type.toASTOperation(): AST.BinaryExpression.Operation? = when (this) {
    Token.Operator.Type.NoEq -> AST.BinaryExpression.Operation.NOT_EQUALS
    Token.Operator.Type.Mul -> AST.BinaryExpression.Operation.MULTIPLICATION
    Token.Operator.Type.Plus -> AST.BinaryExpression.Operation.ADDITION
    Token.Operator.Type.Minus -> AST.BinaryExpression.Operation.SUBTRACTION
    Token.Operator.Type.Div -> AST.BinaryExpression.Operation.DIVISION
    Token.Operator.Type.LtEq -> AST.BinaryExpression.Operation.LESS_EQUALS
    Token.Operator.Type.Lt -> AST.BinaryExpression.Operation.LESS_THAN
    Token.Operator.Type.Eq -> AST.BinaryExpression.Operation.EQUALS
    Token.Operator.Type.Assign -> AST.BinaryExpression.Operation.ASSIGNMENT
    Token.Operator.Type.GtEq -> AST.BinaryExpression.Operation.GREATER_EQUALS
    Token.Operator.Type.Gt -> AST.BinaryExpression.Operation.GREATER_THAN
    Token.Operator.Type.Mod -> AST.BinaryExpression.Operation.MODULO
    Token.Operator.Type.And -> AST.BinaryExpression.Operation.AND
    Token.Operator.Type.Or -> AST.BinaryExpression.Operation.OR
    else -> null
}

abstract class AstDsl<T>(var res: MutableList<T> = mutableListOf())

/**
 * **ONLY** use for testing in the DSL below. Real identifiers need to be converted to symbols using the [StringTable].
 */
private fun String.toSymbol() = Symbol(this, isKeyword = false)

class ClassDeclarationDsl(res: MutableList<Lenient<AST.ClassDeclaration<Lenient<Of>, Lenient<Of>, Lenient<Of>, Lenient<Of>>>> = mutableListOf()) :
    AstDsl<Lenient<AST.ClassDeclaration<Lenient<Of>, Lenient<Of>, Lenient<Of>, Lenient<Of>>>>(res) {
    fun clazz(name: String, block: ClassMemberDsl.() -> Unit) {
        val members = ClassMemberDsl().also { it.block() }
        this.res.add(
            AST.ClassDeclaration<Lenient<Of>, Lenient<Of>, Lenient<Of>, Lenient<Of>>(
                name.toSymbol(),
                members.res
            ).wrapValid()
        )
    }
}

class ClassMemberDsl(res: MutableList<Lenient<AST.ClassMember<Lenient<Of>, Lenient<Of>, Lenient<Of>>>> = mutableListOf()) :
    AstDsl<Lenient<AST.ClassMember<Lenient<Of>, Lenient<Of>, Lenient<Of>>>>(res) {

    fun param(name: String, type: Type<Lenient<Of>>) = AST.Parameter<Lenient<Of>>(name.toSymbol(), type.wrapValid())

    fun field(name: String, type: Type<Lenient<Of>>) {
        this.res.add(AST.Field(name.toSymbol(), type.wrapValid()).wrapValid())
    }

    fun mainMethod(
        name: String,
        returnType: Type<Lenient<Of>>,
        vararg parameters: AST.Parameter<Lenient<Of>>,
        throws: String? = null,
        block: BlockStatementDsl.() -> Unit
    ) {

        this.res.add(
            AST.MainMethod<Lenient<Of>, Lenient<Of>, Lenient<Of>>(
                name.toSymbol(),
                returnType.wrapValid(),
                parameters.toList().map { it.wrapValid() },
                AST.Block<Lenient<Of>, Lenient<Of>, Lenient<Of>>(BlockStatementDsl().also(block).res).wrapValid(),
                throws?.toSymbol()
            ).wrapValid()
        )
    }

    fun method(
        name: String,
        returnType: Type<Lenient<Of>>,
        vararg parameters: AST.Parameter<Lenient<Of>>,
        throws: String? = null,
        block: BlockStatementDsl.() -> Unit
    ) {
        this.res.add(
            AST.Method<Lenient<Of>, Lenient<Of>, Lenient<Of>>(
                name.toSymbol(),
                returnType.wrapValid(),
                parameters.toList().map { it.wrapValid() },
                AST.Block<Lenient<Of>, Lenient<Of>, Lenient<Of>>(BlockStatementDsl().also(block).res).wrapValid(),
                throws?.toSymbol()
            ).wrapValid()
        )
    }
}

object ExprDsl {
    fun <T> literal(v: T) = AST.LiteralExpression(v)
    fun binOp(
        op: AST.BinaryExpression.Operation,
        left: ExprDsl.() -> AST.Expression<Lenient<Of>, Lenient<Of>>,
        right: ExprDsl.() -> AST.Expression<Lenient<Of>, Lenient<Of>>
    ) =
        AST.BinaryExpression(ExprDsl.left().wrapValid(), ExprDsl.right().wrapValid(), op)

    fun ident(name: String) = AST.IdentifierExpression(name.toSymbol())

    fun arrayAccess(
        target: ExprDsl.() -> AST.Expression<Lenient<Of>, Lenient<Of>>,
        index: ExprDsl.() -> AST.Expression<Lenient<Of>, Lenient<Of>>
    ) = AST.ArrayAccessExpression(ExprDsl.target().wrapValid(), ExprDsl.index().wrapValid())

    fun fieldAccess(
        left: ExprDsl.() -> AST.Expression<Lenient<Of>, Lenient<Of>>,
        field: String
    ) = AST.FieldAccessExpression(ExprDsl.left().wrapValid(), field.toSymbol())

    fun newArrayOf(
        type: Type.Array.ArrayType<Lenient<Of>>,
        length: ExprDsl.() -> AST.Expression<Lenient<Of>, Lenient<Of>>
    ) = AST.NewArrayExpression(type.wrapValid(), ExprDsl.length().wrapValid())
}

class BlockStatementDsl(val res: MutableList<Lenient<AST.BlockStatement<Lenient<Of>, Lenient<Of>, Lenient<Of>>>> = mutableListOf()) {
    fun localDeclaration(
        name: String,
        type: Type<Lenient<Of>>,
        initializer: (ExprDsl.() -> AST.Expression<Lenient<Of>, Lenient<Of>>)? = null
    ) {
        res.add(
            AST.LocalVariableDeclarationStatement<Lenient<Of>, Lenient<Of>>(
                name.toSymbol(), type.wrapValid(),
                if (initializer != null) {
                    ExprDsl.initializer().wrapValid()
                } else null
            ).wrapValid()
        )
    }

    fun emptyStatement() = res.add(AST.StmtWrapper(AST.emptyStatement).wrapValid())
    fun block(b: BlockStatementDsl.() -> Unit) {
        res.add(AST.StmtWrapper(AST.Block(BlockStatementDsl().also(b).res)).wrapValid())
    }

    fun expressionStatement(expr: ExprDsl.() -> AST.Expression<Lenient<Of>, Lenient<Of>>) {
        res.add(AST.StmtWrapper(AST.ExpressionStatement(ExprDsl.expr().wrapValid())).wrapValid())
    }

    fun ifStmt(
        cond: ExprDsl.() -> AST.Expression<Lenient<Of>, Lenient<Of>>,
        trueStmt: StatementDsl.() -> AST.Statement<Lenient<Of>, Lenient<Of>, Lenient<Of>>,
        falseStmt: (StatementDsl.() -> AST.Statement<Lenient<Of>, Lenient<Of>, Lenient<Of>>)? = null
    ) = res.add(StatementDsl.ifStmt(cond, trueStmt, falseStmt).wrapBlockStatement().wrapValid())
}

object StatementDsl {
    fun ifStmt(
        cond: ExprDsl.() -> AST.Expression<Lenient<Of>, Lenient<Of>>,
        trueStmt: StatementDsl.() -> AST.Statement<Lenient<Of>, Lenient<Of>, Lenient<Of>>,
        falseStmt: (StatementDsl.() -> AST.Statement<Lenient<Of>, Lenient<Of>, Lenient<Of>>)? = null
    ) = AST.IfStatement(
        ExprDsl.cond().wrapValid(),
        StatementDsl.trueStmt().wrapValid(),
        falseStmt?.let { StatementDsl.it().wrapValid() }
    )
}

class StatementsDsl(val res: MutableList<Lenient<AST.Statement<Lenient<Of>, Lenient<Of>, Lenient<Of>>>> = mutableListOf()) {
    fun block(b: StatementsDsl.() -> Unit) {
        res.add(AST.Block(StatementsDsl().also(b).res.map { it.map { it.wrapBlockStatement() } }).wrapValid())
    }

    fun ifStmt(
        cond: ExprDsl.() -> AST.Expression<Lenient<Of>, Lenient<Of>>,
        trueStmt: StatementDsl.() -> AST.Statement<Lenient<Of>, Lenient<Of>, Lenient<Of>>,
        falseStmt: (StatementDsl.() -> AST.Statement<Lenient<Of>, Lenient<Of>, Lenient<Of>>)? = null
    ) = res.add(StatementDsl.ifStmt(cond, trueStmt, falseStmt).wrapValid())
}

fun astOf(block: ClassDeclarationDsl.() -> Unit) =
    ClassDeclarationDsl().also(block).res
