package edu.kit.compiler.utils

import firm.ClassType
import firm.Graph

fun <T> Sequence<T>.prependIfNotNull(element: T?) = if (element != null) {
    sequenceOf(element) + this
} else {
    this
}

inline fun <reified T> Sequence<T>.toArray() = toList().toTypedArray()

fun String.normalizeLineEndings() = replace("\r\n", "\n").replace('\r', '\n')

fun Graph.display(): String =
    entity?.let {
        val parentName = it.owner?.let { owner -> (owner as? ClassType)?.name } ?: "<???>"
        "$parentName.${it.name}"
    } ?: "<???>"
