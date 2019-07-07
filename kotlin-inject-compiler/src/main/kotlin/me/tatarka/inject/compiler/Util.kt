package me.tatarka.inject.compiler

import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.asTypeName
import me.tatarka.inject.annotations.Scope
import java.lang.IllegalArgumentException
import javax.annotation.processing.Messager
import javax.lang.model.element.AnnotationMirror
import javax.lang.model.element.Element
import javax.lang.model.element.Name
import javax.lang.model.element.TypeElement
import javax.lang.model.type.*
import javax.lang.model.util.SimpleTypeVisitor7
import javax.lang.model.util.Types
import javax.tools.Diagnostic

fun Element.scope(): AnnotationMirror? = annotationMirrors.find {
    it.annotationType.asElement().getAnnotation(Scope::class.java) != null
}

fun Element.scopeType(): TypeElement? = scope()?.let { it.annotationType.asElement() as TypeElement }

fun Name.asScopedProp(): String = "_" + toString().decapitalize()

fun TypeElement.recurseParents(typeUtils: Types, f: (DeclaredType, TypeElement) -> Unit) {
    f(asType() as DeclaredType, this)
    val superclass = superclass
    if (superclass.toString() != "java.lang.Object" && superclass !is NoType) {
        f(superclass as DeclaredType, typeUtils.asElement(superclass) as TypeElement)
    }
    for (iface in interfaces) {
        f(iface as DeclaredType, typeUtils.asElement(iface) as TypeElement)
    }
}

fun TypeMirror.resolve(messager: Messager, declaredType: TypeMirror, type: TypeMirror): TypeMirror {
    if (kind == TypeKind.DECLARED) {
        return this
    }
    if (kind == TypeKind.TYPEVAR && declaredType is DeclaredType) {
        type.accept(object: SimpleTypeVisitor7<TypeMirror?, Void?>() {
            override fun visitTypeVariable(typeVariable: TypeVariable, p: Void?): TypeMirror? {
                messager.printMessage(Diagnostic.Kind.WARNING, "var: ${typeVariable}")
                return null
            }

        }, null)


        for (typeArgument in declaredType.typeArguments) {

            messager.printMessage(Diagnostic.Kind.WARNING, "arg: ${typeArgument}")

        }



//        for (typeParameter in type.typeParameters) {
//            if (typeParameter.asType() == this) {
//                return typeParameter.genericElement.asType()
//            }
//        }
    }
    throw IllegalArgumentException("can't handle kind: $kind")
}