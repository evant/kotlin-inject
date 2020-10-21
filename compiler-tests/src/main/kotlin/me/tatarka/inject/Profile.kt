package me.tatarka.inject

import me.tatarka.inject.compiler.Profiler

fun main() {
    val compiler = ProjectCompiler(
        Target.KSP,
        profiler = object : Profiler {
            override fun onStart() {
                println("generate start")
                readLine()
            }

            override fun onStop() {
                println("generate end")
                readLine()
            }
        }
    )
    compiler.source(
        "MyComponent.kt",
        """
        import me.tatarka.inject.annotations.Component
        import me.tatarka.inject.annotations.Provides
        
        typealias S1 = String
        typealias S2 = String
        typealias S3 = String
        typealias S4 = String
        typealias S5 = String
        
        @Component abstract class MyComponent1(
            @Component val parent: MyComponent2
        ): I1 {
            @Provides fun provideFoo(): S1 = "test"
        }
        
        @Component abstract class MyComponent2(
            @Component val parent: MyComponent3
        ): I2 {
            @Provides fun provideFoo(): S2 = "test"
        }
        
        @Component abstract class MyComponent3(
            @Component val parent: MyComponent4
        ): I3 {
            @Provides fun provideFoo(): S3 = "test"
        }
        
        @Component abstract class MyComponent4(
            @Component val parent: MyComponent5
        ): I4 {
            @Provides fun provideFoo(): S4 = "test"
        }
        
        @Component abstract class MyComponent5: I5 {
            @Provides fun provideFoo(): S5 = "test"
        }
        
        interface I1 : I2 { val foo1: S1 }
        
        interface I2 : I3 { val foo2: S2 }
        
        interface I3: I4 { val foo3: S3 }
        
        interface I4: I5 { val foo4: S4 }
        
        interface I5 { val foo5: S5 }
        """.trimIndent()
    )

    compiler.compile()
}