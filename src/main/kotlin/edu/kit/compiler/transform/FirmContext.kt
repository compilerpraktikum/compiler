package edu.kit.compiler.transform

import firm.Construction
import firm.Entity
import firm.Firm
import firm.Graph
import firm.MethodType
import firm.Mode
import firm.PrimitiveType
import firm.Program
import firm.Type

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
        check(this.construction != null) { "cannot construct a method while another is being constructed" }

        val globalType = Program.getGlobalType()
        val methodEntity = Entity(globalType, name, type)
        val graph = Graph(methodEntity, variables)
        this.construction = Construction(graph)
        block.invoke()
        this.construction!!.finish()
        // dump graph
        this.construction = null
    }
}
