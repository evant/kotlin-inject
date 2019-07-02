package me.tatarka.inject.compiler

import assertk.all
import assertk.assertThat
import assertk.assertions.hasName
import assertk.assertions.hasText
import assertk.assertions.isEqualTo
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.KotlinCompilation.ExitCode.OK
import com.tschuchort.compiletesting.SourceFile
import org.junit.Test

class InjectCompilerTest {

    @Test
    fun generates_a_simple_module_that_provides_a_dep_with_no_arguments() {
        val source = SourceFile.new(
            "test.TestModule.kt", """
            import me.tatarka.inject.annotations.Inject
            import me.tatarka.inject.annotations.Module
            
            @Inject class Foo()
            
            @Module
            abstract class TestModule {
                abstract val foo: Foo
            }
        """
        )
        val result = KotlinCompilation().apply {
            sources = listOf(source)
            inheritClassPath = true
            messageOutputStream = System.err
            annotationProcessors = listOf(InjectCompiler())
            reportOutputFiles = true
            verbose = true
        }.compile()

        assertThat(result.exitCode).isEqualTo(OK)

        assertThat(result.generatedFiles.first { it.name.endsWith(".kt") }).all {
            hasName("test.InjectTestModule.kt")
            hasText(
                """
                internal class InjectTestModule : TestModule {
                    override val foo: Foo get() = Foo()
                }
                
                inline fun TestModule.create(): TestModule = InjectTestModule()
            """
            )
        }
    }
}