package edu.kit.compiler.lex

import java.util.*

/**
 * Stores a mapping between identifier names and associated information ([StringTable.Entry]). It also handles internalizing
 * identifier names to save time and memory.
 * The string table is generated during lexicographic analysis and later utilized during
 * parsing and during semantic analysis.
 */
class StringTable {
    
    private val table = IdentityHashMap<String, Entry>()
    
    /**
     * Tries to register a new identifier if not already known.
     *  @return the internalized identifier name and associated [Entry]
     */
    fun tryRegisterIdentifier(name: String): Pair<String, Entry> {
        val internName = name.intern()
        val entry = table.computeIfAbsent(internName) { Entry(isKeyword = false) }
        return internName to entry
    }
    
    /**
     * Registers a new keyword.
     * @throws[IllegalArgumentException] if the keyword is already registered
     */
    fun registerKeyword(name: String) {
        val internName = name.intern()
        require(!table.contains(internName)) { "keyword already registered" }
        table[internName] = Entry(isKeyword = true)
    }
    
    /**
     * Get the [Entry] associated with the given identifier name.
     */
    fun getEntryOrNull(name: String): Entry? {
        return table[name.intern()]
    }
    
    /**
     * Stores information (-> TODO) associated to an identifier.
     */
    data class Entry(
        val isKeyword: Boolean
    )
    
}
