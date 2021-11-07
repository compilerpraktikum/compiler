package edu.kit.compiler.lex

class StringInputProvider(
    private val content: String,
) : InputProvider {

    var cursor = 0
        private set

    val limit
        get() = content.length

    fun get(position: Int): Char {
        require(position < limit)
        return content[position]
    }

    override fun next(): Char {
        if (cursor >= limit) {
            return InputProvider.END_OF_FILE
        }

        return get(cursor).also {
            cursor += 1
        }
    }

    override fun peek(offset: Int): Char {
        require(offset >= 0)
        val pos = cursor + offset
        if (pos >= limit) {
            return InputProvider.END_OF_FILE
        }
        return get(pos)
    }
}
