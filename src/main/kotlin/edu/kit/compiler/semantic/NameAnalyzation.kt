package edu.kit.compiler.semantic

import edu.kit.compiler.ast.AST
import edu.kit.compiler.ast.AbstractASTVisitor
import edu.kit.compiler.ast.Type
import edu.kit.compiler.ast.accept
import edu.kit.compiler.wrapper.Of
import edu.kit.compiler.wrapper.Unwrappable
import edu.kit.compiler.wrapper.wrappers.Identity

class NameAnalyzationVisitor(
    private val unwrapExpr: Unwrappable<Identity<Of>>,
    private val unwrapStmt: Unwrappable<Identity<Of>>,
    private val unwrapDecl: Unwrappable<Identity<Of>>,
    private val unwrapClass: Unwrappable<Identity<Of>>,
    private val unwrapOther: Unwrappable<Identity<Of>>
) :
    AbstractASTVisitor<Identity<Of>, Identity<Of>, Identity<Of>, Identity<Of>, Identity<Of>>(
        unwrapExpr, unwrapStmt, unwrapDecl, unwrapClass, unwrapOther
    ) {

    private val symbolTable: SymbolTable = SymbolTable()

    override fun visit(arrayType: Type.Array.ArrayType<Identity<Of>>) {
        arrayType.accept(this)
        TODO("remove this method")
    }

    override fun visit(operation: AST.UnaryExpression.Operation) {
        TODO("remove this method")
    }

    override fun visit(localVariableDeclarationStatement: AST.LocalVariableDeclarationStatement<Identity<Of>, Identity<Of>>) {
        symbolTable.add(
            localVariableDeclarationStatement.name,
            VariableDefinition(
                localVariableDeclarationStatement.name,
                localVariableDeclarationStatement
            )
        )
    }
}
