package edu.kit.compiler.parser

import edu.kit.compiler.Token
import kotlinx.coroutines.channels.ReceiveChannel

class LookaheadBuffer(private val receiveChannel: ReceiveChannel<Token>, initialLookahead: Int) {
    private val lookAheadBuffer = ArrayDeque<Token>(initialCapacity = initialLookahead)
}