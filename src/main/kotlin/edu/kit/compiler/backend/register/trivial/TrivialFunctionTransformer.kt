package edu.kit.compiler.backend.register.trivial

import edu.kit.compiler.backend.molkir.RegisterId
import edu.kit.compiler.backend.molkir.Width
import edu.kit.compiler.backend.register.FunctionTransformer
import edu.kit.compiler.backend.register.PlatformInstruction
import edu.kit.compiler.backend.register.PlatformTarget
import edu.kit.compiler.backend.register.calls.CallingConvention
import edu.kit.compiler.backend.register.calls.SimpleCallingConvention
import edu.kit.compiler.backend.molkir.Constant as MolkiConstant
import edu.kit.compiler.backend.molkir.Instruction as MolkiInstruction
import edu.kit.compiler.backend.molkir.Memory as MolkiMemory
import edu.kit.compiler.backend.molkir.Register as MolkiRegister
import edu.kit.compiler.backend.molkir.ReturnRegister as MolkiReturnRegister
import edu.kit.compiler.backend.molkir.Target as MolkiTarget
import edu.kit.compiler.backend.register.PlatformTarget.Register as PlatformRegister

/**
 * Trivial register allocation that just loads operands into registers from stack, executes the operation and dumps
 * pushes result back on stack.
 *
 * @param callingConvention the calling convention this function is called with
 */
class TrivialFunctionTransformer(
    private val callingConvention: CallingConvention,
) : FunctionTransformer {

    companion object {
        private const val RETURN_VALUE_SLOT = -1
    }

    /**
     * All virtual registers are saved in a table on the stack
     */
    private data class StackSlot(val virtualRegisterId: RegisterId, val offset: Int, val width: Width) {
        fun generateMemoryAddress(): PlatformTarget.Memory =
            PlatformTarget.Memory.of((-offset).toString(), PlatformRegister.RBP(), null, null)
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

    override fun transformCode(codeBlock: List<MolkiInstruction>) {
        codeBlock.forEach {
            transformInstruction(it)
            freeAllRegisters()
        }
    }

    override fun getPlatformCode(): List<PlatformInstruction> {
        val prologue = callingConvention.generateFunctionPrologue(this.currentSlotOffset)
        generatedCode.addAll(0, prologue)

        // TODO the epilogue might have to be inserted at multiple locations, if there are early returns.
        //  create a special method then and call it
        val epilogue = if (stackLayout.containsKey(RegisterId(RETURN_VALUE_SLOT))) {
            val returnValueSlot = stackLayout[RegisterId(RETURN_VALUE_SLOT)]!!
            callingConvention.generateFunctionEpilogue(returnValueSlot.generateMemoryAddress(), returnValueSlot.width)
        } else {
            callingConvention.generateFunctionEpilogue(null, null)
        }

        generatedCode.addAll(epilogue)

        return generatedCode
    }

    /**
     * Generate an instruction to load the value of a virtual MolkIR register to a physical platform target (i.e. an
     * actual register or a known memory location)
     */
    private fun generateLoadVirtualRegisterValue(virtualRegister: MolkiRegister, target: PlatformTarget) {
        // check the register has been assigned before
        check(stackLayout.containsKey(virtualRegister.id)) { "unallocated register referenced: ${virtualRegister.toMolki()}" }

        // generate an offset to RSP to load the value from
        val memoryLocation = stackLayout[virtualRegister.id]!!.generateMemoryAddress()

        val instruction = when (virtualRegister.width) {
            Width.BYTE -> PlatformInstruction.movb(memoryLocation, target)
            Width.WORD -> throw IllegalStateException("16bit addressing is not supported")
            Width.DOUBLE -> PlatformInstruction.movl(memoryLocation, target)
            Width.QUAD -> PlatformInstruction.movq(memoryLocation, target)
        }

        generatedCode.add(instruction)
    }

    /**
     * Generate code to spill a value from a register onto the stack
     *
     * @param virtualRegisterId the MolkIR register that is being spilled
     * @param registerWidth the width of the spilled register
     * @param platformRegister the currently associated platform register
     */
    private fun generateSpillCode(
        virtualRegisterId: RegisterId,
        registerWidth: Width,
        platformRegister: PlatformTarget
    ) {
        if (!stackLayout.containsKey(virtualRegisterId)) {
            stackLayout[virtualRegisterId] = StackSlot(virtualRegisterId, currentSlotOffset, registerWidth)

            /* eight byte alignment for fast quad-word-access */
            currentSlotOffset += 8
        }

        val instruction = when (registerWidth) {
            Width.BYTE -> PlatformInstruction.movb(
                platformRegister,
                stackLayout[virtualRegisterId]!!.generateMemoryAddress()
            )
            Width.WORD -> throw IllegalStateException("16bit addressing is not supported")
            Width.DOUBLE -> PlatformInstruction.movl(
                platformRegister,
                stackLayout[virtualRegisterId]!!.generateMemoryAddress()
            )
            Width.QUAD -> PlatformInstruction.movq(
                platformRegister,
                stackLayout[virtualRegisterId]!!.generateMemoryAddress()
            )
        }

        generatedCode.add(instruction)
    }

    /**
     * Allocate a [PlatformRegister] and set its prefix to the correct width.
     */
    private fun allocateRegister(registerWidth: Width): PlatformRegister {
        val platformRegister = this.allocator.allocateRegister()
        this.callingConvention.taintRegister(platformRegister)

        return when (registerWidth) {
            Width.BYTE -> platformRegister.halfWordWidth()
            Width.WORD -> platformRegister.wordWidth()
            Width.DOUBLE -> platformRegister.doubleWidth()
            Width.QUAD -> platformRegister.quadWidth()
        }
    }

    /**
     * Dissociate a [PlatformRegister] from a virtual register and make it available for allocation again.
     */
    private fun freeAllRegisters() {
        allocator.freeAll()
    }

    /**
     * Transform a [MolkiTarget.Input] into a [PlatformTarget] by allocating required registers and loading their
     * respective values.
     */
    private fun transformOperand(molkiTarget: MolkiTarget.Input): PlatformTarget {
        return when (molkiTarget) {
            is MolkiConstant -> PlatformTarget.Constant(molkiTarget.value)
            is MolkiMemory -> generateTransformMemory(molkiTarget)
            is MolkiRegister -> {
                val platformRegister = allocateRegister(molkiTarget.width)
                generateLoadVirtualRegisterValue(molkiTarget, platformRegister)
                platformRegister
            }
        }
    }

    /**
     * Transform a memory expression form MolkIR to PlatformIR
     */
    private fun generateTransformMemory(mem: MolkiMemory): PlatformTarget.Memory {
        val base = if (mem.base != null) {
            val platformRegister = allocateRegister(mem.base.width)
            generateLoadVirtualRegisterValue(mem.base, platformRegister)
            platformRegister
        } else {
            null
        }

        val index = if (mem.index != null) {
            val platformRegister = allocateRegister(mem.index.width)
            generateLoadVirtualRegisterValue(mem.index, platformRegister)
            platformRegister
        } else {
            null
        }

        val const = mem.const

        return if (base != null) {
            if (const != null) {
                PlatformTarget.Memory.of(const, base, index, mem.scale)
            } else {
                PlatformTarget.Memory.of(base, index, mem.scale)
            }
        } else if (const != null) {
            PlatformTarget.Memory.of(const, index, mem.scale)
        } else {
            throw IllegalArgumentException("cannot have memory without base and const")
        }
    }

    /**
     * Transform a [MolkiTarget.Output] into a [PlatformTarget]. If the output is a register,
     * a new one will be allocated, but no value will be loaded
     */
    private fun transformResult(molkiTarget: MolkiTarget.Output): PlatformTarget {
        return when (molkiTarget) {
            is MolkiMemory -> generateTransformMemory(molkiTarget)
            is MolkiRegister -> {
                allocateRegister(molkiTarget.width)
            }
            is MolkiReturnRegister -> {
                allocateRegister(molkiTarget.width)
            }
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
            is MolkiInstruction.Call -> transformFunctionCall(instr)
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
            generateSpillCode(instr.result.id, instr.result.width, right)
        else if (instr.result is MolkiReturnRegister)
            generateSpillCode(RegisterId(RETURN_VALUE_SLOT), instr.result.width, right)
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
        when (instr.result) {
            is MolkiRegister -> generateSpillCode(instr.result.id, instr.result.width, result)
            is MolkiReturnRegister -> generateSpillCode(RegisterId(RETURN_VALUE_SLOT), instr.result.width, result)
            else -> throw IllegalStateException("unexpected: instruction result target is not a register")
        }
    }

    private fun transformFunctionCall(instr: MolkiInstruction.Call) {
        var parameterZone = 0

        for (i in (0 until instr.arguments.size).reversed()) {
            // TODO stack alignment probably must be guaranteed somehow?

            // TODO how do we know how big the arguments are?

            // push argument (possibly without using a register, as memory -> register -> memory probably isn't faster
            // than memory -> memory)
        }

        // generate function call
        generatedCode.add(PlatformInstruction.Call(instr.name))

        // save return value
        if (instr.result != null) {
            // TODO get the calling convention from somewhere else
            val platformRegister = SimpleCallingConvention.getReturnValueTarget()

            when (instr.result) {
                is MolkiRegister -> generateSpillCode(instr.result.id, instr.result.width, platformRegister)
                is MolkiReturnRegister -> generateSpillCode(
                    RegisterId(RETURN_VALUE_SLOT),
                    instr.result.width,
                    platformRegister
                )
                else -> {
                    throw IllegalStateException("unexpected: instruction result target is not a register")
                }
            }
        }

        // cleanup stack
        generatedCode.add(
            PlatformInstruction.addq(
                PlatformTarget.Register.RSP(),
                PlatformTarget.Constant(parameterZone.toString())
            )
        )
    }
}
