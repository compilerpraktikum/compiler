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
class RingBuffer(private val inputChannel: ScatteringByteChannel) : InputProvider {
    
    private companion object {
        const val BUFFER_SIZE = 128
    }
    
    /**
     * A ring buffer with one buffer to read from, one to peek into and one to load in the background.
     */
    private val ring = arrayOf(LoadableBuffer(), LoadableBuffer(), LoadableBuffer())
    
    /**
     * Index of the currently active block
     */
    @Volatile
    private var currentBlockIndex: Int = 0
    
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
        ring.forEach { runBlocking { it.load() } }
    }
    
    /**
     * Obtain the next character and advance the current buffer position by one
     */
    override suspend fun nextChar(): Char? = coroutineScope {
        // if we already exceed the maximum amount of possible blocks, return null
        if (currentBlockIndex >= totalLoadedBlocks && endOfInput) return@coroutineScope null
        
        val currentBuffer = ring[currentBlockIndex % ring.size]
        
        currentBuffer.waitUntilReady()
        
        // throw exception to caller, if previous load failed
        if (currentBuffer.failed) {
            throw currentBuffer.failure!!
        }
        
        val remainingInCurrentBuffer = currentBuffer.byteBuffer.remaining()
        
        // check if current buffer is empty. If it is, we must have reached end of input,
        // because otherwise we would have switched to the next buffer
        if (remainingInCurrentBuffer == 0) {
            assert(endOfInput)
            return@coroutineScope null
        }
        
        // get current byte
        val currentByte = currentBuffer.byteBuffer.get()
        
        // if the buffer is now exhausted, and we have more data, schedule a new read and swap buffer
        if (remainingInCurrentBuffer == 1) {
            if (!endOfInput) {
                launch {
                    currentBuffer.load()
                }
            }
    
            // swap to next buffer
            currentBlockIndex += 1
        }
        
        return@coroutineScope Char(currentByte.toUByte().toInt())
    }
    
    /**
     * Lookahead in the buffer by a given [offset]. By default, peeks ahead 0 characters and thus returns the
     * character that would be returned by [nextChar].
     * Returns `null` if no character is available in the input.
     */
    override suspend fun peek(offset: Int): Char? {
        require(offset < BUFFER_SIZE) { "look-ahead cannot exceed buffer size of $BUFFER_SIZE bytes" }
        return peekInBlock(currentBlockIndex, offset)
    }
    
    /**
     * Recursive function that peeks into buffers once they are loaded, or calls the function again to load the next
     * buffer
     */
    private tailrec suspend fun peekInBlock(blockIndex: Int, offset: Int): Char? {
        // if we exceed the number of loaded blocks and have reached EOF yet, we can return null
        if (blockIndex >= totalLoadedBlocks && endOfInput) return null
        
        val currentBuffer = ring[blockIndex % ring.size]
        currentBuffer.waitUntilReady()
        
        // check how many bytes are in current buffer
        val remainingInCurrentBuffer = currentBuffer.byteBuffer.remaining()
        return if (offset < remainingInCurrentBuffer) {
            val peekedByte = currentBuffer.byteBuffer.get(currentBuffer.byteBuffer.position() + offset)
            
            Char(peekedByte.toUByte().toInt())
        } else {
            peekInBlock(blockIndex + 1, offset - remainingInCurrentBuffer)
        }
    }
    
    /**
     * A buffer that can be loaded with data from [inputChannel] asynchronously
     */
    private inner class LoadableBuffer {
        @Volatile
        var ready: Boolean = false
            private set
        
        @Volatile
        var failed: Boolean = false
            private set
        
        /**
         * Error if anything gone wrong (null if [failed] is false). This exception is not thrown, because it happens
         * asynchronously to [nextChar] and we cannot return the read buffer as a result to the asynchronous
         * calculation, because this would create an enormous amount of allocations. So we need to deal with this
         * failure manually
         */
        @Volatile
        var failure: IOException? = null
        
        /**
         * Fixed size [ByteBuffer] holding data of the [inputChannel]
         */
        val byteBuffer: ByteBuffer = ByteBuffer.allocateDirect(BUFFER_SIZE).order(ByteOrder.LITTLE_ENDIAN)
        
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
        suspend fun load() {
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
        
        /**
         * Wait until the buffer is ready.
         */
        suspend fun waitUntilReady() {
            while (!ready) {
                // if we wait, its most certainly for a character device, so we can afford to wait a full millisecond
                delay(1)
            }
        }
    }
}
