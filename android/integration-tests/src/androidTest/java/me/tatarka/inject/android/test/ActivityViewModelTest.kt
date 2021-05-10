package me.tatarka.inject.android.test

import androidx.activity.viewModels
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentFactory
import androidx.fragment.app.testing.launchFragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.launchActivity
import androidx.test.ext.junit.runners.AndroidJUnit4
import assertk.assertThat
import assertk.assertions.isSameAs
import me.tatarka.inject.android.TestChildViewModelFragment
import me.tatarka.inject.android.TestParentViewModelFragment
import me.tatarka.inject.android.TestSavedStateViewModel
import me.tatarka.inject.android.TestViewModel
import me.tatarka.inject.android.TestViewModelActivity
import me.tatarka.inject.android.TestViewModelFragment
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ActivityViewModelTest {

    @Test
    fun constructs_and_retains_a_view_model_for_an_activity() {
        val scenario = launchActivity<TestViewModelActivity>()

        lateinit var viewModel: TestViewModel
        lateinit var savedStateViewModel: TestSavedStateViewModel
        scenario.onActivity {
            viewModel = it.viewModel
            savedStateViewModel = it.savedStateViewModel
        }
        scenario.recreate()
        scenario.onActivity {
            assertThat(it.viewModel).isSameAs(viewModel)
            assertThat(it.savedStateViewModel).isSameAs(savedStateViewModel)
        }
    }

    @Test
    fun constructs_and_retains_a_view_model_for_a_fragment() {
        val vmFactory = { TestViewModel() }
        val vmSavedStateFactory = { handle: SavedStateHandle -> TestSavedStateViewModel(handle) }
        val scenario = launchFragment { TestViewModelFragment(vmFactory, vmSavedStateFactory) }

        lateinit var viewModel: TestViewModel
        lateinit var savedStateViewModel: TestSavedStateViewModel
        scenario.onFragment {
            viewModel = it.viewModel
            savedStateViewModel = it.savedStateViewModel
        }
        scenario.recreate()
        scenario.onFragment {
            assertThat(it.viewModel).isSameAs(viewModel)
            assertThat(it.savedStateViewModel).isSameAs(savedStateViewModel)
        }
    }

    @Test
    fun constructs_and_retains_a_view_model_from_the_parent_activity() {
        val vmFactory = { TestViewModel() }
        val vmSavedStateFactory = { handle: SavedStateHandle -> TestSavedStateViewModel(handle) }
        val scenario = launchFragment { TestViewModelFragment(vmFactory, vmSavedStateFactory) }

        lateinit var viewModel: TestViewModel
        lateinit var savedStateViewModel: TestSavedStateViewModel
        scenario.onFragment {
            viewModel = it.activityViewModel
            savedStateViewModel = it.savedStateActivityViewModel
        }
        scenario.recreate()
        scenario.onFragment {
            val activityViewModel by it.requireActivity().viewModels<TestViewModel>()
            val savedStateActivityViewModel by it.requireActivity().viewModels<TestSavedStateViewModel>()

            assertThat(activityViewModel).isSameAs(viewModel)
            assertThat(savedStateActivityViewModel).isSameAs(savedStateViewModel)
        }
    }

    @Test
    fun constructs_and_retains_a_view_model_from_the_parent_fragment() {
        val vmFactory = { TestViewModel() }
        val vmSavedStateFactory = { handle: SavedStateHandle -> TestSavedStateViewModel(handle) }
        val scenario = launchFragment<TestParentViewModelFragment>(factory = object : FragmentFactory() {
            override fun instantiate(classLoader: ClassLoader, className: String): Fragment {
                return when (className) {
                    TestParentViewModelFragment::class.java.name -> TestParentViewModelFragment()
                    TestChildViewModelFragment::class.java.name -> TestChildViewModelFragment(
                        vmFactory,
                        vmSavedStateFactory
                    )
                    else -> super.instantiate(classLoader, className)
                }
            }
        })

        lateinit var viewModel: TestViewModel
        lateinit var savedStateViewModel: TestSavedStateViewModel
        scenario.onFragment {
            viewModel = it.childFragment.parentViewModel
            savedStateViewModel = it.childFragment.savedStateParentViewModel
        }
        scenario.recreate()
        scenario.onFragment {
            val parentViewModel by it.viewModels<TestViewModel>()
            val savedStateParentViewModel by it.viewModels<TestSavedStateViewModel>()

            assertThat(parentViewModel).isSameAs(viewModel)
            assertThat(savedStateParentViewModel).isSameAs(savedStateViewModel)
        }
    }
}