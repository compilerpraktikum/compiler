package edu.kit.compiler.lex

import kotlinx.coroutines.*
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.ClosedChannelException
import java.nio.channels.ScatteringByteChannel

private sealed class LoadableBufferState {
    object Uninitialized : LoadableBufferState()
    
    object Ready : LoadableBufferState()
    
    class Failed(val exception: IOException) : LoadableBufferState()
    
    class Loading(val job: Job) : LoadableBufferState()
}

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
        ring.forEach {
            runBlocking {
                it.dispatchLoad()
                it.waitUntilReady() // need to load sequentially to ensure correct order
            }
        }
    }

    /**
     * Obtain the next character and advance the current buffer position by one
     */
    suspend fun nextChar(): Char? = coroutineScope {
        if (currentBlockIndex >= totalLoadedBlocks && endOfInput) {
            return@coroutineScope null
        }
    
        val currentBuffer = ring[currentBlockIndex % ring.size]
    
        currentBuffer.waitUntilReady()
    
        (currentBuffer.state as? LoadableBufferState.Failed)?.let {
            throw it.exception
        }
    
        val remainingInCurrentBuffer = currentBuffer.byteBuffer.remaining()
    
        if (remainingInCurrentBuffer == 0) {
            assert(endOfInput) // we must have reached end of input, because otherwise we would have switched to the next buffer
            return@coroutineScope null
        }
    
        val currentByte = currentBuffer.byteBuffer.get()

        // if the buffer is now exhausted, and we have more data, schedule a new read and swap buffer
        if (remainingInCurrentBuffer == 1) {
            if (!endOfInput) {
                currentBuffer.dispatchLoad()
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
    suspend fun peek(offset: Int = 0): Char? {
        require(offset < BUFFER_SIZE) { "look-ahead cannot exceed buffer size of $BUFFER_SIZE bytes" }
        return peekInBlock(currentBlockIndex, offset)
    }

    /**
     * Recursive function that peeks into buffers once they are loaded, or calls the function again to load the next
     * buffer
     */
    private tailrec suspend fun peekInBlock(blockIndex: Int, offset: Int): Char? {
        if (blockIndex >= totalLoadedBlocks && endOfInput) {
            return null
        }

        val currentBuffer = ring[blockIndex % ring.size]
        currentBuffer.waitUntilReady()

        val remainingInCurrentBuffer = currentBuffer.byteBuffer.remaining()
        return if (offset < remainingInCurrentBuffer) {
            val peekedByte = currentBuffer.byteBuffer
                .get(currentBuffer.byteBuffer.position() + offset)

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
        var state: LoadableBufferState = LoadableBufferState.Uninitialized
            private set
        
        /**
         * Fixed size [ByteBuffer] holding data of the [inputChannel]
         */
        val byteBuffer: ByteBuffer = ByteBuffer.allocateDirect(BUFFER_SIZE).order(ByteOrder.LITTLE_ENDIAN)
        
        fun markReady() {
            totalLoadedBlocks += 1
            state = LoadableBufferState.Ready
        }
        
        fun markFailed(exception: IOException) {
            state = LoadableBufferState.Failed(exception)
            setEOF()
            markReady()
        }
        
        fun setEOF() {
            endOfInput = true
            inputChannel.close()
        }
        
        /**
         * Dispatch an asynchronous loading cycle for this buffer
         */
        suspend fun dispatchLoad() = coroutineScope {
            if (endOfInput) {
                return@coroutineScope
            }
            
            val job = launch(Job(), CoroutineStart.LAZY) {
                // fill exhausted buffer with new data
                byteBuffer.clear()
                
                val readBytes = try {
                    withContext(Dispatchers.IO) {
                        inputChannel.read(byteBuffer)
                    }
                } catch (e: ClosedChannelException) {
                    setEOF()
                } catch (e: IOException) {
                    markFailed(e)
                    return@launch
                }
                
                byteBuffer.flip()
                
                if (readBytes == -1) {
                    setEOF()
                } else {
                    markReady()
                }
            }
            state = LoadableBufferState.Loading(job)
            job.start() // start job here to prevent it from finishing before we set the state to loading
        }

        /**
         * Wait until the buffer is ready.
         */
        suspend fun waitUntilReady() {
            (state as? LoadableBufferState.Loading)?.job?.join()
        }
    }
}
