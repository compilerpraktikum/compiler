package edu.kit.compiler.backend.codegen

import edu.kit.compiler.backend.codegen.CodeGenIR.RegisterRef
import edu.kit.compiler.backend.molkir.Instruction
import edu.kit.compiler.backend.molkir.Memory
import edu.kit.compiler.backend.molkir.Register
import edu.kit.compiler.backend.molkir.ReturnRegister
import edu.kit.compiler.backend.molkir.Width
import edu.kit.compiler.utils.Rule
import edu.kit.compiler.utils.rule
import firm.nodes.Address

fun Replacement?.assertExists() = this ?: error("no replacement found")

val replacementRules = listOf<Rule<CodeGenIR, Replacement, ReplacementScope>>(
    rule("seq") {
        val value = variable<CodeGenIR>()
        val exec = variable<CodeGenIR>()

        match(
            CodeGenIR.Seq(
                value = SaveAnyNodeTo(value),
                exec = SaveAnyNodeTo(exec)
            )
        )

        condition {
            value.get().replacement != null && exec.get().replacement != null
        }

        replaceWith {
            // i = read()   => SEQ(value= REG(1), exec = ASSIGN(REF(1), CALL("read")))
            // replacement = REG(1)   | instruction = instr(exec) + instr(value)
            val valueReplacement = value.get().replacement!!
            val execReplacement = exec.get().replacement!!
            Replacement(
                node = Noop(),
                instructions = execReplacement.instructions
                    .append(valueReplacement.instructions),
                cost = execReplacement.cost + valueReplacement.cost
            )
        }
    },
    rule("read constant: `movq \$a, R_i`") {
        val const = variable<CodeGenIR.Const.Value>()

        match(
            CodeGenIR.Const(const)
        )

        replaceWith {
            val newRegister = newRegister(const.get().mode)
            Replacement(
                node = CodeGenIR.RegisterRef(newRegister),
                instructions = instructionListOf(
                    Instruction.mov(const.get().toMolkIR(), newRegister)
                ),
                cost = 1,
            )
        }
    },
    rule("read memory addr: `movq x, R_i`") {
        val addrValue = variable<Memory>()

        match(
            CodeGenIR.MemoryAddress(addrValue)
        )

        replaceWith {
            val newRegister = newRegister(Width.QUAD)
            Replacement(
                node = CodeGenIR.RegisterRef(newRegister),
                instructions = instructionListOf(
                    Instruction.mov(addrValue.get(), newRegister)
                ),
                cost = 1,
            )
        }
    },
    rule("assign memory register: `movq R_i, x`") {
        val addrValue = variable<Memory>()
        val registerId = variable<Register>()

        match(
            CodeGenIR.Assign(
                CodeGenIR.MemoryAddress(
                    addrValue
                ),
                RegisterRef(registerId)
            )
        )

        replaceWith {
            Replacement(
                node = CodeGenIR.MemoryAddress(addrValue),
                instructions = instructionListOf(
                    Instruction.mov(
                        Memory.of(registerId.get(), width = addrValue.get().width),
                        addrValue.get()
                    )
                ),
                cost = 1,
            )
        }
    },
    rule("assign addr in register value in register: `movq (R_j), (R_i)`") {
        val registerWithAddr = variable<Register>()
        val registerWithValue = variable<Register>()

        val replacementForAddr = variable<Replacement>()
        val replacementForValue = variable<Replacement>()

        match(
            CodeGenIR.Assign(
                CodeGenIR.Indirection(
                    RegisterRef(registerWithAddr, replacementForAddr)
                ),
                RegisterRef(registerWithValue, replacementForValue)
            )
        )

        replaceWith {
            val memoryAddress = Memory.of(registerWithAddr.get(), width = registerWithValue.get().width)
            Replacement(
                node = CodeGenIR.MemoryAddress(memoryAddress),
                instructions = replacementForAddr.get().instructions
                    .append(replacementForValue.get().instructions)
                    .append(
                        Instruction.mov(
                            Memory.of(base = registerWithValue.get(), width = registerWithValue.get().width),
                            Memory.of(base = registerWithAddr.get(), width = registerWithAddr.get().width)
                        )
                    ),
                cost = 1,
            )
        }
    },
    rule("read mem at const offset of register: `movq a(R_j), R_i`") {
        val constValue = variable<CodeGenIR.Const.Value>()
        val register = variable<Register>()
        val resRegister = variable<Register>()
        val widthValue = variable<Width>()

        match(
            CodeGenIR.Indirection(
                CodeGenIR.BinOP(
                    BinOpENUM.ADD,
                    CodeGenIR.Const(constValue),
                    RegisterRef(register),
                )
            )
        )

        condition {
            // TODO: This assumes, that widths of register, value and resRegister are the same. Maybe, we need to check this?
            // hab mal hier als beispiel das condition zeug eingebaut. der aufruf kann weg, falls nicht gebraucht wird
            true
        }

        replaceWith {
            val newRegister = newRegister(widthValue.get())
            Replacement(
                node = RegisterRef(newRegister),
                instructions = instructionListOf(
                    Instruction.mov(
                        Memory.of(const = constValue.get().value, base = register.get(), width = widthValue.get()),
                        resRegister.get()
                    )
                ),
                cost = 1,
            )
        }
    },
    rule("add register values: `addq [ R_i | R_j ] -> R_k`") {
        val leftRegister = variable<Register>()
        val rightRegister = variable<Register>()

        val left = variable<Replacement>()
        val right = variable<Replacement>()

        match(
            CodeGenIR.BinOP(
                BinOpENUM.ADD,
                RegisterRef(leftRegister, left),
                RegisterRef(rightRegister, right)
            )
        )

        replaceWith {
            check(leftRegister.get().width == rightRegister.get().width) { "Widths of `l` and `r` need to be the same in `BinOp(ADD, l, r)`" }

            val resRegister = newRegister(leftRegister.get().width)
            Replacement(
                node = RegisterRef(resRegister),
                instructions = left.get().instructions
                    .append(right.get().instructions)
                    .append(
                        Instruction.addq(leftRegister.get(), rightRegister.get(), resRegister)
                    ),
                cost = left.get().cost + right.get().cost + 1,
            )
        }
    },
    rule("return register content: `movq R_i \$@result`") {
        val resRegister = variable<Register>()
        val replacement = variable<Replacement>()

        match(
            CodeGenIR.Return(
                RegisterRef(resRegister, replacement)
            )
        )

        replaceWith {
            Replacement(
                node = Noop(),
                instructions = replacement.get().instructions
                    .append(
                        Instruction.mov(
                            resRegister.get(),
                            ReturnRegister(resRegister.get().width)
                        )
                    ),
                cost = replacement.get().cost + 1,
            )
        }
    },
    rule("return constant: `movq \$a \$@result`") {
        val constValue = variable<CodeGenIR.Const.Value>()

        match(
            CodeGenIR.Return(
                CodeGenIR.Const(constValue)
            )
        )

        replaceWith {
            Replacement(
                node = Noop(),
                instructions = instructionListOf(
                    Instruction.mov(
                        constValue.get().toMolkIR(),
                        ReturnRegister(constValue.get().mode)
                    )
                ),
                cost = 1,
            )
        }
    },
    rule("load from address in register: `movq 0(\$R_i) -> R_j`") {
        val registerValue = variable<Register>()
        match(
            CodeGenIR.Indirection(RegisterRef(registerValue))
        )
        replaceWith {
            val valueRegister = newRegister(registerValue.get().width)
            Replacement(
                node = RegisterRef(valueRegister),
                instructions = instructionListOf(
                    Instruction.mov(
                        Memory.of(const = "0", base = registerValue.get(), width = registerValue.get().width),
                        valueRegister
                    )
                ),
                cost = 1,
            )
        }
    },
    rule("assign reg from mem addr in reg") {
        val registerWithAddress = variable<Register>()
        val registerToWriteTo = variable<Register>()

        match(
            CodeGenIR.Assign(
                RegisterRef(registerToWriteTo),
                CodeGenIR.Indirection(RegisterRef(registerWithAddress))
            )
        )
        replaceWith {
            Replacement(
                node = RegisterRef(registerToWriteTo),
                instructions = instructionListOf(
                    Instruction.mov(
                        Memory.of(
                            const = "0",
                            base = registerWithAddress.get(),
                            width = registerWithAddress.get().width
                        ),
                        registerToWriteTo.get()
                    )
                ),
                cost = 1,
            )
        }
    },
    rule("assign register register") {
        val registerWithValue = variable<Register>()
        val registerToWriteTo = variable<Register>()

        match(
            CodeGenIR.Assign(
                RegisterRef(registerToWriteTo),
                RegisterRef(registerWithValue)
            )
        )
        replaceWith {
            Replacement(
                node = RegisterRef(registerToWriteTo),
                instructions = instructionListOf(
                    Instruction.mov(registerWithValue.get(), registerToWriteTo.get())
                ),
                cost = 1,
            )
        }
    },
    rule("call function and store to register") {
        val address = variable<Address>()
        val valueRegister = variable<Register>()
        val arguments = variable<List<CodeGenIR>>()

        match(
            CodeGenIR.Assign(
                lhs = CodeGenIR.RegisterRef(valueRegister),
                rhs = CodeGenIR.Call(address, arguments)
            )

        )

        replaceWith {
            val argReplacements = arguments.get().map {it.replacement}
            Replacement(
                node = RegisterRef(valueRegister.get()),
                instructions = instructionListOf(
                    Instruction.call(address.toString(), TODO("argReplacements"), valueRegister.get(), false)
                ),
                cost = argReplacements.sumOf { it!!.cost } + 1
            )
        }
    }
)
