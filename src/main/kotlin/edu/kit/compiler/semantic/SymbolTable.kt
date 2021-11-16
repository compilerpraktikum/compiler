package edu.kit.compiler.semantic

import edu.kit.compiler.ast.AST
import edu.kit.compiler.ast.Identity
import edu.kit.compiler.ast.Of
import edu.kit.compiler.ast.Type
import edu.kit.compiler.lex.Symbol

/**
 * Generic namespace that contains a mapping from [Symbols][Symbol] to [definitions][T].
 */
class Namespace<T> {
    private val entries = mutableMapOf<Symbol, T>()

    /**
     * Try to add the given [definition] to the namespace.
     * @return `true` if insertion was successful, `false` if there was already an entry associated to the given [name]
     */
    fun tryPut(name: Symbol, definition: T): Boolean = entries.putIfAbsent(name, definition) == null

    /**
     * Try to receive the definition associated with the given [name].
     * @return the definition associated with the given [name] or `null` if no such definition exists
     */
    fun get(name: Symbol): T? = entries[name]
}

data class Definition<NodeType>(
    val name: Symbol,
    val node: NodeType,
)

typealias FieldDefinition = Definition<AST.Field<Identity<Of>>>
typealias MethodDefinition = Definition<AST.Method<Identity<Of>, Identity<Of>, Identity<Of>>>
typealias VariableDefinition = Definition<AST.LocalVariableDeclarationStatement<Identity<Of>, Identity<Of>>>

data class DefinitionWithNamespace<NodeType, NamespaceType>(
    val name: Symbol,
    val node: NodeType,
    val namespace: NamespaceType
)

private typealias ASTClass = AST.ClassDeclaration<Identity<Of>, Identity<Of>, Identity<Of>, Identity<Of>>
typealias ClassDefinition = DefinitionWithNamespace<ASTClass, ClassNamespace>

class GlobalNamespace {
    val classes = Namespace<ClassDefinition>()
}

class ClassNamespace(
    val global: GlobalNamespace
) {
    val fields = Namespace<FieldDefinition>()
    val methods = Namespace<MethodDefinition>()
}

class SymbolTable {
    private data class Change(
        val symbol: Symbol,
        val prevDefinition: VariableDefinition?,
    )

    private data class Scope(
        val parent: Scope?,
        val changes: MutableList<Change>
    )

    private var currentScope: Scope = Scope(null, mutableListOf())

    fun enterScope() {
        currentScope = Scope(currentScope, mutableListOf())
    }

    fun leaveScope() {
        val scope = currentScope
        check(scope.parent != null) { "cannot leave root scope" }

        for (change in scope.changes) {
            // iteration order does not matter because every variable can only be changed once per scope
            change.symbol.definition = change.prevDefinition
        }

        currentScope = scope.parent
    }

    fun add(name: Symbol, definition: VariableDefinition) {
        check(currentScope.parent != null) { "cannot add definition to root scope. call enterScope() before adding definitions" }
        currentScope.changes.add(Change(name, name.definition))
        name.definition = definition
    }

    fun lookup(name: Symbol): VariableDefinition? = name.definition

    fun contains(name: Symbol) = lookup(name) == null
}
