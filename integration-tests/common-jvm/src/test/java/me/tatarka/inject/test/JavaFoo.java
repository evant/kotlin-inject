package me.tatarka.inject.test;

import me.tatarka.inject.annotations.Inject;

public class JavaFoo {

    private final Foo foo;

    @Inject
    public JavaFoo(Foo foo) {
        this.foo = foo;
    }

    public Foo getFoo() {
        return foo;
    }
}