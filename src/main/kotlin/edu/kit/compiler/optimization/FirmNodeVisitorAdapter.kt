package edu.kit.compiler.optimization

import firm.nodes.Add
import firm.nodes.Address
import firm.nodes.Align
import firm.nodes.Alloc
import firm.nodes.Anchor
import firm.nodes.And
import firm.nodes.Bad
import firm.nodes.Bitcast
import firm.nodes.Block
import firm.nodes.Builtin
import firm.nodes.Call
import firm.nodes.Cmp
import firm.nodes.Cond
import firm.nodes.Confirm
import firm.nodes.Const
import firm.nodes.Conv
import firm.nodes.CopyB
import firm.nodes.Deleted
import firm.nodes.Div
import firm.nodes.Dummy
import firm.nodes.End
import firm.nodes.Eor
import firm.nodes.Free
import firm.nodes.IJmp
import firm.nodes.Id
import firm.nodes.Jmp
import firm.nodes.Load
import firm.nodes.Member
import firm.nodes.Minus
import firm.nodes.Mod
import firm.nodes.Mul
import firm.nodes.Mulh
import firm.nodes.Mux
import firm.nodes.NoMem
import firm.nodes.Node
import firm.nodes.NodeVisitor
import firm.nodes.Not
import firm.nodes.Offset
import firm.nodes.Or
import firm.nodes.Phi
import firm.nodes.Pin
import firm.nodes.Proj
import firm.nodes.Raise
import firm.nodes.Return
import firm.nodes.Sel
import firm.nodes.Shl
import firm.nodes.Shr
import firm.nodes.Shrs
import firm.nodes.Size
import firm.nodes.Start
import firm.nodes.Store
import firm.nodes.Sub
import firm.nodes.Switch
import firm.nodes.Sync
import firm.nodes.Tuple
import firm.nodes.Unknown

/**
 * A [NodeVisitor][NodeVisitor] wrapper for children to not have to override everything.
 */
abstract class FirmNodeVisitorAdapter : NodeVisitor {

    override fun visit(node: Add) {}
    override fun visit(node: Address) {}
    override fun visit(node: Align) {}
    override fun visit(node: Alloc) {}
    override fun visit(node: Anchor) {}
    override fun visit(node: And) {}
    override fun visit(node: Bad) {}
    override fun visit(node: Bitcast) {}
    override fun visit(node: Block) {}
    override fun visit(node: Builtin) { }
    override fun visit(node: Call) { }
    override fun visit(node: Cmp) { }
    override fun visit(node: Cond) { }
    override fun visit(node: Confirm) { }
    override fun visit(node: Const) { }
    override fun visit(node: Conv) { }
    override fun visit(node: CopyB) { }
    override fun visit(node: Deleted) { }
    override fun visit(node: Div) { }
    override fun visit(node: Dummy) { }
    override fun visit(node: End) { }
    override fun visit(node: Eor) { }
    override fun visit(node: Free) { }
    override fun visit(node: IJmp) { }
    override fun visit(node: Id) { }
    override fun visit(node: Jmp) { }
    override fun visit(node: Load) { }
    override fun visit(node: Member) { }
    override fun visit(node: Minus) { }
    override fun visit(node: Mod) { }
    override fun visit(node: Mul) { }
    override fun visit(node: Mulh) { }
    override fun visit(node: Mux) { }
    override fun visit(node: NoMem) { }
    override fun visit(node: Not) { }
    override fun visit(node: Offset) { }
    override fun visit(node: Or) { }
    override fun visit(node: Phi) { }
    override fun visit(node: Pin) { }
    override fun visit(node: Proj) { }
    override fun visit(node: Raise) { }
    override fun visit(node: Return) { }
    override fun visit(node: Sel) { }
    override fun visit(node: Shl) { }
    override fun visit(node: Shr) { }
    override fun visit(node: Shrs) { }
    override fun visit(node: Size) { }
    override fun visit(node: Start) { }
    override fun visit(node: Store) { }
    override fun visit(node: Sub) { }
    override fun visit(node: Switch) { }
    override fun visit(node: Sync) { }
    override fun visit(node: Tuple) { }
    override fun visit(node: Unknown) { }
    override fun visitUnknown(node: Node) { }
}
