package edu.kit.compiler.transform

import edu.kit.compiler.ast.AST
import edu.kit.compiler.semantic.AstNode
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
import firm.nodes.Cond
import firm.nodes.Div
import firm.nodes.Node
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
     * @param variables number of local variables within the subroutine
     * @param block code fragment that constructs the method's content
     */
    fun subroutine(methodEntity: Entity, variables: Int, block: () -> Unit) {
        check(this.currentConstruction == null) { "cannot construct a method while another is being constructed" }

        this.graph = Graph(methodEntity, variables)
        this.currentConstruction = Construction(this.graph)

        // insert start node
        val startNode = this.construction.newStart()
        this.construction.currentMem = this.construction.newProj(startNode, Mode.getM(), 0)

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
        transformer: AbstractVisitor,
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

    fun doCond(expr: AstNode.Expression, trueBlock: Block, falseBlock: Block, transformer: AbstractVisitor) {
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

    fun whileStatement(whileStatement: AstNode.Statement.WhileStatement, transformer: AbstractVisitor) {
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

    fun ifStatement(withElse: Boolean, ifStatement: AstNode.Statement.IfStatement, transformer: AbstractVisitor) {
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
}
