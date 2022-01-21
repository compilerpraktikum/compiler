package edu.kit.compiler.backend.register.trivial

import edu.kit.compiler.backend.molkir.RegisterId
import edu.kit.compiler.backend.molkir.Width
import edu.kit.compiler.backend.register.PlatformTarget
import edu.kit.compiler.backend.register.PlatformTransformer
import edu.kit.compiler.backend.molkir.Constant as MolkiConstant
import edu.kit.compiler.backend.molkir.Instruction as MolkiInstruction
import edu.kit.compiler.backend.molkir.Memory as MolkiMemory
import edu.kit.compiler.backend.molkir.Register as MolkiRegister
import edu.kit.compiler.backend.molkir.ReturnRegister as MolkiReturnRegister
import edu.kit.compiler.backend.molkir.Target as MolkiTarget
import edu.kit.compiler.backend.register.Instruction as PlatformInstruction
import edu.kit.compiler.backend.register.PlatformTarget.GeneralPurposeRegister as PlatformRegister

/**
 * Trivial register allocation that just loads operands into registers from stack, executes the operation and dumps
 * pushes result back on stack.
 */
class TrivialTransformer : PlatformTransformer {

    /**
     * Current stack layout of virtual registers
     */
    private val stackLayout = ArrayDeque<MolkiRegister>()

    /**
     * Generated code in [RegisterIR]
     */
    private val generatedCode = mutableListOf<PlatformInstruction>()

    /**
     * Register allocator
     */
    private val allocator = TrivialAllocator()

    /**
     * Maps virtual molki registers to platform registers. Those associations shouldn't be valid for more than one
     * instruction, but may be important for generating spill code.
     */
    private val associatedRegisters = mutableMapOf<RegisterId, PlatformRegister>()

    // assume that the `codeBlock` is a whole function
    override fun transformCode(codeBlock: List<MolkiInstruction>): List<PlatformInstruction> {
        codeBlock.forEach(::transformInstruction)
        return generatedCode
    }

    /**
     * Generate an instruction to load the value of a virtual MolkIR register to a physical platform target (i.e. an
     * actual register or a known memory location)
     */
    private fun generateLoadVirtualRegisterValue(virtualRegister: MolkiRegister, target: PlatformTarget) {
        // check the register has been assigned before
        check(stackLayout.any { it.id == virtualRegister.id }) { "unallocated register referenced: ${virtualRegister.toMolki()}" }

        // calculate the current location of the register content on the stack
        val stackDepth = stackLayout.toList()
            .stream()
            .limit(stackLayout.indexOfFirst { it.id == virtualRegister.id }.toLong())
            .map { it.width.inBytes }
            .reduce(0) { x, y -> x + y }

        // generate an offset to RSP to load the value from
        val memoryLocation = PlatformTarget.Memory(stackDepth, PlatformRegister.RSP())

        val instruction = when (virtualRegister.width) {
            Width.BYTE, Width.WORD -> TODO("not implemented")
            Width.DOUBLE -> PlatformInstruction.movl(memoryLocation, target)
            Width.QUAD -> PlatformInstruction.movq(memoryLocation, target)
        }

        generatedCode.add(instruction)
    }

    /**
     * Generate code to spill a value from a register onto the stack
     *
     * @param virtualRegister the MolkIR register that is being spilled
     * @param platformRegister the currently associated platform register
     */
    private fun generateSpillCode(virtualRegister: MolkiRegister, platformRegister: PlatformTarget) {
        val instruction = when (virtualRegister.width) {
            Width.BYTE, Width.WORD -> TODO("not implemented")
            Width.DOUBLE -> PlatformInstruction.pushl(platformRegister)
            Width.QUAD -> PlatformInstruction.pushq(platformRegister)
        }

        stackLayout.addFirst(virtualRegister)
        generatedCode.add(instruction)
    }

    /**
     * Allocate a [PlatformRegister] for a [virtualRegister] and generate an instruction to load its value from the
     * stack.
     */
    private fun allocateRegister(virtualRegister: MolkiRegister): PlatformRegister {
        val platformRegister = this.allocator.allocateRegister()

        when (virtualRegister.width) {
            Width.BYTE -> platformRegister.halfWordWidth()
            Width.WORD -> platformRegister.wordWidth()
            Width.DOUBLE -> platformRegister.doubleWidth()
            Width.QUAD -> platformRegister.quadWidth()
        }

        this.associatedRegisters[virtualRegister.id] = platformRegister
        return platformRegister
    }

    /**
     * Dissociate a [PlatformRegister] from a virtual register and make it available for allocation again.
     */
    private fun freeRegister(virtualRegister: MolkiRegister) {
        val platformRegister = associatedRegisters.remove(virtualRegister.id)
            ?: error("virtual register not associated to platform register")
        allocator.freeRegister(platformRegister)
    }

    /**
     * Transform a [MolkiTarget.Input] into a [PlatformTarget] by allocating required registers and loading their
     * respective values.
     */
    private fun transformOperand(molkiTarget: MolkiTarget.Input): PlatformTarget {
        return when (molkiTarget) {
            is MolkiConstant -> PlatformTarget.Constant(molkiTarget.value)
            is MolkiMemory -> TODO()
            is MolkiRegister -> {
                val platformRegister = allocateRegister(molkiTarget)
                generateLoadVirtualRegisterValue(molkiTarget, platformRegister)
                platformRegister
            }
        }
    }

    /**
     * Transform a [MolkiTarget.Output] into a [PlatformTarget]. If the output is a register,
     * a new one will be allocated, but no value will be loaded
     */
    private fun transformResult(molkiTarget: MolkiTarget.Output): PlatformTarget {
        when (molkiTarget) {
            is MolkiMemory -> TODO()
            is MolkiRegister -> {
                return allocateRegister(molkiTarget)
            }
            is MolkiReturnRegister -> TODO()
        }
    }

    /**
     * Cleanup operands of an instruction by freeing the associated platform registers.
     */
    private fun cleanupOperands(vararg operands: MolkiTarget) {
        operands.forEach { operand ->
            when (operand) {
                is MolkiConstant, is MolkiMemory -> {}
                is MolkiRegister -> freeRegister(operand)
                is MolkiReturnRegister -> TODO()
            }
        }
    }

    /**
     * Transform a MolkIR instruction into a platform target instruction by allocating registers, loading the required
     * values and spilling the result
     */
    private fun transformInstruction(instr: MolkiInstruction) {
        when (instr) {
            is MolkiInstruction.BinaryOperation -> TODO()
            is MolkiInstruction.BinaryOperationWithResult -> transformBinaryOperationWithResult(instr)
            is MolkiInstruction.BinaryOperationWithTwoPartResult -> TODO()
            is MolkiInstruction.Call -> TODO()
            is MolkiInstruction.Jump -> TODO()
            is MolkiInstruction.Label -> TODO()
            is MolkiInstruction.UnaryOperationWithResult -> transformUnaryOperationWithResult(instr)
        }
    }

    /**
     * Transform an operation with two inputs and a result into a binary operation.
     * It is assumed that x86 will treat the right argument as the result target, and thus it will be spilled.
     * Allocates the required registers and spills the result onto the stack.
     */
    private fun transformBinaryOperationWithResult(instr: MolkiInstruction.BinaryOperationWithResult) {
        // transform operands
        val left = transformOperand(instr.left)
        val right = transformOperand(instr.right)

        // transform instruction
        val transformedInstr = PlatformInstruction.BinaryOperation(instr.name, left, right)
        generatedCode.add(transformedInstr)

        // generate spill code
        if (instr.result is MolkiRegister) {
            generateSpillCode(instr.result, right)
        }

        // free allocated registers
        cleanupOperands(instr.left, instr.right)
    }

    /**
     * Transform an operation with one input and with a result into a binary operation.
     * Allocates the required registers and spills the result onto the stack.
     */
    private fun transformUnaryOperationWithResult(instr: MolkiInstruction.UnaryOperationWithResult) {
        // transform operands
        val operand = transformOperand(instr.operand)
        val result = transformResult(instr.result)

        // transform instruction
        val transformedInstr = PlatformInstruction.BinaryOperation(instr.name, operand, result)
        generatedCode.add(transformedInstr)

        // generate spill code
        if (result is PlatformRegister) {
            generateSpillCode(instr.result as MolkiRegister, result)
        }

        // free allocated registers
        cleanupOperands(instr.operand, instr.result)
    }
}
