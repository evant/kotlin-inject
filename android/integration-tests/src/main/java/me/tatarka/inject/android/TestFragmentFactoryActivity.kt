package me.tatarka.inject.android

import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import me.tatarka.inject.android.annotations.GenerateFragmentFactory
import me.tatarka.inject.android.fragment.InjectFragmentFactory
import me.tatarka.inject.annotations.Component
import me.tatarka.inject.annotations.Inject

@Component
@GenerateFragmentFactory(default = true)
abstract class ActivityComponent : FragmentFactoryActivityComponent {
    abstract val fragmentFactory: InjectFragmentFactory
}

class TestFragmentFactoryActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        supportFragmentManager.fragmentFactory = ActivityComponent::class.create().fragmentFactory
        super.onCreate(savedInstanceState)
    }
}

@Inject
class Fragment1 : Fragment()

@Inject
class Fragment2 : Fragment()