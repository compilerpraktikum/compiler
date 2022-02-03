package edu.kit.compiler.backend.register.transformer

import edu.kit.compiler.backend.molkir.Constant
import edu.kit.compiler.backend.molkir.Memory
import edu.kit.compiler.backend.molkir.RegisterId
import edu.kit.compiler.backend.molkir.ReturnRegister
import edu.kit.compiler.backend.molkir.Width
import edu.kit.compiler.backend.register.EnumRegister
import edu.kit.compiler.backend.register.FunctionTransformer
import edu.kit.compiler.backend.register.PlatformInstruction
import edu.kit.compiler.backend.register.PlatformTarget
import edu.kit.compiler.backend.register.PlatformTransformation
import edu.kit.compiler.backend.register.allocator.TrivialAllocator
import edu.kit.compiler.backend.register.calls.CallingConvention
import edu.kit.compiler.backend.register.stack.StackRegisterTable
import edu.kit.compiler.backend.molkir.Instruction as MolkiInstruction
import edu.kit.compiler.backend.molkir.Register as MolkiRegister
import edu.kit.compiler.backend.molkir.Target as MolkiTarget
import edu.kit.compiler.backend.register.PlatformTarget.Register as PlatformRegister

/**
 * @param callingConvention the calling convention this function is called with
 * @param parameters number of function parameters
 */
class TimidFunctionTransformer(
    private val callingConvention: CallingConvention,
    private val parameters: Int
) : FunctionTransformer {

    companion object {
        private const val RETURN_VALUE_SLOT = -1
    }

    /**
     * A table of spill slots for virtual registers on stack
     */
    private val stackRegisterTable = StackRegisterTable()

    /**
     * Generated code in [PlatformInstruction]s
     */
    private val generatedCode = mutableListOf<PlatformInstruction>()

    /**
     * Register allocator
     */
    private val allocator = TrivialAllocator()

    /**
     * A map of virtual registers that are live and their allocated [PlatformRegister], or `null` if the variable has
     * been spilled.
     */
    private val liveVariables = mutableMapOf<RegisterId, PlatformRegister?>()

    /**
     * A list of all variables that are currently linked to a platform register)
     */
    private val linkedVariables = mutableListOf<MolkiRegister>()

    override fun transformCode(codeBlock: List<MolkiInstruction>) {
        // registers referenced in each instruction
        val registers = mutableMapOf<MolkiInstruction, List<Pair<MolkiRegister, Boolean>>>()

        // a mapping of RegisterIds to the instruction (and its index) within the code block where it is last referenced
        val variableDeaths = mutableMapOf<MolkiRegister, Pair<Int, MolkiInstruction>>()

        // calculate variable deaths
        for (i in codeBlock.indices.reversed()) {
            val extractedRegisters = extractRegisters(codeBlock[i])
            extractedRegisters.forEach { (register, _) ->
                variableDeaths.putIfAbsent(register, i to codeBlock[i])
            }

            registers[codeBlock[i]] = extractedRegisters
        }

        // allocate registers and then transform code
        codeBlock.forEach { instruction ->
            registers[instruction]!!.forEach { (register, isInput) ->
                if (liveVariables[register.id] == null) {
                    if (!allocator.hasMoreRegisters()) {
                        val spillCandidate =
                            findSpillCandidate(variableDeaths, registers[instruction]!!.map { it.first.id })

                        spillAndFreeRegister(spillCandidate)
                    }

                    val platformRegister = linkVirtualRegister(register)

                    if (isInput) {
                        generateLoadVirtualRegisterValue(register, platformRegister)
                    }
                }
            }

            transformInstruction(instruction)

            // spill if the variable dies here, and has not been panic-spilled before
            registers[instruction]!!.forEach { (register, _) ->
                if (variableDeaths[register]?.second == instruction && liveVariables[register.id] != null) {
                    spillAndFreeRegister(register)
                }
            }
        }

        check(linkedVariables.isEmpty()) { "some variables have not been spilled before the end of block" }
    }

    /**
     * Extract all referenced registers from an instruction
     *
     * @return a list of register id's referenced in the instruction and boolean flags indicating whether the registers
     * are input registers
     */
    private fun extractRegisters(instr: MolkiInstruction): List<Pair<MolkiRegister, Boolean>> {
        val targets = when (instr) {
            is MolkiInstruction.BinaryOperation -> listOf(instr.left to true, instr.right to true)
            is MolkiInstruction.BinaryOperationWithResult -> listOf(
                instr.left to true,
                instr.right to true,
                instr.result to false
            )
            is MolkiInstruction.Call -> emptyList() // linking for calls is done manually
            is MolkiInstruction.UnaryOperationWithResult -> listOf(instr.operand to true, instr.result to false)
            is MolkiInstruction.DivisionOperation -> emptyList() // linking for divisions is done manually
            is MolkiInstruction.Jump,
            is MolkiInstruction.Label,
            is MolkiInstruction.Comment -> emptyList()
        }

        return targets
            .map { (target, input) ->
                // extract registers from address modes
                if (target is Memory) {
                    listOfNotNull(target.base to input, target.index to input)
                } else {
                    listOf(target to input)
                }
            }
            .flatten()
            .mapNotNull { (target, flag) -> (target as? MolkiRegister)?.to(flag) }
    }

    /**
     * Generate an instruction to load the value of a virtual MolkIR register to a physical platform target (i.e. an
     * actual register or a known memory location)
     */
    private fun generateLoadVirtualRegisterValue(virtualRegister: MolkiRegister, target: PlatformTarget.Register) {
        val instruction = if (virtualRegister.id.value < this.parameters) {
            // generate an offset to RSP to load the value from
            val memoryLocation = callingConvention.getParameterLocation(virtualRegister.id.value)
            PlatformInstruction.mov(memoryLocation, target, virtualRegister.width)
        } else {
            // check the register has been assigned before
            check(stackRegisterTable.containsRegister(virtualRegister.id)) { "unallocated register referenced: ${virtualRegister.toMolki()}" }

            // generate an offset to RSP to load the value from
            val memoryLocation = stackRegisterTable.getRegisterSlot(virtualRegister.id)!!.generateMemoryAddress()
            PlatformInstruction.mov(memoryLocation, target, virtualRegister.width)
        }

        appendInstruction(instruction)
    }

    /**
     * Find the best candidate for generating spill code and free the register
     */
    fun findSpillCandidate(
        variableDeaths: Map<MolkiRegister, Pair<Int, MolkiInstruction>>,
        requiredVariables: List<RegisterId>
    ): MolkiRegister {
        return variableDeaths.keys
            .filter { liveVariables[it.id]?.register != null } // only consider candidates that are actually allocated
            .filter { it.id !in requiredVariables } // don't consider candidates that are required for the current instruction
            .maxByOrNull { register -> variableDeaths[register]!!.first } ?: error("cannot spill any variable")
    }

    override fun getPlatformCode(): List<PlatformInstruction> {
        val prologue = callingConvention.generateFunctionPrologue(stackRegisterTable.registerTableSize)
        generatedCode.addAll(0, prologue)

        val epilogue = if (stackRegisterTable.containsRegister(RegisterId(RETURN_VALUE_SLOT))) {
            val returnValueSlot = stackRegisterTable.getRegisterSlot(RegisterId(RETURN_VALUE_SLOT))!!
            callingConvention.generateFunctionEpilogue(returnValueSlot.generateMemoryAddress(), returnValueSlot.width)
        } else {
            callingConvention.generateFunctionEpilogue(null, null)
        }

        generatedCode.addAll(epilogue)

        return generatedCode
    }

    /**
     * Link a virtual register to a platform register
     */
    private fun linkVirtualRegister(register: MolkiRegister): PlatformRegister {
        check(liveVariables[register.id] == null) { "virtual register ${register.id} is already linked to platform register" }
        val platformRegister = allocateRegister(register.width)
        liveVariables[register.id] = platformRegister
        linkedVariables.add(register)
        return platformRegister
    }

    /**
     * Unlink a virtual register from a platform register
     */
    private fun unlinkVirtualRegister(registerId: RegisterId) {
        val allocated = liveVariables[registerId]!!
        freeRegister(allocated)
        liveVariables[registerId] = null
        linkedVariables.removeIf { it.id == registerId }
    }

    /**
     * Allocate a [PlatformRegister] and set its prefix to the correct width.
     */
    private fun allocateRegister(registerWidth: Width): PlatformRegister {
        val platformRegister = this.allocator.allocateRegister().width(registerWidth)
        prepareRegister(platformRegister)
        return platformRegister
    }

    /**
     * Allocate a specific [PlatformRegister] and set its prefix to the correct width.
     */
    private fun forceAllocateRegister(register: EnumRegister, registerWidth: Width): PlatformRegister {
        val platformRegister = this.allocator.forceAllocate(register).width(registerWidth)
        prepareRegister(platformRegister)
        return platformRegister
    }

    /**
     * Inform calling convention about the used register and setup the register width
     */
    private fun prepareRegister(register: PlatformTarget.Register) {
        this.callingConvention.taintRegister(register)
    }

    /**
     * Free a register for the allocator
     */
    private fun freeRegister(platformRegister: PlatformRegister) {
        allocator.freeRegister(platformRegister)
    }

    /**
     * Dissociate a [PlatformRegister] from a virtual register and make it available for allocation again.
     */
    private fun freeAllRegisters() {
        allocator.freeAll()
    }

    private fun transformOperand(molkiTarget: MolkiTarget.Input): PlatformTarget {
        return when (molkiTarget) {
            is Constant -> PlatformTarget.Constant(molkiTarget.value)
            is Memory -> generateTransformMemory(molkiTarget)
            is MolkiRegister -> {
                liveVariables[molkiTarget.id]!!
            }
        }
    }

    /**
     * Transform a memory expression form MolkIR to PlatformIR
     */
    private fun generateTransformMemory(mem: Memory): PlatformTarget.Memory {
        val base = if (mem.base != null) {
            liveVariables[mem.base.id]!!
        } else {
            null
        }

        val index = if (mem.index != null) {
            liveVariables[mem.index.id]!!
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
            is Memory -> generateTransformMemory(molkiTarget)
            is MolkiRegister -> {
                liveVariables[molkiTarget.id]!!
            }
            is ReturnRegister -> {
                getReturnValueStackSlot(molkiTarget.width)
            }
        }
    }

    /**
     * Get the stack slot where the return value is saved
     */
    private fun getReturnValueStackSlot(width: Width): PlatformTarget {
        val id = RegisterId(RETURN_VALUE_SLOT)
        return if (stackRegisterTable.containsRegister(id)) {
            stackRegisterTable.getRegisterSlot(id)!!.generateMemoryAddress()
        } else {
            stackRegisterTable.createRegisterSlot(id, width).generateMemoryAddress()
        }
    }

    /**
     * Molki supports implicit moves in its triple code. Output an additional move instruction if necessary
     *
     * @param molkiTarget the result target molki specifies
     * @param platformResult the result of the instruction in x86
     */
    private fun generateMoveIfNecessary(molkiTarget: MolkiTarget.Output, platformResult: PlatformTarget) {
        when (molkiTarget) {
            is MolkiRegister -> {
                // don't move if already present
                if (platformResult == liveVariables[molkiTarget.id]!!)
                    return

                appendInstruction(
                    PlatformInstruction.mov(
                        platformResult,
                        liveVariables[molkiTarget.id]!!,
                        molkiTarget.width
                    )
                )
            }
            is ReturnRegister -> {
                val target = getReturnValueStackSlot(molkiTarget.width)

                // don't move if already present
                if (platformResult == target)
                    return

                appendInstruction(
                    PlatformInstruction.mov(
                        platformResult,
                        target,
                        molkiTarget.width
                    )
                )
            }
            else -> {
                // we just assume that the instruction already wrote the result to the correct target, as we made the
                // target an operand to an operation
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
            is MolkiInstruction.BinaryOperationWithResult -> {
                if (instr.type == MolkiInstruction.BinaryOperationWithResult.Type.SUB) {
                    transformSubtraction(instr)
                } else {
                    transformBinaryOperationWithResult(instr)
                }
            }
            is MolkiInstruction.DivisionOperation -> transformDivision(instr)
            is MolkiInstruction.Call -> transformFunctionCall(instr)
            is MolkiInstruction.Jump -> appendInstruction(PlatformInstruction.Jump(instr.name, instr.label))
            is MolkiInstruction.Label -> appendInstruction(PlatformInstruction.Label(instr.name))
            is MolkiInstruction.UnaryOperationWithResult -> transformUnaryOperationWithResult(instr)
            is MolkiInstruction.Comment -> {}
        }
    }

    private fun transformBinaryOperation(instr: MolkiInstruction.BinaryOperation) {
        // transform operands
        val left = transformOperand(instr.left)
        val right = transformOperand(instr.right)

        // transform instruction
        val transformedInstr = PlatformInstruction.BinaryOperation(instr.name, left, right)
        appendInstruction(transformedInstr)
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
        appendInstruction(transformedInstr)

        // generate spill code
        generateMoveIfNecessary(instr.result, right)
    }

    /**
     * Transform a subtraction (which has inverse operand order because AT&T syntax is an unspeakable abomination.
     */
    private fun transformSubtraction(instr: MolkiInstruction.BinaryOperationWithResult) {
        // transform operands
        val left = transformOperand(instr.right)
        val right = transformOperand(instr.left)

        // transform instruction
        val transformedInstr = PlatformInstruction.BinaryOperation(instr.name, left, right)
        appendInstruction(transformedInstr)

        // generate spill code
        generateMoveIfNecessary(instr.result, right)
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
        appendInstruction(transformedInstr)
    }

    private fun transformFunctionCall(instr: MolkiInstruction.Call) {
        // panic mode: spill everything and fallback to the trivial transformer strategy
        spillEverything()

        val callingConvention = if (instr.external)
            PlatformTransformation.getExternalCallingConvention()
        else
            PlatformTransformation.getInternalCallingConvention()

        callingConvention.generateFunctionCall(allocator, instr.arguments.size, this::appendInstruction) {
            for (i in (0 until instr.arguments.size).reversed()) {
                // temporarily link a register so we can load the operand
                val argument = instr.arguments[i]
                if (argument is MolkiRegister) {
                    generateLoadVirtualRegisterValue(argument, linkVirtualRegister(argument))
                }

                val argumentSource = transformOperand(instr.arguments[i])
                prepareArgument(argumentSource, instr.arguments[i].width)

                // immediately free this temporary source, because we already stored the parameter where we need it
                if (argument is MolkiRegister) {
                    unlinkVirtualRegister(argument.id)
                }
            }

            // generate actual call instruction
            generateCall(instr.name)

            // save return value
            if (instr.result != null) {
                // this link remains valid after the call
                // TODO if we forcibly link EAX here, we don't need an additional move
                linkVirtualRegister(instr.result as MolkiRegister)

                val platformRegister =
                    PlatformTransformation.getInternalCallingConvention().getReturnValueTarget(instr.result.width)

                generateMoveIfNecessary(instr.result, platformRegister)
            }

            // cleanup stack
            cleanupStack()
        }
    }

    private fun transformDivision(instr: MolkiInstruction.DivisionOperation) {
        // enter panic mode: spill everything and proceed with trivial strategy
        spillEverything()

        // we therefore can forcibly allocate those registers
        val rdx = forceAllocateRegister(EnumRegister.RDX, Width.QUAD)
        val rax = forceAllocateRegister(EnumRegister.RAX, Width.QUAD)
        val divisorRegister = allocateRegister(Width.QUAD)

        // now link the operand registers, as we already allocated the required registers, and we might need to
        // load the values from registers
        if (instr.left is MolkiRegister) {
            generateLoadVirtualRegisterValue(instr.left, linkVirtualRegister(instr.left))
        }
        if (instr.right is MolkiRegister) {
            generateLoadVirtualRegisterValue(instr.right, linkVirtualRegister(instr.right))
        }

        val source = transformOperand(instr.left)
        val divisorSource = transformOperand(instr.right)

        // link target registers
        if (instr.resultLeft is MolkiRegister)
            linkVirtualRegister(instr.resultLeft)
        if (instr.resultRight is MolkiRegister)
            linkVirtualRegister(instr.resultRight)

        val resultLeft = transformResult(instr.resultLeft)
        val resultRight = transformResult(instr.resultRight)

        // expand 32 bit numbers to 64 bit
        appendInstruction(PlatformInstruction.xor(rdx, rdx, Width.QUAD))
        signedExtend(source, rax)
        signedExtend(divisorSource, divisorRegister)

        // perform division
        appendInstruction(PlatformInstruction.unOp("idivq", divisorRegister))

        // move result to correct target
        appendInstruction(PlatformInstruction.mov(rdx.doubleWidth(), resultLeft, Width.DOUBLE))
        appendInstruction(PlatformInstruction.mov(rax.doubleWidth(), resultRight, Width.DOUBLE))

        // free temporary registers
        freeRegister(rdx)
        freeRegister(rax)
        freeRegister(divisorRegister)
    }

    /**
     * Move [source] into [target] register extending it to 64 bit.
     */
    private fun signedExtend(source: PlatformTarget, target: PlatformTarget.Register) {
        when (source) {
            is PlatformTarget.Constant -> appendInstruction(PlatformInstruction.mov(source, target, Width.QUAD))
            is PlatformTarget.Memory,
            is PlatformTarget.Register -> appendInstruction(PlatformInstruction.binOp("movslq", source, target))
        }
    }

    /**
     * Spill and unlink all virtual registers that are currently linked
     */
    private fun spillEverything() {
        val allVariables = linkedVariables.toList()
        allVariables.forEach(::spillAndFreeRegister)
    }

    private fun spillAndFreeRegister(register: MolkiRegister) {
        require(liveVariables.containsKey(register.id)) { "trying to spill unlinked register" }

        val slot = if (stackRegisterTable.containsRegister(register.id)) {
            stackRegisterTable.getRegisterSlot(register.id)!!
        } else {
            stackRegisterTable.createRegisterSlot(register.id, register.width)
        }

        appendInstruction(
            PlatformInstruction.mov(
                liveVariables[register.id]!!.width(slot.width),
                slot.generateMemoryAddress(),
                slot.width
            )
        )

        unlinkVirtualRegister(register.id)
    }

    /**
     * Append an instruction to the current instruction list
     */
    private fun appendInstruction(instr: PlatformInstruction) {
        generatedCode.add(instr)
    }
}
