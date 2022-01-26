package edu.kit.compiler.transform

import edu.kit.compiler.semantic.SemanticAST
import edu.kit.compiler.semantic.visitor.AbstractVisitor

/**
 * Count local variables of a method and generate a map of definition to id.
 *
 * @param startIndex where to start counting variables. Can be used if implicit variables are added to the front of the
 * variable tuple
 */
class LocalVariableCounter(startIndex: Int, private val includeParameters: Boolean) : AbstractVisitor() {
    private var nextVariableIndex = startIndex

    /**
     * Total number of variables in the visited method
     */
    val numberOfVariables
        get() = nextVariableIndex

    /**
     * Mapping of local variables to an integer id
     */
    val definitionMapping = mutableMapOf<SemanticAST.Statement.LocalVariableDeclaration, Int>()

    /**
     * Mapping of parameters to an integer id
     */
    val parameterMapping = mutableMapOf<SemanticAST.ClassMember.SubroutineDeclaration.Parameter, Int>()

    override fun visitParameter(parameter: SemanticAST.ClassMember.SubroutineDeclaration.Parameter) {
        if (!includeParameters)
            return

        parameterMapping[parameter] = nextVariableIndex++
    }

    override fun visitLocalVariableDeclaration(localVariableDeclaration: SemanticAST.Statement.LocalVariableDeclaration) {
        definitionMapping[localVariableDeclaration] = nextVariableIndex++
    }
}
