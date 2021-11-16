package edu.kit.compiler.lex

class StringInputProvider(
    private val content: String,
) : InputProvider {

    private var nextIndex = 0

    /**
     * Current position in the source content that is always within the range [`0`, `length`]. Can be advanced by [next].
     * @throws[IllegalStateException] if called before the first next() call
     */
    val cursor: Int
        get() {
            check(nextIndex > 0) { "cannot retrieve cursor before the first next() call" }
            return nextIndex - 1
        }

    /**
     * Length of the source content. Equals the first position that is not within bounds of the source content anymore.
     */
    val limit: Int
        get() = content.length

    fun get(position: Int): Char {
        require(position in (0..limit))
        if (position < limit) {
            return content[position]
        } else {
            return InputProvider.END_OF_FILE
        }
    }

    override fun next(): Char {
        if (nextIndex < limit) {
            return get(nextIndex++)
        } else {
            return InputProvider.END_OF_FILE
        }
    }

    override fun peek(offset: Int): Char {
        require(offset >= 0)
        val pos = cursor + 1 + offset
        if (pos >= limit) {
            return InputProvider.END_OF_FILE
        }
        return get(pos)
    }

    fun substring(start: Int, end: Int): String = content.substring(start, end)
}
