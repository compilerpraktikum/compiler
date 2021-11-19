package edu.kit.compiler.semantic

import edu.kit.compiler.ast.AST
import edu.kit.compiler.ast.AbstractASTVisitor
import edu.kit.compiler.ast.Type
import edu.kit.compiler.ast.accept
import edu.kit.compiler.lex.AnnotatableFile
import edu.kit.compiler.lex.AnnotationType
import edu.kit.compiler.lex.Symbol
import edu.kit.compiler.wrapper.IdentityClassDeclaration
import edu.kit.compiler.wrapper.IdentityField
import edu.kit.compiler.wrapper.IdentityMethod
import edu.kit.compiler.wrapper.Of
import edu.kit.compiler.wrapper.Unwrappable
import edu.kit.compiler.wrapper.wrappers.Identity

/**
 * Handles name lookups within the block of a method. Requires all classes + their fields and methods from first analysis pass.
 */
class LocalMethodNamespace(
    private val clazz: ClassNamespace,
    private val sourceFile: AnnotatableFile,
) {
    private val global
        get() = clazz.global

    private val local = SymbolTable()

    fun enterScope() = local.enterScope()
    fun leaveScope() = local.leaveScope()

    fun addDefinition(def: AST.LocalVariableDeclarationStatement</* TODO */>) {
        val name: Symbol = def.name
        val prevDefinition = local.lookup(name)
        if (prevDefinition == null) {
            local.add(name, VariableDefinition(
                name,
                def,
                if (def.type is Type.Class)
                    global.classes.get(def.type.name)!!.namespace
                else
                    null
            ))
        } else {
            sourceFile.annotate(
                AnnotationType.ERROR,
                def.range, // TODO maybe def.nameRange for better error message?
                "local variable with the name '${name.text}' already exists",
                listOf(
                    prevDefinition.node.range, "see previous declaration here"
                )
            )

        }
    }

    // TODO error handling (-> sourceFile.annotate(...)) should probably happen in these methods
    fun lookupClass(name: Symbol) = global.classes.get(name)
    fun lookupField(name: Symbol, inClazz: ClassNamespace? = null) = (inClazz ?: clazz).fields.get(name)
    fun lookupMethod(name: Symbol, inClazz: ClassNamespace? = null) = (inClazz ?: clazz).methods.get(name)
}

/**
    First name analysis pass: Populate Global namespace by visiting all classes:
        <li> visit all fields and put them in the class namespace
        <li> visit all methods and put them in the class namespace
 */
class GlobalNameAnalysisVisitor(
    private val unwrapExpr: Unwrappable<Identity<Of>>,
    private val unwrapStmt: Unwrappable<Identity<Of>>,
    private val unwrapDecl: Unwrappable<Identity<Of>>,
    private val unwrapClass: Unwrappable<Identity<Of>>,
    private val unwrapOther: Unwrappable<Identity<Of>>
) :
    AbstractASTVisitor<Identity<Of>, Identity<Of>, Identity<Of>, Identity<Of>, Identity<Of>>(
        unwrapExpr, unwrapStmt, unwrapDecl, unwrapClass, unwrapOther
    ) {

    private val globalNamespace: GlobalNamespace = GlobalNamespace()
    private lateinit var currentClassNamespace: ClassNamespace

    override fun visit(arrayType: Type.Array.ArrayType<Identity<Of>>) {
        arrayType.accept(this)
        TODO("remove this method")
    }

    override fun visit(operation: AST.UnaryExpression.Operation) {
        TODO("remove this method")
    }

    override fun visit(classDeclaration: IdentityClassDeclaration) {
        currentClassNamespace = ClassNamespace(globalNamespace)
        // call children
        super.visit(classDeclaration)
        if (!globalNamespace.classes.tryPut(
                classDeclaration.name,
                ClassDefinition(classDeclaration.name, classDeclaration, currentClassNamespace))) {
            TODO("throw sth")
        }
    }

    override fun visit(field: IdentityField) {
        if (!currentClassNamespace.fields.tryPut(field.name, FieldDefinition(field.name, field))) {
            TODO("throw sth")
        }
    }

    override fun visit(method: IdentityMethod) {
        if (!currentClassNamespace.methods.tryPut(method.name, MethodDefinition(method.name, method))) {
            TODO("throw sth")
        }
    }
}

/**
    Second name analysis pass: Populate Class namespace by visiting all methods:
        <li> visit all local variable declarations
        <li> visit all local variable usages and map the respective declaration, if there is one. If not, throw.
        <li> visit all methodinvocations, fieldaccess or arrayaccesses
        <li> Some idents belong to other namespaces.
        <li> Use a LocalMethodNamespace for each method.
 */
class MethodNameAnalysisVisitor(
    private val unwrapExpr: Unwrappable<Identity<Of>>,
    private val unwrapStmt: Unwrappable<Identity<Of>>,
    private val unwrapDecl: Unwrappable<Identity<Of>>,
    private val unwrapClass: Unwrappable<Identity<Of>>,
    private val unwrapOther: Unwrappable<Identity<Of>>,
    private val globalNamespace: GlobalNamespace
) :
    AbstractASTVisitor<Identity<Of>, Identity<Of>, Identity<Of>, Identity<Of>, Identity<Of>>(
        unwrapExpr, unwrapStmt, unwrapDecl, unwrapClass, unwrapOther
    ) {

    override fun visit(arrayType: Type.Array.ArrayType<Identity<Of>>) {
        arrayType.accept(this)
        TODO("remove this method")
    }

    override fun visit(operation: AST.UnaryExpression.Operation) {
        TODO("remove this method")
    }

    override fun visit(localVariableDeclarationStatement: AST.LocalVariableDeclarationStatement<Identity<Of>, Identity<Of>>) {
        //todo symboltable from current method
//        symbolTable.add(
//            localVariableDeclarationStatement.name,
//            VariableDefinition(
//                localVariableDeclarationStatement.name,
//                localVariableDeclarationStatement,
//            )
//        )
    }

}
