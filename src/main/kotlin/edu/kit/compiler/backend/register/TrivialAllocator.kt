package edu.kit.compiler.backend.register

import edu.kit.compiler.backend.molkir.RegisterId
import edu.kit.compiler.backend.molkir.Target
import java.util.Stack
import edu.kit.compiler.backend.molkir.Instruction as MolkInstr
import edu.kit.compiler.backend.register.Instruction as RegisterInstr

/**
 * Trivial register allocation that just loads operands into registers from stack, executes the operation and dumps
 * pushes result back on stack.
 */
class TrivialAllocator : AllocationStrategy {

    /**
     * Current stack layout of virtual registers
     */
    private val stackLayout = Stack<RegisterId>()

    private val generatedCode = mutableListOf<RegisterInstr>()

    // assume that the `codeBlock` is a whole function
    override fun allocateRegisters(codeBlock: List<MolkInstr>) {
        for (instruction in codeBlock) {
            when (instruction) {
                is MolkInstr.BinaryOperation -> generateNAryOperation(instruction.left, instruction.right)
                is MolkInstr.BinaryOperationWithResult -> TODO()
                is MolkInstr.BinaryOperationWithTwoPartResult -> TODO()
                is MolkInstr.Call -> TODO()
                is MolkInstr.Jump -> TODO()
                is MolkInstr.Label -> TODO()
            }
        }
    }

    private fun generateNAryOperation(vararg targets: Target) {
        var registerCounter = 0

        for (target in targets) {
            val platformTarget = when (target) {
                is Target.InputOutputTarget.Constant -> PlatformTarget.Constant(target.value)
                is Target.InputOutputTarget.Memory -> TODO()
                is Target.InputOutputTarget.Register -> {
                    // search for the register in stack to know where to load it from
                    val stackDepth = stackLayout.indexOf(target.id)

                    // TODO: this obviously doesn't work for function calls yet, because
                    //  a) calling convention (when applicable) and b) the register array only has so many entries
                    generatedCode.add(
                        RegisterInstr.movq(
                            TODO("memory offset to RSP"),
                            PlatformTarget.GeneralPurposeRegister.registers[registerCounter++]
                        )
                    )

                    // TODO: assign the chosen general purpose register to the target instruction instace
                    // TODO: conver the molki instruction instance to target instruction instance
                }
                is Target.OutputTarget.ReturnRegister -> TODO()
            }

            // TODO if the instruction has a return value, push it onto the stack
        }
    }
}
