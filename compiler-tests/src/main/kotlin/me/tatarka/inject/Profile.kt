package me.tatarka.inject

import me.tatarka.inject.compiler.Profiler

fun main() {
    val compiler = ProjectCompiler(Target.kapt, profiler = object : Profiler {
        override fun onStart() {
            println("generate start")
            readLine()
        }

        override fun onStop() {
            println("generate end")
            readLine()
        }
    })
    compiler.source("MyComponent.kt", """
        import me.tatarka.inject.annotations.Component
        import me.tatarka.inject.annotations.Provides
        
        @Component abstract class MyComponent {
            abstract val foo: String
            
            @Provides fun provideFoo(): String = "test"
        }
    """.trimIndent())

    compiler.compile()
}