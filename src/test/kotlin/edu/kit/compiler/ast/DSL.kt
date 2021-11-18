package edu.kit.compiler.ast

import edu.kit.compiler.ast.AST.wrapBlockStatement
import edu.kit.compiler.lex.StringTable
import edu.kit.compiler.lex.Symbol
import edu.kit.compiler.wrapper.Of
import edu.kit.compiler.wrapper.wrappers.Lenient
import edu.kit.compiler.wrapper.wrappers.wrapValid

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
