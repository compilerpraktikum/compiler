package edu.kit.compiler.lex

import java.io.InputStream

class SyncInputProvider(inputStream: InputStream) : InputProvider {
    var bytes: ByteArray = inputStream.readAllBytes()
    var position = 0

    override fun peek(offset: Int): Char {
        if (position + offset >= bytes.size) {
            return InputProvider.END_OF_FILE
        }

        return bytes[position + offset].toUByte().toInt().toChar()
    }

    override fun next(): Char {
        if (position >= bytes.size) {
            return InputProvider.END_OF_FILE
        }

        val char = bytes[position].toUByte().toInt().toChar()
        position += 1
        return char
    }
}
