package edu.kit.compiler

/**
 * Executes the given [block] in an endless loop. The block is passed an iteration variable as parameter that starts
 * at [start] and is increased by [increment] in each step.
 */
inline fun loop(start: Int = 0, increment: Int = 1, block: (Int) -> Unit) {
    var i = start
    while (true) {
        block(i)
        i += increment
    }
}
