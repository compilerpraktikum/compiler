package edu.kit.compiler.lex

import java.lang.IllegalArgumentException

/**
 * Stores information (-> TODO) associated to an identifier.
 */
data class Symbol(
    val text: String,
    val isKeyword: Boolean,
) : Comparable<Symbol> {
    override fun compareTo(other: Symbol) = text.compareTo(other.text)
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
     *  @return the internalized identifier name and associated [Symbol]
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

    /**
     * Get the [Symbol] associated with the given identifier name.
     */
    fun getSymbolOrNull(name: String): Symbol? {
        return symbols[name]
    }
}
