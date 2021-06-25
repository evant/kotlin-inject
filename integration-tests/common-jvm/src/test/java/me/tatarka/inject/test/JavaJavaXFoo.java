package me.tatarka.inject.test;

import javax.inject.Inject;

public class JavaJavaXFoo {
    private final Foo foo;

    @Inject
    public JavaJavaXFoo(Foo foo) {
        this.foo = foo;
    }

    public Foo getFoo() {
        return foo;
    }
}