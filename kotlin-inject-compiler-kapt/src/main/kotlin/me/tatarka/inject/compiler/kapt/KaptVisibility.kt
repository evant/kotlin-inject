package me.tatarka.inject.compiler.kapt

import kotlinx.metadata.Flag
import kotlinx.metadata.Flags
import me.tatarka.inject.compiler.AstVisibility
import me.tatarka.inject.compiler.VisibleElement
import javax.lang.model.element.Element
import javax.lang.model.element.Modifier

class KaptVisibility(type: Element, flags: Flags?) : VisibleElement {
    override val visibility: AstVisibility = when {
        flags?.let { Flag.Common.IS_INTERNAL(it) } ?: false -> AstVisibility.INTERNAL
        type.modifiers.contains(Modifier.PUBLIC) -> AstVisibility.PUBLIC
        type.modifiers.contains(Modifier.PROTECTED) -> AstVisibility.PROTECTED
        else -> AstVisibility.PRIVATE
    }
}