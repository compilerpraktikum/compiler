package edu.kit.compiler.lexer

import edu.kit.compiler.semantic.VariableDefinition

/**
 * Symbol table entry for identifiers
 */
data class Symbol(
    val text: String,
    val isKeyword: Boolean,
) : Comparable<Symbol> {
    override fun compareTo(other: Symbol) = text.compareTo(other.text)

    var definition: VariableDefinition? = null
}

/**
 * Stores a mapping between identifier names and associated information ([Symbol]). It also handles internalizing
 * identifier names to save time and memory.
 * The string table is generated during lexicographic analysis and later utilized during
 * parsing and during semantic analysis.
 */
class StringTable(
    initializer: StringTable.() -> Unit,
) {

    private val symbols = HashMap<String, Symbol>()

    init {
        initializer()
    }

    /**
     * Tries to register a new identifier if not already known.
     * @return the internalized identifier name and associated [Symbol]
     */
    fun tryRegisterIdentifier(name: String): Symbol {
        return symbols.computeIfAbsent(name) { Symbol(name, isKeyword = false) }
    }

    /**
     * Registers a new keyword.
     * @throws[IllegalArgumentException] if the keyword is already registered
     */
    fun registerKeyword(name: String) {
        require(!symbols.contains(name)) { "keyword '$name' already registered" }
        symbols[name] = Symbol(name, isKeyword = true)
    }
}
