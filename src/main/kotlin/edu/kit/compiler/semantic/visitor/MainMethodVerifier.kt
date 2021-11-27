package edu.kit.compiler.semantic.visitor

import edu.kit.compiler.lex.AnnotationType
import edu.kit.compiler.lex.SourceFile
import edu.kit.compiler.lex.SourceNote
import edu.kit.compiler.lex.SourceRange
import edu.kit.compiler.semantic.AstNode
import edu.kit.compiler.semantic.SemanticType

/**
 * A visitor that ensures that all special semantic constraints of the main method are fulfilled.
 */
class MainMethodVerifier(val sourceFile: SourceFile) : AbstractVisitor() {

    var checkingMainMethodCurrently = false
    var argsName = "args"

    override fun visitMethodDeclaration(methodDeclaration: AstNode.ClassMember.SubroutineDeclaration.MethodDeclaration) {
        // don't visit method blocks, that would be waste of time
    }

    override fun visitMethodInvocationExpression(methodInvocationExpression: AstNode.Expression.MethodInvocationExpression) {
        if (methodInvocationExpression.method.symbol.text == "main")
            sourceFile.annotate(
                AnnotationType.ERROR,
                methodInvocationExpression.sourceRange,
                "the main method cannot be invoked."
            )

        super.visitMethodInvocationExpression(methodInvocationExpression)
    }

    override fun visitMainMethodDeclaration(mainMethodDeclaration: AstNode.ClassMember.SubroutineDeclaration.MainMethodDeclaration) {
        if (mainMethodDeclaration.returnType !is SemanticType.Void) {
            if (mainMethodDeclaration.name.symbol.text == "main")
                sourceFile.annotate(
                    AnnotationType.ERROR,
                    mainMethodDeclaration.name.sourceRange,
                    "the main method must return `void`",
                    listOf(
                        SourceNote(mainMethodDeclaration.name.sourceRange, "hint: change the return type to `void`")
                    )
                )
            else
                sourceFile.annotate(
                    AnnotationType.ERROR,
                    mainMethodDeclaration.name.sourceRange,
                    "only the main method is allowed to be `static`",
                    listOf(
                        SourceNote(mainMethodDeclaration.name.sourceRange, "hint: remove the `static` modifier")
                    )
                )
        } else {
            if (mainMethodDeclaration.name.symbol.text != "main") {
                sourceFile.annotate(
                    AnnotationType.ERROR,
                    mainMethodDeclaration.name.sourceRange,
                    "only the `main` method is allowed to be static",
                    listOf(
                        SourceNote(mainMethodDeclaration.name.sourceRange, "hint: remove the `static` modifier")
                    )
                )
            }
        }

        if (mainMethodDeclaration.parameters.isEmpty()) {
            sourceFile.annotate(
                AnnotationType.ERROR,
                SourceRange(mainMethodDeclaration.block.sourceRange.start, 1),
                "the main method must have exactly one parameter of type `String[]`",
            )
        } else if (mainMethodDeclaration.parameters.size > 2) {
            sourceFile.annotate(
                AnnotationType.ERROR,
                mainMethodDeclaration.parameters[1].sourceRange.extend(mainMethodDeclaration.parameters.last().sourceRange),
                "the main method cannot have more than one parameter",
            )
        } else if (mainMethodDeclaration.parameters[0].type !is SemanticType.Array ||
            (mainMethodDeclaration.parameters[0].type as SemanticType.Array).elementType !is SemanticType.Class ||
            ((mainMethodDeclaration.parameters[0].type as SemanticType.Array).elementType as SemanticType.Class)
                .name.symbol.text != "String"
        ) {
            sourceFile.annotate(
                AnnotationType.ERROR,
                mainMethodDeclaration.parameters[0].sourceRange,
                "the parameter of the `main` method must have type `String[]`",
            )
        }
        if (mainMethodDeclaration.parameters.isNotEmpty()) argsName = mainMethodDeclaration.parameters[0].name.text

        checkingMainMethodCurrently = true
        super.visitMainMethodDeclaration(mainMethodDeclaration)
        checkingMainMethodCurrently = false
    }

    override fun visitLiteralThisExpression(literalThisExpression: AstNode.Expression.LiteralExpression.LiteralThisExpression) {
        checkAndAnnotateSourceFileIfNot(sourceFile, literalThisExpression.sourceRange, "Usage of this in \"static context\" (main method).") { !checkingMainMethodCurrently }
        super.visitLiteralThisExpression(literalThisExpression)
    }

    override fun visitIdentifierExpression(identifierExpression: AstNode.Expression.IdentifierExpression) {
        checkAndAnnotateSourceFileIfNot(sourceFile, identifierExpression.sourceRange, "No usage of parameter \"$argsName\" in main method body") {
            checkingMainMethodCurrently && identifierExpression.name.symbol.text != argsName
        }
        super.visitIdentifierExpression(identifierExpression)
    }
}
