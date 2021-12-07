package edu.kit.compiler.semantic

import edu.kit.compiler.lex.SourceFile
import edu.kit.compiler.lex.SourcePosition
import edu.kit.compiler.lex.StringTable
import edu.kit.compiler.lex.extend

object InternalFunction {
    val SYSTEM_IN_READ = AstNode.Expression.MethodInvocationExpression.Type.Internal("system_read", "System.in.read", SemanticType.Integer, emptyList())
    val SYSTEM_OUT_PRINTLN = AstNode.Expression.MethodInvocationExpression.Type.Internal("system_println", "System.out.println", SemanticType.Void, listOf(SemanticType.Integer))
    val SYSTEM_OUT_WRITE = AstNode.Expression.MethodInvocationExpression.Type.Internal("system_write", "System.out.write", SemanticType.Void, listOf(SemanticType.Integer))
    val SYSTEM_OUT_FLUSH = AstNode.Expression.MethodInvocationExpression.Type.Internal("system_flush", "System.out.flush", SemanticType.Void, emptyList())
}

fun GlobalNamespace.defineBuiltIns(sourceFile: SourceFile, stringTable: StringTable) {
    val dummySourceRange = SourcePosition(sourceFile, 0).extend(0)

    classes.tryPut(
        AstNode.ClassDeclaration(
            AstNode.Identifier(stringTable.tryRegisterIdentifier("String"), dummySourceRange),
            emptyList(),
            dummySourceRange
        ).apply {
            namespace = ClassNamespace(this@defineBuiltIns)
        }.asDefinition(),
        onDuplicate = { check(false) }
    )
}
