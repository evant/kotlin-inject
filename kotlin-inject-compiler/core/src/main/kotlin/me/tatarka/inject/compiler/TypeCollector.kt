package me.tatarka.inject.compiler

import me.tatarka.kotlin.ast.AstAnnotation
import me.tatarka.kotlin.ast.AstClass
import me.tatarka.kotlin.ast.AstElement
import me.tatarka.kotlin.ast.AstMember
import me.tatarka.kotlin.ast.AstProvider
import me.tatarka.kotlin.ast.AstType
import me.tatarka.kotlin.ast.AstVisibility

class TypeCollector(private val provider: AstProvider, private val options: Options) {

    private val typeInfoCache = mutableMapOf<String, TypeInfo>()

    fun collect(astClass: AstClass, accessor: Accessor = Accessor.Empty): Result {
        val typeInfo = collectTypeInfo(astClass)
        return if (!typeInfo.valid) {
            Result(astClass, null, emptyList())
        } else {
            val result = Result(astClass, typeInfo.scopeClass, typeInfo.providerMethods)
            result.collectTypes(astClass, accessor, typeInfo)
            result
        }
    }

    inner class Result internal constructor(
        val astClass: AstClass,
        val scopeClass: AstClass?,
        val providerMethods: List<AstMember>,
    ) {
        // Map of types to inject and how to obtain them.
        private val types = mutableMapOf<TypeKey, Method>()

        // Map of container types to inject. Used for multibinding.
        private val containerTypes = mutableMapOf<ContainerKey, MutableList<Method>>()

        // Map of types obtained from generated provider methods. This can be used for lookup when the underlying method
        // is not available (ex: because we only see an interface, or it's marked protected).
        private val providerTypes = mutableMapOf<TypeKey, ProviderMethod>()

        // Map of scoped components and the accessors to obtain them
        private val scopedAccessors = mutableMapOf<AstType, ScopedComponent>()

        private val parents = mutableListOf<Result>()

        fun iterator(): Iterator<Result> = iterator {
            yield(this@Result)
            for (parent in parents) {
                yieldAll(parent.iterator())
            }
        }

        fun providerType(key: TypeKey): Pair<ProviderMethod, Result>? {
            for (result in iterator()) {
                val type = result.providerTypes[key]
                if (type != null) return type to result
            }
            return null
        }

        fun type(key: TypeKey): Pair<Method, Result>? {
            for (result in iterator()) {
                val type = result.types[key]
                if (type != null) return type to result
            }
            return null
        }

        fun containerArgs(key: ContainerKey): List<Pair<Method, Result>> {
            val results = mutableListOf<Pair<Method, Result>>()
            for (result in iterator()) {
                val types = result.containerTypes[key]
                if (types != null) {
                    results.addAll(types.map { it to result })
                }
            }
            return results
        }

        fun scopedAccessor(type: AstType): Pair<ScopedComponent, Result>? {
            for (result in iterator()) {
                val component = result.scopedAccessors[type]
                if (component != null) return component to result
            }
            return null
        }

        @Suppress("ComplexMethod", "LongMethod", "NestedBlockDepth")
        internal fun collectTypes(
            astClass: AstClass,
            accessor: Accessor,
            typeInfo: TypeInfo,
        ) {
            if (accessor.isNotEmpty()) {
                for (method in typeInfo.providerMethods) {
                    val returnType = method.returnTypeFor(astClass)
                    val key = TypeKey(returnType, method.qualifier(options))
                    addProviderMethod(key, method, accessor)
                }
            }

            for (method in typeInfo.providesMethods) {
                val scope = method.scopeType(options)
                if (scope != null && scope != typeInfo.elementScope) {
                    if (typeInfo.elementScope != null) {
                        provider.error(
                            "@Provides with scope: $scope must match component scope: ${typeInfo.elementScope}",
                            method
                        )
                    } else {
                        provider.error(
                            "@Provides with scope: $scope cannot be provided in an unscoped component",
                            method
                        )
                    }
                }
                val scopedComponent = if (scope != null) astClass else null
                if (method.hasAnnotation(INTO_MAP.packageName, INTO_MAP.simpleName)) {
                    // Pair<A, B> -> Map<A, B>
                    val returnType = method.returnTypeFor(astClass)
                    val key = TypeKey(returnType, method.qualifier(options))
                    val resolvedType = returnType.resolvedType()
                    if (resolvedType.isPair()) {
                        val containerKey = ContainerKey.MapKey(
                            resolvedType.arguments[0],
                            resolvedType.arguments[1],
                            key.qualifier
                        )
                        addContainerType(provider, key, containerKey, method, accessor, scope, scopedComponent)
                    } else {
                        provider.error("@IntoMap must have return type of type Pair", method)
                    }
                } else if (method.hasAnnotation(INTO_SET.packageName, INTO_SET.simpleName)) {
                    // A -> Set<A>
                    val returnType = method.returnTypeFor(astClass)
                    val key = TypeKey(returnType, method.qualifier(options))
                    val containerKey = ContainerKey.SetKey(returnType, key.qualifier)
                    addContainerType(provider, key, containerKey, method, accessor, scope, scopedComponent)
                } else {
                    val returnType = method.returnTypeFor(astClass)
                    val key = TypeKey(returnType, method.qualifier(options))
                    if (accessor.isNotEmpty()) {
                        // May have already added from a resolvable provider
                        if (providerTypes.containsKey(key)) continue
                        // We out outside the current class, so complain if not accessible
                        if (method.visibility == AstVisibility.PROTECTED) {
                            provider.error("@Provides method is not accessible", method)
                        }
                    }
                    addMethod(key, method, accessor, scope, scopedComponent)
                }
            }

            val constructor = astClass.primaryConstructor
            if (constructor != null) {
                for (parameter in constructor.parameters) {
                    if (parameter.isComponent()) {
                        val elemAstClass = parameter.type.toAstClass()
                        val elemTypeInfo = collectTypeInfo(elemAstClass)

                        val parentResult = Result(elemAstClass, scopeClass, providerMethods)
                        parents.add(parentResult)
                        parentResult.collectTypes(
                            astClass = elemAstClass,
                            accessor = accessor + parameter.name,
                            typeInfo = elemTypeInfo
                        )
                    }
                }
            }

            if (typeInfo.elementScope != null) {
                val result = scopedAccessor(typeInfo.elementScope)
                if (result != null) {
                    val (component, _) = result
                    provider.error("Cannot apply scope: ${typeInfo.elementScope}", typeInfo.elementScope)
                    provider.error(
                        "as scope: ${typeInfo.elementScope} is already applied to parent",
                        component.type,
                    )
                } else {
                    scopedAccessors[typeInfo.elementScope] = ScopedComponent(astClass, accessor)
                }
            }
        }

        private fun addContainerType(
            provider: AstProvider,
            key: TypeKey,
            containerKey: ContainerKey,
            method: AstMember,
            accessor: Accessor,
            scope: AstType?,
            scopedComponent: AstClass?,
        ) {
            val current = type(containerKey.containerTypeKey(provider))
            if (current != null) {
                val (creator, _) = current
                duplicate(key, newValue = method, oldValue = creator.method)
            }

            containerTypes.getOrPut(containerKey) { mutableListOf() }
                .add(method(method, accessor, scope, scopedComponent))
        }

        private fun addMethod(
            key: TypeKey,
            method: AstMember,
            accessor: Accessor,
            scope: AstType?,
            scopedComponent: AstClass?,
        ) {
            val oldValue = types[key]
            if (oldValue != null) {
                duplicate(key, newValue = method, oldValue = oldValue.method)
                return
            }

            val containerKey = ContainerKey.fromContainer(key)
            if (containerKey != null) {
                val oldContainerValue = containerTypes[containerKey]
                if (oldContainerValue != null) {
                    duplicate(key, newValue = method, oldValue = oldContainerValue.first().method)
                    return
                }
            }

            types[key] = method(method, accessor, scope, scopedComponent)
        }

        private fun addProviderMethod(key: TypeKey, method: AstMember, accessor: Accessor) {
            // Skip adding if already provided by child component.
            if (!providerTypes.containsKey(key)) {
                providerTypes[key] = ProviderMethod(method, accessor)
            }
        }

        private fun method(method: AstMember, accessor: Accessor, scope: AstType?, scopedComponent: AstClass?) = Method(
            method = method,
            accessor = accessor,
            scope = scope,
            scopedComponent = scopedComponent
        )

        private fun duplicate(key: TypeKey, newValue: AstElement, oldValue: AstElement) {
            provider.error("Cannot provide: $key", newValue)
            provider.error("as it is already provided", oldValue)
        }
    }

    @Suppress("ComplexMethod", "LongMethod", "LoopWithTooManyJumpStatements")
    private fun collectTypeInfo(astClass: AstClass): TypeInfo {
        return typeInfoCache.getOrPut(astClass.toString()) {
            val isComponent = astClass.isComponent()

            val providesMethods = mutableListOf<AstMember>()
            val providerMethods = mutableListOf<AstMember>()

            var scopeClass: AstClass? = null
            var elementScope: AstType? = null

            for (parentClass in astClass.inheritanceChain()) {
                val parentScope = parentClass.scopeType(options)
                if (parentScope != null) {
                    if (scopeClass == null) {
                        scopeClass = parentClass
                        elementScope = parentScope
                    } else {
                        provider.error("Cannot apply scope: $parentScope", parentClass)
                        provider.error(
                            "as scope: $elementScope is already applied",
                            scopeClass
                        )
                    }
                }
            }

            val allMethods = astClass.allMethods
            // some methods may override others
            val methods = mutableMapOf<AstMember, AstMember?>()
            for (method in allMethods) {
                val overrides = methods.keys.find { it.overrides(method) }
                if (overrides != null) {
                    methods[overrides] = method
                } else {
                    methods[method] = null
                }
            }
            for (method in methods.keys) {
                val abstract = method.isAbstract
                val overriden = methods[method]
                if (method.isProvides() || overriden?.isProvides() == true) {
                    if (method.visibility == AstVisibility.PRIVATE) {
                        provider.error("@Provides method must not be private", method)
                        continue
                    }
                    if (method.returnType.isUnit()) {
                        provider.error("@Provides method must return a value", method)
                        continue
                    }
                    if (method.returnType.isPlatform()) {
                        val name = method.returnType.simpleName
                        provider.error(
                            """@Provides method must not return a platform type
                                |This can happen when you call a platform method and leave off an explicit return type.
                                |You can fix this be explicitly declaring the return type as $name or $name?"""
                                .trimMargin(),
                            method
                        )
                        continue
                    }

                    if (isComponent && abstract) {
                        provider.error("@Provides method must have a concrete implementation", method)
                        continue
                    } else {
                        providesMethods.add(method)
                    }
                } else if (method.isProvider()) {
                    val scope = method.scopeType(options)
                    if (scope != null) {
                        provider.warn(
                            "Scope: @${scope.simpleName} has no effect." +
                                " Place on @Provides function or @Inject constructor instead.",
                            method
                        )
                    }
                    providerMethods.add(method)
                }
            }

            TypeInfo(
                providesMethods = providesMethods,
                providerMethods = providerMethods,
                scopeClass = scopeClass,
                elementScope = elementScope
            )
        }
    }
}

class TypeInfo(
    val providesMethods: List<AstMember> = emptyList(),
    val providerMethods: List<AstMember> = emptyList(),
    val scopeClass: AstClass? = null,
    val elementScope: AstType? = null,
    val valid: Boolean = true,
)

class ProviderMethod(
    val method: AstMember,
    val accessor: Accessor,
)

class Method(
    val method: AstMember,
    val accessor: Accessor = Accessor.Empty,
    val scope: AstType? = null,
    val scopedComponent: AstClass? = null,
)

sealed class ContainerKey {
    abstract val creator: String
    abstract fun containerTypeKey(provider: AstProvider): TypeKey

    data class SetKey(val type: AstType, val qualifier: AstAnnotation? = null) : ContainerKey() {
        override val creator: String = "setOf"

        override fun containerTypeKey(provider: AstProvider): TypeKey {
            return TypeKey(provider.declaredTypeOf(Set::class, type), qualifier)
        }
    }

    data class MapKey(val key: AstType, val value: AstType, val qualifier: AstAnnotation? = null) : ContainerKey() {
        override val creator: String = "mapOf"

        override fun containerTypeKey(provider: AstProvider): TypeKey {
            return TypeKey(provider.declaredTypeOf(Map::class, key, value), qualifier)
        }
    }

    companion object {
        fun fromContainer(key: TypeKey): ContainerKey? {
            if (key.type.isSet()) {
                return SetKey(key.type.arguments[0], key.qualifier)
            }
            if (key.type.isMap()) {
                return MapKey(key.type.arguments[0], key.type.arguments[1], key.qualifier)
            }
            return null
        }
    }
}

data class ScopedComponent(
    val type: AstClass,
    val accessor: Accessor,
)