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

    private companion object {
        const val BUFFER_SIZE = 128
    }

    /**
     * A ring buffer where one half is loaded from file, while the other half is read
     */
    private val ring = arrayOf(LoadableBuffer(BUFFER_SIZE), LoadableBuffer(BUFFER_SIZE), LoadableBuffer(BUFFER_SIZE))

    /**
     * Index of the currently active buffer in the ring
     */
    @Volatile
    private var currentRingIndex: Int = 0

    /**
     * Whether the end of the input channel has been reached
     */
    @Volatile
    private var endOfInput: Boolean = false

    /**
     * how many blocks have been read in total
     */
    @Volatile
    private var totalLoadedBlocks = 0

    init {
        ring.forEach { runBlocking { it.dispatchLoad() } }
    }

    /**
     * Obtain the next character and advance the current buffer position by one
     */
    suspend fun nextChar(): Char? {
        // if we already exceed the maximum amount of possible blocks, return null
        if (currentRingIndex >= totalLoadedBlocks && endOfInput)
            return null

        while (!ring[currentRingIndex % ring.size].ready) {
            // if we wait, its most certainly for a character device, so we can afford to wait a full millisecond
            delay(1)
        }

        val remainingInCurrentBuffer = ring[currentRingIndex % ring.size].byteBuffer.remaining()

        // check if current buffer is empty. If it is, we must have reached end of input,
        // because otherwise we would have switched to the next buffer
        if (remainingInCurrentBuffer == 0) {
            assert(endOfInput)
            return null
        }

        // get current byte
        val currentByte = ring[currentRingIndex % ring.size].byteBuffer.get()

        // if the buffer is now exhausted, and we have more data, schedule a new read and swap buffer
        if (remainingInCurrentBuffer == 1) {
            if (!endOfInput) {
                ring[currentRingIndex % ring.size].dispatchLoad()
            }

            // swap to next buffer
            currentRingIndex += 1
        }

        return Char(currentByte.toUByte().toInt())
    }

    /**
     * Lookahead in the buffer by a given [offset]. By default, peeks ahead 0 characters and thus returns the
     * character that would be returned by [nextChar].
     * Returns `null` if no character is available in the input.
     */
    suspend fun peek(offset: Int = 0): Char? {
        check(offset < BUFFER_SIZE) { "look-ahead cannot exceed buffer size of $BUFFER_SIZE bytes" }
        return peekInBuffer(currentRingIndex, offset)
    }

    /**
     * Recursive function that peeks into buffers once they are laoded, or calls the function again to load the next
     * buffer
     */
    private tailrec suspend fun peekInBuffer(bufferIndex: Int, offset: Int): Char? {
        // if we exceed the number of loaded blocks and have reached EOF yet, we can return null
        if (bufferIndex >= totalLoadedBlocks && endOfInput)
            return null

        // if the corresponding buffer isn't available yet, but EOF has not been reached, wait for it
        if (bufferIndex >= totalLoadedBlocks) {
            while (!ring[bufferIndex % ring.size].ready) delay(1)
        }

        // check how many bytes are in current buffer
        val remainingInCurrentBuffer = ring[bufferIndex % ring.size].byteBuffer.remaining()
        return if (offset < remainingInCurrentBuffer) {
            val peekedByte = ring[bufferIndex % ring.size].byteBuffer
                .get(ring[bufferIndex % ring.size].byteBuffer.position() + offset)

            Char(peekedByte.toUByte().toInt())
        } else {
            peekInBuffer(bufferIndex + 1, offset - remainingInCurrentBuffer)
        }
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
                    // increase the total amount of blocks to be processed
                    totalLoadedBlocks += 1

                    // mark that buffer is now ready to be read again
                    markReady()
                }
            }
        }
    }
}