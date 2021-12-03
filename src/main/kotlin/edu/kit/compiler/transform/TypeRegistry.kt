package edu.kit.compiler.transform

import edu.kit.compiler.lex.Symbol
import edu.kit.compiler.semantic.SemanticType
import edu.kit.compiler.semantic.baseType
import edu.kit.compiler.semantic.dimension
import edu.kit.compiler.semantic.display
import firm.ClassType
import firm.Entity
import firm.MethodType
import firm.Mode
import firm.PointerType
import firm.Type

fun SemanticType.toVariableType(): Type = when (this) {
    SemanticType.Integer -> FirmContext.typeRegistry.intType
    SemanticType.Boolean -> FirmContext.typeRegistry.boolType
    is SemanticType.Class -> FirmContext.typeRegistry.getClassReferenceType(name.symbol)
    is SemanticType.Array -> FirmContext.typeRegistry.getArrayReferenceType(this)
    SemanticType.Null,
    SemanticType.Void,
    SemanticType.Error -> throw IllegalArgumentException("invalid type ${display()}")
}

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
        return arrayCache.computeIfAbsent(Pair(baseType, dimension)) {
            var firmType = baseType.toVariableType()
            repeat(dimension) {
                firmType = PointerType(firmType)
            }
            PointerType(firmType)
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

    fun createMethod(inClass: Symbol, name: Symbol, returnType: SemanticType, parameterTypes: List<SemanticType>, isStatic: Boolean): Entity {
        val clazz = getClass(inClass)

        val firmParamTypes = parameterTypes.asSequence().map { it.toVariableType() }.let {
            if (isStatic) {
                it
            } else {
                sequenceOf(clazz.referenceType) + it
            }
        }.toList().toTypedArray()

        val firmReturnType = if (returnType == SemanticType.Void) {
            arrayOf()
        } else {
            arrayOf(returnType.toVariableType())
        }

        val methodType = MethodType(firmParamTypes, firmReturnType)
        val entity = Entity(clazz.type, name.text, methodType).apply {
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
}
