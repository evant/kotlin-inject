package me.tatarka.inject.test

import assertk.all
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isFailure
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import kotlin.test.Test

class FailureTest {
    @get:Rule
    val testProjectDir = TemporaryFolder()

    @Test
    fun fails_if_module_is_not_abstract() {
        assertThat {
            ProjectCompiler(testProjectDir.root).source(
                "test.kt", """
                    import me.tatarka.inject.annotations.Component
                    
                    @Component class MyComponent
                """.trimIndent()
            ).compile()
        }.isFailure().output().contains("@Component class: MyComponent must be abstract")
    }

    @Test
    fun fails_if_module_is_private() {
        assertThat {
            ProjectCompiler(testProjectDir.root).source(
                "test.kt", """
                    import me.tatarka.inject.annotations.Component
                    
                    @Component private abstract class MyComponent
                """.trimIndent()
            ).compile()
        }.isFailure().output().contains("@Component class: MyComponent must not be private")
    }

    @Test
    fun fails_if_provides_is_private() {
        assertThat {
            ProjectCompiler(testProjectDir.root).source(
                "test.kt", """
                    import me.tatarka.inject.annotations.Component
                    import me.tatarka.inject.annotations.Provides
                    
                    @Component abstract class MyComponent {
                        @Provides private fun providesString(): String = "one"
                    }
                """.trimIndent()
            ).compile()
        }.isFailure().output().contains("@Provides method must not be private")
    }

    @Test
    fun fails_if_provides_is_abstract() {
        assertThat {
            ProjectCompiler(testProjectDir.root).source(
                "test.kt", """
                    import me.tatarka.inject.annotations.Component
                    import me.tatarka.inject.annotations.Provides
                    
                    @Component abstract class MyComponent {
                        @Provides abstract fun providesString(): String
                    }
                """.trimIndent()
            ).compile()
        }.isFailure().output().contains("@Provides method must not be abstract")
    }

    @Test
    fun fails_if_provides_returns_unit() {
        assertThat {
            ProjectCompiler(testProjectDir.root).source(
                "test.kt", """
                    import me.tatarka.inject.annotations.Component
                    import me.tatarka.inject.annotations.Provides
                    
                    @Component abstract class MyComponent {
                        @Provides fun providesUnit() { }
                    }
                """.trimIndent()
            ).compile()
        }.isFailure().output().contains("@Provides method must return a value")
    }

    @Test
    fun fails_if_the_same_type_is_provided_more_than_once() {
        assertThat {
            ProjectCompiler(testProjectDir.root).source(
                "test.kt", """
                    import me.tatarka.inject.annotations.Component
                    import me.tatarka.inject.annotations.Provides
                    
                    @Component abstract class MyComponent {
                        @Provides fun providesString1(): String = "one"
                        @Provides fun providesString2(): String = "two"
                    }
                """.trimIndent()
            ).compile()
        }.isFailure().output().all {
            contains("Cannot provide: String")
            contains("as it is already provided")
        }
    }

    @Test
    fun fails_if_type_cannot_be_provided() {
        assertThat {
            ProjectCompiler(testProjectDir.root).source(
                "test.kt", """
                    import me.tatarka.inject.annotations.Component
                    
                    @Component abstract class MyComponent {
                        abstract val s: String                 
                    }
                """.trimIndent()
            ).compile()
        }.isFailure().output().contains("Cannot find an @Inject constructor or provider for: String")
    }

    @Test
    fun fails_if_module_does_not_have_scope_to_provide_dependency() {
        assertThat {
            ProjectCompiler(testProjectDir.root).source(
                "test.kt", """
                    import me.tatarka.inject.annotations.Component
                    import me.tatarka.inject.annotations.Scope
                    import me.tatarka.inject.annotations.Inject

                    @Scope annotation class MyScope
                    @MyScope @Inject class Foo

                    @Component abstract class MyComponent {
                        abstract val f: Foo
                    }
                """.trimIndent()
            ).compile()
        }.isFailure().output().contains("Cannot find module with scope: @MyScope to inject Foo")
    }
}