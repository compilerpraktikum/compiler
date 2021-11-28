package edu.kit.compiler.semantic

import edu.kit.compiler.lex.AnnotatableFile
import edu.kit.compiler.lex.AnnotationType
import edu.kit.compiler.lex.SourceFile
import edu.kit.compiler.lex.SourceNote
import edu.kit.compiler.lex.SourcePosition
import edu.kit.compiler.lex.SourceRange
import edu.kit.compiler.lex.StringTable
import edu.kit.compiler.lex.Symbol
import edu.kit.compiler.lex.extend
import edu.kit.compiler.semantic.visitor.AbstractVisitor
import edu.kit.compiler.semantic.visitor.accept
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

val VariableDefinition.identifier
    get() = when (this.node) {
        is VariableNode.Field -> this.node.node.name
        is VariableNode.LocalVariable -> this.node.node.name
        is VariableNode.Parameter -> this.node.node.name
    }

fun lookupClass(global: GlobalNamespace, sourceFile: AnnotatableFile, classType: SemanticType.Class): ClassDefinition? {
    val def = global.classes.getOrNull(classType.name.symbol)
    if (def == null) {
        sourceFile.annotate(
            AnnotationType.ERROR,
            classType.name.sourceRange,
            "unknown class `${classType.name.text}`"
        )
        return null
    }
    return def
}

/**
 * Handles name lookups within the block of a method. Requires all classes + their fields and methods from first analysis pass.
 *
 * **Note:** All methods also do error handling if their operation fails.
 *
 * @param[clazz] Parent class of the method that lookups are performed within.
 * @param[sourceFile] source file
 * @param[systemSymbol] [Symbol] for the name "System"
 * @param[isStatic] whether the current method is static
 */
class NameResolutionHelper(
    private val clazz: AstNode.ClassDeclaration,
    private val sourceFile: AnnotatableFile,
    private val systemSymbol: Symbol,
    private val isStatic: Boolean,
) {
    private val global
        get() = clazz.namespace.global

    /**
     * [ClassDeclaration][AstNode.ClassDeclaration] of the method's class
     */
    private val thisDefinition = global.classes.getOrNull(clazz.name.symbol)!!

    /**
     * local variables + parameters
     */
    private val local = SymbolTable()

    /**
     * Add parameters to local variable scope.
     * Note: Also handles
     */
    fun registerParameters(parameters: List<AstNode.ClassMember.SubroutineDeclaration.Parameter>) {
        enterScope()
        parameters.forEach {
            addDefinition(it.asDefinition().wrap(), it.name.sourceRange)
        }
    }

    fun unregisterParameters() {
        leaveScope()
    }

    fun enterScope() = local.enterScope()
    fun leaveScope() = local.leaveScope()

    private fun addDefinition(definition: VariableDefinition, identifierSourceRange: SourceRange) {
        val prevDefinition = local.lookup(definition.name)
        if (prevDefinition == null) {
            local.add(definition)
        } else {
            sourceFile.annotate(
                AnnotationType.ERROR,
                identifierSourceRange,
                "local variable with the name '${definition.name.text}' already exists",
                listOf(
                    SourceNote(prevDefinition.identifier.sourceRange, "see previous declaration here")
                )
            )
        }
    }

    /**
     * Try to add definition.
     */
    fun addDefinition(node: AstNode.Statement.LocalVariableDeclaration) {
        addDefinition(node.asDefinition().wrap(), node.name.sourceRange)
    }

    /**
     * Lookup [ClassDefinition] of the given semantic class type.
     */
    fun lookupClass(classType: SemanticType.Class): ClassDefinition? = lookupClass(global, sourceFile, classType)

    /**
     * Get [class definition][ClassDefinition] for the class type given in [inClazz].
     * @return [class definition][ClassDefinition] corresponding to the given class type or `null` if no such class exists
     */
    private fun getClazzByType(inClazz: SemanticType.Class?) = when (inClazz) {
        null -> clazz
        else -> global.classes.getOrNull(inClazz.name.symbol)?.node
    }

    /**
     * Check if lhs of member access (field access or method call) is a valid target for member access (= has class type).
     */
    @OptIn(ExperimentalContracts::class)
    private inline fun ifIsInvalidForMemberAccess(inClazz: SemanticType?, range: SourceRange, operation: String, exit: () -> Nothing) {
        contract {
            returns() implies (inClazz is SemanticType.Class?)
        }

        if (inClazz != null && inClazz !is SemanticType.Class) {
            if (inClazz !is SemanticType.Error) { // suppress error spam
                sourceFile.annotate(
                    AnnotationType.ERROR,
                    range, // TODO the identifier is not really the problem here, so highlighting it is kinda odd
                    "$operation on non-class type `${inClazz.display()}`",
                )
            }
            exit()
        }
    }

    /**
     * Look up a field with the given [name] in the given [class][inClazz].
     */
    fun lookupField(name: AstNode.Identifier, inClazz: SemanticType): FieldDefinition? {
        ifIsInvalidForMemberAccess(inClazz, name.sourceRange, "field access") { return null }

        /* If the definition of [inClazz] cannot be found we can skip any further error handing because the definition
         * of the variable itself already checks that the class definition exists.
         */
        val clazz = getClazzByType(inClazz) ?: return null
        val def = clazz.namespace.fields.getOrNull(name.symbol)
        if (def == null) {
            sourceFile.annotate(
                AnnotationType.ERROR,
                name.sourceRange,
                "unknown field `${clazz.name.text}.${name.symbol.text}`"
            )
            return null
        }
        return def
    }

    /**
     * Look up a method with the given [name] in the given [class][inClazz].
     * @param[inClazz] class in which the method is looked up (defaults to the current class if `null`)
     */
    fun lookupMethod(name: AstNode.Identifier, inClazz: SemanticType? = null): MethodDefinition? {
        ifIsInvalidForMemberAccess(inClazz, name.sourceRange, "method call") { return null }

        if (isStatic && inClazz == null) {
            sourceFile.annotate(
                AnnotationType.ERROR,
                name.sourceRange,
                "cannot call instance method from static method"
            )
            return null
        }

        // see note in lookupField
        val clazz = getClazzByType(inClazz) ?: return null
        val def = clazz.namespace.methods.getOrNull(name.symbol)
        if (def == null) {
            sourceFile.annotate(
                AnnotationType.ERROR,
                name.sourceRange,
                "unknown method `${clazz.name.text}.${name.symbol.text}`" + if (name.symbol.text == "main" && clazz.namespace.hasMainMethod) {
                    " (note: you cannot call the main method of a program)"
                } else ""
            )
            return null
        }
        return def
    }

    /**
     * Look up a variable with the given [name]. The result can be a local variable, method parameter or field of the current class.
     */
    fun lookupVariable(name: AstNode.Identifier): VariableDefinition? {
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
            return null
        } else if (isStatic) {
            sourceFile.annotate(
                AnnotationType.ERROR,
                name.sourceRange,
                "cannot access instance field from static method"
            )
            return null
        }

        return def.wrap()
    }

    /**
     * Get the [definition][ClassDefinition] of the current class.
     */
    fun lookupThis(): ClassDefinition = thisDefinition

    /**
     * Check whether there is any definition (class, field, parameter, local variable) with the name "System" known in the current context.
     */
    fun hasAnyDefinitionForSystem(): Boolean {
        return global.classes.getOrNull("System") != null ||
            clazz.namespace.fields.getOrNull("System") != null ||
            local.contains(systemSymbol)
    }
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
        if (classDeclaration.name.text == "String") {
            sourceFile.annotate(
                AnnotationType.ERROR,
                classDeclaration.name.sourceRange,
                "cannot shadow built-in `String` class"
            )
        } else {
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
        }

        val namespace = ClassNamespace(global)
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
            currentClassNamespace.mainMethodDefinition?.also {
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
        if (currentClassNamespace.mainMethodDefinition != null) {
            printErrorDuplicateMain(mainMethodDeclaration.name.sourceRange, currentClassNamespace.mainMethodDefinition!!.node.name.sourceRange)
            return
        }

        val otherMainDefinition = currentClassNamespace.methods.getOrNull("main")
        if (otherMainDefinition != null) {
            printErrorDuplicateMain(mainMethodDeclaration.name.sourceRange, otherMainDefinition.node.name.sourceRange)
            // continue anyway to prevent misleading error messages (missing main method)
        }

        currentClassNamespace.mainMethodDefinition = mainMethodDeclaration.asDefinition()

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
    private val systemSymbol: Symbol,
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

    private fun handleMethod(definition: AstNode.ClassMember.SubroutineDeclaration, isStatic: Boolean) {
        val namespace = NameResolutionHelper(currentClass, sourceFile, systemSymbol, isStatic)
        namespace.registerParameters(definition.parameters)
        definition.accept(SubroutineNameResolver(namespace, sourceFile))
        namespace.unregisterParameters()
    }

    override fun visitMethodDeclaration(methodDeclaration: AstNode.ClassMember.SubroutineDeclaration.MethodDeclaration) {
        handleMethod(methodDeclaration, false)
    }

    override fun visitMainMethodDeclaration(mainMethodDeclaration: AstNode.ClassMember.SubroutineDeclaration.MainMethodDeclaration) {
        handleMethod(mainMethodDeclaration, true)
    }
}

class SubroutineNameResolver(
    private val namespace: NameResolutionHelper,
    private val sourceFile: AnnotatableFile
) : AbstractVisitor() {
    companion object {
        val SYSTEM_IN_READ = AstNode.Expression.MethodInvocationExpression.Type.Internal("system_read", SemanticType.Integer, emptyList())
        val SYSTEM_OUT_PRINTLN = AstNode.Expression.MethodInvocationExpression.Type.Internal("system_println", SemanticType.Void, listOf(SemanticType.Integer))
        val SYSTEM_OUT_WRITE = AstNode.Expression.MethodInvocationExpression.Type.Internal("system_write", SemanticType.Void, listOf(SemanticType.Integer))
        val SYSTEM_OUT_FLUSH = AstNode.Expression.MethodInvocationExpression.Type.Internal("system_flush", SemanticType.Void, emptyList())
    }

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
        val handled = tryHandleSystemCall(methodInvocationExpression)
        if (handled) {
            return
        }

        super.visitMethodInvocationExpression(methodInvocationExpression)

        methodInvocationExpression.type = namespace.lookupMethod(
            methodInvocationExpression.method, methodInvocationExpression.target?.actualType
        )?.let { AstNode.Expression.MethodInvocationExpression.Type.Normal(it) }
    }

    private fun tryHandleSystemCall(methodInvocationExpression: AstNode.Expression.MethodInvocationExpression): Boolean {
        val fieldAccessExpr = methodInvocationExpression.target
        if (fieldAccessExpr !is AstNode.Expression.FieldAccessExpression) {
            return false
        }

        val identifierExpr = fieldAccessExpr.target
        if (identifierExpr !is AstNode.Expression.IdentifierExpression) {
            return false
        }

        if (identifierExpr.name.text == "System" && !namespace.hasAnyDefinitionForSystem()) {
            val fieldName = fieldAccessExpr.field.text
            val methodName = methodInvocationExpression.method.text
            when (fieldName) {
                "in" -> {
                    when (methodName) {
                        "read" -> methodInvocationExpression.type = SYSTEM_IN_READ
                        else -> {
                            sourceFile.annotate(
                                AnnotationType.ERROR,
                                methodInvocationExpression.sourceRange,
                                "unknown built-in method `System.in.$methodName`"
                            )
                        }
                    }
                }
                "out" -> {
                    when (methodName) {
                        "println" -> methodInvocationExpression.type = SYSTEM_OUT_PRINTLN
                        "write" -> methodInvocationExpression.type = SYSTEM_OUT_WRITE
                        "flush" -> methodInvocationExpression.type = SYSTEM_OUT_FLUSH
                        else -> {
                            sourceFile.annotate(
                                AnnotationType.ERROR,
                                methodInvocationExpression.sourceRange,
                                "unknown built-in method `System.out.$methodName`"
                            )
                        }
                    }
                }
                else -> {
                    sourceFile.annotate(
                        AnnotationType.ERROR,
                        fieldAccessExpr.sourceRange,
                        "unknown built-in field `System.$fieldName`"
                    )
                }
            }

            // we do not need to handle the nested expressions in target, but parameters should still be resolved
            methodInvocationExpression.arguments.forEach { it.accept(this) }

            return true
        }

        return false
    }

    override fun visitClassType(clazz: SemanticType.Class) {
        clazz.definition = namespace.lookupClass(clazz)
    }

    override fun visitLiteralThisExpression(literalThisExpression: AstNode.Expression.LiteralExpression.LiteralThisExpression) {
        literalThisExpression.definition = namespace.lookupThis()
    }

    // not really part of name analysis but given that all the other String checks are in this file it's best to put it here too
    override fun visitNewObjectExpression(newObjectExpression: AstNode.Expression.NewObjectExpression) {
        if (newObjectExpression.clazz.text == "String") {
            sourceFile.annotate(
                AnnotationType.ERROR,
                newObjectExpression.clazz.sourceRange,
                "cannot instantiate built-in class `String`"
            )
        }

        super.visitNewObjectExpression(newObjectExpression)
    }
}

private fun GlobalNamespace.defineBuiltIns(sourceFile: SourceFile, stringTable: StringTable) {
    val dummySourceRange = SourcePosition(sourceFile, 0).extend(0)

    classes.tryPut(
        AstNode.ClassDeclaration(
            AstNode.Identifier(stringTable.tryRegisterIdentifier("String"), dummySourceRange),
            emptyList(),
            dummySourceRange
        ).apply {
            namespace = ClassNamespace(this@defineBuiltIns)
        }.asDefinition(),
        onDuplicate = { check(false) }
    )
}

fun doNameAnalysis(program: AstNode.Program, sourceFile: SourceFile, stringTable: StringTable) {
    val global = GlobalNamespace().apply {
        defineBuiltIns(sourceFile, stringTable)
    }
    program.accept(NamespacePopulator(global, sourceFile))
    program.accept(NameResolver(global, sourceFile, stringTable.tryRegisterIdentifier("System")))
}
