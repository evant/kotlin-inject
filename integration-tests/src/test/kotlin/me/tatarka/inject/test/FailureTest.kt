package me.tatarka.inject.test

import assertk.all
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isFailure
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FailureTest {
    @get:Rule
    val testProjectDir = TemporaryFolder()

    @Test
    fun fails_if_module_is_not_abstract() {
        assertThat {
            ProjectCompiler(testProjectDir.root).source(
                "test.kt", """
                    import me.tatarka.inject.annotations.Module
                    
                    @Module class MyModule
                """.trimIndent()
            ).compile()
        }.isFailure().output().contains("@Module class: MyModule must be abstract")
    }

    @Test
    fun fails_if_module_is_private() {
        assertThat {
            ProjectCompiler(testProjectDir.root).source(
                "test.kt", """
                    import me.tatarka.inject.annotations.Module
                    
                    @Module private abstract class MyModule
                """.trimIndent()
            ).compile()
        }.isFailure().output().contains("@Module class: MyModule must not be private")
    }

    @Test
    fun fails_if_the_same_type_is_provided_more_than_once() {
        assertThat {
            ProjectCompiler(testProjectDir.root).source(
                "test.kt", """
                    import me.tatarka.inject.annotations.Module
                    
                    @Module abstract class MyModule {
                        fun providesString1(): String = "one"
                        fun providesString2(): String = "two"
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
                    import me.tatarka.inject.annotations.Module
                    
                    @Module abstract class MyModule {
                        abstract val s: String                 
                    }
                """.trimIndent()
            ).compile()
        }.isFailure().output().contains("Cannot find an @Inject constructor or provider for: String")
    }
}