package edu.kit.compiler.transform

import edu.kit.compiler.lex.Symbol
import edu.kit.compiler.prependIfNotNull
import edu.kit.compiler.semantic.AstNode
import edu.kit.compiler.semantic.InternalFunction
import edu.kit.compiler.semantic.SemanticType
import edu.kit.compiler.semantic.baseType
import edu.kit.compiler.semantic.dimension
import edu.kit.compiler.semantic.display
import edu.kit.compiler.toArray
import firm.ClassType
import firm.CompoundType
import firm.Entity
import firm.MethodType
import firm.Mode
import firm.PointerType
import firm.Program
import firm.Type

val SemanticType.mode: Mode
    get() = when (this) {
        SemanticType.Integer -> Mode.getIs()
        SemanticType.Boolean -> Mode.getBu()
        is SemanticType.Class -> Mode.getP()
        is SemanticType.Array -> Mode.getP()
        SemanticType.Null -> Mode.getP()
        SemanticType.Void,
        SemanticType.Error -> throw IllegalArgumentException("invalid type ${display()}")
    }

class TypeRegistry {

    val intType: Type = Mode.getIs().type
    val boolType: Type = Mode.getBu().type

    private class Class(
        val referenceType: PointerType,
        val fields: MutableMap<Symbol, Entity> = mutableMapOf(),
        val methods: MutableMap<Symbol, Entity> = mutableMapOf(),
    ) {
        val type: ClassType
            get() = referenceType.pointsTo as ClassType
    }
    private val classes = mutableMapOf<Symbol, Class>()

    /**
     * Create a new class.
     * @throws[IllegalArgumentException] if the class was already registered
     */
    fun createClass(name: Symbol): ClassType {
        val classType = ClassType(name.text)
        val prevValue = classes.putIfAbsent(name, Class(PointerType(classType)))
        require(prevValue == null) { "class `${name.text}` already registered" }
        return classType
    }

    private fun getClass(name: Symbol) = classes[name] ?: throw IllegalArgumentException("unknown class `${name.text}`")
    fun getClassType(name: Symbol) = getClass(name).type
    fun getClassReferenceType(name: Symbol) = getClass(name).referenceType

    private val arrayCache = mutableMapOf<Pair<SemanticType, Int>, PointerType>()
    fun getArrayReferenceType(type: SemanticType.Array): PointerType {
        val baseType = type.baseType
        val dimension = type.dimension
        assert(dimension >= 1)
        return arrayCache.computeIfAbsent(Pair(baseType, dimension)) {
            var firmType = baseType.toVariableType()
            repeat(dimension) {
                firmType = PointerType(firmType)
            }
            firmType as PointerType
        }
    }

    /**
     * Create a new field in an existing class.
     * @throws[IllegalArgumentException] if the field was already registered
     */
    fun createField(inClass: Symbol, name: Symbol, type: SemanticType): Entity {
        val clazz = getClass(inClass)
        val entity = Entity(clazz.type, name.text, type.toVariableType())
        val prevValue = clazz.fields.putIfAbsent(name, entity)
        require(prevValue == null) { "field `${inClass.text}.${name.text}` already registered" }
        return entity
    }

    fun getField(inClass: Symbol, name: Symbol): Entity {
        val clazz = getClass(inClass)
        return clazz.fields[name] ?: throw IllegalArgumentException("unknown field `${inClass.text}.${name.text}`")
    }

    private fun createMethod(parent: CompoundType, name: String, returnType: SemanticType, parameterTypes: List<SemanticType>, thisParam: Type?): Entity {
        val firmParamTypes = parameterTypes.asSequence().map { it.toVariableType() }.prependIfNotNull(thisParam).toArray()
        val firmReturnType = if (returnType == SemanticType.Void) {
            arrayOf()
        } else {
            arrayOf(returnType.toVariableType())
        }

        val methodType = MethodType(firmParamTypes, firmReturnType)
        return Entity(parent, name, methodType)
    }

    fun createMethod(inClass: Symbol, name: Symbol, returnType: SemanticType, parameterTypes: List<SemanticType>, isStatic: Boolean): Entity {
        val clazz = getClass(inClass)

        val thisParam = if (!isStatic) clazz.referenceType else null
        val entity = createMethod(clazz.type, name.text, returnType, parameterTypes, thisParam).apply {
            // set mangled name (-> linker) for non-static methods (static methods don't need this, because by default: linker name == entity name (== "main"))
            if (!isStatic) {
                setLdIdent("${inClass.text}.${name.text}")
            }
        }

        val prevValue = clazz.methods.putIfAbsent(name, entity)
        require(prevValue == null) { "method `${inClass.text}.${name.text}` already registered" }
        return entity
    }

    fun getMethod(inClass: Symbol, name: Symbol): Entity {
        val clazz = getClass(inClass)
        return clazz.methods[name] ?: throw IllegalArgumentException("unknown method `${inClass.text}.${name.text}`")
    }

    private val internalMethods = mutableMapOf<String, Entity>()

    private fun createInternalMethod(func: AstNode.Expression.MethodInvocationExpression.Type.Internal): Entity {
        val entity = createMethod(Program.getGlobalType(), func.name, func.returnType, func.parameters, null)
        val prevValue = internalMethods.putIfAbsent(func.name, entity)
        require(prevValue == null) { "internal method `${func.fullName}` already registered" }
        return entity
    }

    private fun createAllocateFunction(): Entity {
        val paramTypes = arrayOf(Mode.getLu().type)
        val returnType = arrayOf(Mode.getP().type)

        val type = MethodType(paramTypes, returnType)
        val entity = Entity(Program.getGlobalType(), "allocate", type)
        val prevValue = internalMethods.putIfAbsent("allocate", entity)
        require(prevValue == null) { "internal method `allocate` already registered" }
        return entity
    }

    init {
        createInternalMethod(InternalFunction.SYSTEM_IN_READ)
        createInternalMethod(InternalFunction.SYSTEM_OUT_PRINTLN)
        createInternalMethod(InternalFunction.SYSTEM_OUT_WRITE)
        createInternalMethod(InternalFunction.SYSTEM_OUT_FLUSH)

        createAllocateFunction()
    }

    fun getInternalMethod(name: String): Entity {
        return internalMethods[name] ?: throw IllegalArgumentException("unknown internal method `$name`")
    }

    fun SemanticType.toVariableType(): Type = when (this) {
        SemanticType.Integer -> intType
        SemanticType.Boolean -> boolType
        is SemanticType.Class -> getClassReferenceType(name.symbol)
        is SemanticType.Array -> getArrayReferenceType(this)
        SemanticType.Null,
        SemanticType.Void,
        SemanticType.Error -> throw IllegalArgumentException("invalid type ${display()}")
    }
}
