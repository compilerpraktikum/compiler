package edu.kit.compiler.backend.codegen

import com.sun.jna.Pointer
import firm.nodes.Node
import firm.nodes.NodeVisitor

data class RegisterId(private val registerNumber: Int)

class VirtualRegisterTable(val map: MutableMap<Node, RegisterId>, private var lastRegister: Int = 0) {
    fun freshRegisterFor(node: Node): RegisterId {
        val currentRegisterId = lastRegister++;
        val registerId = RegisterId(currentRegisterId)
        map[node] = registerId
        return registerId
    }

    fun getRegisterFor(node: Node): RegisterId? = map[node]
}

sealed class CodeGenTree {
    data class Add(val left: CodeGenTree, val right: CodeGenTree) : CodeGenTree()

    data class Indirection(val addr: CodeGenTree) : CodeGenTree()

    data class Cond(val cond: CodeGenTree, val ifTrue: CodeGenTree, val ifFalse: CodeGenTree) : CodeGenTree()

    data class Assign(val lhs: CodeGenTree, val rhs: CodeGenTree) : CodeGenTree()

    data class Register(val reg: Int) : CodeGenTree()

    data class Const(val const: Int) : CodeGenTree()

    data class RegisterVariable(val name: String) : CodeGenTree()

    data class ConstVariable(val name: String) : CodeGenTree()
}

typealias Pattern = CodeGenTree

class AddReplacementRule: ReplacementRule(
    CodeGenTree.Add(CodeGenTree.RegisterVariable("i"), CodeGenTree.RegisterVariable("j")),
    CodeGenTree.RegisterVariable("j")
) {
    override fun generateCode(
        registerInstantiation: Map<String, CodeGenTree.Register>,
        constInstantiation: Map<String, CodeGenTree.Const>
    ): MolkiIR {
        MolkiIR.addi(registerInstantiation["i"], registerInstantiation["j"])
    }

}

abstract class ReplacementRule(val searchPattern: Pattern, val replacement: Pattern) {
    abstract fun generateCode(
        registerInstantiation: Map<String, CodeGenTree.Register>,
        constInstantiation: Map<String, CodeGenTree.Const>
    ): MolkiIR
}
