package edu.kit.compiler.backend.codegen

import edu.kit.compiler.backend.molkir.Register
import edu.kit.compiler.backend.codegen.CodeGenIR.RegisterRef
import edu.kit.compiler.backend.molkir.Instruction
import edu.kit.compiler.backend.molkir.Memory
import edu.kit.compiler.backend.molkir.Constant
import edu.kit.compiler.backend.molkir.Width

enum class ReplacementRules(val rule: Rule) {
    /**
     * Rule 1: `movq $a, R_i`
     */
    READ_CONSTANT(rule {
        val constValue = value<String>()
        val widthValue = value<Width>()

        match(
            CodeGenIR.Const(constValue, widthValue)
        )
        replaceWith {
            val newRegister = newRegister(widthValue.get())
            RegisterRef(newRegister) to listOf(
                Instruction.movq(Constant(constValue.get(), widthValue.get()), newRegister)
            )
        }
    }),

    /**
     * Rule 2: `movq x, R_i`
     */
    READ_MEMORY_ADDR(rule {
        val addrValue = value<Memory>()

        match(
            CodeGenIR.MemoryAddress(addrValue)
        )

        replaceWith {
            val newRegister = newRegister(Width.QUAD)
            RegisterRef(newRegister) to listOf(
                Instruction.movq(addrValue.get(), newRegister)
            )
        }
    }),

    /**
     * Rule 3: `movq R_i, x`
     */
    ASSIGN_MEMORY_REGISTER(rule {
        val addrValue = value<Memory>()
        val registerId = value<Register>()

        match(
            CodeGenIR.Assign(
                CodeGenIR.MemoryAddress(
                    addrValue
                ),
                RegisterRef(registerId)
            )
        )

        replaceWith {
            CodeGenIR.MemoryAddress(addrValue) to listOf(
                Instruction.movq(Memory.of(registerId.get(), width = addrValue.get().width), addrValue.get())
            )
        }
    }),

    /**
     * Rule 4: `movq R_j, 0(R_i)
     */
    ASSIGN_ADDR_IN_REGISTER_VALUE_IN_REGISTER(rule {
        val registerWithAddr = value<Register>()
        val registerWithValue = value<Register>()

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
            CodeGenIR.MemoryAddress(memoryAddress) to listOf(
                Instruction.movq(
                    Memory.of(registerWithValue.get(), width = registerWithValue.get().width),
                    Memory.of("0", registerWithAddr.get(), width = registerWithAddr.get().width)
                )
            )
        }
    }),

    /**
     * Rule 5: `movq a(R_j), R_i`
     */
    READ_MEM_AT_CONST_OFFSET_OF_REGISTER(rule {
        val constValue = value<String>()
        val register = value<Register>()
        val resRegister = value<Register>()
        val widthValue = value<Width>()

        // TODO: This assumes, that widths of register, value and resRegister are the same. Maybe, we need to check this?
        match(
            CodeGenIR.Indirection(
                CodeGenIR.BinOP(
                    BinOpENUM.ADD,
                    CodeGenIR.Const(constValue, widthValue),
                    RegisterRef(register),
                )
            )
        )

        replaceWith {
            val newRegister = newRegister(widthValue.get())
            RegisterRef(newRegister) to listOf(
                Instruction.movq(
                    Memory.of(const = constValue.get(), base = register.get(), width = widthValue.get()),
                    resRegister.get()
                )
            )
        }
    }),

    /**
     * `addq R_i R_j -> R_k`
     */
    ADD_REGISTER_VALUES(rule {
        val leftRegister = value<Register>()
        val rightRegister = value<Register>()
        match(
            CodeGenIR.BinOP(
                BinOpENUM.ADD,
                RegisterRef(leftRegister),
                RegisterRef(rightRegister)
            )
        )
        replaceWith {
            assert(leftRegister.get().width == rightRegister.get().width) { "Widths of `l` and `r` need to be the same in `BinOp(ADD, l, r)`" }
            val resRegister = newRegister(leftRegister.get().width)
            RegisterRef(resRegister) to listOf(
                Instruction.addq(
                    leftRegister.get(), rightRegister.get(), resRegister
                )
            )
        }
    }),

    /**
     * `movq R_i $@result; jmp functionReturn`
     */
    RETURN_REGISTER_CONTENT(rule {
        val resRegister = value<Register>()
        match(
            CodeGenIR.Return(
                RegisterRef(resRegister)
            )
        )

        replaceWith {
            RegisterRef(resRegister.get()) to listOf(
                Instruction.movq(
                    resRegister.get(),
                    edu.kit.compiler.backend.molkir.ReturnRegister(resRegister.get().width)
                )
                // TODO jmp to returnLabel
            )
        }
    }),

    /**
     * `movq $a $@result; jmp functionReturn`
     */
    RETURN_CONSTANT(rule {
        val constValue = value<String>()
        val widthValue = value<Width>()
        match(
            CodeGenIR.Return(
                CodeGenIR.Const(constValue, widthValue)
            )
        )

        replaceWith {
            CodeGenIR.Const(constValue.get(), widthValue.get()) to listOf(
                Instruction.movq(
                    Constant(constValue.get(), widthValue.get()),
                    edu.kit.compiler.backend.molkir.ReturnRegister(widthValue.get())
                )
            )
        }
    }),

    /**
     * `movq 0($R_i) -> R_j`
     */
    LOAD_FROM_ADDRESS_IN_REGISTER(rule {
        val registerValue = value<Register>()
        match(
            CodeGenIR.Indirection(RegisterRef(registerValue))
        )
        replaceWith {
            val valueRegister = newRegister(registerValue.get().width)
            RegisterRef(valueRegister) to
                listOf(
                    Instruction.movq(
                        Memory.of(const = "0", base = registerValue.get(), width = registerValue.get().width),
                        valueRegister
                    )
                )
        }
    })
    ;

}
