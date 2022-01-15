package edu.kit.compiler.backend.codegen

import edu.kit.compiler.backend.molkir.Instruction
import edu.kit.compiler.backend.molkir.MolkIR
import edu.kit.compiler.backend.molkir.RegisterId
import edu.kit.compiler.backend.molkir.Target.InputOutputTarget.Constant
import edu.kit.compiler.backend.molkir.Target.InputOutputTarget.Register
import edu.kit.compiler.backend.molkir.Width
import firm.nodes.Phi

class VirtualRegisterTable() {
    val map: MutableMap<CodeGenIR, Register> = mutableMapOf()
    private var nextRegisterId: Int = 0

    fun newRegisterFor(node: CodeGenIR, width: Width): Register {
        val registerId = nextRegisterId++
        val register = Register(RegisterId(registerId), width)
        map[node] = register
        return register
    }

    fun newRegisterForPhi(phi: Phi, width: Width): Register {
        val registerId = nextRegisterId++
        val register = Register(RegisterId(registerId), width)
        return register
    }

    fun putPhi(node: CodeGenIR, register: Register) {
        map[node] = register
    }

    fun getRegisterFor(node: CodeGenIR): Register? = map[node]
}

typealias Pattern = CodeGenIR

fun interface ReplacementRule {
    fun matches(node: CodeGenIR, registers: VirtualRegisterTable): Pair<CodeGenIR, MolkIR>?
}

val replacementRules: List<ReplacementRule> = listOf(
    // Constant -> Register
    ReplacementRule { node, registers ->
        if (node is CodeGenIR.Const) {
            val register = registers.newRegisterFor(node, Width.DOUBLE)
            CodeGenIR.RegisterRef(register) to Instruction.movq(Constant(node.const), register)
        } else {
            null
        }
    },
    // Read
)
