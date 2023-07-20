package me.tatarka.inject.test

import assertk.all
import assertk.assertThat
import assertk.assertions.contains
import me.tatarka.inject.ProjectCompiler
import me.tatarka.inject.Target
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.io.File

class WarningTest {

    @TempDir
    lateinit var workingDir: File

    @Test
    fun warns_that_kapt_backend_is_deprecated() {
        val projectCompiler = ProjectCompiler(Target.KAPT, workingDir)

        assertThat(
            projectCompiler.source(
                "MyComponent.kt",
                """
                    import me.tatarka.inject.annotations.Component
                    
                    @Component abstract class MyComponent
                """.trimIndent()
            ).compile()
        ).warnings()
            .contains("The kotlin-inject kapt backend is deprecated and will be removed in a future version.")
    }

    @ParameterizedTest
    @EnumSource(Target::class)
    fun warns_on_implicit_assisted_params(target: Target) {
        val projectCompiler = ProjectCompiler(target, workingDir)

        assertThat(
            projectCompiler.source(
                "MyComponent.kt",
                """
                import me.tatarka.inject.annotations.Component
                import me.tatarka.inject.annotations.Inject
                import me.tatarka.inject.annotations.Provides
                import me.tatarka.inject.annotations.Assisted
                
                @Inject class Bar
                @Inject class Foo(val bar: Bar, assisted: String)
                
                @Component abstract class MyComponent {
                    abstract fun foo(): (String) -> Foo
                }
                """.trimIndent()
            ).compile()
        ).warnings().all {
            contains("Implicit assisted parameters are deprecated and will be removed in a future version.")
            contains("Annotate the following with @Assisted: [assisted: String]")
        }
    }
}