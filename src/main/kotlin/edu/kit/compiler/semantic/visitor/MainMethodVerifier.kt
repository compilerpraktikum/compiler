package edu.kit.compiler.semantic.visitor

import edu.kit.compiler.lex.AnnotationType
import edu.kit.compiler.lex.SourceFile
import edu.kit.compiler.lex.SourceNote
import edu.kit.compiler.lex.SourceRange
import edu.kit.compiler.semantic.AstNode
import edu.kit.compiler.semantic.ParsedType

/**
 * A visitor that ensures that all special semantic constraints of the main method are fulfilled.
 */
class MainMethodVerifier(val sourceFile: SourceFile) : AbstractVisitor() {

    override fun visitMethodDeclaration(methodDeclaration: AstNode.ClassMember.SubroutineDeclaration.MethodDeclaration) {
        // don't visit method blocks, that would be waste of time
    }

    override fun visitMainMethodDeclaration(mainMethodDeclaration: AstNode.ClassMember.SubroutineDeclaration.MainMethodDeclaration) {
        if (mainMethodDeclaration.parsedReturnType !is ParsedType.VoidType) {
            if (mainMethodDeclaration.name.symbol.text == "main")
                sourceFile.annotate(
                    AnnotationType.ERROR,
                    mainMethodDeclaration.name.sourceRange,
                    "the main method must return `void`",
                    listOf(
                        SourceNote(mainMethodDeclaration.name.sourceRange, "change the return type to `void`", "hint")
                    )
                )
            else
                sourceFile.annotate(
                    AnnotationType.ERROR,
                    mainMethodDeclaration.name.sourceRange,
                    "only the main method is allowed to be `static`",
                    listOf(
                        SourceNote(mainMethodDeclaration.name.sourceRange, "remove the `static` modifier", "hint")
                    )
                )
        } else {
            if (mainMethodDeclaration.name.symbol.text != "main") {
                sourceFile.annotate(
                    AnnotationType.ERROR,
                    mainMethodDeclaration.name.sourceRange,
                    "only the `main` method is allowed to be static",
                    listOf(
                        SourceNote(mainMethodDeclaration.name.sourceRange, "remove the `static` modifier", "hint")
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
        } else if (mainMethodDeclaration.parameters[0].type !is ParsedType.ArrayType ||
            (mainMethodDeclaration.parameters[0].type as ParsedType.ArrayType).elementType !is ParsedType.ComplexType ||
            ((mainMethodDeclaration.parameters[0].type as ParsedType.ArrayType).elementType as ParsedType.ComplexType)
                .name.symbol.text != "String"
        ) {
            sourceFile.annotate(
                AnnotationType.ERROR,
                mainMethodDeclaration.parameters[0].sourceRange,
                "the parameter of the `main` method must have type `String[]`",
            )
        }
    }
}
