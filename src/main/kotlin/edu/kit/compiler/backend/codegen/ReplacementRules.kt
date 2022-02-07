package edu.kit.compiler.backend.codegen

import edu.kit.compiler.Compiler
import edu.kit.compiler.backend.codegen.CodeGenIR.RegisterRef
import edu.kit.compiler.backend.molkir.Constant
import edu.kit.compiler.backend.molkir.Instruction
import edu.kit.compiler.backend.molkir.Memory
import edu.kit.compiler.backend.molkir.Register
import edu.kit.compiler.backend.molkir.ReturnRegister
import edu.kit.compiler.backend.molkir.Target
import edu.kit.compiler.backend.molkir.Width
import edu.kit.compiler.utils.ValueHolder
import firm.Mode
import firm.Relation
import firm.nodes.Address
import kotlin.math.absoluteValue
import edu.kit.compiler.utils.rule as buildRule

fun createReplacementRulesFor(optimizationLevel: Compiler.OptimizationLevel): List<ReplacementRule> {
    val builder = RuleBuilder()
    builder.basicRules()
    if (optimizationLevel == Compiler.OptimizationLevel.Full) {
        builder.advancedAddressModes()
        builder.advancedRules()
    }
    return builder.build()
}

private class RuleBuilder {
    private val rules = mutableListOf<ReplacementRule>()

    fun rule(name: String, block: ReplacementRuleBuilderScope.() -> Unit): Boolean {
        return rules.add(
            buildRule(name, block)
        )
    }

    fun build() = rules.toList()
}

private fun RuleBuilder.basicRules() {
    /*****************************
     ** Misc
     *****************************/
    rule("seq") {
        val first = variable<CodeGenIR>()
        val second = variable<CodeGenIR>()

        match(
            CodeGenIR.Seq(
                first = SaveAnyNodeTo(first),
                second = SaveAnyNodeTo(second)
            )
        )

        replaceWith {
            // not a condition as there are no other rules for Seq and to fail early
            check(first.get().replacement != null) { "no replacement found for ${first.get().display()}" }
            check(second.get().replacement != null) { "no replacement found for ${second.get().display()}" }

            val firstReplacement = first.get().replacement!!
            val secondReplacement = second.get().replacement!!
            Replacement(
                node = Noop(),
                instructions = firstReplacement.instructions
                    .append(secondReplacement.instructions),
                cost = firstReplacement.cost + secondReplacement.cost
            )
        }
    }
    rule("return") {
        val target = variable<Target.Input>()
        val replacement = variable<Replacement>()

        match(
            CodeGenIR.Return(
                ConstOrRegisterRef(target, replacement)
            )
        )

        replaceWith {
            Replacement(
                node = Noop(),
                instructions = replacement.get().instructions
                    .append(
                        debugComment(),
                        Instruction.mov(
                            target.get(),
                            ReturnRegister(target.get().width)
                        ),
                    ),
                cost = replacement.get().cost + 1,
            )
        }
    }
    rule("jump") {
        val target = variable<String>()

        match(
            CodeGenIR.Jmp(target)
        )

        replaceWith {
            Replacement(
                node = Noop(),
                instructions = instructionListOf(
                    debugComment(),
                    Instruction.jmp(target.get()),
                ),
                cost = 1
            )
        }
    }
    rule("conditions") {
        val relation = variable<Relation>()
        val left = variable<Target.Input>()
        val leftReplacement = variable<Replacement>()
        val right = variable<Target.Input>()
        val rightReplacement = variable<Replacement>()
        val trueLabel = variable<String>()
        val falseLabel = variable<String>()

        match(
            CodeGenIR.Cond(
                CodeGenIR.Compare(
                    relation,
                    ConstOrRegisterRef(left, leftReplacement),
                    ConstOrRegisterRef(right, rightReplacement)
                ),
                trueLabel,
                falseLabel
            )
        )

        replaceWith {
            val ifTrue = trueLabel.get()
            val ifFalse = falseLabel.get()
            val jmpConstructor = when (relation.get()) {
                Relation.False -> Instruction.Companion::jmp
                Relation.Equal -> Instruction.Companion::je
                Relation.Less -> Instruction.Companion::jl
                Relation.Greater -> Instruction.Companion::jg
                Relation.GreaterEqual -> Instruction.Companion::jge
                Relation.LessGreater -> Instruction.Companion::jne
                Relation.LessEqual -> Instruction.Companion::jle
                else -> error("invalid relation $relation")
            }

            Replacement(
                node = Noop(),
                instructions = leftReplacement.get().instructions
                    .append(rightReplacement.get().instructions)
                    .append(
                        debugComment(),
                        Instruction.cmp(left.get(), right.get()),
                        jmpConstructor(ifTrue),
                        Instruction.jmp(ifFalse),
                    ),
                cost = leftReplacement.get().cost + rightReplacement.get().cost + 3
            )
        }
    }
    rule("conversions") {
        val fromMode = variable<Mode>()
        val toMode = variable<Mode>()

        val register = variable<Target.Input>()
        val operandReplacement = variable<Replacement>()

        match(
            CodeGenIR.Conv(
                fromMode,
                toMode,
                ConstOrRegisterRef(register, operandReplacement)
            )
        )

        replaceWith {
            val from = fromMode.get()
            val to = toMode.get()
            val repl = operandReplacement.get()
            when {
                from.sizeBytes == to.sizeBytes -> {
                    Replacement(
                        node = repl.node,
                        instructions = repl.instructions,
                        cost = repl.cost
                    )
                }
                from == Mode.getIs() && to == Mode.getLu() -> {
                    val result = newRegister(Width.QUAD)

                    Replacement(
                        node = RegisterRef(result),
                        instructions = repl.instructions
                            .append(
                                debugComment(),
                                Instruction.mov(register.get(), result.copy(width = Width.DOUBLE)),
                            ),
                        cost = repl.cost
                    )
                }
                from == Mode.getIs() && to == Mode.getLs() -> {
                    val result = newRegister(Width.QUAD)

                    Replacement(
                        node = RegisterRef(result),
                        instructions = repl.instructions
                            .append(
                                debugComment(),
                                Instruction.movs(register.get(), result),
                            ),
                        cost = repl.cost
                    )
                }
                else -> error("unknown conversion from $from (${from.sizeBytes} Bytes) to $to (${to.sizeBytes} Bytes)")
            }
        }
    }
    rule("read constant into register: `movq \$a, R_i`") {
        val const = variable<CodeGenIR.Const.Value>()

        match(
            CodeGenIR.Const(const)
        )

        replaceWith {
            val newRegister = newRegister(const.get().mode)
            Replacement(
                node = RegisterRef(newRegister),
                instructions = instructionListOf(
                    debugComment(),
                    Instruction.mov(const.get().toMolkIR(), newRegister),
                ),
                cost = 1,
            )
        }
    }
    rule("assign register register") {
        val registerWithValue = variable<Register>()
        val registerToWriteTo = variable<Register>()

        val replacement = ValueHolder.Variable<Replacement>()

        match(
            CodeGenIR.Assign(
                RegisterRef(registerToWriteTo),
                RegisterRef(registerWithValue, replacement)
            )
        )
        replaceWith {
            Replacement(
                node = RegisterRef(registerToWriteTo),
                instructions = replacement.get().instructions.append(
                    debugComment(),
                    Instruction.mov(registerWithValue.get(), registerToWriteTo.get()),
                ),
                cost = 1,
            )
        }
    }

    /*****************************
     ** Call
     *****************************/
    fun ReplacementRuleBuilderScope.handleCall(
        address: ValueHolder.Variable<Address>,
        resultRegister: ValueHolder.Variable<Register>?,
        arguments: ValueHolder.Variable<List<CodeGenIR>>,
    ) {
        val argRegisters = mutableListOf<Register>()
        val argReplacements = mutableListOf<Replacement>()
        condition {
            val register = ValueHolder.Variable<Register>()
            val replacement = ValueHolder.Variable<Replacement>()
            val pattern = RegisterRef(register, replacement)

            argRegisters.clear()
            argReplacements.clear()

            arguments.get().forEach { arg ->
                if (!pattern.matches(arg))
                    return@condition false

                argRegisters.add(register.get())
                argReplacements.add(replacement.get())
            }
            return@condition true
        }

        replaceWith {
            val functionName = address.get().entity.ldName
            Replacement(
                node = Noop(),
                instructions = argReplacements
                    .fold(instructionListOf()) { acc, repl -> acc.append(repl.instructions) }
                    .append(
                        debugComment(),
                        Instruction.call(
                            functionName,
                            argRegisters.toList(), // copy list as it is reused
                            resultRegister?.get(),
                            NameMangling.isExternalFunction(functionName)
                        ),
                    ),
                cost = argReplacements.sumOf { it.cost } + 1
            )
        }
    }
    rule("call function and store to register") {
        val address = variable<Address>()
        val resultRegister = variable<Register>()
        val arguments = variable<List<CodeGenIR>>()

        match(
            CodeGenIR.Assign(
                to = RegisterRef(resultRegister),
                from = CodeGenIR.Call(address, arguments)
            )
        )

        handleCall(address, resultRegister, arguments)
    }
    rule("call function and discard result") {
        val address = variable<Address>()
        val arguments = variable<List<CodeGenIR>>()

        match(
            CodeGenIR.Call(address, arguments)
        )

        handleCall(address, null, arguments)
    }

    /*****************************
     ** Unary operations
     *****************************/
    rule("unary operation (e.g. minus): `negq R_i, R_k`") {
        val unOp = variable<UnaryOpType>()
        val operandTarget = variable<Target.Input>()
        val operandReplacement = variable<Replacement>()

        match(
            CodeGenIR.UnaryOp(
                unOp,
                ConstOrRegisterRef(operandTarget, operandReplacement),
            )
        )

        replaceWith {
            val resRegister = newRegister(operandTarget.get().width)
            Replacement(
                node = RegisterRef(resRegister),
                instructions = operandReplacement.get().instructions
                    .append(
                        debugComment(),
                        unOp.get().molkiOp(operandTarget.get(), resRegister),
                    ),
                cost = operandReplacement.get().cost + 1,
            )
        }
    }

    /*****************************
     ** Binary operations
     *****************************/
    rule("binary operation (e.g. add): `addq [ R_i | R_j ] -> R_k`") {
        val binOp = variable<BinaryOpType>()
        val leftTarget = variable<Target.Input>()
        val rightTarget = variable<Target.Input>()

        val left = variable<Replacement>()
        val right = variable<Replacement>()

        match(
            CodeGenIR.BinaryOp(
                binOp,
                ConstOrRegisterRef(leftTarget, left),
                ConstOrRegisterRef(rightTarget, right)
            )
        )

        replaceWith {
            check(leftTarget.get().width == rightTarget.get().width) {
                "Widths of `l` and `r` need to be the same in `BinOp(${binOp.get()}, ${leftTarget.get().width}, ${rightTarget.get().width})`"
            }
            val resRegister = newRegister(leftTarget.get().width)
            Replacement(
                node = RegisterRef(resRegister),
                instructions = left.get().instructions
                    .append(right.get().instructions)
                    .append(
                        debugComment(),
                        binOp.get().molkiOp(leftTarget.get(), rightTarget.get(), resRegister),
                    ),
                cost = left.get().cost + right.get().cost + 2,
            )
        }
    }
    fun divisionRule(name: String, resultIsFirst: Boolean, createMatcher: (CodeGenIR, CodeGenIR) -> CodeGenIR) = rule(name) {
        val leftTarget = variable<Target.Input>()
        val rightTarget = variable<Target.Input>()

        val left = variable<Replacement>()
        val right = variable<Replacement>()

        match(
            createMatcher(
                ConstOrRegisterRef(leftTarget, left),
                ConstOrRegisterRef(rightTarget, right)
            )
        )

        replaceWith {
            check(leftTarget.get().width == rightTarget.get().width) {
                "Widths of `l` and `r` need to be the same in `Div(${leftTarget.get().width}, ${rightTarget.get().width})`"
            }
            val resRegister = newRegister(leftTarget.get().width)
            val (firstResult, secondResult) = when (resultIsFirst) {
                true -> Pair(resRegister, newRegister(resRegister.width))
                false -> Pair(newRegister(resRegister.width), resRegister)
            }
            Replacement(
                node = RegisterRef(resRegister),
                instructions = left.get().instructions
                    .append(right.get().instructions)
                    .append(
                        debugComment(),
                        Instruction.idiv(leftTarget.get(), rightTarget.get(), firstResult, secondResult),
                    ),
                cost = left.get().cost + right.get().cost + (if (resRegister.width == Width.DOUBLE) 26 else 50),
            )
        }
    }
    divisionRule("div", true, CodeGenIR::Div)
    divisionRule("mod", false, CodeGenIR::Mod)

    /*****************************
     ** Load / Store
     *****************************
     * To reduce duplicated patterns because of different address modes, load and store work like this:
     * The indirection node is replaced by a [memory-address][CodeGenIR.MemoryAddress] by patterns that implement
     * different address modes. The load and store patterns then match for the resulting [memory-address][CodeGenIR.MemoryAddress]
     * of the indirection and use this as their memory operand.
     *****************************/
    rule("basic indirection") {
        val target = variable<Register>()
        val targetReplacement = variable<Replacement>()

        match(
            CodeGenIR.Indirection(
                RegisterRef(target, targetReplacement)
            )
        )

        replaceWith {
            Replacement(
                node = CodeGenIR.MemoryAddress(
                    Memory.of(base = target.get(), width = target.get().width)
                ),
                instructions = targetReplacement.get().instructions,
                cost = targetReplacement.get().cost
            )
        }
    }
    rule("load") {
        val toRegister = variable<Register>()
        val toReplacement = variable<Replacement>()
        val fromMemory = variable<Memory>()
        val fromReplacement = variable<Replacement>()

        match(
            CodeGenIR.Assign(
                to = RegisterRef(toRegister, toReplacement),
                from = CodeGenIR.MemoryAddress(fromMemory, fromReplacement)
            )
        )
        replaceWith {
            Replacement(
                node = Noop(),
                instructions = fromReplacement.get().instructions
                    .append(toReplacement.get().instructions)
                    .append(
                        debugComment(),
                        Instruction.mov(
                            fromMemory.get().width(toRegister.get().width), // TODO new width needed?
                            toRegister.get()
                        ),
                    ),
                cost = fromReplacement.get().cost + toReplacement.get().cost + 1,
            )
        }
    }
    rule("store") {
        val from = variable<Target.Input>()
        val fromReplacement = variable<Replacement>()
        val toMemory = variable<Memory>()
        val toReplacement = variable<Replacement>()

        match(
            CodeGenIR.Assign(
                to = CodeGenIR.MemoryAddress(toMemory, toReplacement),
                from = ConstOrRegisterRef(from, fromReplacement),
            )
        )
        replaceWith {
            Replacement(
                node = Noop(),
                instructions = fromReplacement.get().instructions
                    .append(toReplacement.get().instructions)
                    .append(
                        debugComment(),
                        Instruction.mov(
                            from.get(),
                            toMemory.get().width(from.get().width), // TODO new width needed?
                        ),
                    ),
                cost = fromReplacement.get().cost + toReplacement.get().cost + 1,
            )
        }
    }
}

private fun RuleBuilder.advancedAddressModes() {
    /*****************************
     ** Advanced address modes
     *****************************
     * Basic address modes are handled by add and mul binary ops.
     *****************************/
    rule("offset(%register)") { // field access
        val offset = variable<CodeGenIR.Const.Value>()
        val register = variable<Register>()
        val replacement = variable<Replacement>()

        match(
            CodeGenIR.Indirection(
                CodeGenIR.BinaryOp(
                    BinaryOpType.ADD,
                    CodeGenIR.Const(offset),
                    CodeGenIR.RegisterRef(register, replacement)
                )
            )
        )

        condition {
            offset.get().value.toLong() <= Int.MAX_VALUE.toLong()
        }

        replaceWith {
            Replacement(
                node = CodeGenIR.MemoryAddress(
                    Memory.of(
                        const = offset.get().value,
                        base = register.get(),
                        width = register.get().width,
                    )
                ),
                instructions = replacement.get().instructions,
                cost = replacement.get().cost
            )
        }
    }
    rule("(%register, %index, \$scale)") { // array access
        val base = variable<Register>()
        val baseReplacement = variable<Replacement>()
        val scale = variable<CodeGenIR.Const.Value>()
        val index = variable<Register>()
        val indexReplacement = variable<Replacement>()

        match(
            CodeGenIR.Indirection(
                CodeGenIR.BinaryOp(
                    BinaryOpType.ADD,
                    CodeGenIR.RegisterRef(base, baseReplacement),
                    CodeGenIR.BinaryOp(
                        BinaryOpType.MUL,
                        CodeGenIR.Const(scale),
                        CodeGenIR.RegisterRef(index, indexReplacement),
                    ),
                )
            )
        )

        replaceWith {
            Replacement(
                node = CodeGenIR.MemoryAddress(
                    Memory.of(
                        base = base.get(),
                        index = index.get(),
                        scale = scale.get().value,
                        width = base.get().width,
                    )
                ),
                instructions = baseReplacement.get().instructions
                    .append(indexReplacement.get().instructions),
                cost = baseReplacement.get().cost + indexReplacement.get().cost
            )
        }
    }
}

private fun RuleBuilder.advancedRules() {
    rule("inc") {
        val operand = variable<Register>()
        val operandReplacement = variable<Replacement>()

        match(
            CodeGenIR.BinaryOp(
                BinaryOpType.ADD,
                RegisterRef(operand, operandReplacement),
                CodeGenIR.Const(constant(CodeGenIR.Const.Value("1", Width.DOUBLE)))
            )
        )

        replaceWith {
            val result = newRegister(operand.get().width)
            Replacement(
                node = RegisterRef(result),
                instructions = operandReplacement.get().instructions
                    .append(
                        debugComment(),
                        Instruction.inc(operand.get(), result)
                    ),
                cost = operandReplacement.get().cost + 1
            )
        }
    }
    rule("dec") {
        val operand = variable<Register>()
        val operandReplacement = variable<Replacement>()

        match(
            CodeGenIR.BinaryOp(
                BinaryOpType.SUB,
                RegisterRef(operand, operandReplacement),
                CodeGenIR.Const(constant(CodeGenIR.Const.Value("1", Width.DOUBLE)))
            )
        )

        replaceWith {
            val result = newRegister(operand.get().width)
            Replacement(
                node = RegisterRef(result),
                instructions = operandReplacement.get().instructions
                    .append(
                        debugComment(),
                        Instruction.dec(operand.get(), result)
                    ),
                cost = operandReplacement.get().cost + 1
            )
        }
    }

    fun Int.positiveIsPowerOf2(): Boolean {
        check(this >= 0 || this == Int.MIN_VALUE) { "only works for positive numbers and Int.MIN_VALUE" }
        return countOneBits() == 1
    }
    fun Int.positiveLog2(): Int {
        check(this >= 0 || this == Int.MIN_VALUE) { "only works for positive numbers and Int.MIN_VALUE" }
        return 32 - countLeadingZeroBits() - 1
    }
    rule("multiplication with power of 2") {
        val left = variable<Register>()
        val leftReplacement = variable<Replacement>()
        val factor = variable<CodeGenIR.Const.Value>()

        match(
            CodeGenIR.BinaryOp(
                BinaryOpType.MUL,
                RegisterRef(left, leftReplacement),
                CodeGenIR.Const(factor)
            )
        )

        // even though this is not explicitly handled, the below code does also work for `factor == Int.MIN_VALUE`
        var factorInt: Int? = null
        condition {
            factorInt = factor.get().value.toInt()
            factorInt!!.absoluteValue.positiveIsPowerOf2()
        }

        replaceWith {
            val log2 = factorInt!!.absoluteValue.positiveLog2()
            val result = newRegister(left.get().width)
            Replacement(
                node = RegisterRef(result),
                instructions = leftReplacement.get().instructions
                    .append(
                        debugComment(),
                        Instruction.shl(left.get(), Constant(log2.toString(), Width.BYTE), result),
                        if (factorInt!! < 0) {
                            Instruction.neg(result, result)
                        } else {
                            null
                        }
                    ),
                cost = leftReplacement.get().cost + 1
            )
        }
    }
    rule("division by power of 2") {
        val left = variable<Register>()
        val leftReplacement = variable<Replacement>()
        val divisor = variable<CodeGenIR.Const.Value>()

        match(
            CodeGenIR.Div(
                RegisterRef(left, leftReplacement),
                CodeGenIR.Const(divisor)
            )
        )

        // even though this is not explicitly handled, the below code does also work for `factor == Int.MIN_VALUE`
        var divisorInt: Int? = null
        condition {
            divisorInt = divisor.get().value.toInt()
            divisorInt!!.absoluteValue.positiveIsPowerOf2()
        }

        replaceWith {
            /*
             * (any >> log2) does not work for negative numbers as it leads to rounding errors if the number is not
             * exactly dividable by [divisor]. Example:
             *   -5 /  2 == -2
             * but
             *   -5 >> 1 == -3
             * The following construction works around this problem by adding 1 to the result exactly when the number
             * is negative and not dividable by [divisor].
             */
            val width = left.get().width
            val log2 = divisorInt!!.absoluteValue.positiveLog2()
            val result = newRegister(width)

            Replacement(
                node = RegisterRef(result),
                instructions = leftReplacement.get().instructions
                    .append(
                        debugComment(),
                        /*
                         * First, we create a node that is (2^log2 - 1) if the number is negative and 0 if it is positive. To
                         * do so, we shift right (signed) by (log2 - 1). This extends the sign bit to a sequence of (log2) bits.
                         * We then shift (unsigned) the result by (32 - log2) bits to the right to move that sequence to the right
                         * end of the number. Example: For log2 = 3 -> 2^log1 = 8 = 1000 in binary and dividend = -1_879_048_197
                         *   -1_879_048_197                         -> 10001111111111111111111111111011
                         *   signed right shift by (3 - 1) == 2     -> 11100011111111111111111111111110 // note that it starts with 3 ones
                         *   unsigned right shift by (32 - 3) == 29 -> 00000000000000000000000000000111
                         * If the number is positive, the above sequence of operations creates the number 0.
                         */
                        if (log2 > 1) {
                            Instruction.sar(left.get(), Constant(log2 - 1, Width.BYTE), result)
                        } else {
                            Instruction.mov(left.get(), result)
                        },
                        Instruction.shr(result, Constant(32 - log2, Width.BYTE), result),
                        /*
                         * We now add the generated number to the dividend.
                         * If the dividend is positive
                         *   -> the generated number is 0 and this is a no-op
                         * If the dividend is dividable by divisor
                         *   -> it ends in (log2) zeros and thereby adding (roundingAddend) does NOT create a carry and only the last log2 bits change
                         *      Example:
                         *        divisor  = -4 -> positive log2 = 2
                         *        dividend = -12 = ..110100
                         *        roundingAddend = ..000011
                         *        rounded        = ..110111 // note: number does not change past the first (log2) bits from the right
                         * If the dividend is NOT dividable by divisor
                         *   -> the right-most (log2) bits are NOT all zero and thereby adding (roundingAddend) creates a carry which
                         *      increments the (log2 + 1)th bit from the right end
                         *      Example:
                         *        divisor  = -4 -> positive log2 = 2
                         *        dividend = -7  = ..111001
                         *        roundingAddend = ..000011
                         *        rounded        = ..111100 // note: (log2 + 1) = 3rd bit from the right is incremented
                         */
                        Instruction.add(left.get(), result, result),
                        /*
                         * Now we can finally do the real division by (signed) shifting (log2) bits to the right. Normally, this would
                         * create the rounding problem mentioned above, but in all cases where the rounding error arises (negative dividend
                         * that is not dividable by divisor) we have already incremented the (log2 + 1)th bit which is now (after shifting)
                         * the 1st bit from the right. Thereby the rounding error is corrected.
                         */
                        Instruction.sar(result, Constant(log2, Width.BYTE), result),
                        if (divisorInt!! < 0) {
                            Instruction.neg(result, result)
                        } else {
                            null
                        }
                    ),
                cost = leftReplacement.get().cost + 1
            )
        }
    }
}
