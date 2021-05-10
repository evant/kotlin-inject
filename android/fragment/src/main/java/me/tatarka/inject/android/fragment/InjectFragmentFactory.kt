package me.tatarka.inject.android.fragment

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentFactory
import me.tatarka.inject.annotations.Inject

/**
 * The return type you should return from a `@Provides @IntoMap` function to inject it into [InjectFragmentFactory].
 */
typealias FragmentEntry = Pair<String, () -> Fragment>

/**
 * Creates a [FragmentEntry] from the given fragment [factory].
 */
inline fun <reified F : Fragment> FragmentEntry(noinline factory: () -> F): FragmentEntry =
    F::class.java.canonicalName!! to factory

/**
 * A [FragmentFactory] that allows fragment constructor injection. You can provide the fragments to inject with
 * `@Provides @IntoMap`.
 *
 * ```
 * @Component abstract class FragmentsComponent {
 *     abstract val fragmentFactory: InjectFragmentFactory
 *
 *     val (() -> MyFragment1).myFragment1: FragmentEntry
 *         @Provides @IntoMap get() = FragmentEntry(this)
 *
 *     val (() -> MyFragment2).myFragment2: FragmentEntry
 *         @Provides @IntoMap get() = FragmentEntry(this)
 * }
 * ```
 */
@Inject
class InjectFragmentFactory(private val fragmentFactories: Map<String, () -> Fragment>) : FragmentFactory() {
    override fun instantiate(classLoader: ClassLoader, className: String): Fragment {
        return fragmentFactories[className]?.invoke() ?: super.instantiate(classLoader, className)
    }
}