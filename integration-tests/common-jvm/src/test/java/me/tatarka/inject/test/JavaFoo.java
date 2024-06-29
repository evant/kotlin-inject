package me.tatarka.inject.test;

import me.tatarka.inject.annotations.Inject;

public class JavaFoo {

    private final IFoo foo;

    @Inject
    public JavaFoo(IFoo foo) {
        this.foo = foo;
    }

    public IFoo getFoo() {
        return foo;
    }
}