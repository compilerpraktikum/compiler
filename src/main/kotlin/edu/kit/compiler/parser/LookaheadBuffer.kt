package edu.kit.compiler.parser

import edu.kit.compiler.loop
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.flow

class LookaheadBuffer<T>(
    private val receiveChannel: ReceiveChannel<T>
) {
    private val buffer = ArrayDeque<T>(initialCapacity = 16)
    
    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun get(): T {
        return if (buffer.isNotEmpty()) {
            buffer.removeFirst()
        } else {
            require(!receiveChannel.isClosedForReceive) { "tried to read from a closed channel" }
            receiveChannel.receive()
        }
    }
    
    suspend fun peek(offset: Int = 0): T {
        require(offset >= 0)
        tryFillBuffer(offset + 1)
        require(offset < buffer.size) { "tried to peek past the end of the channel" }
        return buffer[offset]
    }
    
    suspend fun tryPeek(offset: Int = 0): T? {
        require(offset >= 0)
        tryFillBuffer(offset + 1)
        return if (offset < buffer.size) {
            buffer[offset]
        } else {
            null
        }
    }
    
    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun tryFillBuffer(numElements: Int) {
        if (buffer.size >= numElements) {
            return
        }
        
        repeat(numElements - buffer.size) {
            if (receiveChannel.isClosedForReceive) {
                return
            }
            buffer.add(receiveChannel.receive())
        }
    }
}

fun <T> LookaheadBuffer<T>.peekFlow() = flow {
    loop {
        val element = tryPeek(it) ?: return@flow
        emit(element)
    }
}
