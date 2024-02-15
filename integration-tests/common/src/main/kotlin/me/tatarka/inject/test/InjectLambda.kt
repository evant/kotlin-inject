package me.tatarka.inject.test

import me.tatarka.inject.annotations.Component
import me.tatarka.inject.annotations.IntoMap
import me.tatarka.inject.annotations.IntoSet
import me.tatarka.inject.annotations.Provides

@Component
abstract class InjectLambdaComponent {
    abstract val lambda: () -> String

    @Provides
    fun provideLambda(): () -> String = { "foo" }
}

@Component
abstract class InjectLambdaSetComponent {
    abstract val lambdaSet: Set<() -> String>

    @Provides
    @IntoSet
    fun lambda1(): () -> String = { "one" }

    @Provides
    @IntoSet
    fun lambda2(): () -> String = { "two" }
}

@Component
abstract class InjectLambdaMapComponent {
    abstract val mapSet: Map<String, () -> String>

    @Provides
    @IntoMap
    fun lambda1(): Pair<String, () -> String> = "1" to { "one" }

    @Provides
    @IntoMap
    fun lambda2(): Pair<String, () -> String> = "2" to { "two" }
}
