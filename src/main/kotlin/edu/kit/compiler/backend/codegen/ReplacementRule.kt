package edu.kit.compiler.backend.codegen

import com.sun.jna.Pointer
import edu.kit.compiler.backend.molkir.Instruction
import edu.kit.compiler.backend.molkir.MolkIR
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
    data class Add(val left: CodeGenTree, val right: CodeGenTree, val res: CodeGenTree) : CodeGenTree()

    data class Indirection(val addr: CodeGenTree) : CodeGenTree()

    data class Cond(val cond: CodeGenTree, val ifTrue: CodeGenTree, val ifFalse: CodeGenTree) : CodeGenTree()

    data class Assign(val lhs: CodeGenTree, val rhs: CodeGenTree) : CodeGenTree()

    data class Register(val reg: Int) : CodeGenTree()

    data class Const(val const: Int) : CodeGenTree()

    data class RegisterVariable(val name: String) : CodeGenTree()

    data class ConstVariable(val name: String) : CodeGenTree()
}

typealias Pattern = CodeGenTree

abstract class ReplacementRule2() {
    abstract fun matches(node: CodeGenTree) : Pair<CodeGenTree, MolkIR>?
}

class AddReplacementRule2 : ReplacementRule2() {
    override fun matches(node: CodeGenTree): Pair<CodeGenTree, MolkIR>? {
        if (node is CodeGenTree.Add) {
            if (node.right is CodeGenTree.Register) {
                if (node.left is CodeGenTree.Register) {
                    return Pair(node.res, MolkIR.addi(node.left, node.right, node.res))
                }
                return null
            }
            return null
        }
        return null
    }
}

class AddReplacementRule : ReplacementRule(
    CodeGenTree.Add(CodeGenTree.RegisterVariable("i"), CodeGenTree.RegisterVariable("j")),
    CodeGenTree.RegisterVariable("k")
) {
    override fun generateCode(
        registerInstantiation: Map<String, CodeGenTree.Register>,
        constInstantiation: Map<String, CodeGenTree.Const>
    ): MolkIR {
        Instruction.BinaryOperationWithResult(
            "addl",
            registerInstantiation["oo"],
            registerInstantiation["j"],
            registerInstantiation["k"]
        )
    }

}

abstract class ReplacementRule(val searchPattern: Pattern, val replacement: Pattern) {
    abstract fun generateCode(
        registerInstantiation: Map<String, CodeGenTree.Register>,
        constInstantiation: Map<String, CodeGenTree.Const>
    ): MolkiIR
}
