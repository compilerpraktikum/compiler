package edu.kit.compiler.semantic.visitor

import edu.kit.compiler.lex.AnnotationType
import edu.kit.compiler.lex.SourceFile
import edu.kit.compiler.lex.extend
import edu.kit.compiler.semantic.AstNode
import edu.kit.compiler.semantic.SemanticType

/**
 * A visitor that ensures that all special semantic constraints of the main method are fulfilled.
 */
class MainMethodVerifier(val sourceFile: SourceFile) : AbstractVisitor() {

    private var argsName: String? = null

    override fun visitMethodDeclaration(methodDeclaration: AstNode.ClassMember.SubroutineDeclaration.MethodDeclaration) {
        // do not descend normal method
    }

    override fun visitMainMethodDeclaration(mainMethodDeclaration: AstNode.ClassMember.SubroutineDeclaration.MainMethodDeclaration) {
        if (mainMethodDeclaration.name.symbol.text == "main") {
            if (mainMethodDeclaration.returnType !is SemanticType.Void) {
                sourceFile.annotate(
                    AnnotationType.ERROR,
                    mainMethodDeclaration.name.sourceRange,
                    "main method must return `void`"
                )
            }
        } else {
            sourceFile.annotate(
                AnnotationType.ERROR,
                mainMethodDeclaration.name.sourceRange,
                "only the main method is allowed to be static"
            )
        }

        when (mainMethodDeclaration.parameters.size) {
            0 -> {
                sourceFile.annotate(
                    AnnotationType.ERROR,
                    mainMethodDeclaration.block.sourceRange.start.extend(1),
                    "main method must have exactly one parameter of type `String[]`",
                )
            }
            1 -> {
                val param = mainMethodDeclaration.parameters[0]
                if (param.type !is SemanticType.Array || param.type.elementType !is SemanticType.Class || param.type.elementType.name.text != "String") {
                    sourceFile.annotate(
                        AnnotationType.ERROR,
                        mainMethodDeclaration.parameters[0].sourceRange,
                        "parameter of main method must have type `String[]`",
                    )
                }
                argsName = param.name.text
            }
            else -> {
                sourceFile.annotate(
                    AnnotationType.ERROR,
                    mainMethodDeclaration.parameters[1].sourceRange.extend(mainMethodDeclaration.parameters.last().sourceRange),
                    "main method cannot have more than one parameter",
                )
            }
        }

        super.visitMainMethodDeclaration(mainMethodDeclaration)
    }

    override fun visitLiteralThisExpression(literalThisExpression: AstNode.Expression.LiteralExpression.LiteralThisExpression) {
        errorIfFalse(sourceFile, literalThisExpression.sourceRange, "cannot use `this` in static context (main method)") { false }
        super.visitLiteralThisExpression(literalThisExpression)
    }

    override fun visitIdentifierExpression(identifierExpression: AstNode.Expression.IdentifierExpression) {
        errorIfFalse(sourceFile, identifierExpression.sourceRange, "usage of parameter `$argsName` in main method body not allowed") {
            identifierExpression.name.symbol.text != argsName
        }
        super.visitIdentifierExpression(identifierExpression)
    }
}
