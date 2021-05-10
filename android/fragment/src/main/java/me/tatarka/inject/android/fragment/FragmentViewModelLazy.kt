package me.tatarka.inject.android.fragment

import androidx.annotation.MainThread
import androidx.fragment.app.Fragment
import androidx.lifecycle.AbstractSavedStateViewModelFactory
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelLazy
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner

/**
 * Returns a property delegate to access [ViewModel] by **default** scoped to this [Fragment]:
 * ```
 * @Inject
 * class MyFragment(myViewModel: () -> MyViewModel) : Fragment() {
 *     val viewModel = viewModels(myViewModel)
 * }
 * ```
 *
 * This property can be accessed only after this Fragment is attached i.e., after
 * [Fragment.onAttach()], and access prior to that will result in IllegalArgumentException.
 */
@MainThread
inline fun <reified VM : ViewModel> Fragment.viewModels(
    crossinline factory: () -> VM
): Lazy<VM> = viewModels({ this }, factory)

/**
 * Returns a property delegate to access [ViewModel] by **default** scoped to this [Fragment]:
 * ```
 * @Inject
 * class MyFragment(myViewModel: () -> MyViewModel) : Fragment() {
 *     val viewModel = viewModels(myViewModel)
 * }
 * ```
 *
 * Default scope may be overridden with parameter [ownerProducer]:
 * ```
 * class MyFragment(myParentViewModel: () -> MyParentViewModel) : Fragment() {
 *     val viewModel = viewModels({requireParentFragment()}, myParentViewModel)
 * }
 * ```
 *
 * This property can be accessed only after this Fragment is attached i.e., after
 * [Fragment.onAttach()], and access prior to that will result in IllegalArgumentException.
 */
@MainThread
inline fun <reified VM : ViewModel> Fragment.viewModels(
    noinline ownerProducer: () -> ViewModelStoreOwner,
    crossinline factory: () -> VM
): Lazy<VM> = ViewModelLazy(VM::class, { ownerProducer().viewModelStore }, {
    object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel?> create(modelClass: Class<T>): T = factory() as T
    }
})

/**
 * Returns a property delegate to access [ViewModel] by **default** scoped to this [Fragment]:
 * ```
 * @Inject
 * class MyFragment(myViewModel: (SavedStateHandle) -> MyViewModel) : Fragment() {
 *     val viewModel = viewModels(myViewModel)
 * }
 * ```
 *
 * This property can be accessed only after this Fragment is attached i.e., after
 * [Fragment.onAttach()], and access prior to that will result in IllegalArgumentException.
 */
@MainThread
inline fun <reified VM : ViewModel> Fragment.viewModels(
    crossinline factory: (SavedStateHandle) -> VM
): Lazy<VM> = viewModels({ this }, factory)

/**
 * Returns a property delegate to access [ViewModel] by **default** scoped to this [Fragment]:
 * ```
 * @Inject
 * class MyFragment(myViewModel: (SavedStateHandle) -> MyViewModel) : Fragment() {
 *     val viewModel = viewModels(myViewModel)
 * }
 * ```
 *
 * Default scope may be overridden with parameter [ownerProducer]:
 * ```
 * class MyFragment(parentViewModel: (SavedStateHandle) -> ParentViewModel) : Fragment() {
 *     val viewModel = viewModels({requireParentFragment()}, parentViewModel)
 * }
 * ```
 *
 * This property can be accessed only after this Fragment is attached i.e., after
 * [Fragment.onAttach()], and access prior to that will result in IllegalArgumentException.
 */
@MainThread
inline fun <reified VM : ViewModel> Fragment.viewModels(
    noinline ownerProducer: () -> ViewModelStoreOwner,
    crossinline factory: (SavedStateHandle) -> VM,
): Lazy<VM> = ViewModelLazy(VM::class, { ownerProducer().viewModelStore }, {
    object : AbstractSavedStateViewModelFactory(this, arguments) {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel?> create(
            key: String,
            modelClass: Class<T>,
            handle: SavedStateHandle
        ): T = factory(handle) as T
    }
})

/**
 * Returns a property delegate to access parent activity's [ViewModel], the [factory] will be used to create [ViewModel]
 * first time.
 *
 * ```
 * @Inject
 * class MyFragment(myActivityViewModel: () -> MyActivityViewModel) : Fragment() {
 *     val viewModel by activityViewModels(myActivityViewModel)
 * }
 * ```
 *
 * This property can be accessed only after this Fragment is attached i.e., after
 * [Fragment.onAttach()], and access prior to that will result in IllegalArgumentException.
 */
@MainThread
inline fun <reified VM : ViewModel> Fragment.activityViewModels(
    crossinline factory: () -> VM
): Lazy<VM> = ViewModelLazy(VM::class, { requireActivity().viewModelStore }, {
    object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel?> create(modelClass: Class<T>): T = factory() as T
    }
})

/**
 * Returns a property delegate to access parent activity's [ViewModel], the [factory] will be used to create [ViewModel]
 * first time.
 *
 * ```
 * @Inject
 * class MyFragment(myActivityViewModel: (SavedStateHandel) -> MyActivityViewModel) : Fragment() {
 *     val viewModel by activityViewModels(myActivityViewModel)
 * }
 * ```
 *
 * This property can be accessed only after this Fragment is attached i.e., after
 * [Fragment.onAttach()], and access prior to that will result in IllegalArgumentException.
 */
@MainThread
inline fun <reified VM : ViewModel> Fragment.activityViewModels(
    crossinline factory: (SavedStateHandle) -> VM
): Lazy<VM> = ViewModelLazy(VM::class, { requireActivity().viewModelStore }, {
    object : AbstractSavedStateViewModelFactory(this, arguments) {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel?> create(
            key: String,
            modelClass: Class<T>,
            handle: SavedStateHandle
        ): T = factory(handle) as T
    }
})