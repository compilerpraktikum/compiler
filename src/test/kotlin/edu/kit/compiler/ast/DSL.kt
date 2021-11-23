package edu.kit.compiler.ast

import edu.kit.compiler.ast.AST.wrapBlockStatement
import edu.kit.compiler.lex.StringTable
import edu.kit.compiler.lex.Symbol
import edu.kit.compiler.wrapper.wrappers.Lenient
import edu.kit.compiler.wrapper.wrappers.wrapValid

abstract class AstDsl<T>(var res: MutableList<T> = mutableListOf())

/**
 * **ONLY** use for testing in the DSL below. Real identifiers need to be converted to symbols using the [StringTable].
 */
private fun String.toSymbol() = Symbol(this, isKeyword = false)

class ClassDeclarationDsl(res: MutableList<Lenient<AST.ClassDeclaration>> = mutableListOf()) :
    AstDsl<Lenient<AST.ClassDeclaration>>(res) {
    fun clazz(name: String, block: ClassMemberDsl.() -> Unit) {
        val members = ClassMemberDsl().also { it.block() }
        this.res.add(
            AST.ClassDeclaration(
                name.toSymbol(),
                members.res
            ).wrapValid()
        )
    }
}

class ClassMemberDsl(res: MutableList<Lenient<AST.ClassMember>> = mutableListOf()) :
    AstDsl<Lenient<AST.ClassMember>>(res) {

    fun param(name: String, type: Type) = AST.Parameter(name.toSymbol(), type.wrapValid())

    fun field(name: String, type: Type) {
        this.res.add(AST.Field(name.toSymbol(), type.wrapValid()).wrapValid())
    }

    fun mainMethod(
        name: String,
        returnType: Type,
        vararg parameters: AST.Parameter,
        throws: String? = null,
        block: BlockStatementDsl.() -> Unit
    ) {

        this.res.add(
            AST.MainMethod(
                name.toSymbol(),
                returnType.wrapValid(),
                parameters.toList().map { it.wrapValid() },
                AST.Block(BlockStatementDsl().also(block).res).wrapValid(),
                throws?.toSymbol()
            ).wrapValid()
        )
    }

    fun method(
        name: String,
        returnType: Type,
        vararg parameters: AST.Parameter,
        throws: String? = null,
        block: BlockStatementDsl.() -> Unit
    ) {
        this.res.add(
            AST.Method(
                name.toSymbol(),
                returnType.wrapValid(),
                parameters.toList().map { it.wrapValid() },
                AST.Block(BlockStatementDsl().also(block).res).wrapValid(),
                throws?.toSymbol()
            ).wrapValid()
        )
    }
}

object ExprDsl {
    fun <T> literal(v: T) = AST.LiteralExpression(v)
    fun binOp(
        op: AST.BinaryExpression.Operation,
        left: ExprDsl.() -> AST.Expression,
        right: ExprDsl.() -> AST.Expression
    ) =
        AST.BinaryExpression(ExprDsl.left().wrapValid(), ExprDsl.right().wrapValid(), op)

    fun ident(name: String) = AST.IdentifierExpression(name.toSymbol())

    fun arrayAccess(
        target: ExprDsl.() -> AST.Expression,
        index: ExprDsl.() -> AST.Expression
    ) = AST.ArrayAccessExpression(ExprDsl.target().wrapValid(), ExprDsl.index().wrapValid())

    fun fieldAccess(
        left: ExprDsl.() -> AST.Expression,
        field: String
    ) = AST.FieldAccessExpression(ExprDsl.left().wrapValid(), field.toSymbol())

    fun newArrayOf(
        type: Type.Array.ArrayType,
        length: ExprDsl.() -> AST.Expression
    ) = AST.NewArrayExpression(type.wrapValid(), ExprDsl.length().wrapValid())
}

class BlockStatementDsl(val res: MutableList<Lenient<AST.BlockStatement>> = mutableListOf()) {
    fun localDeclaration(
        name: String,
        type: Type,
        initializer: (ExprDsl.() -> AST.Expression)? = null
    ) {
        res.add(
            AST.LocalVariableDeclarationStatement(
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

    fun expressionStatement(expr: ExprDsl.() -> AST.Expression) {
        res.add(AST.StmtWrapper(AST.ExpressionStatement(ExprDsl.expr().wrapValid())).wrapValid())
    }

    fun ifStmt(
        cond: ExprDsl.() -> AST.Expression,
        trueStmt: StatementDsl.() -> AST.Statement,
        falseStmt: (StatementDsl.() -> AST.Statement)? = null
    ) = res.add(StatementDsl.ifStmt(cond, trueStmt, falseStmt).wrapBlockStatement().wrapValid())
}

object StatementDsl {
    fun ifStmt(
        cond: ExprDsl.() -> AST.Expression,
        trueStmt: StatementDsl.() -> AST.Statement,
        falseStmt: (StatementDsl.() -> AST.Statement)? = null
    ) = AST.IfStatement(
        ExprDsl.cond().wrapValid(),
        StatementDsl.trueStmt().wrapValid(),
        falseStmt?.let { StatementDsl.it().wrapValid() }
    )
}

class StatementsDsl(val res: MutableList<Lenient<AST.Statement>> = mutableListOf()) {
    fun block(b: StatementsDsl.() -> Unit) {
        res.add(AST.Block(StatementsDsl().also(b).res.map { it.map { it.wrapBlockStatement() } }).wrapValid())
    }

    fun ifStmt(
        cond: ExprDsl.() -> AST.Expression,
        trueStmt: StatementDsl.() -> AST.Statement,
        falseStmt: (StatementDsl.() -> AST.Statement)? = null
    ) = res.add(StatementDsl.ifStmt(cond, trueStmt, falseStmt).wrapValid())
}

fun astOf(block: ClassDeclarationDsl.() -> Unit) =
    ClassDeclarationDsl().also(block).res
