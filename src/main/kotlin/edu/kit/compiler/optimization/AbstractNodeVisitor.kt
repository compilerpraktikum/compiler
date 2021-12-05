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
abstract class AbstractNodeVisitor : NodeVisitor {

    override fun visit(node: Add) {
        // TODO no visiting necessary, presumably?
    }

    override fun visit(node: Address) {
        // TODO no visiting necessary, presumably?
    }

    override fun visit(node: Align) {
        // TODO no visiting necessary, presumably?
    }

    override fun visit(node: Alloc) {
        // TODO no visiting necessary, presumably?
    }

    override fun visit(node: Anchor) {
        // TODO no visiting necessary, presumably?
    }

    override fun visit(node: And) {
        // TODO no visiting necessary, presumably?
    }

    override fun visit(node: Bad) {
        // TODO no visiting necessary, presumably?
    }

    override fun visit(node: Bitcast) {
        // TODO no visiting necessary, presumably?
    }

    override fun visit(node: Block) {
        // TODO no visiting necessary, presumably?
    }

    override fun visit(node: Builtin) {
        // TODO no visiting necessary, presumably?
    }

    override fun visit(node: Call) {
        // TODO no visiting necessary, presumably?
    }

    override fun visit(node: Cmp) {
        // TODO no visiting necessary, presumably?
    }

    override fun visit(node: Cond) {
        // TODO no visiting necessary, presumably?
    }

    override fun visit(node: Confirm) {
        // TODO no visiting necessary, presumably?
    }

    override fun visit(node: Const) {
        // TODO no visiting necessary, presumably?
    }

    override fun visit(node: Conv) {
        // TODO no visiting necessary, presumably?
    }

    override fun visit(node: CopyB) {
        // TODO no visiting necessary, presumably?
    }

    override fun visit(node: Deleted) {
        // TODO no visiting necessary, presumably?
    }

    override fun visit(node: Div) {
        // TODO no visiting necessary, presumably?
    }

    override fun visit(node: Dummy) {
        // TODO no visiting necessary, presumably?
    }

    override fun visit(node: End) {
        // TODO no visiting necessary, presumably?
    }

    override fun visit(node: Eor) {
        // TODO no visiting necessary, presumably?
    }

    override fun visit(node: Free) {
        // TODO no visiting necessary, presumably?
    }

    override fun visit(node: IJmp) {
        // TODO no visiting necessary, presumably?
    }

    override fun visit(node: Id) {
        // TODO no visiting necessary, presumably?
    }

    override fun visit(node: Jmp) {
        // TODO no visiting necessary, presumably?
    }

    override fun visit(node: Load) {
        // TODO no visiting necessary, presumably?
    }

    override fun visit(node: Member) {
        // TODO no visiting necessary, presumably?
    }

    override fun visit(node: Minus) {
        // TODO no visiting necessary, presumably?
    }

    override fun visit(node: Mod) {
        // TODO no visiting necessary, presumably?
    }

    override fun visit(node: Mul) {
        // TODO no visiting necessary, presumably?
    }

    override fun visit(node: Mulh) {
        // TODO no visiting necessary, presumably?
    }

    override fun visit(node: Mux) {
        // TODO no visiting necessary, presumably?
    }

    override fun visit(node: NoMem) {
        // TODO no visiting necessary, presumably?
    }

    override fun visit(node: Not) {
        // TODO no visiting necessary, presumably?
    }

    override fun visit(node: Offset) {
        // TODO no visiting necessary, presumably?
    }

    override fun visit(node: Or) {
        // TODO no visiting necessary, presumably?
    }

    override fun visit(node: Phi) {
        // TODO no visiting necessary, presumably?
    }

    override fun visit(node: Pin) {
        // TODO no visiting necessary, presumably?
    }

    override fun visit(node: Proj) {
        // TODO no visiting necessary, presumably?
    }

    override fun visit(node: Raise) {
        // TODO no visiting necessary, presumably?
    }

    override fun visit(node: Return) {
        // TODO no visiting necessary, presumably?
    }

    override fun visit(node: Sel) {
        // TODO no visiting necessary, presumably?
    }

    override fun visit(node: Shl) {
        // TODO no visiting necessary, presumably?
    }

    override fun visit(node: Shr) {
        // TODO no visiting necessary, presumably?
    }

    override fun visit(node: Shrs) {
        // TODO no visiting necessary, presumably?
    }

    override fun visit(node: Size) {
        // TODO no visiting necessary, presumably?
    }

    override fun visit(node: Start) {
        // TODO no visiting necessary, presumably?
    }

    override fun visit(node: Store) {
        // TODO no visiting necessary, presumably?
    }

    override fun visit(node: Sub) {
        // TODO no visiting necessary, presumably?
    }

    override fun visit(node: Switch) {
        // TODO no visiting necessary, presumably?
    }

    override fun visit(node: Sync) {
        // TODO no visiting necessary, presumably?
    }

    override fun visit(node: Tuple) {
        // TODO no visiting necessary, presumably?
    }

    override fun visit(node: Unknown) {
        // TODO no visiting necessary, presumably?
    }

    override fun visitUnknown(node: Node) {
        // TODO no visiting necessary, presumably?
    }
}
