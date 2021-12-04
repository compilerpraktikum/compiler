package edu.kit.compiler.transform

import edu.kit.compiler.ast.AST
import edu.kit.compiler.semantic.AstNode
import edu.kit.compiler.semantic.SemanticType
import edu.kit.compiler.semantic.VariableNode
import edu.kit.compiler.semantic.visitor.AbstractVisitor
import edu.kit.compiler.semantic.visitor.accept
import firm.Construction
import firm.Dump
import firm.Entity
import firm.Firm
import firm.Graph
import firm.Mode
import firm.Program
import firm.Relation
import firm.SegmentType
import firm.Type
import firm.bindings.binding_ircons
import firm.nodes.Block
import firm.nodes.Call
import firm.nodes.Cond
import firm.nodes.Div
import firm.nodes.Load
import firm.nodes.Node
import firm.nodes.Start
import firm.nodes.Store
import java.util.Stack

fun SemanticType.toVariableType(): Type = with(FirmContext.typeRegistry) {
    toVariableType()
}

/**
 * Facade for all jFirm-related calls.
 */
object FirmContext {
    /**
     * Firm global type singleton
     */
    private val global: SegmentType
        get() = Program.getGlobalType()

    /**
     * Utility to manage [firm-types][Type].
     */
    lateinit var typeRegistry: TypeRegistry

    /**
     * Current active construction. This is hidden here, because we can use the visitor pattern, if the active
     * construction is held in the background (and never exposed). This comes with the limitation that only one method
     * can be constructed at a time, but this is inherent to the AST visitor pattern anyway.
     */
    private val construction: Construction
        get() = currentConstruction!!

    private var currentConstruction: Construction? = null

    /**
     * Graph of current active [construction]
     */
    private var graph: Graph? = null

    /**
     * A list of all return nodes of a method. Must be connected to the end node in the end.
     */
    private val returnNodes = mutableListOf<Node>()

    /**
     * A list of blocks containing a return node. Those do not require jumps in the end (like then/else blocks)
     */
    private val exitBlocks = mutableListOf<Block>()

    /**
     * A stack for constructing expressions using a visitor. Each constructed expression is added onto the stack,
     * each expression that needs arguments just takes them from the stack. Since the AST is guaranteed to be valid,
     * this cannot fail.
     */
    private val expressionStack = Stack<Node>()

    /**
     * Initialize the firm framework
     */
    fun init() {
        Firm.init("x86_64-linux-gnu", arrayOf("pic=1"))
        println("Initialized libFirm Version: ${Firm.getMajorVersion()}.${Firm.getMinorVersion()}")

        typeRegistry = TypeRegistry()
    }

    /**
     * Construct a method. Within [block] no other method may be constructed.
     *
     * @param methodEntity method entity
     * @param method method AST node
     * @param variables number of local variables and parameters within the subroutine
     * @param block code fragment that constructs the method's content
     */
    fun subroutine(
        methodEntity: Entity,
        method: AstNode.ClassMember.SubroutineDeclaration.MethodDeclaration,
        variables: Int,
        block: () -> Unit
    ) {
        val parameterTuple = prepareSubroutine(methodEntity, variables)

        // set `this`
        construction.setVariable(
            0,
            construction.newProj(parameterTuple, typeRegistry.getClassReferenceType(method.owner.name.symbol).mode, 0)
        )

        // load parameters into helper variables
        for (index in (1..method.parameters.size)) {
            construction.setVariable(
                index,
                construction.newProj(parameterTuple, method.parameters[index - 1].type.mode, index)
            )
        }

        finishSubroutine(block)
    }

    /**
     * Construct the main method. Within [block] no other method may be constructed.
     *
     * @param methodEntity method entity
     * @param variables number of local variables and parameters within the subroutine
     * @param block code fragment that constructs the method's content
     */
    fun subroutine(
        methodEntity: Entity,
        variables: Int,
        block: () -> Unit
    ) {
        prepareSubroutine(methodEntity, variables)
        finishSubroutine(block)
    }

    /**
     * Prepare construction of a new subroutine
     */
    private fun prepareSubroutine(methodEntity: Entity, variables: Int): Node {
        check(this.currentConstruction == null) { "cannot construct a method while another is being constructed" }

        this.graph = Graph(methodEntity, variables)
        this.currentConstruction = Construction(this.graph)

        // insert start node
        val startNode = this.construction.newStart()

        // set memory state
        this.construction.currentMem = this.construction.newProj(startNode, Mode.getM(), Start.pnM)

        // load parameters and store in local helper variables
        return construction.newProj(startNode, Mode.getT(), Start.pnTArgs)
    }

    /**
     * Finish construction of a subroutine
     */
    private fun finishSubroutine(block: () -> Unit) {
        // construct method
        block.invoke()

        // insert end node
        returnNodes.forEach(this.graph!!.endBlock::addPred)
        this.construction.newEnd(emptyArray())
        this.graph!!.endBlock.mature()

        this.construction.finish()

        Dump.dumpGraph(graph, "after-construction")
        this.currentConstruction = null
        this.graph = null
        this.returnNodes.clear()
        this.exitBlocks.clear()
    }

    /**
     * Construct a binary expression.
     *
     * @param expr the [AstNode.Expression.BinaryOperation] variant that is being constructed
     * @param surroundingMethod the method this expression is in
     * @param transformer the [TransformationMethodVisitor] that will generate code for partial expressions
     */
    fun binaryExpression(
        expr: AstNode.Expression.BinaryOperation,
        surroundingMethod: AstNode.ClassMember.SubroutineDeclaration,
        transformer: TransformationMethodVisitor
    ) {
        val expression = when (expr.operation) {
            AST.BinaryExpression.Operation.ASSIGNMENT -> {
                generateAssignment(expr, surroundingMethod, transformer)
                return
            }
            AST.BinaryExpression.Operation.OR -> generateShortCircuitEvaluation(
                expr.left,
                expr.right,
                transformer,
                AST.BinaryExpression.Operation.OR
            )
            AST.BinaryExpression.Operation.AND -> generateShortCircuitEvaluation(
                expr.left,
                expr.right,
                transformer,
                AST.BinaryExpression.Operation.AND
            )
            else -> {
                expr.left.accept(transformer)
                expr.right.accept(transformer)

                val secondNode = expressionStack.pop()
                val firstNode = expressionStack.pop()

                when (expr.operation) {
                    AST.BinaryExpression.Operation.EQUALS -> convertBoolToByte(
                        this.construction.newCmp(firstNode, secondNode, Relation.Equal)
                    )
                    AST.BinaryExpression.Operation.NOT_EQUALS -> convertBoolToByte(
                        this.construction.newCmp(firstNode, secondNode, Relation.LessGreater)
                    )
                    AST.BinaryExpression.Operation.LESS_THAN -> convertBoolToByte(
                        this.construction.newCmp(firstNode, secondNode, Relation.Less)
                    )
                    AST.BinaryExpression.Operation.GREATER_THAN -> convertBoolToByte(
                        this.construction.newCmp(firstNode, secondNode, Relation.Greater)
                    )
                    AST.BinaryExpression.Operation.LESS_EQUALS -> convertBoolToByte(
                        this.construction.newCmp(firstNode, secondNode, Relation.LessEqual)
                    )
                    AST.BinaryExpression.Operation.GREATER_EQUALS -> convertBoolToByte(
                        this.construction.newCmp(firstNode, secondNode, Relation.GreaterEqual)
                    )
                    AST.BinaryExpression.Operation.ADDITION -> this.construction.newAdd(firstNode, secondNode)
                    AST.BinaryExpression.Operation.SUBTRACTION -> this.construction.newSub(firstNode, secondNode)
                    AST.BinaryExpression.Operation.MULTIPLICATION -> this.construction.newMul(firstNode, secondNode)
                    AST.BinaryExpression.Operation.DIVISION -> {
                        val m = this.construction.currentMem
                        val div = this.construction.newDiv(
                            m,
                            firstNode,
                            secondNode,
                            binding_ircons.op_pin_state.op_pin_state_pinned
                        )
                        this.construction.currentMem = construction.newProj(div, Mode.getM(), Div.pnM)
                        construction.newProj(div, typeRegistry.intType.mode, Div.pnRes)
                    }
                    AST.BinaryExpression.Operation.MODULO -> {
                        val m = this.construction.currentMem
                        val div = this.construction.newMod(
                            m,
                            firstNode,
                            secondNode,
                            binding_ircons.op_pin_state.op_pin_state_pinned
                        )
                        this.construction.currentMem = construction.newProj(div, Mode.getM(), Div.pnM)
                        construction.newProj(div, typeRegistry.intType.mode, Div.pnRes)
                    }
                    AST.BinaryExpression.Operation.ASSIGNMENT,
                    AST.BinaryExpression.Operation.OR,
                    AST.BinaryExpression.Operation.AND -> throw AssertionError("unreachable")
                }
            }
        }

        this.expressionStack.push(expression)
    }

    /**
     * Generate memory access to assign the right-hand side of [expr] to the left-hand side and push the value onto the
     * expression stack
     *
     * @param expr the assignment expression
     * @param surroundingMethod the surrounding method of this expression
     * @param transformer an [AbstractVisitor] that will transform the partial expressions of this assignemtn
     */
    private fun generateAssignment(
        expr: AstNode.Expression.BinaryOperation,
        surroundingMethod: AstNode.ClassMember.SubroutineDeclaration,
        transformer: TransformationMethodVisitor
    ) {
        val leftHandSide = expr.left
        val rightHandSide = expr.right

        rightHandSide.accept(transformer)
        val value = expressionStack.pop()

        when (leftHandSide) {
            is AstNode.Expression.ArrayAccessExpression -> {
                leftHandSide.target.accept(transformer)
                leftHandSide.index.accept(transformer)
                arrayWriteAccess(leftHandSide, value)
            }
            is AstNode.Expression.FieldAccessExpression -> fieldWriteAccess(leftHandSide, value)
            is AstNode.Expression.IdentifierExpression -> identifierWriteAccess(
                leftHandSide,
                surroundingMethod,
                transformer,
                value
            )
            else -> throw AssertionError("invalid AST in transformation")
        }
    }

    /**
     * Generate control flow to push a byte using a boolean condition.
     *
     * @param comparison the boolean comparison whose result gets converted
     */
    private fun convertBoolToByte(comparison: Node): Node {
        val trueBlock = construction.newBlock()
        val falseBlock = construction.newBlock()
        val afterBlock = construction.newBlock()

        generateBooleanConditionAndJumps(comparison, trueBlock, falseBlock)
        return generateBooleanToBytePhi(trueBlock, falseBlock, afterBlock).also {
            trueBlock.mature()
            falseBlock.mature()
        }
    }

    /**
     * Generate a phi-expression that will evaluate to 1 if [trueBlock] was taken and 0 if [falseBlock] was taken. Then
     * both blocks jump to [afterBlock] and it will be the new active block of [construction].
     *
     * @param trueBlock the block that shall evaluate to 1
     * @param falseBlock the block that shall evaluate to 0
     * @param afterBlock the block where execution shall continue
     *
     * @return a node containing the byte value of the boolean operation
     */
    private fun generateBooleanToBytePhi(trueBlock: Block, falseBlock: Block, afterBlock: Block): Node {
        construction.currentBlock = trueBlock
        val oneNode = construction.newConst(1, Mode.getBu())
        createUnconditionalJump(afterBlock)
        construction.currentBlock = falseBlock
        val zeroNode = construction.newConst(0, Mode.getBu())
        createUnconditionalJump(afterBlock)
        afterBlock.mature()
        construction.currentBlock = afterBlock

        return construction.newPhi(
            arrayOf(oneNode, zeroNode),
            Mode.getBu()
        )
    }

    /**
     * Evaluate a binary boolean expression using short-circuit rules, for non-condition operations.
     * Use [generateShortCircuitCondition] for conditions.
     *
     * @param left left side of the expression
     * @param right right side of the expression
     * @param transformer an [AbstractVisitor] that performs code transformation for expressions
     * @param operation either [AST.BinaryExpression.Operation.OR] or [AST.BinaryExpression.Operation.AND]
     *
     * @return a node that contains the result of the boolean operation (as a byte)
     */
    private fun generateShortCircuitEvaluation(
        left: AstNode.Expression,
        right: AstNode.Expression,
        transformer: AbstractVisitor,
        operation: AST.BinaryExpression.Operation
    ): Node {
        left.accept(transformer)
        val rightSideBlock = construction.newBlock()
        val trueBlock = construction.newBlock()
        val falseBlock = construction.newBlock()
        val afterBlock = construction.newBlock()

        when (operation) {
            AST.BinaryExpression.Operation.OR -> generateBooleanCheck(expressionStack.pop(), trueBlock, rightSideBlock)
            AST.BinaryExpression.Operation.AND -> generateBooleanCheck(
                expressionStack.pop(),
                rightSideBlock,
                falseBlock
            )
            else -> throw AssertionError()
        }

        rightSideBlock.mature()
        construction.currentBlock = rightSideBlock
        right.accept(transformer)
        generateBooleanCheck(expressionStack.pop(), trueBlock, falseBlock)

        trueBlock.mature()
        falseBlock.mature()

        return generateBooleanToBytePhi(trueBlock, falseBlock, afterBlock).also { afterBlock.mature() }
    }

    /**
     * Generate a boolean condition with jumps to [trueBlock] and [falseBlock] from an [comparison] (that must be a comparison)
     */
    private fun generateBooleanConditionAndJumps(comparison: Node, trueBlock: Block, falseBlock: Block) {
        val condition = construction.newCond(comparison)
        trueBlock.addPred(construction.newProj(condition, Mode.getX(), Cond.pnTrue))
        falseBlock.addPred(construction.newProj(condition, Mode.getX(), Cond.pnFalse))
    }

    /**
     * Generate a condition that checks a boolean (byte) expression for the value `1` and jumps to the respective block.
     *
     * @param expr the expression that evaluates to 1 or 0
     * @param trueBlock where to jump if the expression is 1
     * @param falseBlock where to jump if the expression is 0
     */
    private fun generateBooleanCheck(expr: Node, trueBlock: Block, falseBlock: Block) {
        val cmp = construction.newCmp(expr, construction.newConst(1, Mode.getBu()), Relation.Equal)
        generateBooleanConditionAndJumps(cmp, trueBlock, falseBlock)
    }

    /**
     * Generate a boolean binary condition using short-circuit logic as a condition expression. Do not use this method
     * for non-conditional expressions.
     */
    private fun generateShortCircuitCondition(
        expr: AstNode.Expression.BinaryOperation,
        trueBlock: Block,
        falseBlock: Block,
        transformer: TransformationMethodVisitor,
        op: AST.BinaryExpression.Operation
    ) {
        val rightBlock = construction.newBlock()
        when (op) {
            AST.BinaryExpression.Operation.OR -> generateBooleanCondition(expr.left, trueBlock, rightBlock, transformer)
            AST.BinaryExpression.Operation.AND -> generateBooleanCondition(
                expr.left,
                rightBlock,
                falseBlock,
                transformer
            )
            else -> throw AssertionError()
        }

        rightBlock.mature()
        construction.currentBlock = rightBlock

        generateBooleanCondition(expr.right, trueBlock, falseBlock, transformer)
    }

    /**
     * Generate a condition from an expression. The expression must not be further visited by the [transformer], because
     * this method decides which parts of the expression will be visited, and in which order.
     *
     * @param expr the expression to generate a condition from
     * @param trueBlock where to jump if the condition evaluates to true
     * @param falseBlock where to jump if the condition evaluates to false
     * @param transformer the [TransformationMethodVisitor] that is being used to generated partial expressions of the
     * condition
     */
    private fun generateBooleanCondition(
        expr: AstNode.Expression,
        trueBlock: Block,
        falseBlock: Block,
        transformer: TransformationMethodVisitor
    ) {
        when (expr) {
            is AstNode.Expression.BinaryOperation -> {
                when (expr.operation) {
                    AST.BinaryExpression.Operation.ASSIGNMENT -> {
                        expr.accept(transformer)
                        generateBooleanCheck(expressionStack.pop(), trueBlock, falseBlock)
                    }
                    AST.BinaryExpression.Operation.OR -> {
                        generateShortCircuitCondition(
                            expr,
                            trueBlock,
                            falseBlock,
                            transformer,
                            AST.BinaryExpression.Operation.OR
                        )
                    }
                    AST.BinaryExpression.Operation.AND -> {
                        generateShortCircuitCondition(
                            expr,
                            trueBlock,
                            falseBlock,
                            transformer,
                            AST.BinaryExpression.Operation.AND
                        )
                    }
                    AST.BinaryExpression.Operation.EQUALS ->
                        generateRelationCondition(
                            expr.left,
                            expr.right,
                            trueBlock,
                            falseBlock,
                            Relation.Equal,
                            transformer
                        )
                    AST.BinaryExpression.Operation.NOT_EQUALS ->
                        generateRelationCondition(
                            expr.left,
                            expr.right,
                            trueBlock,
                            falseBlock,
                            Relation.LessGreater,
                            transformer
                        )
                    AST.BinaryExpression.Operation.LESS_THAN ->
                        generateRelationCondition(
                            expr.left,
                            expr.right,
                            trueBlock,
                            falseBlock,
                            Relation.Less,
                            transformer
                        )
                    AST.BinaryExpression.Operation.GREATER_THAN ->
                        generateRelationCondition(
                            expr.left,
                            expr.right,
                            trueBlock,
                            falseBlock,
                            Relation.Greater,
                            transformer
                        )
                    AST.BinaryExpression.Operation.LESS_EQUALS ->
                        generateRelationCondition(
                            expr.left,
                            expr.right,
                            trueBlock,
                            falseBlock,
                            Relation.LessEqual,
                            transformer
                        )
                    AST.BinaryExpression.Operation.GREATER_EQUALS ->
                        generateRelationCondition(
                            expr.left,
                            expr.right,
                            trueBlock,
                            falseBlock,
                            Relation.GreaterEqual,
                            transformer
                        )
                    AST.BinaryExpression.Operation.ADDITION,
                    AST.BinaryExpression.Operation.SUBTRACTION,
                    AST.BinaryExpression.Operation.MULTIPLICATION,
                    AST.BinaryExpression.Operation.DIVISION,
                    AST.BinaryExpression.Operation.MODULO -> throw AssertionError("cannot have a numeric operation as a condition")
                }
            }
            is AstNode.Expression.MethodInvocationExpression,
            is AstNode.Expression.FieldAccessExpression,
            is AstNode.Expression.IdentifierExpression,
            is AstNode.Expression.ArrayAccessExpression -> {
                expr.accept(transformer)
                generateBooleanCheck(expressionStack.pop(), trueBlock, falseBlock)
            }
            is AstNode.Expression.LiteralExpression.LiteralBoolExpression -> {
                generateBooleanCheck(
                    construction.newConst(if (expr.value) 1 else 0, Mode.getBu()),
                    trueBlock,
                    falseBlock
                )
            }
            is AstNode.Expression.UnaryOperation -> when (expr.operation) {
                AST.UnaryExpression.Operation.NOT -> generateBooleanCondition(
                    expr.inner,
                    falseBlock,
                    trueBlock,
                    transformer
                )
                AST.UnaryExpression.Operation.MINUS -> throw AssertionError("cannot have a numeric operation as a condition")
            }
            is AstNode.Expression.NewArrayExpression,
            is AstNode.Expression.NewObjectExpression,
            is AstNode.Expression.LiteralExpression.LiteralIntExpression,
            is AstNode.Expression.LiteralExpression.LiteralNullExpression,
            is AstNode.Expression.LiteralExpression.LiteralThisExpression -> throw AssertionError("cannot have non-boolean expression as condition")
        }
    }

    /**
     * Create a condition and the respective jumps for a relation expression. This is only intended for relations, not
     * for general boolean expressions
     *
     * @param left left argument of the relation
     * @param right right argument of the relation
     * @param trueBlock where to jump if the arguments are in relation
     * @param falseBlock where to jump if the arguments are not in relation
     * @param relation the relation to test for
     * @param transformer the transformer that is being used to generate code
     */
    private fun generateRelationCondition(
        left: AstNode.Expression,
        right: AstNode.Expression,
        trueBlock: Block,
        falseBlock: Block,
        relation: Relation,
        transformer: AbstractVisitor
    ) {
        left.accept(transformer)
        val leftNode = expressionStack.pop()
        right.accept(transformer)
        val rightNode = expressionStack.pop()

        val cmp = construction.newCmp(leftNode, rightNode, relation)
        generateBooleanConditionAndJumps(cmp, trueBlock, falseBlock)
    }

    /**
     * Construct a unary expression. This is very similar to [binaryExpression] and the given sample code can be applied
     * directly to unary expressions.
     *
     * @param operation the [AST.UnaryExpression.Operation] variant that is being constructed
     *
     * @sample ConstructExpressions
     */
    fun unaryExpression(operation: AST.UnaryExpression.Operation) {
        val expression = when (operation) {
            AST.UnaryExpression.Operation.NOT -> this.construction.newNot(expressionStack.pop())
            AST.UnaryExpression.Operation.MINUS -> this.construction.newMinus(expressionStack.pop())
        }

        this.expressionStack.push(expression)
    }

    /**
     * Push a boolean literal into the currently constructed expression
     */
    fun literalBool(value: Boolean) {
        val intRepr = if (value) 1 else 0
        this.expressionStack.push(this.construction.newConst(intRepr, typeRegistry.boolType.mode))
    }

    /**
     * Push an integer literal into the currently constructed expression
     */
    fun literalInt(value: Int) {
        this.expressionStack.push(this.construction.newConst(value, typeRegistry.intType.mode))
    }

    /**
     * Generate a while-loop with all necessary control flow. All condition expressions will be evaluated using the
     * short-circuit rules.
     */
    fun whileStatement(whileStatement: AstNode.Statement.WhileStatement, transformer: TransformationMethodVisitor) {
        val conditionBlock = construction.newBlock()
        val doBlock = construction.newBlock()
        val afterBlock = construction.newBlock()

        conditionBlock.addPred(construction.newJmp())
        construction.currentBlock = conditionBlock
        generateBooleanCondition(whileStatement.condition, doBlock, afterBlock, transformer)

        doBlock.mature()
        construction.currentBlock = doBlock
        whileStatement.statement.accept(transformer)
        createUnconditionalJump(conditionBlock)
        conditionBlock.mature()

        afterBlock.mature()
        construction.currentBlock = afterBlock
    }

    /**
     * Generate an if-statement and all necessary control flow. All condition expressions will be evaluated using the
     * short-circuit rules.
     */
    fun ifStatement(
        withElse: Boolean,
        ifStatement: AstNode.Statement.IfStatement,
        transformer: TransformationMethodVisitor
    ) {
        val thenBlock = construction.newBlock()
        val afterBlock = construction.newBlock()

        val elseBlock = if (withElse) {
            construction.newBlock()
        } else {
            afterBlock
        }

        generateBooleanCondition(ifStatement.condition, thenBlock, elseBlock, transformer)
        thenBlock.mature()

        construction.currentBlock = thenBlock
        ifStatement.thenCase.accept(transformer)
        createUnconditionalJump(afterBlock)

        if (withElse) {
            elseBlock.mature()
            construction.currentBlock = elseBlock
            ifStatement.elseCase?.accept(transformer)
            createUnconditionalJump(afterBlock)
        }

        afterBlock.mature()
        construction.currentBlock = afterBlock
    }

    /**
     * Create an unconditional jump (iff the block does not contain a `return` statement).
     */
    private fun createUnconditionalJump(target: Block) {
        if (!exitBlocks.contains(construction.currentBlock))
            target.addPred(construction.newJmp())
    }

    /**
     * Generate a return statement and add the returned expression to it, if any.
     */
    fun returnStatement(withExpression: Boolean) {
        val mem = this.construction.currentMem

        val returnNode = if (withExpression) {
            this.construction.newReturn(mem, arrayOf(expressionStack.pop()))
        } else {
            this.construction.newReturn(mem, emptyArray())
        }

        this.returnNodes.add(returnNode)
        this.exitBlocks.add(construction.currentBlock)
    }

    /**
     * Generate an access node to any defined identifier: A field of the local class, a local variable or a parameter.
     *
     * @param identifierExpression the expression referencing the value to load
     * @param surroundingMethod the method that contains the [identifierExpression]
     * @param transformer the [TransformationMethodVisitor] that generates code for the method
     */
    fun identifierReadAccess(
        identifierExpression: AstNode.Expression.IdentifierExpression,
        surroundingMethod: AstNode.ClassMember.SubroutineDeclaration,
        transformer: TransformationMethodVisitor
    ) {
        require(!identifierExpression.isLeftHandAssignment) { "trying to generate memory access to LH side." }

        val valueNode = when (val definition = identifierExpression.definition!!.node) {
            is VariableNode.Field -> {
                // an identifierExpression on a field is always an implicit field access on `this`,
                // so we have to load the pointer. If it wasn't an access on `this`, it wouldn't be an
                // identifier-expression but a field-access-expression
                loadThis(surroundingMethod as AstNode.ClassMember.SubroutineDeclaration.MethodDeclaration)
                val loadNode = construction.newLoad(
                    construction.currentMem,
                    getFieldMember(definition.node),
                    definition.type.mode
                )
                construction.currentMem = construction.newProj(loadNode, Mode.getM(), Load.pnM)
                construction.newProj(loadNode, identifierExpression.definition!!.node.type.mode, Load.pnRes)
            }
            is VariableNode.LocalVariable -> construction.getVariable(
                transformer.localVariableDeclarations[definition.node]!!,
                definition.node.type.mode
            )
            is VariableNode.Parameter -> construction.getVariable(
                transformer.parameterDeclarations[definition.node]!!,
                definition.node.type.mode
            )
        }

        expressionStack.push(valueNode)
    }

    /**
     * Write [value] to a member or variable identified by [identifierExpression] and then push [value] onto the
     * expression stack.
     */
    fun identifierWriteAccess(
        identifierExpression: AstNode.Expression.IdentifierExpression,
        surroundingMethod: AstNode.ClassMember.SubroutineDeclaration,
        transformer: TransformationMethodVisitor,
        value: Node
    ) {
        when (val definition = identifierExpression.definition!!.node) {
            is VariableNode.Field -> {
                // an identifierExpression on a field is always an implicit field access on `this`,
                // so we have to load the pointer. If it wasn't an access on `this`, it wouldn't be an
                // identifier-expression but a field-access-expression
                loadThis(surroundingMethod as AstNode.ClassMember.SubroutineDeclaration.MethodDeclaration)
                val loadNode = construction.newStore(
                    construction.currentMem,
                    getFieldMember(definition.node),
                    value,
                    typeRegistry.getField(definition.node.owner.name.symbol, definition.node.name.symbol).type
                )
                construction.currentMem = construction.newProj(loadNode, Mode.getM(), Load.pnM)
            }
            is VariableNode.LocalVariable -> construction.setVariable(
                transformer.localVariableDeclarations[definition.node]!!,
                value
            )
            is VariableNode.Parameter -> construction.setVariable(
                transformer.parameterDeclarations[definition.node]!!,
                value
            )
        }

        expressionStack.push(value)
    }

    /**
     * Generate an access node to any defined field of an explicit target expression (like `a.field`). Implicit field
     * access on the `this` pointer are not [AstNode.Expression.FieldAccessExpression] and therefore not handled here.
     * If the access is writing, the node pushed to the expression stack will be the accessed member, if the access is
     * reading, the node will be the member's content.
     *
     * @param fieldAccessExpression the expression referencing the field to load
     */
    fun fieldReadAccess(
        fieldAccessExpression: AstNode.Expression.FieldAccessExpression,
    ) {
        require(!fieldAccessExpression.isLeftHandAssignment) { "trying to generate memory access to LH side." }

        val loadNode = construction.newLoad(
            construction.currentMem,
            getFieldMember(fieldAccessExpression.definition!!.node),
            fieldAccessExpression.definition!!.node.type.mode
        )
        construction.currentMem = construction.newProj(loadNode, Mode.getM(), Load.pnM)
        expressionStack.push(
            construction.newProj(
                loadNode,
                fieldAccessExpression.definition!!.node.type.mode,
                Load.pnRes
            )
        )
    }

    /**
     * Generate a write access event to a field and push the result value onto the expression stack
     */
    fun fieldWriteAccess(fieldAccessExpression: AstNode.Expression.FieldAccessExpression, value: Node) {
        val storeNode = construction.newStore(
            construction.currentMem,
            getFieldMember(fieldAccessExpression.definition!!.node),
            value,
            typeRegistry.getField(
                fieldAccessExpression.definition!!.node.owner.name.symbol,
                fieldAccessExpression.field.symbol
            ).type
        )

        construction.currentMem = construction.newProj(storeNode, Mode.getM(), Store.pnM)
        expressionStack.push(value)
    }

    /**
     * Generate a field member node for access
     */
    private fun getFieldMember(fieldDeclaration: AstNode.ClassMember.FieldDeclaration): Node {
        return construction.newMember(
            expressionStack.pop(),
            typeRegistry.getField(
                fieldDeclaration.owner.name.symbol,
                fieldDeclaration.name.symbol
            )
        )
    }

    /**
     * Generate a memory access to an array. The index calculation will be done manually, because array in Mini-Java
     * aren't fixed-size.
     *
     * @param arrayAccess the array access expression
     */
    fun arrayReadAccess(arrayAccess: AstNode.Expression.ArrayAccessExpression) {
        val indexNode = expressionStack.pop()
        val targetNode = expressionStack.pop()
        val addressNode = getArrayPointer(arrayAccess, indexNode, targetNode)

        val loadNode = construction.newLoad(
            this.construction.currentMem,
            construction.newConv(addressNode, Mode.getP()),
            arrayAccess.actualType.mode
        )

        val newMem = construction.newProj(loadNode, Mode.getM(), Load.pnM)
        construction.currentMem = newMem

        this.expressionStack.push(
            construction.newProj(
                loadNode,
                arrayAccess.actualType.toVariableType().mode,
                Load.pnRes
            )
        )
    }

    /**
     * Generate a write access event to an array and push the result value onto the expression stack
     */
    fun arrayWriteAccess(arrayAccess: AstNode.Expression.ArrayAccessExpression, value: Node) {
        val indexNode = expressionStack.pop()
        val targetNode = expressionStack.pop()
        val addressNode = getArrayPointer(arrayAccess, indexNode, targetNode)

        val storeNode = construction.newStore(
            this.construction.currentMem,
            construction.newConv(addressNode, Mode.getP()),
            value
        )

        val newMem = construction.newProj(storeNode, Mode.getM(), Store.pnM)
        construction.currentMem = newMem

        expressionStack.push(value)
    }

    /**
     * Calculate the address of an array element
     */
    private fun getArrayPointer(
        arrayAccess: AstNode.Expression.ArrayAccessExpression,
        indexNode: Node,
        targetNode: Node
    ): Node {
        // multiply index by type size and add to base address. Use Ls as the type to fit all
        return construction.newAdd(
            construction.newConv(
                targetNode,
                Mode.getLs()
            ),
            construction.newMul(
                construction.newConv(
                    construction.newConst(arrayAccess.actualType.mode.sizeBytes, Mode.getBu()),
                    Mode.getLs()
                ),
                construction.newConv(
                    indexNode,
                    Mode.getLs()
                )
            )
        )
    }

    /**
     * Generate a method call and push its result onto the expression stack. If the call does not return a value, a
     * dummy value will be placed on the stack, so expression-statements (which contain method calls that don't return
     * anything) don't pop wrong data from the stack.
     */
    fun methodInvocation(
        methodInvocationExpression: AstNode.Expression.MethodInvocationExpression,
        surroundingMethod: AstNode.ClassMember.SubroutineDeclaration
    ) {
        val numberOfArguments = methodInvocationExpression.arguments.size
        val args = arrayOfNulls<Node>(numberOfArguments + 1)

        // if no target is specified, it is implicitely `this`, but we need to generate the code for it manually, because
        // the transformer won't do it if `target == null`
        if (methodInvocationExpression.target == null)
            loadThis(surroundingMethod as AstNode.ClassMember.SubroutineDeclaration.MethodDeclaration)

        // target of the method call (might be `this`)
        val target = expressionStack.peek()

        args[0] = expressionStack.pop()
        for (i in 1 until args.size) {
            args[i] = this.expressionStack.pop()
        }
        // parameters in reverse order and dont forget 'this'

        val definition = when (val type = methodInvocationExpression.type!!) {
            is AstNode.Expression.MethodInvocationExpression.Type.Internal -> TODO()
            is AstNode.Expression.MethodInvocationExpression.Type.Normal -> type.definition
        }

        val call =
            construction.newCall(
                construction.currentMem,
                construction.newMember(
                    target,
                    typeRegistry.getMethod(definition.node.owner.name.symbol, methodInvocationExpression.method.symbol)
                ),
                args,
                typeRegistry.getMethod(definition.node.owner.name.symbol, methodInvocationExpression.method.symbol).type
            )
        val resultTuple = construction.newProj(call, Mode.getT(), Call.pnTResult)
        construction.currentMem = construction.newProj(call, Mode.getM(), Call.pnM)

        // special case for "void return"
        if (methodInvocationExpression.type!!.returnType is SemanticType.Void) {
            expressionStack.push(construction.newProj(resultTuple, Mode.getANY(), 0))
        } else {
            expressionStack.push(
                construction.newProj(
                    resultTuple,
                    methodInvocationExpression.actualType.mode,
                    0
                )
            )
        }
    }

    /**
     * Load `this` pointer into expression stack
     */
    fun loadThis(methodDeclaration: AstNode.ClassMember.SubroutineDeclaration.MethodDeclaration) {
        expressionStack.push(
            construction.getVariable(
                0,
                typeRegistry.getClassReferenceType(methodDeclaration.owner.name.symbol).mode
            )
        )
    }

    /**
     * Store the initial value of a local variable in its state
     */
    fun localVariableDeclaration(
        localVariableDeclaration: AstNode.Statement.LocalVariableDeclaration,
        transformer: TransformationMethodVisitor
    ) {
        if (localVariableDeclaration.initializer != null) {
            construction.setVariable(
                transformer.localVariableDeclarations[localVariableDeclaration]!!,
                expressionStack.pop()
            )
        }
    }

    /**
     * Load a null-pointer onto the expression stack
     */
    fun loadNull() {
        expressionStack.push(construction.newConst(0, Mode.getP()))
    }

    /**
     * An expression statement just pops the expression result from the expression stack.
     */
    fun expressionStatement() {
        expressionStack.pop()
    }

    /**
     * Allocate space for an array on the heap and push the pointer to it into the expression stack. The allocated
     * memory will be initialized conforming to the java standard.
     *
     * @param newArrayExpression the new-array expression
     */
    fun newArrayAllocation(newArrayExpression: AstNode.Expression.NewArrayExpression) {
        val typeSizeNode = construction.newConst(newArrayExpression.type.mode.sizeBytes, Mode.getBu())
        val arrayLengthNode = expressionStack.pop()
        val arraySizeNode = construction.newMul(typeSizeNode, arrayLengthNode)

        // todo: call std interface for new memory

        // todo initialize array with 0, false, or null
    }

    /**
     * Allocate space for an object on the heap and push the pointer to it into the expression stack. The allocated
     * memory will be initialized conforming to the java standard.
     *
     * @param newObjectExpression the new-object expression
     */
    fun newObjectAllocation(newObjectExpression: AstNode.Expression.NewObjectExpression) {
        val typeSizeNode =
            construction.newConst(typeRegistry.getClassType(newObjectExpression.clazz.symbol).size, Mode.getBu())

        // todo: call std interface for new memory

        // todo initialize members with 0, false, or null
    }
}
