package edu.kit.compiler.semantic

import edu.kit.compiler.lexer.Symbol

/**
 * Definition of a class, class member or local variable
 */
class Definition<NodeType>(
    val name: Symbol,
    val node: NodeType,
)

sealed class VariableNode {
    abstract val type: SemanticType

    class Field(val node: AstNode.ClassMember.FieldDeclaration) : VariableNode() {
        override val type: SemanticType
            get() = node.type
    }

    class Parameter(val node: AstNode.ClassMember.SubroutineDeclaration.Parameter) : VariableNode() {
        override val type: SemanticType
            get() = node.type
    }

    class LocalVariable(val node: AstNode.Statement.LocalVariableDeclaration) : VariableNode() {
        override val type: SemanticType
            get() = node.type
    }
}
typealias VariableDefinition = Definition<VariableNode>

typealias FieldDefinition = Definition<AstNode.ClassMember.FieldDeclaration>
fun AstNode.ClassMember.FieldDeclaration.asDefinition() = FieldDefinition(name.symbol, this)
@JvmName("wrapFieldDefinition")
fun FieldDefinition.wrap() = VariableDefinition(name, VariableNode.Field(node))

typealias ParameterDefinition = Definition<AstNode.ClassMember.SubroutineDeclaration.Parameter>
fun AstNode.ClassMember.SubroutineDeclaration.Parameter.asDefinition() = ParameterDefinition(name.symbol, this)
@JvmName("wrapParameterDefinition")
fun ParameterDefinition.wrap() = VariableDefinition(name, VariableNode.Parameter(node))

typealias LocalVariableDefinition = Definition<AstNode.Statement.LocalVariableDeclaration>
fun AstNode.Statement.LocalVariableDeclaration.asDefinition() = LocalVariableDefinition(name.symbol, this)
@JvmName("wrapLocalVariableDefinition")
fun LocalVariableDefinition.wrap() = VariableDefinition(name, VariableNode.LocalVariable(node))

typealias ClassDefinition = Definition<AstNode.ClassDeclaration>
fun AstNode.ClassDeclaration.asDefinition() = ClassDefinition(name.symbol, this)

typealias MethodDefinition = Definition<AstNode.ClassMember.SubroutineDeclaration.MethodDeclaration>
fun AstNode.ClassMember.SubroutineDeclaration.MethodDeclaration.asDefinition() = MethodDefinition(name.symbol, this)

typealias MainMethodDefinition = Definition<AstNode.ClassMember.SubroutineDeclaration.MainMethodDeclaration>
fun AstNode.ClassMember.SubroutineDeclaration.MainMethodDeclaration.asDefinition() = MainMethodDefinition(name.symbol, this)

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

    fun getOrNull(name: String): Definition<T>? = entries.asSequence().find { it.key.text == name }?.value
}

class GlobalNamespace {
    val classes = Namespace<AstNode.ClassDeclaration>()
}

class ClassNamespace(
    val global: GlobalNamespace
) {
    val fields = Namespace<AstNode.ClassMember.FieldDeclaration>()
    val methods = Namespace<AstNode.ClassMember.SubroutineDeclaration.MethodDeclaration>()

    var mainMethodDefinition: MainMethodDefinition? = null
    val hasMainMethod
        get() = mainMethodDefinition != null
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

    fun add(definition: VariableDefinition) {
        check(currentScope.parent != null) { "cannot add definition to root scope. call enterScope() before adding definitions" }
        val name = definition.name
        currentScope.changes.add(Change(name, name.definition))
        name.definition = definition
    }

    fun lookup(name: Symbol): VariableDefinition? = name.definition

    fun contains(name: Symbol) = lookup(name) != null
}
