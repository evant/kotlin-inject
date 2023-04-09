# Testing

Using dependency injecting can make testing easier, below is some strategies you can employ to use kotlin-inject in
tests.

## Call the constructor directly

In the simplest case you don't need to use kotlin-inject at all. You can call the constructor directly providing
dependencies.

```kotlin
@Inject
@ApplicationScope
class UserAccountsRepository(accountService: AccountService, userService: UserService)

@Inject
class ProfileScreen(userAccountsRepository: UserAccountsRepository)

@Test
fun test_profile_screen() {
    val userAccountRepository = UserAccountRepository(FakeAccountService(), FakeUserService())
    val profileScreen = ProfileScreen(userAccountRepository)
    // test screen
}
```

This can work well when your dependencies are uncomplicated to set up. However, it means you lose the benefits of a
dependency injection library. You have to write that setup code yourself and update it when the dependencies change.
Therefore, it may be useful to use kotlin-inject in your tests as well.

## Why not mock?

You may be tempted to use a mocking library to simplify your setup code.

```kotlin
@Test
fun test_profile_screen() {
    val mockUserAccountRepository = mockk<UserAccountRepository>()
    every { mockUserAccountRepository.getUserInfo(any()) } returns UserInfo(
        userId = "123",
        userName = "Tamra",
        accountStatus = SUBSCRIBED,
    )
    val profileScreen = ProfileScreen(mockUserAccountRepository)
    // test screen
}
```

However, there are serious downsides to this approach.

1. You still have to update your test code every time a dependency to `ProfileScreen` is added or removed.
2. You have to update your test code when a new _method_ is called on a dependency inside `ProfileScreen`.
3. This setup code often has to be duplicated in several places as multiple classes under test use the same dependency.
4. Your mocked `UserAccountRepositry`'s behavior may differ from the real one in ways where your test may cover bugs in
   production code.

Instead, **it's better to use real dependencies when you can, only faking the edges of your system.** You may also use
kotlin-inject in your test code to keep the setup code simple.

## Replacing dependencies with fakes

Let's say you provide your dependencies as follows:

```kotlin
@Component
@ApplicationScope
abstract class ApplicationComponent {
    val HttpAccountService.bind: AccountService
        @Provides get() = this
    val DbUserService.bind: UserService
        @Provides get() = this
}
```

You can create a test application component as follows:

```kotlin
class TestFakes(
    @get:Provides val accountService: AccountService = FakeAccountService(),
    @get:Provides val userService: UserService = FakeUserService(),
)

@Component
@ApplicationScope
abstract class TestApplicationComponent(@Component val fakes: TestFakes = TestFakes())
```

You can then use this `TestApplictionComponent` in your tests. It allows you to both use the default fakes and replace
specific ones for certain tests.

```kotlin
class ProfileScreenTest {

    @Test
    fun test_using_default_fakes() {
        val component = TestComponent::class.create()
        val profileScreen = component.profileScreen
        // test screen
    }

    @Test
    fun test_using_custom_fake() {
        val component = TestComponent::class.create(TestApplicationComponent::class.create(TestFakes(
            accountService = object : FakeAccountService() {
                override fun getAccount(accountId: String): Account = throw Exception("failed to get account")
            }
        )))
        val profileScreen = component.profileScreen
        // test screen
    }

    @Component
    abstract class TestComponent(@Component val parent: TestApplicationComponent = TestApplicationComponent::class.create()) {
        abstract val profileScreen: ProfileScreen
    }
}
```

In a real application you are likely to have a mix of provided dependencies you want to replace with fakes and ones you
don't. You can accomplish this by pulling them out into an interface that's used both in your application component and
your test application component.

```kotlin
interface CommonComponent {
    @ApplicationScope
    val globalScope: CoroutineScope
        @Provides get() = CoroutineScope(Job())
}

@Component
@ApplicationScope
abstract class ApplicationComponent : CommonComponent

@Component
@ApplicationScope
abstract class TestApplicationComponent(@Component val fakes: TestFakes = TestFakes()) : CommonComponent
```