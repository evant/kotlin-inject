# Architecture

This document describes the high-level architecture of kotlin-inject. If you want to familiarize yourself with the code
base, you are just in the right place!

## Bird's Eye View

At the highest level kotlin-inject takes kotlin AST and generates kotlin code based off of it. You can think of it like
a compiler in that regard, and it goes through similar steps. The steps are as follows:

Kotlin AST -> Collect Types -> Resolve Types -> Optimize -> Generate Code

we will go through each one of these steps in turn.

### Kotlin AST

This represents the kotlin source code. Historically both KSP and KAPT backends were supported, to accomplish this, the
AST is wrapped. The interface is defined in `kotlin-inject-compiler/core/Ast` and the implementation is in
`kotlin-inject-compiler/ksp/KSAst`. Note: the wrappers aren't a full implementation of the AST but provide only what's
necessary for this project. There is additional glue-code in `kotlin-inject-compiler/ksp` to run the processor. This
abstraction is being kept around to see where
kotlin's [k2 compiler api lands](https://youtrack.jetbrains.com/issue/KT-49508).
It's possible it'll be removed in the future. Everything else is in `kotlin-inject-compiler/core` and the remainder of
these steps will focus on that module exclusively.

### Collect Types

Types are collected in `TypeCollector`. It will scan a `@Component` class and it's superclass/interfaces looking for
methods to provide types. Validation is also done here to ensure a type's scope annotation correct and that a type is
not provided multiple times.

### Resolve Types

Types are resolved in `TypeResultResolver`. This figures out how to construct a type and returns a `TypeResult` that has
this information. This is a sealed class that includes all the ways a type can be constructed. `TypeResult`s are cached
so that the same instances is shared in all places a type is used. This creates a graph
(specifically A Directed-Acyclic-Graph) of dependencies.

For example, if you had the following types:

```
@Inject class Foo()
@Inject class Bar(foo: Foo)
@Inject class Baz(bar: Bar, foo: Foo)
```

and you asked to resolve type `Baz`, you'd get a graph like:

```
Baz─►Bar─►Foo
 │         ▲
 └─────────┘
```

### Optimize

The graph is then optimized in `TypeResultOptimizer`. Currently, what is done is to look for types with more than one
parent and pull them out into their own private getter. More optimizations may be done here in the future.

### Generate Code

Finally, code is generated using [KotlinPoet](https://square.github.io/kotlinpoet/). This is done in the `*Generator`
classes.

## Testing

The bulk of the testing is done in `integration-tests`. These declare `@Component`s in various ways and ensure that the
code is generated, compiles correctly, and returns the expected results.

To test validation errors, there are also a few tests using
[Kotlin Compile Testing](https://github.com/tschuchortdev/kotlin-compile-testing) in `compiler-inject-compiler:test`.

Note that there a no tests testing that the generated source code looks a certain way. This is intentional, as these
test can be quite brittle. What's important is the generated code _behaves_ as expected, not exactly what it looks like.
However, more targeted unit tests may be added in the future to ensure certain optimizations are being applied correctly.