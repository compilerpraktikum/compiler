package edu.kit.compiler.parser

import edu.kit.compiler.loop
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.flow

class LookaheadBuffer<T>(
    private val receiveChannel: ReceiveChannel<T>
) {
    private val buffer = ArrayDeque<T>(initialCapacity = 16)
    
    suspend fun get(): T {
        return if (buffer.isNotEmpty()) {
            buffer.removeFirst()
        } else {
            receiveChannel.receive()
        }
    }
    
    suspend fun peek(offset: Int = 0): T {
        require(offset >= 0)
        fillBuffer(offset + 1)
        return buffer.get(offset)
    }
    
    private suspend fun fillBuffer(numElements: Int) {
        if (buffer.size >= numElements) {
            return
        }
        
        repeat(numElements - buffer.size) {
            buffer.add(receiveChannel.receive())
        }
    }
}

fun <T> LookaheadBuffer<T>.peakFlow() = flow {
    loop {
        emit(peek(it))
    }
}
