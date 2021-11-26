package edu.kit.compiler.semantic

import edu.kit.compiler.lex.AnnotatableFile
import edu.kit.compiler.lex.AnnotationType
import edu.kit.compiler.lex.SourceFile
import edu.kit.compiler.lex.SourceNote
import edu.kit.compiler.lex.SourceRange
import edu.kit.compiler.lex.Symbol
import edu.kit.compiler.semantic.visitor.AbstractVisitor
import edu.kit.compiler.semantic.visitor.accept

val VariableDefinition.identifier
    get() = when (this.node) {
        is VariableNode.Field -> this.node.node.name
        is VariableNode.LocalVariable -> this.node.node.name
        is VariableNode.Parameter -> this.node.node.name
    }

fun lookupClass(global: GlobalNamespace, sourceFile: AnnotatableFile, classType: SemanticType.Class): Definition<AstNode.ClassDeclaration> {
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

/**
 * Handles name lookups within the block of a method. Requires all classes + their fields and methods from first analysis pass.
 */
class NameResolutionHelper(
    private val clazz: AstNode.ClassDeclaration,
    private val sourceFile: AnnotatableFile,
) {
    private val global
        get() = clazz.namespace.global

    private val thisDefinition = global.classes.getOrNull(clazz.name.symbol)!!

    private val local = SymbolTable()

    fun registerParameters(parameters: List<AstNode.ClassMember.SubroutineDeclaration.Parameter>) {
        enterScope()
        parameters.forEach {
            local.add(it.asDefinition().wrap())
        }
    }

    fun unregisterParameters() {
        leaveScope()
    }

    fun enterScope() = local.enterScope()
    fun leaveScope() = local.leaveScope()

    fun addDefinition(node: AstNode.Statement.LocalVariableDeclaration) {
        val name: Symbol = node.name.symbol
        val prevDefinition = local.lookup(name)
        if (prevDefinition == null) {
            local.add(node.asDefinition().wrap())
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

    fun lookupClass(classType: SemanticType.Class): ClassDefinition = lookupClass(global, sourceFile, classType)

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
                "unknown method `${clazz.name.text}.${name.symbol.text}`" + if (name.symbol.text == "main" && clazz.namespace.hasMainMethod) {
                    " (note: you cannot call the main method of a program)"
                } else ""
            )
            TODO()
        }
        return def
    }

    fun lookupVariable(name: AstNode.Identifier): VariableDefinition {
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

    fun lookupThis(): ClassDefinition = thisDefinition
}

/**
 * First name analysis pass: Populate global namespace with all classes and populate class namespaces with contained fields and methods.
 */
@Suppress("SpellCheckingInspection")
class NamespacePopulator(
    private val global: GlobalNamespace,
    private val sourceFile: AnnotatableFile,
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
                    SourceNote(it.node.name.sourceRange, "see previous definition here")
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
                    SourceNote(prev.node.name.sourceRange, "see previous definition here")
                )
            )
        })

        // do not descend
    }

    override fun visitMethodDeclaration(methodDeclaration: AstNode.ClassMember.SubroutineDeclaration.MethodDeclaration) {
        if (methodDeclaration.name.text == "main") {
            currentClassNamespace.mainMethodDefinition?.let {
                printErrorDuplicateMain(methodDeclaration.name.sourceRange, it.node.sourceRange)
                // continue anyway to prevent misleading error messages when calling main ("unknown method" even though 2 methods with the name exist)
            }
        }

        currentClassNamespace.methods.tryPut(methodDeclaration.asDefinition(), onDuplicate = { prev ->
            sourceFile.annotate(
                AnnotationType.ERROR,
                methodDeclaration.name.sourceRange,
                "method `${methodDeclaration.name.text}` is already defined",
                listOf(
                    SourceNote(prev.node.name.sourceRange, "see previous definition here")
                )
            )
        })

        // do not descend
    }

    override fun visitMainMethodDeclaration(mainMethodDeclaration: AstNode.ClassMember.SubroutineDeclaration.MainMethodDeclaration) {
        currentClassNamespace.mainMethodDefinition = mainMethodDeclaration.asDefinition()

        val otherMainDefinition = currentClassNamespace.methods.getOrNull("main")
        if (otherMainDefinition != null) {
            printErrorDuplicateMain(mainMethodDeclaration.sourceRange, otherMainDefinition.node.name.sourceRange)
        }

        // do not descend
    }

    private fun printErrorDuplicateMain(current: SourceRange, previous: SourceRange) {
        sourceFile.annotate(
            AnnotationType.ERROR,
            current,
            "method `main` is already defined",
            listOf(
                SourceNote(previous, "see previous definition here")
            )
        )
    }
}

/**
 * Second name analysis pass:
 */
class NameResolver(
    private val global: GlobalNamespace,
    private val sourceFile: AnnotatableFile,
) : AbstractVisitor() {

    lateinit var currentClass: AstNode.ClassDeclaration

    override fun visitClassDeclaration(classDeclaration: AstNode.ClassDeclaration) {
        currentClass = classDeclaration

        super.visitClassDeclaration(classDeclaration)
    }

    override fun visitFieldDeclaration(fieldDeclaration: AstNode.ClassMember.FieldDeclaration) {
        val type = fieldDeclaration.type
        if (type is SemanticType.Class) {
            type.definition = lookupClass(global, sourceFile, type)
        }
    }

    private inline fun <reified T : AstNode.ClassMember.SubroutineDeclaration> handleMethod(definition: T) {
        val namespace = NameResolutionHelper(currentClass, sourceFile)
        namespace.registerParameters(definition.parameters)
        definition.accept(SubroutineNameResolver(namespace))
        namespace.unregisterParameters()
    }

    override fun visitMethodDeclaration(methodDeclaration: AstNode.ClassMember.SubroutineDeclaration.MethodDeclaration) {
        handleMethod(methodDeclaration)
    }

    override fun visitMainMethodDeclaration(mainMethodDeclaration: AstNode.ClassMember.SubroutineDeclaration.MainMethodDeclaration) {
        handleMethod(mainMethodDeclaration)
    }
}

class SubroutineNameResolver(
    private val namespace: NameResolutionHelper,
) : AbstractVisitor() {

    override fun visitBlock(block: AstNode.Statement.Block) {
        namespace.enterScope()
        super.visitBlock(block)
        namespace.leaveScope()
    }

    override fun visitLocalVariableDeclaration(localVariableDeclaration: AstNode.Statement.LocalVariableDeclaration) {
        namespace.addDefinition(localVariableDeclaration)
        super.visitLocalVariableDeclaration(localVariableDeclaration)
    }

    override fun visitIdentifierExpression(identifierExpression: AstNode.Expression.IdentifierExpression) {
        identifierExpression.definition = namespace.lookupVariable(
            identifierExpression.name
        )
    }

    override fun visitFieldAccessExpression(fieldAccessExpression: AstNode.Expression.FieldAccessExpression) {
        super.visitFieldAccessExpression(fieldAccessExpression)

        fieldAccessExpression.definition = namespace.lookupField(
            fieldAccessExpression.field, fieldAccessExpression.target.actualType
        )
    }

    override fun visitMethodInvocationExpression(methodInvocationExpression: AstNode.Expression.MethodInvocationExpression) {
        super.visitMethodInvocationExpression(methodInvocationExpression)

        methodInvocationExpression.definition = namespace.lookupMethod(
            methodInvocationExpression.method, methodInvocationExpression.target?.actualType
        )
    }

    override fun visitClassType(clazz: SemanticType.Class) {
        clazz.definition = namespace.lookupClass(clazz)
    }

    override fun visitLiteralThisExpression(literalThisExpression: AstNode.Expression.LiteralExpression.LiteralThisExpression) {
        literalThisExpression.definition = namespace.lookupThis()
    }
}

fun doNameAnalysis(program: AstNode.Program, sourceFile: SourceFile) {
    val global = GlobalNamespace()
    program.accept(NamespacePopulator(global, sourceFile))
    program.accept(NameResolver(global, sourceFile))
}
