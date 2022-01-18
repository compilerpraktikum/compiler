package edu.kit.compiler.semantic.visitor

import edu.kit.compiler.semantic.SemanticAST
import edu.kit.compiler.source.AnnotationType
import edu.kit.compiler.source.SourceFile
import edu.kit.compiler.source.SourceNote
import edu.kit.compiler.source.extend

/**
 * A visitor that ensures that only one main method exists per compilation unit
 */
class MainMethodCounter(val sourceFile: SourceFile) : AbstractVisitor() {

    private var foundMainMethod: SemanticAST.ClassMember.SubroutineDeclaration.MainMethodDeclaration? = null

    override fun visitProgram(program: SemanticAST.Program) {
        super.visitProgram(program)
        if (foundMainMethod == null) {
            sourceFile.annotate(
                AnnotationType.ERROR,
                program.sourceRange.start.extend(1),
                "missing main method"
            )
        }
    }

    override fun visitMethodDeclaration(methodDeclaration: SemanticAST.ClassMember.SubroutineDeclaration.MethodDeclaration) {
        // don't visit method blocks, that would be waste of time
    }

    override fun visitMainMethodDeclaration(mainMethodDeclaration: SemanticAST.ClassMember.SubroutineDeclaration.MainMethodDeclaration) {
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
                        "see previous main method declaration here"
                    )
                )
            )
        }
    }
}
