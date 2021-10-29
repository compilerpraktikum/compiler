package edu.kit.compiler.lex

import kotlinx.coroutines.*
import java.io.IOException
import java.io.InputStream

/**
 * A ring-buffered
 */
class BufferedInputProvider(private val inputStream: InputStream, private val capacity: Int = 128) : InputProvider {
    
    /**
     * A fixed-size buffer which is reused once data has been processed
     */
    private val cyclicBuffer = ByteArray(capacity)
    
    /**
     * Current position in the buffer. It points at the character that will be returned by the next [next]-call.
     */
    @Volatile
    private var cursor = 0
    
    /**
     * Amount of bytes ready in the buffer (exclusive bound). Does not wrap around the buffer capacity (i.e. it requires
     * a modulo operation).
     */
    @Volatile
    private var limit = 0
    
    /**
     * Marker that indicates if the [inputStream] is already closed. Does not mean that the [cyclicBuffer] is exhausted.
     */
    @Volatile
    private var endOfFile = false
    
    /**
     * Set to an [IOException] if any arises during reading. It gets stored here until the next call of [next] which
     * will throw it to the caller.
     */
    @Volatile
    private var failure: IOException? = null
    
    /**
     * Currently available amount of data in bytes
     */
    private val available: Int
        get() = limit - cursor
    
    /**
     * Whether more data is available in the internal buffer
     */
    private val hasMoreData: Boolean
        get() = available > 0
    
    /**
     * Obtain the next character and advance the current buffer position by one.
     *
     * @return next character from the input or `null` if the end of the input stream has been reached
     *
     * @throws IOException if an error occurred during processing of the input stream
     */
    suspend fun next(): Char? {
        while (!hasMoreData && !endOfFile && failure == null) {
            currentJob?.join() ?: tryDispatchLoad()
        }
        
        if (failure != null) {
            throw failure!!
        }
        
        val currentCharacter = if (hasMoreData) {
            cyclicBuffer[cursor++ % capacity].decode()
        } else {
            assert(endOfFile)
            null
        }
        
        tryDispatchLoad()
        
        return currentCharacter
    }
    
    /**
     * Encode a signed byte as an ASCII character from the extended ASCII page.
     */
    private fun Byte.decode(): Char {
        return Char(toUByte().toInt())
    }
    
    /**
     * Lookahead in the buffer by a given [offset].
     *
     * @param offset amount of bytes to peek after the current position. By default, peeks ahead 0 characters and thus
     * returns the character that would be returned by the next [next]-call.
     *
     * @return the character [offset] bytes behind the current position in the input, or `null` if the offset exceeds
     * the amount of data in the input stream.
     *
     * @throws IllegalArgumentException if the offset exceeds the buffer's capacity
     */
    override suspend fun peek(offset: Int): Char? {
        check(offset < capacity) { "offset cannot exceed buffer capacity" }
    
        val index = cursor + offset
        
        // load until enough data has been loaded or no more data can be loaded
        while (index > limit && !endOfFile) {
            currentJob?.join() ?: tryDispatchLoad(ignoreCapacity = true)
        }
        
        return if (index < limit) {
            cyclicBuffer[index % capacity].decode()
        } else {
            assert(endOfFile)
            null
        }
    }
    
    override suspend fun nextChar(): Char? {
        return this.next()
    }
    
    private var currentJob: Job? = null
    
    private suspend fun tryDispatchLoad(ignoreCapacity: Boolean = false) = coroutineScope {
        synchronized(this) {
            if (currentJob != null) {
                return@coroutineScope
            }
            
            if (endOfFile) {
                return@coroutineScope
            }
            
            val freeSpace = capacity - available
            if (!ignoreCapacity && freeSpace < (capacity / 8)) { // TODO better heuristic?
                return@coroutineScope
            }
            
            val job = launch(Job() + Dispatchers.IO, CoroutineStart.LAZY) {
                val data = ByteArray(freeSpace)
                
                @Suppress("BlockingMethodInNonBlockingContext") // we are in the right context (Dispatchers.IO)
                val bytesRead = try {
                    inputStream.read(data)
                } catch (ex: IOException) {
                    failure = ex
                    return@launch
                }
                
                if (bytesRead == -1) {
                    endOfFile = true
                    return@launch
                }
                
                val currentLimit = limit
                val spaceUntilEnd = capacity - (currentLimit % capacity)
                val bytesCopiedUntilEnd = bytesRead.coerceAtMost(spaceUntilEnd)
                System.arraycopy(data, 0, cyclicBuffer, (currentLimit % capacity), bytesCopiedUntilEnd)
                val remaining = bytesRead - bytesCopiedUntilEnd
                if (remaining > 0) {
                    System.arraycopy(data, bytesCopiedUntilEnd, cyclicBuffer, 0, remaining)
                }
                
                limit = currentLimit + bytesRead
            }
            job.invokeOnCompletion {
                synchronized(this@BufferedInputProvider) {
                    currentJob = null
                }
            }
            currentJob = job
            job.start()
        }
    }
}
