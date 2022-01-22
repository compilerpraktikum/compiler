package edu.kit.compiler.backend.codegen

class ReplacementSystem(private val registerTable: VirtualRegisterTable) : CodeGenIRVisitor<MatchResult?> {
    private fun findOptimalReplacementRule(node: CodeGenIR): MatchResult? {
        println("looking for optimal replacement for $node")
        return ReplacementRules
            .values()
            .mapNotNull { it.rule.match(node, registerTable) }
            .also { println("matching rules $it") }
            .minByOrNull { it.cost }
            .also { println("optimal rule $it") }

    }

    override fun transform(node: CodeGenIR.BinOP, left: MatchResult?, right: MatchResult?): MatchResult? {
        val innerReplaced = node.copy(left = left?.replacement ?: node.left, right = right?.replacement ?: node.right)
        val replaced = findOptimalReplacementRule(innerReplaced)
        val leftInstructions = left?.instructions ?: listOf()
        val rightInstructions = right?. instructions ?: listOf()
        return replaced?.copy(instructions = leftInstructions + rightInstructions + replaced.instructions)
    }

    override fun transform(node: CodeGenIR.Indirection, addr: MatchResult?): MatchResult? {
        val innerReplaced = node.copy(addr = addr?.replacement ?: node.addr)
        return findOptimalReplacementRule(innerReplaced)?.prependInstructions(addr?.instructions)
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
        TODO("Not yet implemented")
    }

    override fun transform(node: CodeGenIR.RegisterRef): MatchResult? {
        return null // no further optimizations possible
    }

    override fun transform(node: CodeGenIR.Const): MatchResult? {
        return findOptimalReplacementRule(node)
    }

    override fun transform(node: CodeGenIR.Call, arguments: List<MatchResult?>): MatchResult? {
        val argumentsReplaced = arguments.zip(node.arguments).map { it.first?.replacement ?: it.second  }
        val innerReplaced = node.copy(arguments = argumentsReplaced)
        return findOptimalReplacementRule(innerReplaced)?.prependInstructions(arguments.mapNotNull { it?.instructions }.flatten())
    }


}
