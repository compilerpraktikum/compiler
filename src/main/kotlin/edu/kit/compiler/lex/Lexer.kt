package edu.kit.compiler.lex

/**
 * lexicographic analysis and tokenization of an input stream.
 *
 * @param input input abstracted in a ring buffer
 * @param stringTable string table of current compilation run
 */
class Lexer(
    private val input: RingBuffer,
    private val stringTable: StringTable
) {

}