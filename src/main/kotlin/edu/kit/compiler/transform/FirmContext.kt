package edu.kit.compiler.transform

import edu.kit.compiler.ast.AST
import edu.kit.compiler.lex.Symbol
import firm.ClassType
import firm.Construction
import firm.Dump
import firm.Entity
import firm.Firm
import firm.Graph
import firm.MethodType
import firm.Mode
import firm.PrimitiveType
import firm.Program
import firm.Relation
import firm.Type
import firm.bindings.binding_ircons
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
    val globalType
        get() = Program.getGlobalType()

    lateinit var intType: PrimitiveType
        private set

    lateinit var boolType: PrimitiveType
        private set

    lateinit var voidType: PrimitiveType
        private set

    private val constructedClassTypes = mutableMapOf<Symbol, ClassType>()

    /**
     * All class types available in the firm context, accessible by their symbol
     */
    val classTypes: Map<Symbol, ClassType> = constructedClassTypes

    /**
     * Current active construction. This is hidden here, because we can use the visitor pattern, if the active
     * construction is held in the background (and never exposed). This comes with the limitation that only one method
     * can be constructed at a time, but this is inherent to the AST visitor pattern anyway.
     */
    private var construction: Construction? = null

    /**
     * Graph of current active [construction]
     */
    private var graph: Graph? = null

    /**
     * A list of all return nodes of a method. Must be connected to the end node in the end.
     */
    private val returnNodes = mutableListOf<Node>()

    /**
     * A stack for constructing expressions using a visitor. Each constructed expression is added to the current top
     * [ExpressionStackElement]. This way, when an expression is being transformed, it pushes an element onto the stack,
     * then recursively constructs the inner expressions, and then uses their results to construct itself.
     */
    private val expressionStack = Stack<ExpressionStackElement>()

    /**
     * Initialize the firm framework
     */
    fun init() {
        Firm.init("x86_64-linux-gnu", arrayOf("pic=1"))
        println("Initialized libFirm Version: ${Firm.getMajorVersion()}.${Firm.getMinorVersion()}")

        intType = PrimitiveType(Mode.getIs())
        boolType = PrimitiveType(Mode.getBu())
        voidType = PrimitiveType(Mode.getM()) // todo: is void a memory/sync mode?
    }

    /**
     * Create a type for a method and return it
     */
    fun constructMethodType(returnType: Type, vararg parameterTypes: Type): MethodType {
        return MethodType(parameterTypes, arrayOf(returnType))
    }

    /**
     * Create a new type for a class and add it to the class type map [classTypes]
     */
    fun constructClassType(symbol: Symbol): ClassType {
        val t = ClassType(symbol.text)
        this.constructedClassTypes[symbol] = t
        return t
    }

    /**
     * Create a field and add it to its owner class
     */
    fun constructField(type: Type, name: Symbol, ownerName: Symbol): Entity {
        val owner = classTypes[ownerName]!!
        return Entity(owner, name.text, type)
    }

    /**
     * Construct a method. Within [block] no other method may be constructed.
     *
     * @param variables number of local variables within the subroutine
     * @param type function type
     * @param block code fragment that constructs the method's content
     */
    fun subroutine(variables: Int, name: String, type: MethodType, block: () -> Unit) {
        check(this.construction == null) { "cannot construct a method while another is being constructed" }

        val methodEntity = Entity(globalType, name, type)
        this.graph = Graph(methodEntity, variables)
        this.construction = Construction(this.graph)

        // insert start node
        val startNode = this.construction!!.newStart()
        this.construction!!.currentMem = this.construction!!.newProj(startNode, Mode.getM(), 0)

        // construct method
        block.invoke()

        // insert end node
        returnNodes.forEach(this.graph!!.endBlock::addPred)
        this.construction!!.newEnd(emptyArray())
        this.graph!!.endBlock.mature()

        this.construction!!.finish()

        Dump.dumpGraph(graph, "after-construction")
        this.construction = null
        this.graph = null
        this.returnNodes.clear()
    }

    /**
     * Construct a binary expression.
     *
     * @param operation the [AST.BinaryExpression.Operation] variant that is being constructed
     * @sample block code fragment that constructs the inner expressions
     *
     * @sample ConstructExpressions
     */
    fun binaryExpression(operation: AST.BinaryExpression.Operation, block: () -> Unit) {
        val f = ExpressionStackElement()
        this.expressionStack.push(f)
        block.invoke()
        this.expressionStack.pop()

        val expression = when (operation) {
            AST.BinaryExpression.Operation.ASSIGNMENT -> TODO()
            AST.BinaryExpression.Operation.OR -> this.construction!!.newOr(f.firstNode, f.secondNode)
            AST.BinaryExpression.Operation.AND -> this.construction!!.newAnd(f.firstNode, f.secondNode)
            AST.BinaryExpression.Operation.EQUALS -> this.construction!!.newCmp(
                f.firstNode,
                f.secondNode,
                Relation.Equal
            )
            AST.BinaryExpression.Operation.NOT_EQUALS -> this.construction!!.newCmp(
                f.firstNode,
                f.secondNode,
                Relation.LessGreater
            )
            AST.BinaryExpression.Operation.LESS_THAN -> this.construction!!.newCmp(
                f.firstNode,
                f.secondNode,
                Relation.Less
            )
            AST.BinaryExpression.Operation.GREATER_THAN -> this.construction!!.newCmp(
                f.firstNode,
                f.secondNode,
                Relation.Greater
            )
            AST.BinaryExpression.Operation.LESS_EQUALS -> this.construction!!.newCmp(
                f.firstNode,
                f.secondNode,
                Relation.LessEqual
            )
            AST.BinaryExpression.Operation.GREATER_EQUALS -> this.construction!!.newCmp(
                f.firstNode,
                f.secondNode,
                Relation.GreaterEqual
            )
            AST.BinaryExpression.Operation.ADDITION -> this.construction!!.newAdd(f.firstNode, f.secondNode)
            AST.BinaryExpression.Operation.SUBTRACTION -> this.construction!!.newSub(f.firstNode, f.secondNode)
            AST.BinaryExpression.Operation.MULTIPLICATION -> this.construction!!.newMul(f.firstNode, f.secondNode)
            AST.BinaryExpression.Operation.DIVISION -> {
                val m = this.construction!!.currentMem
                val div = this.construction!!.newDiv(
                    m,
                    f.firstNode,
                    f.secondNode,
                    binding_ircons.op_pin_state.op_pin_state_pinned
                )
                this.construction!!.currentMem = construction!!.newProj(div, Mode.getM(), Div.pnM)
                construction!!.newProj(div, intType.mode, Div.pnRes)
            }
            AST.BinaryExpression.Operation.MODULO -> {
                val m = this.construction!!.currentMem
                val div = this.construction!!.newMod(
                    m,
                    f.firstNode,
                    f.secondNode,
                    binding_ircons.op_pin_state.op_pin_state_pinned
                )
                this.construction!!.currentMem = construction!!.newProj(div, Mode.getM(), Div.pnM)
                construction!!.newProj(div, intType.mode, Div.pnRes)
            }
        }

        this.expressionStack.peek().push(expression)
    }

    /**
     * Construct a unary expression. This is very similar to [binaryExpression] and the given sample code can be applied
     * directly to unary expressions.
     *
     * @param operation the [AST.UnaryExpression.Operation] variant that is being constructed
     * @sample block code fragment that constructs the inner expressions
     *
     * @sample ConstructExpressions
     */
    fun unaryExpression(operation: AST.UnaryExpression.Operation, block: () -> Unit) {
        val f = ExpressionStackElement()
        this.expressionStack.push(f)
        block.invoke()
        this.expressionStack.pop()

        val expression = when (operation) {
            AST.UnaryExpression.Operation.NOT -> this.construction!!.newNot(f.firstNode)
            AST.UnaryExpression.Operation.MINUS -> this.construction!!.newMinus(f.firstNode)
        }

        this.expressionStack.peek().push(expression)
    }

    /**
     * Push a boolean literal into the currently constructed expression
     */
    fun literalBool(value: Boolean) {
        val intRepr = if (value) 1 else 0
        this.expressionStack.peek().push(this.construction!!.newConst(intRepr, boolType.mode))
    }

    /**
     * Push an integer literal into the currently constructed expression
     */
    fun literalInt(value: Int) {
        this.expressionStack.peek().push(this.construction!!.newConst(value, intType.mode))
    }

    fun returnStatement(block: (() -> Unit)? = null) {
        val returnNode = if (block != null) {
            val f = ExpressionStackElement()
            this.expressionStack.push(f)
            block.invoke()
            this.expressionStack.pop()

            val mem = this.construction!!.currentMem
            this.construction!!.newReturn(mem, arrayOf(f.firstNode))
        } else {
            val mem = this.construction!!.currentMem
            this.construction!!.newReturn(mem, emptyArray())
        }

        this.returnNodes.add(returnNode)
    }

    private class ExpressionStackElement {
        /**
         * Inner node of an expression. Will be initialized when the respective expression is fully constructed and then
         * consumed by the outer expression.
         */
        lateinit var firstNode: Node

        /**
         * Whether the first node has been initialized yet
         */
        private var firstNodeInitialized: Boolean = false

        /**
         * Second node of a binary expression. May stay uninitialized, if the expression this frame belongs to is a
         * unary expression (but since the AST is legal, it will never be accessed in that case, i.e. the contract is
         * not violated.
         */
        lateinit var secondNode: Node

        /**
         * Push a constructed firm expression node into this stack element.
         */
        fun push(node: Node) {
            if (firstNodeInitialized) {
                secondNode = node
            } else {
                firstNode = node
                firstNodeInitialized = true
            }
        }
    }
}
