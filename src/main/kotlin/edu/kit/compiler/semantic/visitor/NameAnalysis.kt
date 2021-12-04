package edu.kit.compiler.semantic

import edu.kit.compiler.lex.AnnotatableFile
import edu.kit.compiler.lex.SourceFile
import edu.kit.compiler.lex.SourceRange
import edu.kit.compiler.lex.StringTable
import edu.kit.compiler.lex.Symbol
import edu.kit.compiler.semantic.visitor.AbstractVisitor
import edu.kit.compiler.semantic.visitor.accept
import edu.kit.compiler.semantic.visitor.at
import edu.kit.compiler.semantic.visitor.error
import edu.kit.compiler.semantic.visitor.errorIf
import edu.kit.compiler.semantic.visitor.note
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
        sourceFile.error {
            "unknown class `${classType.name.text}`" at classType.name.sourceRange
        }
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
            sourceFile.error {
                "local variable with the name `${definition.name.text}` already exists" at identifierSourceRange note (
                    "see previous declaration here" at prevDefinition.identifier.sourceRange
                    )
            }
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
     * Get [class definition][ClassDefinition] for the class type given in [inClass].
     * @return [class definition][ClassDefinition] corresponding to the given class type or `null` if no such class exists
     */
    private fun getClassByType(inClass: SemanticType.Class?) = when (inClass) {
        null -> clazz
        else -> global.classes.getOrNull(inClass.name.symbol)?.node
    }

    /**
     * Check if lhs of member access (field access or method call) is a valid target for member access (= has class type).
     */
    @OptIn(ExperimentalContracts::class)
    private inline fun ifIsInvalidForMemberAccess(inClass: SemanticType?, range: SourceRange, operation: String, exit: () -> Nothing) {
        contract {
            returns() implies (inClass is SemanticType.Class?)
        }

        if (inClass != null && inClass !is SemanticType.Class) {
            if (inClass !is SemanticType.Error) { // suppress error spam
                sourceFile.error {
                    "$operation on non-class type ${inClass.display()}" at range // TODO the identifier is not really the problem here, so highlighting it is kinda odd
                }
            }
            exit()
        }
    }

    /**
     * Look up a field with the given [name] in the given [class][inClass].
     */
    fun lookupField(name: AstNode.Identifier, inClass: SemanticType): FieldDefinition? {
        ifIsInvalidForMemberAccess(inClass, name.sourceRange, "field access") { return null }

        /* If the definition of [inClass] cannot be found we can skip any further error handing because the definition
         * of the variable itself already checks that the class definition exists.
         */
        val clazz = getClassByType(inClass) ?: return null
        val def = clazz.namespace.fields.getOrNull(name.symbol)
        if (def == null) {
            sourceFile.error {
                "unknown field `${clazz.name.text}.${name.symbol.text}`" at name.sourceRange
            }
            return null
        }
        return def
    }

    /**
     * Look up a method with the given [name] in the given [class][inClass].
     * @param[inClass] class in which the method is looked up (defaults to the current class if `null`)
     */
    fun lookupMethod(name: AstNode.Identifier, inClass: SemanticType? = null): MethodDefinition? {
        ifIsInvalidForMemberAccess(inClass, name.sourceRange, "method call") { return null }

        // see note in lookupField
        val clazz = getClassByType(inClass) ?: return null
        val def = clazz.namespace.methods.getOrNull(name.symbol)
        if (def == null) {
            sourceFile.error {
                val mainMethodNote = if (name.symbol.text == "main" && clazz.namespace.hasMainMethod) {
                    " (note: you cannot call the main method of a program)"
                } else ""
                ("unknown method `${clazz.name.text}.${name.symbol.text}`$mainMethodNote") at name.sourceRange
            }
            return null
        }

        if (isStatic && inClass == null) {
            sourceFile.error {
                "cannot call instance method from static method" at name.sourceRange
            }
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
            sourceFile.error {
                "unknown variable `${name.symbol.text}`" at name.sourceRange
            }
            return null
        } else if (isStatic) {
            sourceFile.error {
                "cannot access instance field from static method" at name.sourceRange
            }
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

    private lateinit var currentClass: AstNode.ClassDeclaration
    private val currentClassNamespace: ClassNamespace
        get() = currentClass.namespace

    override fun visitClassDeclaration(classDeclaration: AstNode.ClassDeclaration) {
        if (classDeclaration.name.text == "String") {
            sourceFile.error {
                "cannot shadow built-in class `String`" at classDeclaration.name.sourceRange
            }
        } else {
            global.classes.tryPut(classDeclaration.asDefinition(), onDuplicate = {
                sourceFile.error {
                    "class `${classDeclaration.name.text}` is already defined" at classDeclaration.name.sourceRange note (
                        "see previous definition here" at it.node.name.sourceRange
                        )
                }
            })
        }

        val namespace = ClassNamespace(global)
        classDeclaration.namespace = namespace
        currentClass = classDeclaration

        super.visitClassDeclaration(classDeclaration) // descend
    }

    override fun visitFieldDeclaration(fieldDeclaration: AstNode.ClassMember.FieldDeclaration) {
        currentClassNamespace.fields.tryPut(fieldDeclaration.asDefinition(), onDuplicate = { prev ->
            sourceFile.error {
                "field `${currentClass.name.text}.${fieldDeclaration.name.text}` is already defined" at fieldDeclaration.name.sourceRange note (
                    "see previous definition here" at prev.node.name.sourceRange
                    )
            }
        })

        fieldDeclaration.owner = currentClass

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
            sourceFile.error {
                "method `${currentClass.name.text}.${methodDeclaration.name.text}` is already defined" at methodDeclaration.name.sourceRange note (
                    "see previous definition here" at prev.node.name.sourceRange
                    )
            }
        })

        methodDeclaration.owner = currentClass
        methodDeclaration.parameters.forEach {
            it.owner = methodDeclaration
        }

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

        mainMethodDeclaration.owner = currentClass
        mainMethodDeclaration.parameters.forEach {
            it.owner = mainMethodDeclaration
        }

        // do not descend
    }

    private fun printErrorDuplicateMain(current: SourceRange, previous: SourceRange) {
        sourceFile.error {
            "method `${currentClass.name.text}.main` is already defined" at current note (
                "see previous definition here" at previous
                )
        }
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
        } else if (type is SemanticType.Array) {
            val baseType = type.baseType
            if (baseType is SemanticType.Class) {
                baseType.definition = lookupClass(global, sourceFile, baseType)
            }
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
                        "read" -> methodInvocationExpression.type = InternalFunction.SYSTEM_IN_READ
                        else -> {
                            sourceFile.error {
                                "unknown built-in method `System.in.$methodName`" at methodInvocationExpression.sourceRange
                            }
                        }
                    }
                }
                "out" -> {
                    when (methodName) {
                        "println" -> methodInvocationExpression.type = InternalFunction.SYSTEM_OUT_PRINTLN
                        "write" -> methodInvocationExpression.type = InternalFunction.SYSTEM_OUT_WRITE
                        "flush" -> methodInvocationExpression.type = InternalFunction.SYSTEM_OUT_FLUSH
                        else -> {
                            sourceFile.error {
                                "unknown built-in method `System.out.$methodName`" at methodInvocationExpression.sourceRange
                            }
                        }
                    }
                }
                else -> {
                    sourceFile.error {
                        "unknown built-in field `System.$fieldName`" at fieldAccessExpr.sourceRange
                    }
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
        sourceFile.errorIf(newObjectExpression.clazz.text == "String") {
            "cannot instantiate built-in class `String`" at newObjectExpression.sourceRange
        }

        super.visitNewObjectExpression(newObjectExpression)
    }
}

fun doNameAnalysis(program: AstNode.Program, sourceFile: SourceFile, stringTable: StringTable) {
    val global = GlobalNamespace().apply {
        defineBuiltIns(sourceFile, stringTable)
    }
    program.accept(NamespacePopulator(global, sourceFile))
    program.accept(NameResolver(global, sourceFile, stringTable.tryRegisterIdentifier("System")))
}
