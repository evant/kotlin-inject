package me.tatarka.inject.android.activity

import androidx.activity.ComponentActivity
import androidx.annotation.MainThread
import androidx.lifecycle.AbstractSavedStateViewModelFactory
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelLazy
import androidx.lifecycle.ViewModelProvider

/**
 * Returns a [Lazy] delegate to access the ComponentActivity's ViewModel, the instance returned from [factory] will be
 * used to create [ViewModel] first time.
 *
 * ```
 * @Inject class MyViewModel
 *
 * @Component interface ActivityComponent {
 *     val myViewModel: () -> MyViewModel
 * }
 *
 * class MyComponentActivity : ComponentActivity() {
 *     val component by lazy { ActivityComponent::class.create() }
 *     val viewModel by viewModels { component.myViewModel() }
 * }
 * ```
 *
 * This property can be accessed only after the Activity is attached to the Application,
 * and access prior to that will result in IllegalArgumentException.
 */
@MainThread
inline fun <reified VM : ViewModel> ComponentActivity.viewModels(crossinline factory: () -> VM): Lazy<VM> =
    ViewModelLazy(VM::class, { viewModelStore }, {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel?> create(modelClass: Class<T>): T = factory() as T
        }
    })

/**
 * Returns a [Lazy] delegate to access the ComponentActivity's ViewModel, the instance returned from [factory] will be
 * used to create [ViewModel] first time.
 *
 * ```
 * @Inject class MyViewModel(handle: SavedStateHandle)
 *
 * @Component interface ActivityComponent {
 *     val myViewModel: (SavedStateHandle) -> MyViewModel
 * }
 *
 * class MyComponentActivity : ComponentActivity() {
 *     val component by lazy { ActivityComponent::class.create() }
 *     val viewModel by viewModels { handle -> component.myViewModel(handle) }
 * }
 * ```
 *
 * This property can be accessed only after the Activity is attached to the Application,
 * and access prior to that will result in IllegalArgumentException.
 */
@MainThread
inline fun <reified VM : ViewModel> ComponentActivity.viewModels(crossinline factory: (SavedStateHandle) -> VM): Lazy<VM> =
    ViewModelLazy(VM::class, { viewModelStore }, {
        object : AbstractSavedStateViewModelFactory(this, intent?.extras) {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel?> create(
                key: String,
                modelClass: Class<T>,
                handle: SavedStateHandle
            ): T = factory(handle) as T
        }
    })