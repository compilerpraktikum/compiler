package edu.kit.compiler.backend.codegen

import edu.kit.compiler.backend.molkir.Register
import edu.kit.compiler.backend.codegen.CodeGenIR.RegisterRef
import edu.kit.compiler.backend.molkir.Instruction
import edu.kit.compiler.backend.molkir.Memory
import edu.kit.compiler.backend.molkir.RegisterId
import edu.kit.compiler.backend.molkir.Width
import firm.nodes.Node
import edu.kit.compiler.backend.molkir.Register as MolkiRegister

enum class ReplacementRules(val rule: Rule) {
    /**
     * Rule 1: `movq $a, R_i`
     */
    READ_CONSTANT(rule {
        val constValue = value<String>()

        match(
            CodeGenIR.Const(constValue)
        )
        println("after match $constValue")
        replaceWith {
            println("perform do constant replacement")
            val newRegister = newRegister(Width.QUAD)
            RegisterRef(newRegister) to listOf(
                Instruction.movq(Memory.of(constValue.get()), newRegister)
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
                Instruction.movq(Memory.of(registerId.get()), addrValue.get())
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
            val memoryAddress = Memory.of(registerWithAddr.get())
            CodeGenIR.MemoryAddress(memoryAddress) to listOf(
                Instruction.movq(
                    Memory.of(registerWithValue.get()),
                    Memory.of("0", registerWithAddr.get())
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

        match(
            CodeGenIR.Indirection(
                CodeGenIR.BinOP(
                    BinOpENUM.ADD,
                    CodeGenIR.Const(constValue),
                    RegisterRef(register),
                )
            )
        )

        replaceWith {
            val newRegister = newRegister(Width.QUAD)
            RegisterRef(newRegister) to listOf(
                Instruction.movq(
                    Memory.of(const = constValue.get(), base = register.get()),
                    resRegister.get()
                )
            )
        }
    }),

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
    })
    ;

}
