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
     * All virtual registers are saved in a table on the stack
     */
    private data class StackSlot(val virtualRegisterId: RegisterId, val offset: Int, val width: Width) {
        fun generateMemoryAddress(): PlatformTarget.Memory = PlatformTarget.Memory(-offset, PlatformRegister.RBP())
    }

    /**
     * Stores the current offset where a new [StackSlot] is placed on the stack
     */
    private var currentSlotOffset = 0

    /**
     * Current stack layout of virtual registers
     */
    private val stackLayout = mutableMapOf<RegisterId, StackSlot>()

    /**
     * Generated code in [PlatformInstruction]s
     */
    private val generatedCode = mutableListOf<PlatformInstruction>()

    /**
     * Register allocator
     */
    private val allocator = TrivialAllocator()

    // assume that the `codeBlock` is a whole function
    override fun transformCode(codeBlock: List<MolkiInstruction>): List<PlatformInstruction> {
        codeBlock.forEach(::transformInstruction)
        generatedCode.add(
            0,
            PlatformInstruction.subq(PlatformRegister.RBP(), PlatformTarget.Constant(currentSlotOffset.toString()))
        )
        return generatedCode
    }

    /**
     * Generate an instruction to load the value of a virtual MolkIR register to a physical platform target (i.e. an
     * actual register or a known memory location)
     */
    private fun generateLoadVirtualRegisterValue(virtualRegister: MolkiRegister, target: PlatformTarget) {
        // check the register has been assigned before
        check(stackLayout.containsKey(virtualRegister.id)) { "unallocated register referenced: ${virtualRegister.toMolki()}" }

        // calculate the current location of the register content on the stack
        val slotOffset = stackLayout[virtualRegister.id]!!.offset

        // generate an offset to RSP to load the value from
        val memoryLocation = stackLayout[virtualRegister.id]!!.generateMemoryAddress()

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
        if (!stackLayout.containsKey(virtualRegister.id)) {
            stackLayout[virtualRegister.id] = StackSlot(virtualRegister.id, currentSlotOffset, virtualRegister.width)

            /* eight byte alignment for fast quad-word-access */
            currentSlotOffset += 8
        }

        val instruction = when (virtualRegister.width) {
            Width.BYTE -> PlatformInstruction.movb(
                platformRegister,
                stackLayout[virtualRegister.id]!!.generateMemoryAddress()
            )
            Width.WORD -> TODO("not implemented")
            Width.DOUBLE -> PlatformInstruction.movl(
                platformRegister,
                stackLayout[virtualRegister.id]!!.generateMemoryAddress()
            )
            Width.QUAD -> PlatformInstruction.movq(
                platformRegister,
                stackLayout[virtualRegister.id]!!.generateMemoryAddress()
            )
        }

        generatedCode.add(instruction)
    }

    /**
     * Allocate a [PlatformRegister] for a [virtualRegister] and generate an instruction to load its value from the
     * stack.
     */
    private fun allocateRegister(virtualRegister: MolkiRegister): PlatformRegister {
        val platformRegister = this.allocator.allocateRegister()

        return when (virtualRegister.width) {
            Width.BYTE -> platformRegister.halfWordWidth()
            Width.WORD -> platformRegister.wordWidth()
            Width.DOUBLE -> platformRegister.doubleWidth()
            Width.QUAD -> platformRegister.quadWidth()
        }
    }

    /**
     * Dissociate a [PlatformRegister] from a virtual register and make it available for allocation again.
     */
    private fun freeRegister(register: PlatformRegister) {
        allocator.freeRegister(register)
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
     * Transform a MolkIR instruction into a platform target instruction by allocating registers, loading the required
     * values and spilling the result
     */
    private fun transformInstruction(instr: MolkiInstruction) {
        when (instr) {
            is MolkiInstruction.BinaryOperation -> transformBinaryOperation(instr)
            is MolkiInstruction.BinaryOperationWithResult -> transformBinaryOperationWithResult(instr)
            is MolkiInstruction.BinaryOperationWithTwoPartResult -> TODO()
            is MolkiInstruction.Call -> TODO()
            is MolkiInstruction.Jump -> PlatformInstruction.Jump(instr.name, instr.label)
            is MolkiInstruction.Label -> PlatformInstruction.Label(instr.name)
            is MolkiInstruction.UnaryOperationWithResult -> transformUnaryOperationWithResult(instr)
        }
    }

    private fun transformBinaryOperation(instr: edu.kit.compiler.backend.molkir.Instruction.BinaryOperation) {
        // transform operands
        val left = transformOperand(instr.left)
        val right = transformOperand(instr.right)

        // transform instruction
        val transformedInstr = PlatformInstruction.BinaryOperation(instr.name, left, right)
        generatedCode.add(transformedInstr)

        // free allocated registers
        arrayOf(left, right).filterIsInstance<PlatformRegister>().forEach(::freeRegister)
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
        if (instr.result is MolkiRegister)
            generateSpillCode(instr.result, right)

        // free allocated registers
        arrayOf(left, right).filterIsInstance<PlatformRegister>().forEach(::freeRegister)
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
        if (instr.result is MolkiRegister)
            generateSpillCode(instr.result, result)

        // free allocated registers
        arrayOf(operand, result).filterIsInstance<PlatformRegister>().forEach(::freeRegister)
    }
}
