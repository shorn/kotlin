/*
 * Copyright 2010-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.kotlin.psi2ir

import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.PropertyDescriptor
import org.jetbrains.kotlin.descriptors.PropertyGetterDescriptor
import org.jetbrains.kotlin.descriptors.PropertySetterDescriptor
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.IrBody
import org.jetbrains.kotlin.ir.expressions.IrExpressionBodyImpl
import org.jetbrains.kotlin.ir.expressions.IrReturnExpressionImpl
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.jetbrains.kotlin.resolve.BindingContext

interface IrDeclarationGenerator : IrGenerator

abstract class IrDeclarationGeneratorBase(
        override val context: IrGeneratorContext,
        val container: IrDeclarationOwnerN,
        val declarationFactory: IrDeclarationFactoryBase
) : IrDeclarationGenerator {
    private fun <D : IrMemberDeclaration> D.register(): D =
            apply { container.addChildDeclaration(this) }

    fun generateAnnotationEntries(annotationEntries: List<KtAnnotationEntry>) {
        // TODO create IrAnnotation's for each KtAnnotationEntry
    }

    fun generateMemberDeclaration(ktDeclaration: KtDeclaration) {
        // TODO visitor?
        when (ktDeclaration) {
            is KtNamedFunction ->
                generateFunctionDeclaration(ktDeclaration)
            is KtProperty ->
                generatePropertyDeclaration(ktDeclaration)
            is KtClassOrObject ->
                TODO("classOrObject")
            is KtTypeAlias ->
                TODO("typealias")
        }
    }

    fun generateFunctionDeclaration(ktNamedFunction: KtNamedFunction) {
        val functionDescriptor = getOrFail(BindingContext.FUNCTION, ktNamedFunction) { "unresolved fun" }
        val body = generateExpressionBody(functionDescriptor, ktNamedFunction.bodyExpression ?: TODO("function without body expression"))
        declarationFactory.createFunction(ktNamedFunction, functionDescriptor, body).register()
    }

    fun generatePropertyDeclaration(ktProperty: KtProperty) {
        val propertyDescriptor = getPropertyDescriptor(ktProperty)
        if (ktProperty.hasDelegate()) TODO("handle delegated property")
        val initializer = ktProperty.initializer?.let { generateExpressionBody(propertyDescriptor, it) }
        val irProperty = declarationFactory.createSimpleProperty(ktProperty, propertyDescriptor, initializer).register()
        ktProperty.getter?.let { ktGetter ->
            val accessorDescriptor = getOrFail(BindingContext.PROPERTY_ACCESSOR, ktGetter) { "unresolved getter" }
            val getterDescriptor = accessorDescriptor as? PropertyGetterDescriptor ?: TODO("not a getter?")
            val getterBody = generateExpressionBody(getterDescriptor, ktGetter.bodyExpression ?: TODO("default getter"))
            declarationFactory.createPropertyGetter(ktGetter, irProperty, getterDescriptor, getterBody).register()
        }
        ktProperty.setter?.let { ktSetter ->
            val accessorDescriptor = getOrFail(BindingContext.PROPERTY_ACCESSOR, ktSetter) { "unresolved setter" }
            val setterDescriptor = accessorDescriptor as? PropertySetterDescriptor ?: TODO("not a setter?")
            val setterBody = generateExpressionBody(setterDescriptor, ktSetter.bodyExpression ?: TODO("default setter"))
            declarationFactory.createPropertySetter(ktSetter, irProperty, setterDescriptor, setterBody).register()
        }
    }

    private fun getPropertyDescriptor(ktProperty: KtProperty): PropertyDescriptor {
        val variableDescriptor = getOrFail(BindingContext.VARIABLE, ktProperty) { "unresolved property" }
        val propertyDescriptor = variableDescriptor as? PropertyDescriptor ?: TODO("not a property?")
        return propertyDescriptor
    }

    fun generateExpressionBody(scopeOwner: DeclarationDescriptor, ktBody: KtExpression): IrBody =
            IrExpressionBodyImpl(ktBody.startOffset, ktBody.endOffset).apply {
                argument = IrExpressionGenerator(context, IrLocalDeclarationsFactory(scopeOwner)).generateExpression(ktBody)
            }
}