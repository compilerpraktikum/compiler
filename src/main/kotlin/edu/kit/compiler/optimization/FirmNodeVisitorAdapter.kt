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

    open fun defaultVisit(node: Node) {}

    override fun visit(node: Add) { defaultVisit(node) }
    override fun visit(node: Address) { defaultVisit(node) }
    override fun visit(node: Align) { defaultVisit(node) }
    override fun visit(node: Alloc) { defaultVisit(node) }
    override fun visit(node: Anchor) { defaultVisit(node) }
    override fun visit(node: And) { defaultVisit(node) }
    override fun visit(node: Bad) { defaultVisit(node) }
    override fun visit(node: Bitcast) { defaultVisit(node) }
    override fun visit(node: Block) { defaultVisit(node) }
    override fun visit(node: Builtin) { defaultVisit(node) }
    override fun visit(node: Call) { defaultVisit(node) }
    override fun visit(node: Cmp) { defaultVisit(node) }
    override fun visit(node: Cond) { defaultVisit(node) }
    override fun visit(node: Confirm) { defaultVisit(node) }
    override fun visit(node: Const) { defaultVisit(node) }
    override fun visit(node: Conv) { defaultVisit(node) }
    override fun visit(node: CopyB) { defaultVisit(node) }
    override fun visit(node: Deleted) { defaultVisit(node) }
    override fun visit(node: Div) { defaultVisit(node) }
    override fun visit(node: Dummy) { defaultVisit(node) }
    override fun visit(node: End) { defaultVisit(node) }
    override fun visit(node: Eor) { defaultVisit(node) }
    override fun visit(node: Free) { defaultVisit(node) }
    override fun visit(node: IJmp) { defaultVisit(node) }
    override fun visit(node: Id) { defaultVisit(node) }
    override fun visit(node: Jmp) { defaultVisit(node) }
    override fun visit(node: Load) { defaultVisit(node) }
    override fun visit(node: Member) { defaultVisit(node) }
    override fun visit(node: Minus) { defaultVisit(node) }
    override fun visit(node: Mod) { defaultVisit(node) }
    override fun visit(node: Mul) { defaultVisit(node) }
    override fun visit(node: Mulh) { defaultVisit(node) }
    override fun visit(node: Mux) { defaultVisit(node) }
    override fun visit(node: NoMem) { defaultVisit(node) }
    override fun visit(node: Not) { defaultVisit(node) }
    override fun visit(node: Offset) { defaultVisit(node) }
    override fun visit(node: Or) { defaultVisit(node) }
    override fun visit(node: Phi) { defaultVisit(node) }
    override fun visit(node: Pin) { defaultVisit(node) }
    override fun visit(node: Proj) { defaultVisit(node) }
    override fun visit(node: Raise) { defaultVisit(node) }
    override fun visit(node: Return) { defaultVisit(node) }
    override fun visit(node: Sel) { defaultVisit(node) }
    override fun visit(node: Shl) { defaultVisit(node) }
    override fun visit(node: Shr) { defaultVisit(node) }
    override fun visit(node: Shrs) { defaultVisit(node) }
    override fun visit(node: Size) { defaultVisit(node) }
    override fun visit(node: Start) { defaultVisit(node) }
    override fun visit(node: Store) { defaultVisit(node) }
    override fun visit(node: Sub) { defaultVisit(node) }
    override fun visit(node: Switch) { defaultVisit(node) }
    override fun visit(node: Sync) { defaultVisit(node) }
    override fun visit(node: Tuple) { defaultVisit(node) }
    override fun visit(node: Unknown) { defaultVisit(node) }
    override fun visitUnknown(node: Node) { defaultVisit(node) }
}
