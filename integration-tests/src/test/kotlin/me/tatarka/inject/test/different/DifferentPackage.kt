package me.tatarka.inject.test.different

import me.tatarka.inject.annotations.Component
import me.tatarka.inject.annotations.Inject
import me.tatarka.inject.annotations.Provides
import me.tatarka.inject.test.CustomScope

class DifferentPackageFoo {
    @Inject class Factory
}

@CustomScope @Component abstract class DifferentPackageScopedComponent {
    @Provides @CustomScope fun differentPackageFoo() = DifferentPackageFoo()
}
