package me.tatarka.inject.compiler

import me.tatarka.inject.annotations.IntoMap
import me.tatarka.inject.annotations.IntoSet
import me.tatarka.inject.compiler.ContainerCreator.mapOf
import me.tatarka.inject.compiler.ContainerCreator.setOf
import java.lang.reflect.GenericArrayType

private val TYPE_INFO_CACHE = mutableMapOf<AstClass, TypeInfo>()

class TypeCollector private constructor(private val provider: AstProvider, private val options: Options) :
    AstProvider by provider {

    companion object {
        operator fun invoke(
            provider: AstProvider,
            options: Options,
            astClass: AstClass,
            accessor: String? = null,
        ): TypeCollector = TypeCollector(provider, options).apply {
            val typeInfo = collectTypeInfo(astClass)
            collectTypes(astClass, accessor, typeInfo)
            scopeClass = typeInfo.scopeClass
            providerMethods = typeInfo.providerMethods
        }
    }

    // Map of types to inject and how to obtain them
    private val types = mutableMapOf<TypeKey, TypeCreator>()

    // Map of types that can be provided from abstract methods
    private val _providerMethods = mutableMapOf<TypeKey, TypeCreator.Method>()

    // Map of scoped components and the accessors to obtain them
    private val scopedAccessors = mutableMapOf<AstType, ScopedComponent>()

    // Map of bounded generic types
    private val genericTypes = mutableMapOf<GenericKey, TypeCreator>()

    var scopeClass: AstClass? = null
        private set

    var providerMethods: List<AstMethod> = emptyList()
        private set

    private fun collectTypes(
        astClass: AstClass,
        accessor: String? = null,
        typeInfo: TypeInfo,
    ) {
        if (typeInfo.elementScope != null) {
            scopedAccessors[typeInfo.elementScope] = ScopedComponent(astClass, accessor)
        }

        for (method in typeInfo.providesMethods) {
            val scopeType = method.scopeType(options)
            if (scopeType != null && scopeType != typeInfo.elementScope) {
                error("@Provides scope:${scopeType} must match component scope: ${typeInfo.elementScope}", method)
            }
            val scopedComponent = if (scopeType != null) astClass else null
            if (method.hasAnnotation<IntoMap>()) {
                // Pair<A, B> -> Map<A, B>
                val type = method.returnTypeFor(astClass)
                if (type.packageName == "kotlin" && type.simpleName == "Pair") {
                    val typeArgs = method.returnTypeFor(astClass).arguments
                    val mapType =
                        TypeKey(
                            declaredTypeOf(Map::class, typeArgs[0], typeArgs[1]),
                            method.qualifier(options)
                        )
                    addContainerType(mapType, mapOf, method, accessor, scopedComponent)
                } else {
                    error("@IntoMap must have return type of type Pair", method)
                }
            } else if (method.hasAnnotation<IntoSet>()) {
                // A -> Set<A>
                val setType = TypeKey(declaredTypeOf(Set::class, method.returnTypeFor(astClass)))
                addContainerType(setType, setOf, method, accessor, scopedComponent)
            } else {
                val returnType = method.returnTypeFor(astClass)
                if (returnType.isGeneric) {
                    val params = method.typeParameters
                    if (isUnbounded(returnType, params)) {
                        val typeName = returnType.toString()
                        error(
                            """Unbounded generic return types are not supported.
                            |You can either specify a type bound: <${typeName}: Foo> or wrap in a concrete class Foo<${typeName}>.
                        """.trimMargin(), method
                        )
                    } else {
                        val key = GenericKey(returnType, params, method.qualifier(options))
                        addGenericMethod(key, method, accessor, scopedComponent)
                    }
                } else {
                    val key = TypeKey(returnType, method.qualifier(options))
                    addMethod(key, method, accessor, scopedComponent)
                }
            }
        }

        for (method in typeInfo.providerMethods) {
            val returnType = method.returnTypeFor(astClass)
            val key = TypeKey(returnType, method.qualifier(options))
            if (typeInfo.isComponent) {
                addProviderMethod(key, method, accessor)
            } else {
                // If we aren't a component ourselves, allow obtaining types from providers as these may be contributed from the
                // eventual implementation
                addMethod(key, method, accessor, scopedComponent = null)
            }
        }

        val constructor = astClass.primaryConstructor
        if (constructor != null) {
            for (parameter in constructor.parameters) {
                if (parameter.isComponent()) {
                    val elemAstClass = parameter.type.toAstClass()
                    val elemTypeInfo = collectTypeInfo(elemAstClass)
                    collectTypes(
                        astClass = elemAstClass,
                        accessor = if (accessor != null) "$accessor.${parameter.name}" else parameter.name,
                        typeInfo = elemTypeInfo
                    )
                }
            }
        }
    }

    private fun collectTypeInfo(astClass: AstClass): TypeInfo {
        return TYPE_INFO_CACHE.getOrPut(astClass) {
            val isComponent = astClass.isComponent()

            val concreteMethods = mutableSetOf<AstMethod>()
            val providesMethods = mutableListOf<AstMethod>()
            val providerMethods = mutableListOf<AstMethod>()

            var scopeClass: AstClass? = null
            var elementScope: AstType? = null

            astClass.visitInheritanceChain { parentClass ->
                val parentScope = parentClass.scopeType(options)
                if (parentScope != null) {
                    if (scopeClass == null) {
                        scopeClass = parentClass
                        elementScope = parentScope
                    } else {
                        messenger.error("Cannot apply scope: $parentScope", parentClass)
                        messenger.error(
                            "as scope: $elementScope is already applied",
                            scopeClass
                        )
                    }
                }

                for (method in parentClass.methods) {
                    val abstract = method.isAbstract
                    if (isComponent && !abstract) {
                        concreteMethods.add(method)
                    }
                    if (method.isProvides()) {
                        if (method.isPrivate) {
                            error("@Provides method must not be private", method)
                            continue
                        }
                        if (method.returnType.isUnit()) {
                            error("@Provides method must return a value", method)
                            continue
                        }
                        if (isComponent && abstract) {
                            val providesImpl = concreteMethods.find { it.overrides(method) }
                            if (providesImpl == null) {
                                error("@Provides method must have a concrete implementation", method)
                                continue
                            }
                            concreteMethods.remove(providesImpl)
                            providesMethods.add(providesImpl)
                        } else {
                            providesMethods.add(method)
                        }
                    }
                    if (method.isProvider()) {
                        providerMethods.add(method)
                    }
                }
            }

            TypeInfo(
                isComponent = isComponent,
                providesMethods = providesMethods,
                providerMethods = providerMethods,
                scopeClass = scopeClass,
                elementScope = elementScope
            )
        }
    }

    private fun addContainerType(
        key: TypeKey,
        creator: ContainerCreator,
        method: AstMethod,
        accessor: String?,
        scopedComponent: AstClass?
    ) {
        val current = types[key]
        if (current == null) {
            types[key] = TypeCreator.Container(
                creator = creator,
                source = method,
                args = mutableListOf(method(method, accessor, scopedComponent))
            )
        } else if (current is TypeCreator.Container && current.creator == creator) {
            current.args.add(method(method, accessor, scopedComponent))
        } else {
            duplicate(key.toString(), newValue = method, oldValue = current.source)
        }
    }

    private fun addMethod(key: TypeKey, method: AstMethod, accessor: String?, scopedComponent: AstClass?) {
        val oldValue = types[key]
        if (oldValue == null) {
            types[key] = method(method, accessor, scopedComponent)
        } else {
            duplicate(key.toString(), newValue = method, oldValue = oldValue.source)
        }
    }

    private fun addGenericMethod(key: GenericKey, method: AstMethod, accessor: String?, scopedComponent: AstClass?) {
        val oldValue = genericTypes[key]
        if (oldValue == null) {
            genericTypes[key] = method(method, accessor, scopedComponent)
        } else {
            duplicate(key.genericType.toString(), newValue = method, oldValue = oldValue.source)
        }
    }

    private fun addProviderMethod(key: TypeKey, method: AstMethod, accessor: String?) {
        if (!_providerMethods.containsKey(key)) {
            _providerMethods[key] = method(method, accessor, scopedComponent = null)
        }
    }

    private fun method(method: AstMethod, accessor: String?, scopedComponent: AstClass?) = TypeCreator.Method(
        method = method,
        accessor = accessor,
        scopedComponent = scopedComponent
    )

    private fun duplicate(keyName: String, newValue: AstElement, oldValue: AstElement) {
        error("Cannot provide: $keyName", newValue)
        error("as it is already provided", oldValue)
    }

    fun resolve(key: TypeKey, skipSelf: Boolean = false): TypeCreator? {
        if (!skipSelf) {
            val result = _providerMethods[key]
            if (result != null) {
                return result
            }
        }
        val result = types[key]
        if (result != null) {
            return result
        }

        val results = genericTypes.filter { (genericKey, _) -> genericKey.isAssignableFrom(key.type) }
        if (results.size == 1) {
            return results.values.first()
        } else if (results.size > 1) {
            error("Multiple generic provides match $key")
            for ((genericKey, creator) in results) {
                error(genericKey.toString(), creator.source)
            }
            return null
        }

        val astClass = key.type.toAstClass()
        if (astClass.isInject(options)) {
            val scope = astClass.scopeType(options)
            val scopedComponent = if (scope != null) scopedAccessors[scope] else null
            if (scope != null && scopedComponent == null) {
                error("Cannot find component with scope: @$scope to inject $astClass", astClass)
                return null
            }
            return TypeCreator.Constructor(
                astClass.primaryConstructor!!,
                accessor = scopedComponent?.accessor,
                scopedComponent = scopedComponent?.type
            )
        }
        return null
    }
}

class TypeInfo(
    val isComponent: Boolean,
    val providesMethods: List<AstMethod>,
    val providerMethods: List<AstMethod>,
    val scopeClass: AstClass? = null,
    val elementScope: AstType? = null,
)

sealed class TypeCreator(val source: AstElement) {

    class Constructor(
        val constructor: AstConstructor,
        val accessor: String? = null,
        val scopedComponent: AstClass? = null
    ) :
        TypeCreator(constructor)

    class Method(
        val method: AstMethod,
        val accessor: String? = null,
        val scopedComponent: AstClass? = null
    ) : TypeCreator(method)

    class Container(val creator: ContainerCreator, source: AstElement, val args: MutableList<Method>) :
        TypeCreator(source)
}

enum class ContainerCreator {
    mapOf, setOf
}

data class ScopedComponent(
    val type: AstClass,
    val accessor: String?
)

data class GenericKey(
    val genericType: AstType,
    val typeParams: List<AstTypeParam>,
    val qualifier: AstAnnotation?
) {
    fun isAssignableFrom(type: AstType): Boolean {
        return genericType.isAssignableFrom(type, typeParams)
    }

    private fun AstType.isAssignableFrom(type: AstType, typeParams: List<AstTypeParam>): Boolean {
        return if (arguments.isEmpty()) {
            val param = typeParams.find { it.name == this.simpleName } ?: return false
            return param.bounds.isEmpty() || param.bounds.all { it.isAssignableFrom(type) }
        } else {
            packageName == type.packageName &&
                    simpleName == type.simpleName &&
                    arguments.isAssignableFrom(type.arguments, typeParams)
        }
    }

    private fun List<AstType>.isAssignableFrom(args: List<AstType>, typeParams: List<AstTypeParam>): Boolean {
        if (size != args.size) {
            return false
        }
        for (i in 0 until args.size) {
            val arg1 = get(i)
            val arg2 = args[i]
            if (!arg1.isAssignableFrom(arg2, typeParams)) {
                return false
            }
        }
        return true
    }

    override fun toString(): String = StringBuilder().apply {
        if (qualifier != null) {
            append(qualifier)
            append(" ")
        }
        genericType.toString(typeParams, this)
    }.toString()

    private fun AstType.toString(typeParams: List<AstTypeParam>, builder: StringBuilder) {
        if (arguments.isEmpty()) {
            builder.append(this)
            val param = typeParams.find { it.name == this.simpleName }
            if (param != null) {
                builder.append(": ")
                param.bounds.joinTo(builder, ", ")
            }
        } else {
            builder.append("$packageName.$simpleName<")
            arguments.forEachIndexed { index, argument ->
                argument.toString(typeParams, builder)
                if (index < arguments.size - 1) {
                    builder.append(", ")
                }
            }
            builder.append(">")
        }
    }
}


private fun isUnbounded(type: AstType, typeParams: List<AstTypeParam>): Boolean {
    if (!type.isTypeParam) {
        return false
    }
    val param = typeParams.find { it.name == type.simpleName }
    return param == null || param.bounds.isEmpty() || param.bounds.all { it.isAny() }
}