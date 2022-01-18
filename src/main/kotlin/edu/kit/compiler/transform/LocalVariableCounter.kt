package edu.kit.compiler.transform

import edu.kit.compiler.semantic.SemanticAST
import edu.kit.compiler.semantic.SemanticType
import edu.kit.compiler.semantic.visitor.AbstractVisitor

/**
 * Count local variables of a method and generate a map of definition to id.
 *
 * @param startIndex where to start counting variables. Can be used if implicit variables are added to the front of the
 * variable tuple
 */
class LocalVariableCounter(startIndex: Int) : AbstractVisitor() {

    /**
     * Total number of variables in the visited method
     */
    var numberOfVariables = startIndex

    /**
     * Mapping of local variables to an integer id
     */
    val definitionMapping = mutableMapOf<SemanticAST.Statement.LocalVariableDeclaration, Int>()

    /**
     * Mapping of parameters to an integer id
     */
    val parameterMapping = mutableMapOf<SemanticAST.ClassMember.SubroutineDeclaration.Parameter, Int>()

    override fun visitParameter(parameter: SemanticAST.ClassMember.SubroutineDeclaration.Parameter) {
        if (!(parameter.type is SemanticType.Class && parameter.type.name.text == "String")) {
            parameterMapping[parameter] = numberOfVariables++
        }
    }

    override fun visitLocalVariableDeclaration(localVariableDeclaration: SemanticAST.Statement.LocalVariableDeclaration) {
        definitionMapping[localVariableDeclaration] = numberOfVariables++
    }
}
