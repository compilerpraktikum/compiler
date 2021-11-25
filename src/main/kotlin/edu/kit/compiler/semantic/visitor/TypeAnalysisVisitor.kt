package edu.kit.compiler.semantic.visitor

import edu.kit.compiler.lex.SourceFile
import edu.kit.compiler.semantic.AstNode
import edu.kit.compiler.semantic.ParsedType
import edu.kit.compiler.semantic.SemanticType

/**
 * Type analysis. Run name analysis beforehand.
 *
 *
 * @param sourceFile input token stream to annotate with errors
 */
class TypeAnalysisVisitor(private val sourceFile: SourceFile) : AbstractVisitor() {

    override fun visitParameter(parameter: AstNode.ClassMember.SubroutineDeclaration.Parameter) {
        checkAndMessageIfNot("No void typed parameters.") { parameter.type !is ParsedType.VoidType }
        parameter.semanticType = constructSemanticType(parameter.type, TODO("get class declaration, check before"))
    }

    /**
     * L-Values:
     */
    override fun visitLocalVariableDeclaration(localVariableDeclaration: AstNode.Statement.LocalVariableDeclaration) {
        // type check on children
        super.visitLocalVariableDeclaration(localVariableDeclaration)

        // L-Values check!
        checkAndMessageIfNot("No \"void\" variables.") { localVariableDeclaration.type is ParsedType.VoidType }
        checkAndMessageIfNot("No \"String\" instantiation.") {
            localVariableDeclaration.type is ParsedType.ComplexType && localVariableDeclaration.type.name.symbol.text == "String"
        }

        // type check
        checkAndMessageIfNot("Initializer and declared type don't match") {
            (localVariableDeclaration.initializer?.actualSemanticType ?: SemanticType.ErrorType) != localVariableDeclaration.type
        }
        TODO("impl!")
    }

    override fun visitArrayAccessExpression(arrayAccessExpression: AstNode.Expression.ArrayAccessExpression) {
        super.visitArrayAccessExpression(arrayAccessExpression)
        // left-to-right: check target, then check index.
        checkAndMessageIfNot("Array access target is no array.") {
            arrayAccessExpression.target.actualSemanticType is SemanticType.ArrayType
        }
        checkAndMessageIfNot("Only \"Int\"-typed array indices.") {
            arrayAccessExpression.index.actualSemanticType is SemanticType.IntType
        }
        // If everything's correct, the arrayAccessExpression's type is the elementtype
        arrayAccessExpression.actualSemanticType = (arrayAccessExpression.target as SemanticType.ArrayType).elementType
    }

    override fun visitFieldAccessExpression(fieldAccessExpression: AstNode.Expression.FieldAccessExpression) {
        super.visitFieldAccessExpression(fieldAccessExpression)
        checkAndMessageIfNot("") { fieldAccessExpression.target.actualSemanticType is SemanticType.ComplexType }
        when (fieldAccessExpression.target.actualSemanticType) {
            is SemanticType.ArrayType -> TODO("Error")

        }
        // TODO identifier ==> Definition from current namespace!
//        fieldAccessExpression.actualSemanticType = fieldAccessExpression.field
    }

    private fun checkAndMessageIfNot(errorMsg: String, function: () -> kotlin.Boolean) {
        if (!function()) {
            TODO("Print Error: $errorMsg")
        }
    }

    // TODO: more: Expressions, parameters, ....
}
