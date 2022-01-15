package edu.kit.compiler.backend.codegen

import edu.kit.compiler.backend.molkir.Target
import firm.TargetValue
import firm.nodes.Node

sealed class CodeGenIR {
    //TODO make binops
    data class BinOP(val left: CodeGenIR, val right: CodeGenIR, val operation: Node, val res: CodeGenIR) : CodeGenIR()

//    data class Add(val left: CodeGenIR, val right: CodeGenIR, val res: CodeGenIR) : CodeGenIR()

    data class Indirection(val addr: CodeGenIR) : CodeGenIR()

    data class MemoryAddress(val reg: Target.InputOutputTarget.Memory) : CodeGenIR()

    data class Cond(val cond: CodeGenIR, val ifTrue: CodeGenIR, val ifFalse: CodeGenIR) : CodeGenIR()

    data class Assign(val lhs: CodeGenIR, val rhs: CodeGenIR) : CodeGenIR()

    data class RegisterRef(val reg: Target.InputOutputTarget.Register) : CodeGenIR()

    data class Const(val const: String) : CodeGenIR()
}
