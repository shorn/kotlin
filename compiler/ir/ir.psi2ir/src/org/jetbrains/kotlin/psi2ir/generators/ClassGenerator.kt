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

package org.jetbrains.kotlin.psi2ir.generators

import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.IrExpressionBodyImpl
import org.jetbrains.kotlin.ir.expressions.IrGetVariableImpl
import org.jetbrains.kotlin.ir.expressions.IrOperator
import org.jetbrains.kotlin.psi.KtAnonymousInitializer
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtSecondaryConstructor
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.jetbrains.kotlin.resolve.BindingContext

class ClassGenerator(val declarationGenerator: DeclarationGenerator) : Generator {
    override val context: GeneratorContext get() = declarationGenerator.context

    fun generateClass(ktClassOrObject: KtClassOrObject): IrClass {
        val descriptor = getOrFail(BindingContext.CLASS, ktClassOrObject)
        val irClass = IrClassImpl(ktClassOrObject.startOffset, ktClassOrObject.endOffset, IrDeclarationOrigin.DEFINED, descriptor)

        generatePrimaryConstructor(irClass, ktClassOrObject)

        generatePropertiesDeclaredInPrimaryConstructor(irClass, ktClassOrObject)

        val shouldGenerateNestedInitializers = generateMembersDeclaredInClassBody(irClass, ktClassOrObject)

        if (shouldGenerateNestedInitializers) {
            irClass.nestedInitializers = BodyGenerator(descriptor, context).generateNestedInitializersBody(ktClassOrObject)
        }

        return irClass
    }

    private fun generatePrimaryConstructor(irClass: IrClassImpl, ktClassOrObject: KtClassOrObject) {
        val primaryConstructorDescriptor = irClass.descriptor.unsubstitutedPrimaryConstructor ?: return

        val irPrimaryConstructor = IrFunctionImpl(ktClassOrObject.startOffset, ktClassOrObject.endOffset, IrDeclarationOrigin.DEFINED,
                                                  primaryConstructorDescriptor)

        irPrimaryConstructor.body = BodyGenerator(primaryConstructorDescriptor, context).generatePrimaryConstructorBody(ktClassOrObject)

        irClass.addMember(irPrimaryConstructor)
    }

    private fun generatePropertiesDeclaredInPrimaryConstructor(irClass: IrClassImpl, ktClassOrObject: KtClassOrObject) {
        ktClassOrObject.getPrimaryConstructor()?.let { ktPrimaryConstructor ->
            for (ktParameter in ktPrimaryConstructor.valueParameters) {
                if (ktParameter.hasValOrVar()) {
                    irClass.addMember(generatePropertyForPrimaryConstructorParameter(ktParameter))
                }
            }
        }
    }

    private fun generateMembersDeclaredInClassBody(irClass: IrClassImpl, ktClassOrObject: KtClassOrObject): Boolean {
        var hasConstructorsWithNestedInitializersCall = false

        ktClassOrObject.getBody()?.let { ktClassBody ->
            for (ktDeclaration in ktClassBody.declarations) {
                if (ktDeclaration is KtAnonymousInitializer) continue

                val irMember =
                        if (ktDeclaration is KtSecondaryConstructor && isConstructorDelegatingToSuper(ktDeclaration, irClass.descriptor)) {
                            hasConstructorsWithNestedInitializersCall = true
                            declarationGenerator.generateSecondaryConstructorWithNestedInitializers(ktDeclaration, ktClassOrObject)
                        }
                        else
                            declarationGenerator.generateMemberDeclaration(ktDeclaration)

                irClass.addMember(irMember)
                if (irMember is IrProperty) {
                    irMember.getter?.let { irClass.addMember(it) }
                    irMember.setter?.let { irClass.addMember(it) }
                }
            }
        }

        return hasConstructorsWithNestedInitializersCall
    }

    private fun isConstructorDelegatingToSuper(ktConstructor: KtSecondaryConstructor, classOwner: ClassDescriptor): Boolean {
        val delegatingResolvedCall = getResolvedCall(ktConstructor.getDelegationCall())!!
        val calleeOwner = delegatingResolvedCall.resultingDescriptor.containingDeclaration as ClassDescriptor
        return calleeOwner != classOwner
    }

    private fun generatePropertyForPrimaryConstructorParameter(ktParameter: KtParameter): IrDeclaration {
        val valueParameterDescriptor = getOrFail(BindingContext.VALUE_PARAMETER, ktParameter)
        val propertyDescriptor = getOrFail(BindingContext.PRIMARY_CONSTRUCTOR_PARAMETER, ktParameter)
        val irProperty = IrSimplePropertyImpl(ktParameter.startOffset, ktParameter.endOffset, IrDeclarationOrigin.DEFINED, propertyDescriptor)
        val irGetParameter = IrGetVariableImpl(ktParameter.startOffset, ktParameter.endOffset,
                                               valueParameterDescriptor, IrOperator.INITIALIZE_PROPERTY_FROM_PARAMETER)
        irProperty.valueInitializer = IrExpressionBodyImpl(ktParameter.startOffset, ktParameter.endOffset, irGetParameter)
        return irProperty
    }
}
