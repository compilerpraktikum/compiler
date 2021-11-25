package edu.kit.compiler.transform

import edu.kit.compiler.ast.AST
import firm.Construction
import firm.Dump
import firm.Entity
import firm.Firm
import firm.Graph
import firm.MethodType
import firm.Mode
import firm.PrimitiveType
import firm.Program
import firm.Type
import firm.nodes.Node
import java.util.Stack

/**
 * Facade for all jFirm-related calls.
 */
object FirmContext {

    lateinit var intType: PrimitiveType
        private set

    lateinit var boolType: PrimitiveType
        private set

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

    private val expressionStack = Stack<ExpressionStackElement>()

    /**
     * Initialize the firm framework
     */
    fun init() {
        Firm.init("x86_64-linux-gnu", arrayOf("pic=1"))
        println(
            String.format(
                "Initialized libFirm Version: %1s.%2s\n",
                Firm.getMinorVersion(),
                Firm.getMajorVersion()
            )
        )

        intType = PrimitiveType(Mode.getIs())
        boolType = PrimitiveType(Mode.getIs())
    }

    fun constructMethodType(returnType: Type, vararg parameterTypes: Type): MethodType {
        return MethodType(parameterTypes, arrayOf(returnType))
    }

    fun subroutine(variables: Int, name: String, type: MethodType, block: () -> Unit) {
        check(this.construction == null) { "cannot construct a method while another is being constructed" }

        val globalType = Program.getGlobalType()
        val methodEntity = Entity(globalType, name, type)
        this.graph = Graph(methodEntity, variables)
        this.construction = Construction(this.graph)
        block.invoke()
        this.construction!!.finish()

        Dump.dumpGraph(graph, "after-construction")
        this.construction = null
        this.graph = null
    }

    fun binaryExpression(operation: AST.BinaryExpression.Operation, block: () -> Unit) {
        val f = ExpressionStackElement()
        this.expressionStack.push(f)
        block.invoke()
        this.expressionStack.pop()

        val expression = when (operation) {
            AST.BinaryExpression.Operation.ASSIGNMENT -> TODO()
            AST.BinaryExpression.Operation.OR -> this.construction!!.newOr(f.firstNode, f.secondNode)
            AST.BinaryExpression.Operation.AND -> this.construction!!.newAnd(f.firstNode, f.secondNode)
            AST.BinaryExpression.Operation.EQUALS -> TODO()
            AST.BinaryExpression.Operation.NOT_EQUALS -> TODO()
            AST.BinaryExpression.Operation.LESS_THAN -> TODO()
            AST.BinaryExpression.Operation.GREATER_THAN -> TODO()
            AST.BinaryExpression.Operation.LESS_EQUALS -> TODO()
            AST.BinaryExpression.Operation.GREATER_EQUALS -> TODO()
            AST.BinaryExpression.Operation.ADDITION -> this.construction!!.newAdd(f.firstNode, f.secondNode)
            AST.BinaryExpression.Operation.SUBTRACTION -> this.construction!!.newSub(f.firstNode, f.secondNode)
            AST.BinaryExpression.Operation.MULTIPLICATION -> this.construction!!.newMul(f.firstNode, f.secondNode)
            AST.BinaryExpression.Operation.DIVISION -> TODO()
            AST.BinaryExpression.Operation.MODULO -> TODO()
        }

        this.graph!!.keepAlive(expression)
        this.expressionStack.peek().push(expression)
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
