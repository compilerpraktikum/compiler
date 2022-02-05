package edu.kit.compiler.backend.codegen

import edu.kit.compiler.backend.molkir.RegisterId
import edu.kit.compiler.backend.molkir.Width
import firm.nodes.Node
import edu.kit.compiler.backend.molkir.Register as MolkiRegister

class VirtualRegisterTable {
    val map: MutableMap<Node, MolkiRegister> = mutableMapOf()
    private var nextRegisterId: Int = 0

    /**
     *
     */
    fun getOrCreateRegisterFor(node: Node): MolkiRegister {
        return map[node] ?: newRegisterFor(node)
    }

    private fun newRegisterFor(node: Node): MolkiRegister {
        if (map[node] != null) error("invalid call to newRegisterFor node $node. The node already has a register")
        val registerId = nextRegisterId++
        val width = Width.fromByteSize(node.mode.sizeBytes)
            ?: error("cannot infer register width from mode \"${node.mode.name}\" of node $node")
        val molkiRegister = MolkiRegister(RegisterId(registerId), width)
        map[node] = molkiRegister
        return molkiRegister
    }

    fun newRegister(width: Width): MolkiRegister {
        val registerId = nextRegisterId++
        return MolkiRegister(RegisterId(registerId), width)
    }
}
