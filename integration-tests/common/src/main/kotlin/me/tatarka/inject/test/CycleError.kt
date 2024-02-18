package me.tatarka.inject.test

import me.tatarka.inject.annotations.Inject

@Inject class A(val b: B)
@Inject class B(val a: A)

// @Component abstract class MyComponent {
//    abstract val a: A
// }
