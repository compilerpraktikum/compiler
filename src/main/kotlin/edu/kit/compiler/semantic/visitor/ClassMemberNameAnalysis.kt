package edu.kit.compiler.semantic.visitor

import edu.kit.compiler.lex.AnnotationType
import edu.kit.compiler.lex.SourceFile
import edu.kit.compiler.lex.SourceNote
import edu.kit.compiler.lex.Symbol
import edu.kit.compiler.semantic.AstNode
import edu.kit.compiler.semantic.Namespace

/**
 * First stage of the name analysis that collects names of all classes and their members.
 *
 * @param sourceFile The input stream to be annotated with error messages
 */
class ClassMemberNameAnalysis(private val sourceFile: SourceFile) : AbstractVisitor() {

    override fun visitClassDeclaration(classDeclaration: AstNode.ClassDeclaration) {
        val classNamespace = Namespace.ClassNamespace()
        val memberAnalysis = MemberAnalysis(classDeclaration.name, sourceFile, classNamespace)
        classDeclaration.members.forEach { it.accept(memberAnalysis) }

        Namespace.GlobalNamespace.classDefinitions.putIfAbsent(classDeclaration.name, classDeclaration)?.onError {
            sourceFile.annotate(
                AnnotationType.ERROR,
                classDeclaration.sourceRange, // todo: class name token
                "type `${classDeclaration.name}` is already defined",
                listOf(
                    SourceNote(
                        Namespace.GlobalNamespace.classDefinitions[classDeclaration.name]!!.sourceRange, // todo name token
                        "class already defined here"
                    )
                )
            )
        }

        classDeclaration.classNamespace = classNamespace
    }
}

/**
 * Visitor that analyzes one class and adds its members to the given [classNamespace]. None of the overridden methods
 * calls the visitor for subsequent AST nodes, to avoid unnecessary visits.
 *
 * @param surroundingClass the surrounding class's name
 * @param sourceFile input stream to annotate with error messages
 * @param classNamespace surrounding scope
 */
private class MemberAnalysis(
    private val surroundingClass: Symbol,
    private val sourceFile: SourceFile,
    private val classNamespace: Namespace.ClassNamespace
) : AbstractVisitor() {

    override fun visitFieldDeclaration(fieldDeclaration: AstNode.ClassMember.FieldDeclaration) {
        classNamespace.fieldDefinitions.putIfAbsent(fieldDeclaration.name, fieldDeclaration)?.onError {
            sourceFile.annotate(
                AnnotationType.ERROR,
                fieldDeclaration.sourceRange, // todo: field name token
                "field `${fieldDeclaration.name}` is already defined in `${surroundingClass.text}`",
                listOf(
                    SourceNote(
                        classNamespace.fieldDefinitions[fieldDeclaration.name]!!.sourceRange, // todo field name token
                        "field already defined here"
                    )
                )
            )
        }
    }

    override fun visitMethodDeclaration(methodDeclaration: AstNode.ClassMember.SubroutineDeclaration.MethodDeclaration) {
        classNamespace.methodDefinitions.putIfAbsent(methodDeclaration.name, methodDeclaration)?.onError {
            sourceFile.annotate(
                AnnotationType.ERROR,
                methodDeclaration.sourceRange, // todo: method name token
                "field `${methodDeclaration.name}` is already defined in `${surroundingClass.text}`",
                listOf(
                    SourceNote(
                        classNamespace.methodDefinitions[methodDeclaration.name]!!.sourceRange, // todo method name token
                        "method already defined here"
                    )
                )
            )
        }
    }

    override fun visitMainMethodDeclaration(mainMethodDeclaration: AstNode.ClassMember.SubroutineDeclaration.MainMethodDeclaration) {
        // the main method must not be referenced, however we can still add it, so we can give better error messages if it is
        classNamespace.methodDefinitions.putIfAbsent(mainMethodDeclaration.name, mainMethodDeclaration)?.onError {
            sourceFile.annotate(
                AnnotationType.ERROR,
                mainMethodDeclaration.sourceRange, // todo: method name token
                "field `${mainMethodDeclaration.name}` is already defined in `${surroundingClass.text}`",
                listOf(
                    SourceNote(
                        classNamespace.methodDefinitions[mainMethodDeclaration.name]!!.sourceRange, // todo method name token
                        "method already defined here"
                    )
                )
            )
        }
    }
}
