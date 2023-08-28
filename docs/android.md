# Android

This document covers special considerations and patterns useful for the android platform.

## Injecting into platform classes

Android has many classes where you don't control the constructions of (`Activity`,`Service`, etc.). You may have noticed
that kotlin-inject only supports constructor injection, so what do you do?

### Pull code out of the platform class

You can pull out the dependencies and related code into its own class and inject that instead. You may find this makes
it easier to test as well.

instead of:

```kotlin
class MyActivity : Activity() {
    @Inject
    lateinit var imageLoader: ImageLoader

    @Inject
    lateinit var analytics: Analytics

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate()
        setContentView(R.layout.my_layout)
        // ...
        imageLoader.loadImage(imageView, "https://...")
    }

    override fun onResume() {
        super.onResume()
        analytics.screenView("My Screen")
    }
}
```

do:

```kotlin
@Inject
class MyScreen(private val imageLoader: ImageLoader, private val analytics: Analytics) {
    fun loadImage(imageView: ImageView) {
        imageLoader.loadImage(imageView, "https://...")
    }

    fun onResume() {
        analytics.screenView("My Screen")
    }
}

@Component
abstract class ActivityComponent(@Component val parent: ApplicationComponent) {
    abstract val myScreen: MyScreen
}

class MyActivity : Activity() {
    private lateinit var myScreen: MyScreen

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate()
        myScreen = ActivityComponent::class.create(ApplicationComponent.getInstance(this)).myScreen
        setContentView(R.layout.my_layout)
        // ...
        myScreen.loadImage(imageView)
    }

    override fun onResume() {
        super.onResume()
        myScreen.onResume()
    }
}
```

### Use FragmentFactory

For fragments, you can do one better. You can use constructor injection by providing a
custom [FragmentFactory](https://developer.android.com/reference/androidx/fragment/app/FragmentFactory).

```kotlin
@Inject
class InjectFragmentFactory(
    private val homeFragment: () -> HomeFragment,
    private val settingsFragment: () -> SettingsFragment,
) : FragmentFactory() {
    override fun instantiate(classLoader: ClassLoader, className: String): Fragment {
        return when (className) {
            name<HomeFragment> -> homeFragment()
            name<SettingsFragment> -> settingsFragment()
            else -> super.instantiate(classLoader, className)
        }
    }

    private inline fun <reified C> name() = C::class.qualifiedName
}

@Component
abstract class MainActivityComponent(@Component val parent: ApplicationComponent) {
    abstract val fragmentFactory: InjectFragmentFactory
}

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        supportFragmentManager.fragmentFactory =
            MainActivityComponent::class.create(ApplicationComponent.getInstance(this))
                .fragmentFactory
        super.onCreate(savedInstanceState)
    }
}

@Inject
class HomeFragment(private val imageLoader: ImageLoader) : Fragment()

@Inject
class SettingsFragment(private val imageLoader: ImageLoader) : Fragment()
```

## ViewModels

ViewModels need special care when constructing so that they can be retained across configuration changes. To do this,
you can inject a function that creates the ViewModel instead of the ViewModel directly.

```kotlin
@Inject
class HomeViewModel(private val repository: HomeRepository) : ViewModel()

@Inject
class HomeFragment(homeViewModel: () -> HomeViewModel) : Fragment() {
    private val viewModel by viewModels {
        viewModelFactory { addInitializer(HomeViewModel::class) { homeViewModel() } }
    }
}
```

or if you want to use [SavedStateHandle](https://developer.android.com/reference/androidx/lifecycle/SavedStateHandle)

```kotlin
@Inject
class HomeViewModel(private val repository: HomeRepository, @Assisted handle: SavedStateHandle) : ViewModel()

@Inject
class HomeFragment(homeViewModel: (SavedStateHandle) -> HomeViewModel) : Fragment() {
    private val viewModel by viewModels {
        viewModelFactory { addInitializer(HomeViewModel::class) { homeViewModel(createSavedStateHandle()) } }
    }
}
```

You may want to create helper functions for these.

```kotlin
import androidx.fragment.app.viewModels

/**
 * [viewModels] helper that allows you to pass a single factory function.
 */
inline fun <reified VM : ViewModel> Fragment.viewModel(crossinline factory: () -> VM): Lazy<VM> =
    viewModels {
        viewModelFactory { addInitializer(VM::class) { factory() } }
    }

/**
 * [viewModels] helper that allows you to pass a single factory function using a [SavedStateHandle].
 */
inline fun <reified VM : ViewModel> Fragment.viewModel(crossinline factory: (SavedStateHandle) -> VM): Lazy<VM> =
    viewModels {
        viewModelFactory { addInitializer(VM::class) { factory(createSavedStateHandle()) } }
    }

@Inject
class HomeFragment(homeViewModel: () -> HomeViewModel) : Fragment() {
    private val viewModel by viewModel(homeViewModel)
}

@Inject
class HomeFragment(homeViewModel: (SavedStateHandle) -> HomeViewModel) : Fragment() {
    private val viewModel by viewModel(homeViewModel)
}
```

## Compose

kotlin-inject's [function injection](../README.md#function-injection) works quite nicely with compose.

```kotlin
typealias Home = @Composable () -> Unit

@Inject
@Composable
fun Home(repo: HomeRepository) {
     // ...
}

@Component
abstract class ApplicationComponent() {
    abstract val home: Home
}

class MyActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate()
        val home = ApplicationComponent().home
        setContent {
            home()
        }
    }
}
```

Similar to within fragments, you can inject a function that creates a `ViewModel`.

```kotlin
@Inject
class HomeViewModel(private val repository: HomeRepository) : ViewModel()
@Inject
class OtherViewModel(private val repository: OtherRepository, @Assisted handle: SavedStateHandle): ViewModel()

@Inject
@Composable
fun Home(homeViewModel: () -> HomeViewModel, otherViewModel: (SavedStateHandle) -> OtherViewModel) {
    val homeViewModel = viewModel { homeViewModel() }
    val otherViewModel = viewModel { otherViewModel(createSavedStateHandle()) }
    ...
}
```

# Build Variants

You may want to provide different dependencies based on the build-type/flavor. You can do this by splitting those out
into a `VariantComponent` interface that's declared in each variant.

```kotlin
// debug/java/com.example.inject/VariantComponent.kt
interface VariantComponent {
    val DebugClient.bind: Client
        @Provides get() = this
}

// release/java/com.example.inject/VariantComponent.kt
interface VariantComponent {
    val ReleaseClient.bind: Client
        @Provides get() = this
}

// main/java/com.example.inject/ApplicationComponent.kt
@Component
abstract class ApplicationComponent : VariantComponent
```
