package edu.kit.compiler.utils

fun <T> Sequence<T>.prependIfNotNull(element: T?) = if (element != null) {
    sequenceOf(element) + this
} else {
    this
}

inline fun <reified T> Sequence<T>.toArray() = toList().toTypedArray()

fun String.normalizeLineEndings() = replace("\r\n", "\n").replace('\r', '\n')
