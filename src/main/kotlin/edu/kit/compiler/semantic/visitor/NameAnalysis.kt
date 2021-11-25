package edu.kit.compiler.semantic

import edu.kit.compiler.lex.AnnotatableFile
import edu.kit.compiler.lex.AnnotationType
import edu.kit.compiler.lex.SourceFile
import edu.kit.compiler.lex.SourceNote
import edu.kit.compiler.lex.Symbol
import edu.kit.compiler.semantic.visitor.AbstractVisitor
import edu.kit.compiler.semantic.visitor.accept

val VariableDefinition.identifier
    get() = when (this.node) {
        is VariableNode.Field -> this.node.node.name
        is VariableNode.LocalVariable -> this.node.node.name
        is VariableNode.Parameter -> this.node.node.name
    }

/**
 * Handles name lookups within the block of a method. Requires all classes + their fields and methods from first analysis pass.
 */
class LocalMethodNamespace(
    private val clazz: AstNode.ClassDeclaration,
    parameters: List<AstNode.ClassMember.SubroutineDeclaration.Parameter>,
    private val sourceFile: AnnotatableFile,
) {
    private val global
        get() = clazz.namespace.global

    private val paramsByName = parameters.associateBy { it.name.symbol }

    private val local = SymbolTable()

    fun enterScope() = local.enterScope()
    fun leaveScope() = local.leaveScope()

    fun addDefinition(node: AstNode.Statement.LocalVariableDeclaration) {
        val name: Symbol = node.name.symbol
        val prevDefinition = local.lookup(name)
        if (prevDefinition == null) {
            local.add(Definition(name, node))
        } else {
            sourceFile.annotate(
                AnnotationType.ERROR,
                node.name.sourceRange,
                "local variable with the name '${name.text}' already exists",
                listOf(
                    SourceNote(prevDefinition.identifier.sourceRange, "see previous declaration here")
                )
            )
        }
    }

    // TODO error handling (-> sourceFile.annotate(...)) should probably happen in these methods
    fun lookupClass(classType: SemanticType.Class): ClassDefinition {
        val def = global.classes.getOrNull(classType.name.symbol)
        if (def == null) {
            sourceFile.annotate(
                AnnotationType.ERROR,
                classType.name.sourceRange,
                "unknown class `${classType.name.text}`"
            )
            TODO()
        }
        return def
    }

    private fun getClazzByType(inClazz: SemanticType.Class?) =
        inClazz?.let { global.classes.getOrNull(it.name.symbol)!!.node } ?: clazz

    fun lookupField(name: AstNode.Identifier, inClazz: SemanticType? = null): FieldDefinition {
        if (inClazz !is SemanticType.Class) {
            sourceFile.annotate(
                AnnotationType.ERROR,
                name.sourceRange,
                "field access on non-class type"
            )
            TODO()
        }

        val clazz = getClazzByType(inClazz)
        val def = clazz.namespace.fields.getOrNull(name.symbol)
        if (def == null) {
            sourceFile.annotate(
                AnnotationType.ERROR,
                name.sourceRange,
                "unknown field `${clazz.name.text}.${name.symbol.text}`"
            )
            TODO()
        }
        return def
    }
    fun lookupMethod(name: AstNode.Identifier, inClazz: SemanticType? = null): MethodDefinition {
        if (inClazz != null && inClazz !is SemanticType.Class) {
            sourceFile.annotate(
                AnnotationType.ERROR,
                name.sourceRange,
                "field access on non-class type"
            )
            TODO()
        }

        val clazz = getClazzByType(inClazz as SemanticType.Class?)
        val def = clazz.namespace.methods.getOrNull(name.symbol)
        if (def == null) {
            sourceFile.annotate(
                AnnotationType.ERROR,
                name.sourceRange,
                "unknown method `${clazz.name.text}.${name.symbol.text}`"
            )
            TODO()
        }
        return def
    }
    fun lookupVariable(name: AstNode.Identifier): Definition<VariableNode> {
        local.lookup(name.symbol)?.let {
            return it
        }

        val def = clazz.namespace.fields.getOrNull(name.symbol)
        if (def == null) {
            sourceFile.annotate(
                AnnotationType.ERROR,
                name.sourceRange,
                "unknown variable `${name.symbol.text}`"
            )
            TODO()
        }
        return def.wrap()
    }
}

/**
 * First name analysis pass: Populate global namespace with all classes and populate class namespaces with contained fields and methods.
 */
@Suppress("SpellCheckingInspection")
class NamespacePopulator(
    private val global: GlobalNamespace,
    private val sourceFile: SourceFile,
) : AbstractVisitor() {

    private lateinit var currentClassNamespace: ClassNamespace

    override fun visitClassDeclaration(classDeclaration: AstNode.ClassDeclaration) {
        val namespace = ClassNamespace(global)

        global.classes.tryPut(classDeclaration.asDefinition(), onDuplicate = {
            sourceFile.annotate(
                AnnotationType.ERROR,
                classDeclaration.name.sourceRange,
                "class `${classDeclaration.name.text}` is already defined",
                listOf(
                    SourceNote(
                        it.node.name.sourceRange,
                        "see previous definition here"
                    )
                )
            )
        })

        classDeclaration.namespace = namespace
        currentClassNamespace = namespace

        super.visitClassDeclaration(classDeclaration) // descend
    }

    override fun visitFieldDeclaration(fieldDeclaration: AstNode.ClassMember.FieldDeclaration) {
        currentClassNamespace.fields.tryPut(fieldDeclaration.asDefinition(), onDuplicate = { prev ->
            sourceFile.annotate(
                AnnotationType.ERROR,
                fieldDeclaration.name.sourceRange,
                "field `${fieldDeclaration.name.text}` is already defined",
                listOf(
                    SourceNote(
                        prev.node.name.sourceRange,
                        "see previous definition here"
                    )
                )
            )
        })
        // do not descend
    }

    override fun visitMethodDeclaration(methodDeclaration: AstNode.ClassMember.SubroutineDeclaration.MethodDeclaration) {
        currentClassNamespace.methods.tryPut(methodDeclaration.asDefinition(), onDuplicate = { prev ->
            sourceFile.annotate(
                AnnotationType.ERROR,
                methodDeclaration.name.sourceRange,
                "field `${methodDeclaration.name.text}` is already defined",
                listOf(
                    SourceNote(
                        prev.node.name.sourceRange,
                        "see previous definition here"
                    )
                )
            )
        })
        // do not descend
    }
}

/**
 * Second name analysis pass:
 */
class SubroutineNameResolver(
    private val global: GlobalNamespace,
    private val sourceFile: SourceFile,
) : AbstractVisitor() {
    lateinit var currentClass: AstNode.ClassDeclaration
    lateinit var currentMethodNamespace: LocalMethodNamespace

    override fun visitClassDeclaration(classDeclaration: AstNode.ClassDeclaration) {
        currentClass = classDeclaration

        super.visitClassDeclaration(classDeclaration)
    }

    override fun visitMethodDeclaration(methodDeclaration: AstNode.ClassMember.SubroutineDeclaration.MethodDeclaration) {
        currentMethodNamespace = LocalMethodNamespace(currentClass, methodDeclaration.parameters, sourceFile)
        super.visitMethodDeclaration(methodDeclaration)
    }

    override fun visitMainMethodDeclaration(mainMethodDeclaration: AstNode.ClassMember.SubroutineDeclaration.MainMethodDeclaration) {
        currentMethodNamespace = LocalMethodNamespace(currentClass, mainMethodDeclaration.parameters, sourceFile)
        super.visitMainMethodDeclaration(mainMethodDeclaration)
    }

    override fun visitBlock(block: AstNode.Statement.Block) {
        currentMethodNamespace.enterScope()
        super.visitBlock(block)
        currentMethodNamespace.leaveScope()
    }

    override fun visitLocalVariableDeclaration(localVariableDeclaration: AstNode.Statement.LocalVariableDeclaration) {
        currentMethodNamespace.addDefinition(localVariableDeclaration)
        super.visitLocalVariableDeclaration(localVariableDeclaration)
    }

    override fun visitIdentifierExpression(identifierExpression: AstNode.Expression.IdentifierExpression) {
        identifierExpression.definition = currentMethodNamespace.lookupVariable(
            identifierExpression.name
        )
    }

    override fun visitFieldAccessExpression(fieldAccessExpression: AstNode.Expression.FieldAccessExpression) {
        super.visitFieldAccessExpression(fieldAccessExpression)

        fieldAccessExpression.definition = currentMethodNamespace.lookupField(
            fieldAccessExpression.field, fieldAccessExpression.target.actualType
        )
    }

    override fun visitMethodInvocationExpression(methodInvocationExpression: AstNode.Expression.MethodInvocationExpression) {
        super.visitMethodInvocationExpression(methodInvocationExpression)

        methodInvocationExpression.definition = currentMethodNamespace.lookupMethod(
            methodInvocationExpression.method, methodInvocationExpression.target?.actualType
        )
    }

    override fun visitComplexType(clazz: SemanticType.Class) {
        clazz.definition = currentMethodNamespace.lookupClass(clazz)
    }
}

fun doNameAnalysis(program: AstNode.Program, sourceFile: SourceFile) {
    val global = GlobalNamespace()
    program.accept(NamespacePopulator(global, sourceFile))
    program.accept(SubroutineNameResolver(global, sourceFile))
}
