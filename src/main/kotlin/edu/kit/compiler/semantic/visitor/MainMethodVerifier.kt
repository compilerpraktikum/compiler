package edu.kit.compiler.semantic.visitor

import edu.kit.compiler.semantic.SemanticAST
import edu.kit.compiler.semantic.SemanticType
import edu.kit.compiler.source.AnnotationType
import edu.kit.compiler.source.SourceFile
import edu.kit.compiler.source.extend

/**
 * A visitor that ensures that all special semantic constraints of the main method are fulfilled.
 */
class MainMethodVerifier(val sourceFile: SourceFile) : AbstractVisitor() {

    private var argsName: String? = null

    override fun visitMethodDeclaration(methodDeclaration: SemanticAST.ClassMember.SubroutineDeclaration.MethodDeclaration) {
        // do not descend normal method
    }

    override fun visitMainMethodDeclaration(mainMethodDeclaration: SemanticAST.ClassMember.SubroutineDeclaration.MainMethodDeclaration) {
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
            // prevent follow-up errors, because the method is likely not meant to be static
            return
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

    override fun visitLiteralThisExpression(literalThisExpression: SemanticAST.Expression.LiteralExpression.LiteralThisExpression) {
        sourceFile.error {
            "cannot use `this` in static context (main method)" at literalThisExpression.sourceRange
        }
        super.visitLiteralThisExpression(literalThisExpression)
    }

    override fun visitIdentifierExpression(identifierExpression: SemanticAST.Expression.IdentifierExpression) {
        sourceFile.errorIf(identifierExpression.name.symbol.text == argsName) {
            "usage of parameter `$argsName` in main method body not allowed" at identifierExpression.sourceRange
        }
        super.visitIdentifierExpression(identifierExpression)
    }
}
