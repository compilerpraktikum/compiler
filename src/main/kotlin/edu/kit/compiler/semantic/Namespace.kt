package edu.kit.compiler.semantic

sealed class Namespace {

    object GlobalNamespace : Namespace()

    class ClassNamespace : Namespace()

    open class LocalNamespace : Namespace()

    class MethodNamespace : LocalNamespace()
}
