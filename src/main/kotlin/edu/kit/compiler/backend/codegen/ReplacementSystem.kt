package edu.kit.compiler.backend.codegen

import edu.kit.compiler.backend.molkir.Instruction

class ReplacementSystem(private val registerTable: VirtualRegisterTable) : CodeGenIRVisitor<MatchResult?> {
    private fun chooseOptimalReplacement(
        innerReplaced: CodeGenIR,
        innerInstructions: List<Instruction>,
        innerCost: Int,
        original: CodeGenIR
    ): MatchResult? {
        println("try with inner replaced:")
        val innerMatch = findOptimalReplacementRule(innerReplaced)

        println("try with original:")

        return minOf(
            innerMatch?.copy(
                instructions = innerInstructions + innerMatch.instructions,
                cost = innerCost + innerReplaced.cost()
            ),
            findOptimalReplacementRule(original),
            nullsLast(Comparator.comparing {
                it.cost
            }))
    }


    private fun findOptimalReplacementRule(node: CodeGenIR): MatchResult? {
        println("looking for optimal replacement for $node")
        return ReplacementRules
            .values()
            .mapNotNull { it.rule.match(node, registerTable) }
            .also { println("   |-> matching rules $it") }
            .minByOrNull { it.cost }
            .also { println("   |-> optimal rule $it") }
    }

    override fun transform(node: CodeGenIR.BinOP, left: MatchResult?, right: MatchResult?): MatchResult? {
        val innerReplaced = node.copy(left = left!!.replacement, right = right!!.replacement)

        return chooseOptimalReplacement(
            innerReplaced,
            left.instructions + right.instructions,
            left.cost + right.cost,
            node
        )
    }

    override fun transform(node: CodeGenIR.Indirection, addr: MatchResult?): MatchResult? {
        val innerReplaced = node.copy(addr = addr!!.replacement)

        return chooseOptimalReplacement(
            innerReplaced,
            addr.instructions,
            addr.cost,
            node
        )
    }

    override fun transform(node: CodeGenIR.MemoryAddress): MatchResult? {
        return findOptimalReplacementRule(node)
    }

    override fun transform(
        node: CodeGenIR.Cond,
        cond: MatchResult?,
        ifTrue: MatchResult?,
        ifFalse: MatchResult?
    ): MatchResult? {
        TODO("Not yet implemented")
    }

    override fun transform(node: CodeGenIR.Assign, lhs: MatchResult?, rhs: MatchResult?): MatchResult? {
        val innerReplaced = node.copy(lhs = lhs!!.replacement, rhs = rhs!!.replacement)

        return chooseOptimalReplacement(
            innerReplaced,
            lhs.instructions + rhs.instructions,
            lhs.cost + rhs.cost,
            node
        )
    }

    override fun transform(node: CodeGenIR.RegisterRef): MatchResult {
        return MatchResult(node, listOf(), 0)
    }

    override fun transform(node: CodeGenIR.Const): MatchResult? {
        return findOptimalReplacementRule(node)
    }

    override fun transform(node: CodeGenIR.Call, arguments: List<MatchResult?>): MatchResult? {
        val args = arguments.map { it!! }
        val innerReplaced = node.copy(arguments = args.map(MatchResult::replacement))

        return chooseOptimalReplacement(
            innerReplaced,
            args.flatMap(MatchResult::instructions),
            args.sumOf(MatchResult::cost),
            node
        )
    }

    override fun transform(node: CodeGenIR.Compare, left: MatchResult?, right: MatchResult?): MatchResult? {
        TODO("Not yet implemented")
    }

    override fun transform(node: CodeGenIR.Conv, opTree: MatchResult?): MatchResult? {
        TODO("Not yet implemented")
    }

    override fun transform(node: CodeGenIR.Return, returnValue: MatchResult?): MatchResult? {
        val innerReplaced = node.copy(returnValue = returnValue!!.replacement)

        return chooseOptimalReplacement(
            innerReplaced,
            returnValue.instructions,
            returnValue.cost,
            node
        ).also { println("!!chose $it") }
    }

    override fun transform(node: CodeGenIR.Seq, value: MatchResult?, exec: MatchResult?): MatchResult? {
        val innerReplaced = node.copy(value = value!!.replacement, exec = exec!!.replacement)

        val optimal = chooseOptimalReplacement(
            innerReplaced,
            value.instructions + exec.instructions,
            value.cost + exec.cost,
            node
        )
        @Suppress("IfThenToElvis")
        return if (optimal == null) {
            // This is a special rule for Seq!!
            // If `findOptimalReplacementRule` does not find a rule, we use this special rule
            // to replace Seq(<A>, <B>) with <A>, Instructions(<A>) + Instructions(<B>)
            MatchResult(value.replacement, value.instructions + exec.instructions, value.cost + exec.cost).also {
                println("-< using fallback replacement for SEQ: $it")
            }
        } else {
            optimal.also {
                println("-< using rule replaced for SEQ: $it")
            }
        }
    }


}
