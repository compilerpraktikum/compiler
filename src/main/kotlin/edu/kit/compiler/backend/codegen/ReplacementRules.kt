package edu.kit.compiler.backend.codegen

import edu.kit.compiler.Compiler
import edu.kit.compiler.backend.codegen.CodeGenIR.RegisterRef
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
            println("binop $binOp")
            val resRegister = newRegister(leftTarget.get().width)
            Replacement(
                node = RegisterRef(resRegister),
                instructions = left.get().instructions
                    .append(right.get().instructions)
                    .append(
                        debugComment(),
                        binOp.get().molkiOp(leftTarget.get(), rightTarget.get(), resRegister),
                    ),
                cost = left.get().cost + right.get().cost + 1,
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
            println(name)
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
        val fromRegister = variable<Register>()
        val fromReplacement = variable<Replacement>()
        val toMemory = variable<Memory>()
        val toReplacement = variable<Replacement>()

        match(
            CodeGenIR.Assign(
                to = CodeGenIR.MemoryAddress(toMemory, toReplacement),
                from = RegisterRef(fromRegister, fromReplacement),
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
                            fromRegister.get(),
                            toMemory.get().width(fromRegister.get().width), // TODO new width needed?
                        ),
                    ),
                cost = fromReplacement.get().cost + toReplacement.get().cost + 1,
            )
        }
    }
}

@Suppress("unused")
private fun RuleBuilder.advancedAddressModes() {
    /*****************************
     ** Advanced address modes
     *****************************
     * Basic address modes are handled by add and mul binary ops.
     *****************************/
    // TODO
}

@Suppress("unused")
private fun RuleBuilder.advancedRules() {
    // TODO
}
