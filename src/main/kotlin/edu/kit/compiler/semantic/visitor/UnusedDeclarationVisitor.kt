package edu.kit.compiler.semantic.visitor

import edu.kit.compiler.semantic.SemanticAST
import edu.kit.compiler.semantic.SemanticType
import edu.kit.compiler.semantic.VariableNode
import edu.kit.compiler.source.SourceFile

/**
 * Annotate [sourceFile] with warnings if declarations (class, method, field, parameter, local variable) are unused.
 *
 * Note: Requires name analysis do be completed.
 */
class UnusedDeclarationVisitor(val sourceFile: SourceFile) : AbstractVisitor() {
    // mapping: declaration -> is used
    private val classDeclarations = mutableMapOf<SemanticAST.ClassDeclaration, Boolean>()
    private val methodDeclarations = mutableMapOf<SemanticAST.ClassMember.SubroutineDeclaration.MethodDeclaration, Boolean>()
    private val fieldDeclarations = mutableMapOf<SemanticAST.ClassMember.FieldDeclaration, Boolean>()
    private val parameterDeclarations = mutableMapOf<SemanticAST.ClassMember.SubroutineDeclaration.Parameter, Boolean>()
    private val localVariableDeclarations = mutableMapOf<SemanticAST.Statement.LocalVariableDeclaration, Boolean>()

    private lateinit var currentClassDeclaration: SemanticAST.ClassDeclaration

    // A program without main method is invalid, but semantic checks are not aborted, so this might be `null`.
    private var mainClassDeclaration: SemanticAST.ClassDeclaration? = null

    override fun visitProgram(program: SemanticAST.Program) {
        super.visitProgram(program)
        annotate()
    }

    override fun visitClassDeclaration(classDeclaration: SemanticAST.ClassDeclaration) {
        classDeclarations.computeIfAbsent(classDeclaration) { false }
        currentClassDeclaration = classDeclaration
        super.visitClassDeclaration(classDeclaration)
    }

    override fun visitMainMethodDeclaration(mainMethodDeclaration: SemanticAST.ClassMember.SubroutineDeclaration.MainMethodDeclaration) {
        // main method is never unused, so do not add to methodDeclarations
        mainClassDeclaration = currentClassDeclaration
        // do not descend into parameters (because they must be unused) and return type (because it must be void)
        mainMethodDeclaration.block.accept(this)
    }

    override fun visitMethodDeclaration(methodDeclaration: SemanticAST.ClassMember.SubroutineDeclaration.MethodDeclaration) {
        methodDeclarations.computeIfAbsent(methodDeclaration) { false }
        super.visitMethodDeclaration(methodDeclaration)
    }

    override fun visitParameter(parameter: SemanticAST.ClassMember.SubroutineDeclaration.Parameter) {
        parameterDeclarations.computeIfAbsent(parameter) { false }
        super.visitParameter(parameter)
    }

    override fun visitFieldDeclaration(fieldDeclaration: SemanticAST.ClassMember.FieldDeclaration) {
        fieldDeclarations.computeIfAbsent(fieldDeclaration) { false }
        super.visitFieldDeclaration(fieldDeclaration)
    }

    override fun visitLocalVariableDeclaration(localVariableDeclaration: SemanticAST.Statement.LocalVariableDeclaration) {
        localVariableDeclarations.computeIfAbsent(localVariableDeclaration) { false }
        super.visitLocalVariableDeclaration(localVariableDeclaration)
    }

    override fun visitClassType(clazz: SemanticType.Class) {
        clazz.definition?.also {
            // references to a class from within that class, do not change whether the class is unused
            if (it.node != currentClassDeclaration) {
                classDeclarations[it.node] = true
            }
        }
        super.visitClassType(clazz)
    }

    override fun visitMethodInvocationExpression(methodInvocationExpression: SemanticAST.Expression.MethodInvocationExpression) {
        when (val type = methodInvocationExpression.type) {
            is SemanticAST.Expression.MethodInvocationExpression.Type.Internal -> {
                // do not descend into `target`
                methodInvocationExpression.arguments.forEach { it.accept(this) }
                return
            }
            is SemanticAST.Expression.MethodInvocationExpression.Type.Normal -> {
                methodDeclarations[type.definition.node] = true
            }
            null -> {}
        }
        super.visitMethodInvocationExpression(methodInvocationExpression)
    }

    override fun visitFieldAccessExpression(fieldAccessExpression: SemanticAST.Expression.FieldAccessExpression) {
        fieldAccessExpression.definition?.also {
            fieldDeclarations[it.node] = true
        }
        super.visitFieldAccessExpression(fieldAccessExpression)
    }

    override fun visitIdentifierExpression(identifierExpression: SemanticAST.Expression.IdentifierExpression) {
        identifierExpression.definition?.also {
            when (it.node) {
                is VariableNode.Field -> {
                    fieldDeclarations[it.node.node] = true
                }
                is VariableNode.Parameter -> {
                    parameterDeclarations[it.node.node] = true
                }
                is VariableNode.LocalVariable -> {
                    localVariableDeclarations[it.node.node] = true
                }
            }
        }
        super.visitIdentifierExpression(identifierExpression)
    }

    private fun annotate() {
        classDeclarations.forEach { (decl, used) ->
            sourceFile.warningIf(!used && decl != mainClassDeclaration) {
                "class ${decl.display()} is never used" at decl.name.sourceRange
            }
        }

        // only warn for methods / fields if the class itself is used (-> otherwise they are inherently unused)
        // note: the main class is never unused
        methodDeclarations.forEach { (decl, used) ->
            sourceFile.warningIf(!used && (classDeclarations[decl.owner]!! || decl.owner == mainClassDeclaration)) {
                "method ${decl.display()} is never used" at decl.name.sourceRange
            }
        }

        fieldDeclarations.forEach { (decl, used) ->
            sourceFile.warningIf(!used && (classDeclarations[decl.owner]!! || decl.owner == mainClassDeclaration)) {
                "field ${decl.display()} is never used" at decl.name.sourceRange
            }
        }

        parameterDeclarations.forEach { (decl, used) ->
            sourceFile.warningIfNot(used) {
                "parameter ${decl.display()} is never used" at decl.name.sourceRange
            }
        }

        localVariableDeclarations.forEach { (decl, used) ->
            sourceFile.warningIfNot(used) {
                "variable ${decl.display()} is never used" at decl.name.sourceRange
            }
        }
    }
}
