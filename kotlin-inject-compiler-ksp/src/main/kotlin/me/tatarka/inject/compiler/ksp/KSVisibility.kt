package me.tatarka.inject.compiler.ksp

import com.google.devtools.ksp.isInternal
import com.google.devtools.ksp.isProtected
import com.google.devtools.ksp.isPublic
import com.google.devtools.ksp.symbol.KSDeclaration
import me.tatarka.inject.compiler.AstVisibility
import me.tatarka.inject.compiler.VisibleElement

class KSVisibility(declaration: KSDeclaration) : VisibleElement {
    override val visibility: AstVisibility = when {
        declaration.isPublic() -> AstVisibility.PUBLIC
        declaration.isProtected() -> AstVisibility.PROTECTED
        declaration.isInternal() -> AstVisibility.INTERNAL
        else -> AstVisibility.PRIVATE
    }
}