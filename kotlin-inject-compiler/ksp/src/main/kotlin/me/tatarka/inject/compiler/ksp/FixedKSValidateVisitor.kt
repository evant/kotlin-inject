package me.tatarka.inject.compiler.ksp

import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSNode
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.visitor.KSValidateVisitor

/**
 * Work-around for https://github.com/google/ksp/pull/698
 */
class FixedKSValidateVisitor(private val predicate: (KSNode?, KSNode) -> Boolean) : KSValidateVisitor(predicate) {
    override fun visitFunctionDeclaration(function: KSFunctionDeclaration, data: KSNode?): Boolean {
        if (!predicate(function, function.returnType!!)) {
            return true
        }
        return super.visitFunctionDeclaration(function, data)
    }

    override fun visitPropertyDeclaration(property: KSPropertyDeclaration, data: KSNode?): Boolean {
        if (predicate(property, property.type) && !property.type.accept(this, data)) {
            return false
        }
        if (!this.visitDeclaration(property, data)) {
            return false
        }
        return true
    }
}