package edu.kit.compiler.semantic

import edu.kit.compiler.lexer.StringTable
import edu.kit.compiler.source.SourceFile
import edu.kit.compiler.source.SourcePosition
import edu.kit.compiler.source.extend

object InternalFunction {
    val SYSTEM_IN_READ = SemanticAST.Expression.MethodInvocationExpression.Type.Internal("system_read", "System.in.read", SemanticType.Integer, emptyList())
    val SYSTEM_OUT_PRINTLN = SemanticAST.Expression.MethodInvocationExpression.Type.Internal("system_println", "System.out.println", SemanticType.Void, listOf(SemanticType.Integer))
    val SYSTEM_OUT_WRITE = SemanticAST.Expression.MethodInvocationExpression.Type.Internal("system_write", "System.out.write", SemanticType.Void, listOf(SemanticType.Integer))
    val SYSTEM_OUT_FLUSH = SemanticAST.Expression.MethodInvocationExpression.Type.Internal("system_flush", "System.out.flush", SemanticType.Void, emptyList())
}

fun GlobalNamespace.defineBuiltIns(sourceFile: SourceFile, stringTable: StringTable) {
    val dummySourceRange = SourcePosition(sourceFile, 0).extend(0)

    classes.tryPut(
        SemanticAST.ClassDeclaration(
            SemanticAST.Identifier(stringTable.tryRegisterIdentifier("String"), dummySourceRange),
            emptyList(),
            dummySourceRange
        ).apply {
            namespace = ClassNamespace(this@defineBuiltIns)
        }.asDefinition(),
        onDuplicate = { check(false) }
    )
}
