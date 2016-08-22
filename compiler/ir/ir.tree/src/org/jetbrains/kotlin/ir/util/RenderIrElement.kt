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

package org.jetbrains.kotlin.ir.util

import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.visitors.IrElementVisitor
import org.jetbrains.kotlin.renderer.ClassifierNamePolicy
import org.jetbrains.kotlin.renderer.DescriptorRenderer
import org.jetbrains.kotlin.renderer.DescriptorRendererModifier
import org.jetbrains.kotlin.renderer.OverrideRenderingPolicy
import org.jetbrains.kotlin.types.KotlinType

fun IrElement.render() = accept(RenderIrElementVisitor(), null)

class RenderIrElementVisitor : IrElementVisitor<String, Nothing?> {
    override fun visitElement(element: IrElement, data: Nothing?): String =
            "? ${element.javaClass.simpleName}"

    override fun visitDeclaration(declaration: IrDeclaration, data: Nothing?): String =
            "? ${declaration.javaClass.simpleName} ${declaration.descriptor?.name}"

    override fun visitFile(declaration: IrFile, data: Nothing?): String =
            "IrFile ${declaration.name}"

    override fun visitFunction(declaration: IrFunction, data: Nothing?): String =
            "IrFunction ${declaration.descriptor.render()}"

    override fun visitProperty(declaration: IrProperty, data: Nothing?): String =
            "IrProperty ${declaration.descriptor.render()} getter=${declaration.getter?.name()} setter=${declaration.setter?.name()}"

    override fun visitPropertyGetter(declaration: IrPropertyGetter, data: Nothing?): String =
            "IrPropertyGetter ${declaration.descriptor.render()} property=${declaration.property?.name()}"

    override fun visitPropertySetter(declaration: IrPropertySetter, data: Nothing?): String =
            "IrPropertySetter ${declaration.descriptor.render()} property=${declaration.property?.name()}"

    override fun visitLocalVariable(declaration: IrVariable, data: Nothing?): String =
            "VAR ${declaration.descriptor.render()}"

    override fun visitExpressionBody(body: IrExpressionBody, data: Nothing?): String =
            "IrExpressionBody"

    override fun visitExpression(expression: IrExpression, data: Nothing?): String =
            "? ${expression.javaClass.simpleName} type=${expression.renderType()}"

    override fun <T> visitConst(expression: IrConst<T>, data: Nothing?): String =
            "LITERAL ${expression.kind} type=${expression.renderType()} value='${expression.value}'"

    override fun visitBlock(expression: IrBlock, data: Nothing?): String =
            "BLOCK type=${expression.renderType()} hasResult=${expression.hasResult} operator=${expression.operator}"

    override fun visitReturn(expression: IrReturn, data: Nothing?): String =
            "RETURN type=${expression.renderType()}"

    override fun visitGetExtensionReceiver(expression: IrGetExtensionReceiver, data: Nothing?): String =
            "\$RECEIVER of: ${expression.descriptor.containingDeclaration.name} type=${expression.renderType()}"

    override fun visitThisReference(expression: IrThisReference, data: Nothing?): String =
            "THIS ${expression.classDescriptor.render()} type=${expression.renderType()}"

    override fun visitCall(expression: IrCall, data: Nothing?): String =
            "CALL ${if (expression.isSafe) "?." else "."}${expression.descriptor.name} " +
            "type=${expression.renderType()} operator=${expression.operator}"

    override fun visitGetVariable(expression: IrGetVariable, data: Nothing?): String =
            "GET_VAR ${expression.descriptor.name} type=${expression.renderType()} operator=${expression.operator}"

    override fun visitSetVariable(expression: IrSetVariable, data: Nothing?): String =
            "SET_VAR ${expression.descriptor.name} type=${expression.renderType()} operator=${expression.operator}"

    override fun visitGetObjectValue(expression: IrGetObjectValue, data: Nothing?): String =
            "GET_OBJECT ${expression.descriptor.name} type=${expression.renderType()}"

    override fun visitGetEnumValue(expression: IrGetEnumValue, data: Nothing?): String =
            "GET_ENUM_VALUE ${expression.descriptor.name} type=${expression.renderType()}"

    override fun visitStringConcatenation(expression: IrStringConcatenation, data: Nothing?): String =
            "STRING_CONCATENATION type=${expression.renderType()}"

    override fun visitTypeOperator(expression: IrTypeOperatorCall, data: Nothing?): String =
            "TYPE_OP operator=${expression.operator} typeOperand=${expression.typeOperand.render()}"

    override fun visitWhen(expression: IrWhen, data: Nothing?): String =
            "WHEN type=${expression.type.render()} operator=${expression.operator}"

    override fun visitDummyDeclaration(declaration: IrDummyDeclaration, data: Nothing?): String =
            "DUMMY ${declaration.descriptor.name}"

    override fun visitDummyExpression(expression: IrDummyExpression, data: Nothing?): String =
            "DUMMY ${expression.description} type=${expression.renderType()}"

    companion object {
        private val DESCRIPTOR_RENDERER = DescriptorRenderer.withOptions {
            withDefinedIn = false
            overrideRenderingPolicy = OverrideRenderingPolicy.RENDER_OPEN_OVERRIDE
            includePropertyConstant = true
            classifierNamePolicy = ClassifierNamePolicy.FULLY_QUALIFIED
            verbose = true
            modifiers = DescriptorRendererModifier.ALL
        }

        internal fun IrDeclaration.name(): String =
                descriptor?.let { it.name.toString() } ?: "<none>"

        internal fun DeclarationDescriptor?.render(): String =
                this?.let { DESCRIPTOR_RENDERER.render(it) } ?: "<none>"

        internal fun IrExpression.renderType(): String =
                type.render()

        internal fun KotlinType?.render(): String =
                this?.let { DESCRIPTOR_RENDERER.renderType(it) } ?: "<no-type>"
    }
}