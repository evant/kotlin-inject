package me.tatarka.inject.test

import me.tatarka.inject.ProjectCompiler
import me.tatarka.inject.Target
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.io.File

class InheritanceTest {

    private val target = Target.KSP2

    @TempDir
    lateinit var workingDir: File

    @ParameterizedTest
    @EnumSource(Target::class)
    fun abstract_functions_can_be_implemented_by_parent_component_interfaces(target: Target) {
        val projectCompiler = ProjectCompiler(target, workingDir)

        assertDoesNotThrow {
            projectCompiler.source(
                "MyComponent.kt",
                """
                    package com.test
                    
                    import me.tatarka.inject.annotations.Component
                    import me.tatarka.inject.annotations.Provides
                    
                    interface Interface1 {
                        fun string(): String
                    }
                    
                    interface Interface2 : Interface1 {
                        override fun string(): String = "abc"
                    }

                    @Component 
                    abstract class MyComponent1 : Interface1, Interface2

                    @Component 
                    abstract class MyComponent2 : Interface2, Interface1
                """.trimIndent()
            ).compile()
        }
    }
}
