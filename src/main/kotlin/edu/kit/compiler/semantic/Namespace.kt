package edu.kit.compiler.semantic

import edu.kit.compiler.lex.Symbol

sealed class Namespace {

    /**
     * Singleton global namespace. Since mini java does not support modules, we only ever need one instance of this
     */
    object GlobalNamespace : Namespace() {
        /**
         * All class definitions of the compilation unit
         */
        val classDefinitions = mutableMapOf<Symbol, AstNode.ClassDeclaration>()
    }

    /**
     * Namespace of a class containing all its members
     */
    class ClassNamespace : Namespace() {
        /**
         * All fields of this class scope
         */
        val fieldDefinitions = mutableMapOf<Symbol, AstNode.ClassMember.FieldDeclaration>()

        /**
         * All methods of this class scope
         */
        val methodDefinitions = mutableMapOf<Symbol, AstNode.ClassMember.SubroutineDeclaration>()
    }

    /**
     * A local namespace of a block of statements. A special case of this is a [MethodNamespace]
     */
    open class LocalNamespace : Namespace()

    /**
     * A method namespace. It is similar to a [LocalNamespace] but also contains parameters
     */
    class MethodNamespace : LocalNamespace()
}
