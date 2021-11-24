package edu.kit.compiler.semantic

import edu.kit.compiler.lex.Symbol

/**
 * Definition of a class member or local variable
 */
class Definition<NodeType>(
    val name: Symbol,
    val node: NodeType,
)

typealias ClassDefinition = Definition<AstNode.ClassDeclaration>
fun AstNode.ClassDeclaration.asDefinition() = ClassDefinition(name.symbol, this)

typealias FieldDefinition = Definition<AstNode.ClassMember.FieldDeclaration>
fun AstNode.ClassMember.FieldDeclaration.asDefinition() = FieldDefinition(name.symbol, this)

typealias MethodDefinition = Definition<AstNode.ClassMember.SubroutineDeclaration.MethodDeclaration>
fun AstNode.ClassMember.SubroutineDeclaration.MethodDeclaration.asDefinition() = MethodDefinition(name.symbol, this)

sealed class VariableNode {
    class Field(val node: AstNode.ClassMember.FieldDeclaration) : VariableNode()
    class Parameter(val node: AstNode.ClassMember.SubroutineDeclaration.Parameter) : VariableNode()
    class LocalVariable(val node: AstNode.Statement.LocalVariableDeclaration) : VariableNode()
}
typealias VariableDefinition = Definition<VariableNode>

typealias LocalVariableDefinition = Definition<AstNode.Statement.LocalVariableDeclaration>
@JvmName("wrapLocalVariableDefinition")
fun LocalVariableDefinition.wrap() = VariableDefinition(name, VariableNode.LocalVariable(node))

@JvmName("wrapFieldDefinition")
fun Definition<AstNode.ClassMember.FieldDeclaration>.wrap() = VariableDefinition(name, VariableNode.Field(node))

/**
 * Generic namespace that contains a mapping from [Symbols][Symbol] to [definitions][T].
 */
class Namespace<T> {
    private val entries = mutableMapOf<Symbol, Definition<T>>()

    /**
     * Try to add the given [definition] to the namespace.
     * @return `true` if insertion was successful, `false` if there was already an entry associated to the given [name]
     */
    fun tryPut(definition: Definition<T>, onDuplicate: (Definition<T>) -> Unit) {
        entries.putIfAbsent(definition.name, definition)?.let(onDuplicate)
    }

    /**
     * Try to receive the definition associated with the given [name].
     * @return the definition associated with the given [name] or `null` if no such definition exists
     */
    fun getOrNull(name: Symbol): Definition<T>? = entries[name]
}

class GlobalNamespace {
    val classes = Namespace<AstNode.ClassDeclaration>()
}

class ClassNamespace(
    val global: GlobalNamespace
) {
    val fields = Namespace<AstNode.ClassMember.FieldDeclaration>()
    val methods = Namespace<AstNode.ClassMember.SubroutineDeclaration.MethodDeclaration>()
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

    fun add(definition: LocalVariableDefinition) {
        check(currentScope.parent != null) { "cannot add definition to root scope. call enterScope() before adding definitions" }
        val name = definition.name
        currentScope.changes.add(Change(name, name.definition))
        name.definition = definition.wrap()
    }

    fun lookup(name: Symbol): VariableDefinition? = name.definition

    fun contains(name: Symbol) = lookup(name) == null
}
