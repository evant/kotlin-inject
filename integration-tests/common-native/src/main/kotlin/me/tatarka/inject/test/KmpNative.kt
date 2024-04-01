package me.tatarka.inject.test

import me.tatarka.inject.annotations.TargetComponentAccessor

@TargetComponentAccessor
expect fun createNative(): KmpComponent2
