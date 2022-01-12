package edu.kit.compiler.backend.codegen

import edu.kit.compiler.optimization.FirmNodeVisitorAdapter
import firm.nodes.Add
import firm.nodes.And
import firm.nodes.Call
import firm.nodes.Cmp
import firm.nodes.Cond
import firm.nodes.Const
import firm.nodes.Conv
import firm.nodes.Div
import firm.nodes.Eor
import firm.nodes.Id
import firm.nodes.Jmp
import firm.nodes.Load
import firm.nodes.Minus
import firm.nodes.Mod
import firm.nodes.Mul
import firm.nodes.Mulh
import firm.nodes.Node
import firm.nodes.Not
import firm.nodes.Or
import firm.nodes.Phi
import firm.nodes.Proj
import firm.nodes.Return
import firm.nodes.Shl
import firm.nodes.Shr
import firm.nodes.Shrs
import firm.nodes.Store
import firm.nodes.Sub

class FirmGraphToCodeGenTreeGraphGenerator : FirmNodeVisitorAdapter() {

    override fun visit(node: Add) {
        super.visit(node)
    }

    override fun visit(node: And) {
        super.visit(node)
    }

    override fun visit(node: Call) {
        super.visit(node)
    }

    override fun visit(node: Cmp) {
        super.visit(node)
    }

    override fun visit(node: Cond) {
        super.visit(node)
    }

    override fun visit(node: Const) {
        super.visit(node)
    }

    override fun visit(node: Conv) {
        super.visit(node)
    }

    override fun visit(node: Div) {
        super.visit(node)
    }

    override fun visit(node: Eor) {
        super.visit(node)
    }

    override fun visit(node: Jmp) {
        super.visit(node)
    }

    override fun visit(node: Load) {
        super.visit(node)
    }

    override fun visit(node: Minus) {
        super.visit(node)
    }

    override fun visit(node: Mod) {
        super.visit(node)
    }

    override fun visit(node: Mul) {
        super.visit(node)
    }

    override fun visit(node: Mulh) {
        super.visit(node)
    }

    override fun visit(node: Not) {
        super.visit(node)
    }

    override fun visit(node: Or) {
        super.visit(node)
    }

    override fun visit(node: Phi) {
        super.visit(node)
    }

    override fun visit(node: Proj) {
        super.visit(node)
    }

    override fun visit(node: Return) {
        super.visit(node)
    }

    override fun visit(node: Store) {
        super.visit(node)
    }

    override fun visit(node: Sub) {
        super.visit(node)
    }

    override fun visitUnknown(node: Node) {
        super.visitUnknown(node)
    }

    override fun visit(node: Shl) {
        super.visit(node)
    }

    override fun visit(node: Shr) {
        super.visit(node)
    }

    override fun visit(node: Shrs) {
        super.visit(node)
    }

//TODO Vorgehensweise:
    /*
        * Grundblockweise. Verweise auf andere Grundblöcke müssen aufgelöst werden.
        * Phis?
        *
        * 1. Firm-Graph ==> Code-Gen-Tree
        * 2. Phi-Abbau: f.A. Phis
        *   1. jedes Phi kriegt neues Register r.
        *   2. Für jede Phi-Kante [PhiY->(a,b)] wird in den entsprechenden Grundblöcken ein Assignment y = ... gesetzt.
        *   4. Kritische Kanten: Betrachte [PhiY->(a,b)]. Falls (zB) b in noch einem anderen [PhiZ->(b,c)] verwendet wird,
        *   füge zwischen Block(y) und Block(b), sowie zwischen  Block(z) und Block(b) je einen Block ein, in welchem y bzw. z auf b gesetzt wird.
        * 3. Bottom-Up Pattern Matching auf CodeGenTree. ==> Molki-Code
        * 4. Swap-Problem: Falls [PhiY->(a,PhiZ)] und PhiZ im selben Grundblock liegt, benötigt PhiY den Wert von "vorher"
        *
        *
     */

}
