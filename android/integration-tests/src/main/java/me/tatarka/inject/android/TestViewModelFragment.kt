package me.tatarka.inject.android

import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.lifecycle.SavedStateHandle
import me.tatarka.inject.android.fragment.activityViewModels
import me.tatarka.inject.android.fragment.viewModels

class TestViewModelFragment(
    vmFactory: () -> TestViewModel,
    vmSavedStateFactory: (SavedStateHandle) -> TestSavedStateViewModel
) : Fragment() {

    val viewModel by viewModels(vmFactory)
    val savedStateViewModel by viewModels(vmSavedStateFactory)

    val activityViewModel by activityViewModels(vmFactory)
    val savedStateActivityViewModel by activityViewModels(vmSavedStateFactory)
}

class TestParentViewModelFragment : Fragment() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            childFragmentManager.commit {
                add(TestChildViewModelFragment::class.java, null, "test")
            }
        }
    }

    val childFragment: TestChildViewModelFragment
        get() = childFragmentManager.findFragmentByTag("test") as TestChildViewModelFragment
}

class TestChildViewModelFragment(
    vmFactory: () -> TestViewModel,
    vmSavedStateFactory: (SavedStateHandle) -> TestSavedStateViewModel
) : Fragment() {

    val parentViewModel by viewModels({ requireParentFragment() }, vmFactory)
    val savedStateParentViewModel by viewModels({ requireParentFragment() }, vmSavedStateFactory)
}