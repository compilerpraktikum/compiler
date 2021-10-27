package edu.kit.compiler.lex

import kotlinx.coroutines.*
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.ClosedChannelException
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
     * A ring buffer with one buffer to read from, one to peek into and one to load in the background.
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
    suspend fun nextChar(): Char? = coroutineScope {
        // if we already exceed the maximum amount of possible blocks, return null
        if (currentRingIndex >= totalLoadedBlocks && endOfInput)
            return@coroutineScope null

        while (!ring[currentRingIndex % ring.size].ready) {
            // if we wait, its most certainly for a character device, so we can afford to wait a full millisecond
            delay(1)
        }

        // throw exception to caller, if previous load failed
        if (ring[currentRingIndex % ring.size].failed) {
            throw ring[currentRingIndex % ring.size].failure!!
        }

        val remainingInCurrentBuffer = ring[currentRingIndex % ring.size].byteBuffer.remaining()

        // check if current buffer is empty. If it is, we must have reached end of input,
        // because otherwise we would have switched to the next buffer
        if (remainingInCurrentBuffer == 0) {
            assert(endOfInput)
            return@coroutineScope null
        }

        // get current byte
        val currentByte = ring[currentRingIndex % ring.size].byteBuffer.get()

        // if the buffer is now exhausted, and we have more data, schedule a new read and swap buffer
        if (remainingInCurrentBuffer == 1) {
            if (!endOfInput) {
                // don't capture `currentRingIndex` in the closure, as this would create a race condition
                val reloadedRingIndex = currentRingIndex
                launch {
                    ring[reloadedRingIndex % ring.size].dispatchLoad()
                }
            }

            // swap to next buffer
            currentRingIndex += 1
        }

        return@coroutineScope Char(currentByte.toUByte().toInt())
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
        while (!ring[bufferIndex % ring.size].ready) delay(1)

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

        @Volatile
        var failed: Boolean = false

        /**
         * Fixed size [ByteBuffer] holding data of the [inputChannel]
         */
        val byteBuffer: ByteBuffer

        /**
         * Error if anything gone wrong (null if [failed] is false). This exception is not thrown, because it happens
         * asynchronously to [nextChar] and we cannot return the read buffer as a result to the asynchronous
         * calculation, because this would create an enormous amount of allocations. So we need to deal with this
         * failure manually
         */
        var failure: IOException? = null

        init {
            this.byteBuffer = ByteBuffer.allocateDirect(size).order(ByteOrder.LITTLE_ENDIAN)
        }

        fun markEmpty() {
            this.ready = false
        }

        fun markReady() {
            // increase the total amount of blocks to be processed
            totalLoadedBlocks += 1

            this.ready = true
        }

        fun setFailed(exception: IOException) {
            this.failed = true
            this.failure = exception
            setEOF()
            markReady()
        }

        fun setEOF() {
            endOfInput = true
            inputChannel.close()
        }

        /**
         * Dispatch a loading cycle for this buffer
         */
        suspend fun dispatchLoad() {
            if (endOfInput) {
                // nothing to do
                return
            }
    
            markEmpty()
            withContext(Dispatchers.IO) {
                // fill exhausted buffer with new data
                byteBuffer.clear()

                val readBytes = try {
                    inputChannel.read(byteBuffer)
                } catch (e: ClosedChannelException) {
                    setEOF()
                } catch (e: IOException) {
                    setFailed(e)
                    return@withContext
                }

                byteBuffer.flip()

                // if we reached EOF, mark that no more dispatches should happen
                if (readBytes == -1) {
                    setEOF()
                } else {
                    // mark that buffer is now ready to be read again
                    markReady()
                }
            }
        }
    }
}