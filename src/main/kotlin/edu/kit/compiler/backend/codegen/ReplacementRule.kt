package edu.kit.compiler.backend.codegen

import edu.kit.compiler.backend.molkir.Constant
import edu.kit.compiler.backend.molkir.Instruction
import edu.kit.compiler.backend.molkir.MolkIR
import edu.kit.compiler.backend.molkir.Register
import edu.kit.compiler.backend.molkir.RegisterId
import edu.kit.compiler.backend.molkir.Width

class VirtualRegisterTable(val map: MutableMap<CodeGenTree, Register>, private var lastRegister: Int = 0) {
    fun freshRegisterFor(node: CodeGenTree, width: Width): Register {
        val currentRegisterId = lastRegister++
        val registerId = Register(RegisterId(currentRegisterId), width)
        map[node] = registerId
        return registerId
    }

    fun getRegisterFor(node: CodeGenTree): Register? = map[node]
}

sealed class CodeGenTree {
    data class Add(val left: CodeGenTree, val right: CodeGenTree, val res: CodeGenTree) : CodeGenTree()

    data class Indirection(val addr: CodeGenTree) : CodeGenTree()

    data class Cond(val cond: CodeGenTree, val ifTrue: CodeGenTree, val ifFalse: CodeGenTree) : CodeGenTree()

    data class Assign(val lhs: CodeGenTree, val rhs: CodeGenTree) : CodeGenTree()

    data class RegisterRef(val reg: Register) : CodeGenTree()

    data class Const(val const: String) : CodeGenTree()
}

typealias Pattern = CodeGenTree

fun interface ReplacementRule {
    fun matches(node: CodeGenTree, registers: VirtualRegisterTable): Pair<CodeGenTree, MolkIR>?
}

val replacementRules: List<ReplacementRule> = listOf(
    // Constant -> Register
    ReplacementRule { node, registers ->
        if (node is CodeGenTree.Const) {
            val register = registers.freshRegisterFor(node, Width.DOUBLE)
            CodeGenTree.RegisterRef(register) to Instruction.movq(Constant(node.const), register)
        } else {
            null
        }
    },
    // Read
)
