package edu.kit.compiler.backend.codegen

import edu.kit.compiler.backend.codegen.CodeGenIR.RegisterRef
import edu.kit.compiler.backend.molkir.Instruction
import edu.kit.compiler.backend.molkir.Memory
import edu.kit.compiler.backend.molkir.Register
import edu.kit.compiler.backend.molkir.ReturnRegister
import edu.kit.compiler.backend.molkir.Width
import edu.kit.compiler.utils.Rule
import edu.kit.compiler.utils.rule

fun Replacement?.assertExists() = this ?: error("no replacement found")

val replacementRules = listOf<Rule<CodeGenIR, Replacement, ReplacementScope>>(
    rule("seq") {
        val value = variable<CodeGenIR>()
        val exec = variable<CodeGenIR>()

        match(
            CodeGenIR.Seq(value, exec)
        )

        replaceWith {
            val execReplacement = exec.get().replacement.assertExists() // TODO does is need to exist?
            val valueReplacement = value.get().replacement
            Replacement(
                node = value.get(),
                instructions = execReplacement.instructions.apply {
                    valueReplacement?.let { append(it.instructions) }
                },
                cost = execReplacement.cost + (valueReplacement?.cost ?: 0)
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
                node = RegisterRef(newRegister),
                instructions = instructionListOf(
                    Instruction.movq(const.get().toMolki(), newRegister)
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
                    Instruction.movq(addrValue.get(), newRegister)
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
                    Instruction.movq(
                        Memory.of(registerId.get(), width = addrValue.get().width),
                        addrValue.get()
                    )
                ),
                cost = 1,
            )
        }
    },
    rule("assign addr in register value in register: `movq R_j, (R_i)`") {
        val registerWithAddr = variable<Register>()
        val registerWithValue = variable<Register>()

        match(
            CodeGenIR.Assign(
                CodeGenIR.Indirection(
                    RegisterRef(registerWithAddr)
                ),
                RegisterRef(registerWithValue)
            )
        )

        replaceWith {
            val memoryAddress = Memory.of(registerWithAddr.get(), width = registerWithValue.get().width)
            Replacement(
                node = CodeGenIR.MemoryAddress(memoryAddress),
                instructions = instructionListOf(
                    Instruction.movq(
                        Memory.of(registerWithValue.get(), width = registerWithValue.get().width),
                        Memory.of("0", registerWithAddr.get(), width = registerWithAddr.get().width)
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
            true
        }

        replaceWith {
            val newRegister = newRegister(widthValue.get())
            Replacement(
                node = RegisterRef(newRegister),
                instructions = instructionListOf(
                    Instruction.movq(
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
    rule("return register content: `movq R_i \$@result; jmp functionReturn`") {
        val resRegister = variable<Register>()

        match(
            CodeGenIR.Return(
                RegisterRef(resRegister)
            )
        )

        replaceWith {
            Replacement(
                node = RegisterRef(resRegister.get()), // TODO: really the result register? maybe sth like Noop or whatever
                instructions = instructionListOf(
                    Instruction.movq(
                        resRegister.get(),
                        ReturnRegister(resRegister.get().width)
                    )
                    // TODO jmp to returnLabel
                ),
                cost = 1,
            )
        }
    },
    rule("return constant: `movq \$a \$@result; jmp functionReturn`") {
        val constValue = variable<CodeGenIR.Const.Value>()

        match(
            CodeGenIR.Return(
                CodeGenIR.Const(constValue)
            )
        )

        replaceWith {
            Replacement(
                node = CodeGenIR.Const(TODO("some noop")),
                instructions = instructionListOf(
                    Instruction.movq(
                        constValue.get().toMolki(),
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
                    Instruction.movq(
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
                    Instruction.movq(
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
                    Instruction.movq(registerWithValue.get(), registerToWriteTo.get())
                ),
                cost = 1,
            )
        }
    }
)
