package edu.kit.compiler.parser

/**
 * Buffers a [Sequence] to allow variable lookahead. Elements received during [peek]-operations are saved in a
 * queue until a matching call to [get]. Note, that no elements are buffered if not explicitly required by a call to
 * [peek].
 *
 * @param[sequence] the sequence that is buffered for lookahead.
 */
class LookaheadBuffer<T>(
    sequence: Sequence<T>
) {
    private val iterator = sequence.iterator()
    private val buffer = ArrayDeque<T>(initialCapacity = 16)

    /**
     * Get the next element of the sequence.
     *
     * @throws IllegalStateException if called on an exhausted channel.
     */
    fun get(): T {
        return if (buffer.isNotEmpty()) {
            buffer.removeFirst()
        } else {
            check(iterator.hasNext()) { "no elements left" }
            iterator.next()
        }
    }

    /**
     * Peek into the sequence skipping the first [offset] elements.
     *
     * @param offset the amount of items to skip in the channel counting from the current next element. An offset of 0
     * will return the element a call to [get] would return.
     *
     * @return the element at index [offset]
     *
     * @throws IllegalStateException if the offset lies behind the end of sequence
     */
    fun peek(offset: Int = 0): T {
        require(offset >= 0)
        ensureBufferFilled(offset + 1)
        check(offset < buffer.size) { "tried to peek past the end of the sequence" }
        return buffer[offset]
    }

    /**
     * Ensure that the internal [buffer] is filled with at least [numElements] items.
     *
     * @return `true`, if the buffer is guaranteed to contained [numElements] items, `false` if not enough elements remain
     * in the sequence.
     */
    private fun ensureBufferFilled(numElements: Int): Boolean {
        if (buffer.size >= numElements) {
            return true
        }

        repeat(numElements - buffer.size) {
            if (!iterator.hasNext()) {
                return false
            }
            buffer.add(iterator.next())
        }

        return true
    }
}
