package edu.kit.compiler.semantic.visitor

import edu.kit.compiler.lex.AnnotationType
import edu.kit.compiler.lex.SourceFile
import edu.kit.compiler.lex.SourceNote
import edu.kit.compiler.lex.SourceRange
import edu.kit.compiler.semantic.AstNode

/**
 * A visitor that ensures that only one main method exists per compilation unit
 */
class MainMethodCounter(val sourceFile: SourceFile) : AbstractVisitor() {

    private var foundMainMethod: AstNode.ClassMember.SubroutineDeclaration.MainMethodDeclaration? = null

    override fun visitProgram(program: AstNode.Program) {
        super.visitProgram(program)
        if (foundMainMethod == null) {
            sourceFile.annotate(
                AnnotationType.ERROR,
                SourceRange(program.sourceRange.start, 1),
                "missing main method"
            )
        }
    }

    override fun visitMethodDeclaration(methodDeclaration: AstNode.ClassMember.SubroutineDeclaration.MethodDeclaration) {
        // don't visit method blocks, that would be waste of time
    }

    override fun visitMainMethodDeclaration(mainMethodDeclaration: AstNode.ClassMember.SubroutineDeclaration.MainMethodDeclaration) {
        if (foundMainMethod == null) {
            foundMainMethod = mainMethodDeclaration
        } else {
            sourceFile.annotate(
                AnnotationType.ERROR,
                mainMethodDeclaration.name.sourceRange,
                "only one main method is allowed per program",
                listOf(
                    SourceNote(
                        foundMainMethod!!.name.sourceRange,
                        "a main method is already defined here"
                    )
                )
            )
        }
    }
}
