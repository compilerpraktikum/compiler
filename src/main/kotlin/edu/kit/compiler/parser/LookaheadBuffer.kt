package edu.kit.compiler.parser

import edu.kit.compiler.loop
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.flow

/**
 * Buffers a [ReceiveChannel] to allow variable lookahead. Elements received during [peek]-operations are saved in a
 * queue until a matching call of [get]. Note, that no elements are buffered if not explicitly required by a call to
 * [peek].
 *
 * @param receiveChannel the channel that is being buffered for lookahead.
 */
class LookaheadBuffer<T>(
    private val receiveChannel: ReceiveChannel<T>
) {
    private val buffer = ArrayDeque<T>(initialCapacity = 16)
    
    /**
     * Get the current foremost element of the channel and advance the channel by one.
     *
     * @throws IllegalStateException if called on an exhausted channel.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun get(): T {
        return if (buffer.isNotEmpty()) {
            buffer.removeFirst()
        } else {
            check(!receiveChannel.isClosedForReceive) { "tried to read from a closed channel" }
            receiveChannel.receive()
        }
    }
    
    /**
     * Peek into the channel skipping [offset] elements behind the current first element.
     *
     * @param offset the amount of items to skip in the channel counting from the current next element. An offset of 0
     * will return the element a call to [get] would return as well.
     *
     * @return the element at index [offset]
     *
     * @throws IllegalArgumentException if the offset lies behind the end of channel.
     */
    suspend fun peek(offset: Int = 0): T {
        require(offset >= 0)
        ensureBufferFilled(offset + 1)
        require(offset < buffer.size) { "tried to peek past the end of the channel" }
        return buffer[offset]
    }
    
    /**
     * Try to peek into the channel skipping [offset] elements behind the current first element.
     * If the channel is not long enough, `null` is returned.
     *
     * @param offset the amount of items to skip in the channel counting from the current next element. An offset of 0
     * will return the element a call to [get] would return as well.
     *
     * @return the element at index [offset] or `null`, if the channel ends before that.
     */
    suspend fun tryPeek(offset: Int = 0): T? {
        require(offset >= 0)
        
        return if (ensureBufferFilled(offset + 1)) {
            buffer[offset]
        } else {
            null
        }
    }
    
    /**
     * Ensure that the internal [buffer] is filled with at least [numElements] items.
     *
     * @return true, if the buffer is guaranteed to contained [numElements] items, false if not enough elements remain
     * in the channel (in which case all remaining elements have been buffered and the channel is now exhausted).
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun ensureBufferFilled(numElements: Int): Boolean {
        if (buffer.size >= numElements) {
            return true
        }
        
        repeat(numElements - buffer.size) {
            if (receiveChannel.isClosedForReceive) {
                return false
            }
            buffer.add(receiveChannel.receive())
        }
        
        return true
    }
}

/**
 * Generates a secondary flow out of a [LookaheadBuffer] that emits values from the buffered [channel][ReceiveChannel]
 * without consuming them.
 */
fun <T> LookaheadBuffer<T>.peekFlow() = flow {
    loop {
        val element = tryPeek(it) ?: return@flow
        emit(element)
    }
}
