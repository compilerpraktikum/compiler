package edu.kit.compiler.lex

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
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
    private val ring = arrayOf(LoadableBuffer(128), LoadableBuffer(128), LoadableBuffer(128))

    @Volatile
    private var currentRingIndex: Int = 0

    @Volatile
    private var endOfInput: Boolean = false

    init {
        ring.forEach { runBlocking { it.dispatchLoad() } }
    }

    /**
     * Obtain the next character and advance the current buffer position by one
     */
    suspend fun nextChar(): Char? {
        while (!ring[currentRingIndex].ready) {
            // if we wait, its most certainly for a character device, so we can afford to wait a full millisecond
            delay(1)
        }

        val remainingInCurrentBuffer = ring[currentRingIndex].byteBuffer.remaining()

        // check if current buffer is empty. If it is, we must have reached end of input
        if (remainingInCurrentBuffer == 0) {
            assert(endOfInput)
            return null
        }

        // get current byte
        val currentByte = ring[currentRingIndex].byteBuffer.get()

        // if the buffer is now exhausted, and we have more data, schedule a new read and swap buffer
        if (remainingInCurrentBuffer == 1) {
            if (!endOfInput) {
                ring[currentRingIndex].dispatchLoad()
            }

            // if next buffer still contains data, swap to it, even if we already reached EOF
            if (ring[(currentRingIndex + 1) % ring.size].ready) {
                currentRingIndex = (currentRingIndex + 1) % ring.size
            }
        }

        return Char(currentByte.toUByte().toInt())
    }

    /**
     * A buffer that can be loaded with data from [inputChannel] asynchronously
     */
    private inner class LoadableBuffer(size: Int) {
        @Volatile
        var ready: Boolean = false
            private set

        /**
         * Fixed size [ByteBuffer] holding data of the [inputChannel]
         */
        val byteBuffer: ByteBuffer

        init {
            this.byteBuffer = ByteBuffer.allocateDirect(size).order(ByteOrder.LITTLE_ENDIAN)
        }

        fun markEmpty() {
            this.ready = false
        }

        fun markReady() {
            this.ready = true
        }

        /**
         * Dispatch a loading cycle for this buffer
         */
        suspend fun dispatchLoad() {
            this.markEmpty()
            withContext(Dispatchers.IO) {
                // fill exhausted buffer with new data
                byteBuffer.clear()
                val readBytes = inputChannel.read(byteBuffer)
                byteBuffer.flip()

                // if we reached EOF, mark that no more dispatches should happen
                if (readBytes == -1) {
                    inputChannel.close()
                    endOfInput = true
                } else {
                    // mark that buffer is now ready to be read again
                    markReady()
                }
            }
        }
    }
}