package me.tatarka.inject.test

import assertk.assertThat
import assertk.assertions.isInstanceOf
import kotlin.test.Test

class KmpNativeTest {
    @Test
    fun actual_createKmp_functions_are_generated() {
        assertThat(createNative()).isInstanceOf<KmpComponent2>()
    }
}
