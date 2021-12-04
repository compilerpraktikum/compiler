package edu.kit.compiler.transform

import edu.kit.compiler.ast.AST
import edu.kit.compiler.semantic.AstNode
import edu.kit.compiler.semantic.SemanticType
import edu.kit.compiler.semantic.VariableNode
import edu.kit.compiler.semantic.display
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
import java.util.Stack

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
     * @param variables number of local variables within the subroutine
     * @param block code fragment that constructs the method's content
     */
    fun subroutine(
        methodEntity: Entity,
        method: AstNode.ClassMember.SubroutineDeclaration.MethodDeclaration,
        variables: Int,
        block: () -> Unit
    ) {
        check(this.currentConstruction == null) { "cannot construct a method while another is being constructed" }

        this.graph = Graph(methodEntity, variables)
        this.currentConstruction = Construction(this.graph)

        // insert start node
        val startNode = this.construction.newStart()

        // set memory state
        this.construction.currentMem = this.construction.newProj(startNode, Mode.getM(), Start.pnM)

        // load parameters and store in local helper variables
        val parameterTuple = construction.newProj(startNode, Mode.getT(), Start.pnTArgs)
        construction.setVariable(
            0,
            construction.newProj(parameterTuple, typeRegistry.getClassReferenceType(method.owner.name.symbol).mode, 0)
        )
        for (index in (1..method.parameters.size)) {
            construction.setVariable(
                index,
                construction.newProj(parameterTuple, method.parameters[index - 1].type.mode, index)
            )
        }

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
    }

    /**
     * Construct a binary expression.
     *
     * @param operation the [AST.BinaryExpression.Operation] variant that is being constructed
     *
     * @sample ConstructExpressions
     */
    fun binaryExpression(operation: AST.BinaryExpression.Operation) {
        val secondNode = expressionStack.pop()
        val firstNode = expressionStack.pop()

        val expression = when (operation) {
            AST.BinaryExpression.Operation.ASSIGNMENT -> TODO()
            AST.BinaryExpression.Operation.OR -> this.construction.newOr(firstNode, secondNode)
            AST.BinaryExpression.Operation.AND -> this.construction.newAnd(firstNode, secondNode)
            AST.BinaryExpression.Operation.EQUALS -> this.construction.newCmp(
                firstNode,
                secondNode,
                Relation.Equal
            )
            AST.BinaryExpression.Operation.NOT_EQUALS -> this.construction.newCmp(
                firstNode,
                secondNode,
                Relation.LessGreater
            )
            AST.BinaryExpression.Operation.LESS_THAN -> this.construction.newCmp(
                firstNode,
                secondNode,
                Relation.Less
            )
            AST.BinaryExpression.Operation.GREATER_THAN -> this.construction.newCmp(
                firstNode,
                secondNode,
                Relation.Greater
            )
            AST.BinaryExpression.Operation.LESS_EQUALS -> this.construction.newCmp(
                firstNode,
                secondNode,
                Relation.LessEqual
            )
            AST.BinaryExpression.Operation.GREATER_EQUALS -> this.construction.newCmp(
                firstNode,
                secondNode,
                Relation.GreaterEqual
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
        }

        this.expressionStack.push(expression)
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

    private fun doCondShortEval(
        expr: AstNode.Expression.BinaryOperation,
        trueBlock: Block,
        falseBlock: Block,
        transformer: TransformationMethodVisitor,
        op: AST.BinaryExpression.Operation
    ) {
        val rightBlock = construction.newBlock()
        when (op) {
            AST.BinaryExpression.Operation.OR -> doCond(expr.left, trueBlock, rightBlock, transformer)
            AST.BinaryExpression.Operation.AND -> doCond(expr.left, rightBlock, falseBlock, transformer)
            else -> throw AssertionError()
        }

        rightBlock.mature()
        construction.currentBlock = rightBlock

        doCond(expr.right, trueBlock, falseBlock, transformer)
    }

    fun doCond(
        expr: AstNode.Expression,
        trueBlock: Block,
        falseBlock: Block,
        transformer: TransformationMethodVisitor
    ) {
        when (expr) {
            is AstNode.Expression.ArrayAccessExpression -> TODO()
            is AstNode.Expression.BinaryOperation -> {
                when (expr.operation) {
                    AST.BinaryExpression.Operation.ASSIGNMENT -> TODO()
                    AST.BinaryExpression.Operation.OR -> {
                        doCondShortEval(expr, trueBlock, falseBlock, transformer, AST.BinaryExpression.Operation.OR)
                    }
                    AST.BinaryExpression.Operation.AND -> {
                        doCondShortEval(expr, trueBlock, falseBlock, transformer, AST.BinaryExpression.Operation.AND)
                    }
                    AST.BinaryExpression.Operation.EQUALS ->
                        doCondRelation(expr.left, expr.right, trueBlock, falseBlock, Relation.Equal, transformer)
                    AST.BinaryExpression.Operation.NOT_EQUALS ->
                        doCondRelation(expr.left, expr.right, trueBlock, falseBlock, Relation.LessGreater, transformer)
                    AST.BinaryExpression.Operation.LESS_THAN ->
                        doCondRelation(expr.left, expr.right, trueBlock, falseBlock, Relation.Less, transformer)
                    AST.BinaryExpression.Operation.GREATER_THAN ->
                        doCondRelation(expr.left, expr.right, trueBlock, falseBlock, Relation.Greater, transformer)
                    AST.BinaryExpression.Operation.LESS_EQUALS ->
                        doCondRelation(expr.left, expr.right, trueBlock, falseBlock, Relation.LessEqual, transformer)
                    AST.BinaryExpression.Operation.GREATER_EQUALS ->
                        doCondRelation(expr.left, expr.right, trueBlock, falseBlock, Relation.GreaterEqual, transformer)
                    AST.BinaryExpression.Operation.ADDITION,
                    AST.BinaryExpression.Operation.SUBTRACTION,
                    AST.BinaryExpression.Operation.MULTIPLICATION,
                    AST.BinaryExpression.Operation.DIVISION,
                    AST.BinaryExpression.Operation.MODULO -> throw AssertionError("cannot have a numeric operation as a condition")
                }
            }
            is AstNode.Expression.FieldAccessExpression -> TODO()
            is AstNode.Expression.IdentifierExpression -> TODO()
            is AstNode.Expression.LiteralExpression.LiteralBoolExpression -> {
                if (expr.value) {
                    trueBlock.addPred(construction.newJmp())
                } else {
                    falseBlock.addPred(construction.newJmp())
                }
            }
            is AstNode.Expression.MethodInvocationExpression -> TODO()
            is AstNode.Expression.UnaryOperation -> when (expr.operation) {
                AST.UnaryExpression.Operation.NOT -> doCond(expr.inner, falseBlock, trueBlock, transformer)
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
     * Create a condition and the respective jumps for a relation expression.
     *
     * @param left left argument of the relation
     * @param right right argument of the relation
     * @param trueBlock where to jump if the arguments are in relation
     * @param falseBlock where to jump if the arguments are not in relation
     * @param relation the relation to test for
     * @param transformer the transformer that is being used to generate code
     */
    private fun doCondRelation(
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
        val cond = construction.newCond(cmp)
        trueBlock.addPred(construction.newProj(cond, Mode.getX(), Cond.pnTrue))
        falseBlock.addPred(construction.newProj(cond, Mode.getX(), Cond.pnFalse))
    }

    fun whileStatement(whileStatement: AstNode.Statement.WhileStatement, transformer: TransformationMethodVisitor) {
        val conditionBlock = construction.newBlock()
        val doBlock = construction.newBlock()
        val afterBlock = construction.newBlock()

        conditionBlock.addPred(construction.newJmp())
        construction.currentBlock = conditionBlock
        doCond(whileStatement.condition, doBlock, afterBlock, transformer)

        doBlock.mature()
        construction.currentBlock = doBlock
        whileStatement.statement.accept(transformer)
        conditionBlock.addPred(construction.newJmp())
        conditionBlock.mature()

        afterBlock.mature()
        construction.currentBlock = afterBlock
    }

    fun methodInvocation(
        methodInvocationExpression: AstNode.Expression.MethodInvocationExpression,
        surroundingMethod: AstNode.ClassMember.SubroutineDeclaration.MethodDeclaration,
        transformer: TransformationMethodVisitor
    ) {
        val numberOfArguments = methodInvocationExpression.arguments.size
        val args = arrayOfNulls<Node>(numberOfArguments + 1)

        // if no target is specified, it is implicitely `this`, but we need to generate the code for it manually, because
        // the transformer won't do it if `target == null`
        if (methodInvocationExpression.target == null)
            loadThis(surroundingMethod)

        args[0] = expressionStack.pop()
        for (i in 1 until args.size) {
            args[i] = this.expressionStack.pop()
        }
        // parameters in reverse order and dont forget 'this'

        val definition = when (val type = methodInvocationExpression.type!!) {
            is AstNode.Expression.MethodInvocationExpression.Type.Internal -> TODO()
            is AstNode.Expression.MethodInvocationExpression.Type.Normal -> type.definition
        }

        // for address calculation of the method
        loadThis(surroundingMethod)

        val call =
            construction.newCall(
                construction.currentMem,
                construction.newMember(
                    expressionStack.pop(),
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

    fun arrayAccess(arrayAccess: AstNode.Expression.ArrayAccessExpression, transformer: TransformationMethodVisitor) {
        val mem = this.construction.currentMem
        val indexNode = expressionStack.pop()
        val targetNode = expressionStack.pop()
        // val type = typeRegistry.getArrayReferenceType(arrayAccess.actualType as SemanticType.Array)
        // construction.newLoad(mem, Pointer.new(targetNode + indexNode))
        println(arrayAccess.target.actualType.display())
        val loadNode = construction.newLoad(
            mem,
            construction.newSel(targetNode, indexNode, arrayAccess.target.actualType.toVariableType()),
            Mode.getLs()
        )

        val newMem = construction.newProj(loadNode, Mode.getM(), 0) // TODO 0
        construction.currentMem = newMem

        this.expressionStack.push(
            construction.newProj(
                loadNode,
                arrayAccess.actualType.toVariableType().mode,
                0
            )
        ) // TODO 0
    }

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

        doCond(ifStatement.condition, thenBlock, elseBlock, transformer)
        thenBlock.mature()

        if (withElse)
            elseBlock.mature()

        construction.currentBlock = thenBlock

        afterBlock.addPred(construction.newJmp())
        ifStatement.thenCase.accept(transformer)

        if (withElse) {
            construction.currentBlock = elseBlock
            ifStatement.elseCase?.accept(transformer)
            afterBlock.addPred(construction.newJmp())
        }

        afterBlock.mature()
        construction.currentBlock = afterBlock
    }

    fun returnStatement(withExpression: Boolean) {
        val mem = this.construction.currentMem

        val returnNode = if (withExpression) {
            this.construction.newReturn(mem, arrayOf(expressionStack.pop()))
        } else {
            this.construction.newReturn(mem, emptyArray())
        }

        this.returnNodes.add(returnNode)
    }

    /**
     * Generate an access node to any defined identifier: A field of the local class, a local variable or a parameter.
     *
     * @param identifierExpression the expression referencing the value to load
     * @param surroundingMethod the method that contains the [identifierExpression]
     * @param transformer the [TransformationMethodVisitor] that generates code for the method
     */
    fun memoryAccess(
        identifierExpression: AstNode.Expression.IdentifierExpression,
        surroundingMethod: AstNode.ClassMember.SubroutineDeclaration.MethodDeclaration,
        transformer: TransformationMethodVisitor
    ) {
        val valueNode = when (val definition = identifierExpression.definition!!.node) {
            is VariableNode.Field -> {
                // an identifierExpression on a field is always an implicit field access on `this`,
                // so we have to load the pointer. If it wasn't an access on `this`, it wouldn't be an
                // identifier-expression but a field-access-expression
                loadThis(surroundingMethod)
                val loadNode = construction.newLoad(
                    construction.currentMem,
                    construction.newMember(
                        expressionStack.pop(),
                        typeRegistry.getField(definition.node.owner.name.symbol, definition.node.name.symbol)
                    ),
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
     * Generate an access node to any defined field of an explicit target expression (like `a.field`).
     *
     * @param fieldAccessExpression the expression referencing the field to load
     * @param surroundingMethod the method that contains the [fieldAccessExpression]
     * @param transformer the [TransformationMethodVisitor] that generates code for the method
     */
    fun memoryAccess(
        fieldAccessExpression: AstNode.Expression.FieldAccessExpression,
    ) {
        val loadNode = construction.newLoad(
            construction.currentMem,
            construction.newMember(
                expressionStack.pop(),
                typeRegistry.getField(
                    fieldAccessExpression.definition!!.node.owner.name.symbol,
                    fieldAccessExpression.definition!!.name
                )
            ),
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

    fun loadNull() {
        expressionStack.push(construction.newConst(0, Mode.getP()))
    }

    /**
     * An expression statement just pops the expression stack.
     */
    fun expressionStatement() {
        // TODO: we must not pop if the last expression was a void method that did not push anything to the stack
        //  we must either push something to the stack anyway (probably the better solution, because there won't be any
        //  void-expressions that utilize the stacked void result in any way, becuase that would be illegal) or check
        //  it here and refuse to pop
        expressionStack.pop()
    }

    fun newArrayAllocation(newArrayExpression: AstNode.Expression.NewArrayExpression) {
        val typeSizeNode = construction.newConst(newArrayExpression.type.mode.sizeBytes, Mode.getBu())
        val arrayLengthNode = expressionStack.pop()
        val arraySizeNode = construction.newMul(typeSizeNode, arrayLengthNode)

        // todo: call std interface for new memory

        // todo initialize array with 0, false, or null
    }

    fun newObjectAllocation(newObjectExpression: AstNode.Expression.NewObjectExpression) {
        val typeSizeNode =
            construction.newConst(typeRegistry.getClassType(newObjectExpression.clazz.symbol).size, Mode.getBu())

        // todo: call std interface for new memory

        // todo initialize members with 0, false, or null
    }
}
