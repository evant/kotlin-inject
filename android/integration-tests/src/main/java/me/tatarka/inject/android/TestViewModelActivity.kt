package me.tatarka.inject.android

import androidx.activity.ComponentActivity
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import me.tatarka.inject.android.activity.viewModels

class TestViewModelActivity : ComponentActivity() {
    val vmFactory = { TestViewModel() }
    val vmSavedStateFactory = { handle: SavedStateHandle -> TestSavedStateViewModel(handle) }

    val viewModel by viewModels(vmFactory)
    val savedStateViewModel by viewModels(vmSavedStateFactory)
}

class TestViewModel : ViewModel() {
}

class TestSavedStateViewModel(val handle: SavedStateHandle) : ViewModel()