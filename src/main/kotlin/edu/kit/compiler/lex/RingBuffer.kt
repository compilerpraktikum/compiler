package edu.kit.compiler.lex

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.ScatteringByteChannel

/**
 * A ring-buffered interface to read the compilation unit. It asynchronously reads into two [ByteBuffers][ByteBuffer]
 * and swaps between them when reading characters.
 */
class RingBuffer(private val inputChannel: ScatteringByteChannel) {

    /**
     * A ring buffer where one half is loaded from file, while the other half is read
     */
    private val ring = arrayOf(
        ByteBuffer.allocateDirect(64).order(ByteOrder.LITTLE_ENDIAN),
        ByteBuffer.allocateDirect(64).order(ByteOrder.LITTLE_ENDIAN)
    )

    @Volatile
    private var currentRingIndex: Int = 0

    @Volatile
    private var swapReady: Boolean = false

    @Volatile
    private var endOfInput: Boolean = false

    init {
        inputChannel.read(ring)
        ring.forEach { it.flip() }
        swapReady = true
    }

    /**
     * Obtain the next character and advance the current buffer position by one
     */
    suspend fun nextChar(): Char? {
        val remaining = ring[currentRingIndex].remaining()

        if (remaining == 0 && !endOfInput) {
            // wait until we can swap to the next buffer
            while (!swapReady) {
                delay(1)
            }

            // mark that we cannot swap buffers back to the current one, yet
            swapReady = false

            // dispatch read operation
            if (inputChannel.isOpen) {
                val swapRingIndex = currentRingIndex
                withContext(Dispatchers.IO) {
                    // fill exhausted buffer with new data
                    ring[swapRingIndex].clear()
                    val readBytes = inputChannel.read(ring, swapRingIndex, 1)
                    ring[swapRingIndex].flip()

                    // mark that swap is now ready
                    swapReady = true

                    // if we reached EOF, mark that no more dispatches should happen
                    if (readBytes == -1L) {
                        inputChannel.close()
                        endOfInput = true
                    }
                }
            }

            // swap buffers to the currently available
            currentRingIndex = (currentRingIndex + 1) % ring.size
        } else if (endOfInput) {
            return null
        }

        return Char(ring[currentRingIndex].get().toUByte().toInt())
    }
}