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

import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOriginKind
import org.jetbrains.kotlin.ir.declarations.IrLocalVariableImpl
import org.jetbrains.kotlin.ir.descriptors.IrTemporaryVariableDescriptor
import org.jetbrains.kotlin.ir.descriptors.IrTemporaryVariableDescriptorImpl
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.calls.callUtil.isSafeCall
import org.jetbrains.kotlin.resolve.calls.model.*
import org.jetbrains.kotlin.resolve.scopes.receivers.*
import org.jetbrains.kotlin.types.KotlinType
import java.util.*

class IrCallGenerator(
        override val context: IrGeneratorContext,
        val irExpressionGenerator: IrExpressionGenerator
) : IrGenerator {
    fun generateCall(
            ktExpression: KtExpression,
            resolvedCall: ResolvedCall<out CallableDescriptor>,
            operator: IrOperator? = null,
            superQualifier: ClassDescriptor? = null
    ): IrExpression {
        val descriptor = resolvedCall.resultingDescriptor
        return when (descriptor) {
            is PropertyDescriptor ->
                IrGetPropertyExpressionImpl(
                        ktExpression.startOffset, ktExpression.endOffset, getTypeOrFail(ktExpression),
                        resolvedCall.call.isSafeCall(), descriptor
                ).apply {
                    dispatchReceiver = generateReceiver(ktExpression, resolvedCall.dispatchReceiver)
                    extensionReceiver = generateReceiver(ktExpression, resolvedCall.extensionReceiver)
                }
            is FunctionDescriptor ->
                generateFunctionCall(descriptor, ktExpression, operator, resolvedCall, superQualifier)
            else ->
                TODO("Unexpected callable descriptor: $descriptor ${descriptor.javaClass.simpleName}")
        }
    }

    private fun ResolvedCall<*>.requiresArgumentReordering(): Boolean {
        var lastValueParameterIndex = -1
        for (valueArgument in call.valueArguments) {
            val argumentMapping = getArgumentMapping(valueArgument)
            if (argumentMapping !is ArgumentMatch || argumentMapping.isError()) {
                error("Value argument in function call is mapped with error")
            }
            val argumentIndex = argumentMapping.valueParameter.index
            if (argumentIndex < lastValueParameterIndex) return true
            lastValueParameterIndex = argumentIndex
        }
        return false
    }

    private fun generateFunctionCall(
            descriptor: FunctionDescriptor,
            ktExpression: KtExpression,
            operator: IrOperator?,
            resolvedCall: ResolvedCall<out CallableDescriptor>,
            superQualifier: ClassDescriptor?
    ): IrExpression {
        val resultType = getTypeOrFail(ktExpression)
        val irCall = IrCallExpressionImpl(
                ktExpression.startOffset, ktExpression.endOffset, resultType,
                descriptor, resolvedCall.call.isSafeCall(), operator, superQualifier
        )
        irCall.dispatchReceiver = generateReceiver(ktExpression, resolvedCall.dispatchReceiver)
        irCall.extensionReceiver = generateReceiver(ktExpression, resolvedCall.extensionReceiver)

        return if (resolvedCall.requiresArgumentReordering()) {
            generateCallWithArgumentReordering(irCall, ktExpression, resolvedCall, resultType)
        }
        else {
            irCall.apply {
                val valueArguments = resolvedCall.valueArgumentsByIndex
                for (index in valueArguments!!.indices) {
                    val valueArgument = valueArguments[index]
                    val irArgument = generateValueArgument(valueArgument) ?: continue
                    irCall.putValueArgument(index, irArgument)
                }
            }
        }
    }

    private fun generateCallWithArgumentReordering(
            irCall: IrCallExpression,
            ktExpression: KtExpression,
            resolvedCall: ResolvedCall<out CallableDescriptor>,
            resultType: KotlinType
    ): IrExpression {
        val valueArgumentsInEvaluationOrder = resolvedCall.valueArguments.values
        val hasResult = isUsedAsExpression(ktExpression)
        val irBlock = IrBlockExpressionImpl(ktExpression.startOffset, ktExpression.endOffset, resultType, hasResult)

        val temporaryVariablesForValueArguments = HashMap<ResolvedValueArgument, Pair<VariableDescriptor, IrExpression>>()
        for (valueArgument in valueArgumentsInEvaluationOrder) {
            val irArgument = generateValueArgument(valueArgument) ?: continue
            val irTemporary = irExpressionGenerator.declarationFactory.createTemporaryVariable(irArgument)
            val irTemporaryDeclaration = IrLocalVariableDeclarationExpressionImpl(irArgument.startOffset, irArgument.endOffset, irArgument.type)
            irTemporaryDeclaration.childDeclaration = irTemporary

            irBlock.addChildExpression(irTemporaryDeclaration)

            temporaryVariablesForValueArguments[valueArgument] = Pair(irTemporary.descriptor, irArgument)
        }

        for ((index, valueArgument) in resolvedCall.valueArgumentsByIndex!!.withIndex()) {
            val (temporaryDescriptor, irArgument) = temporaryVariablesForValueArguments[valueArgument]!!
            val irGetTemporary = IrGetVariableExpressionImpl(irArgument.startOffset, irArgument.endOffset,
                                                             irArgument.type, temporaryDescriptor)
            irCall.putValueArgument(index, irGetTemporary)
        }

        irBlock.addChildExpression(irCall)

        return irBlock
    }

    private fun generateReceiver(ktExpression: KtExpression, receiver: ReceiverValue?): IrExpression? =
            when (receiver) {
                is ImplicitClassReceiver ->
                    IrThisExpressionImpl(ktExpression.startOffset, ktExpression.startOffset, receiver.type, receiver.classDescriptor)
                is ThisClassReceiver ->
                    (receiver as? ExpressionReceiver)?.expression?.let { receiverExpression ->
                        IrThisExpressionImpl(receiverExpression.startOffset, receiverExpression.endOffset, receiver.type, receiver.classDescriptor)
                    } ?: TODO("Non-implicit ThisClassReceiver should be an expression receiver")
                is ExpressionReceiver ->
                    irExpressionGenerator.generateExpression(receiver.expression)
                is ClassValueReceiver ->
                    IrGetObjectValueExpressionImpl(receiver.expression.startOffset, receiver.expression.endOffset, receiver.type,
                                                   receiver.classQualifier.descriptor)
                is ExtensionReceiver ->
                    IrGetExtensionReceiverExpressionImpl(ktExpression.startOffset, ktExpression.startOffset, receiver.type,
                                                         receiver.declarationDescriptor.extensionReceiverParameter!!)
                null ->
                    null
                else ->
                    TODO("Receiver: ${receiver.javaClass.simpleName}")
            }

    private fun generateValueArgument(valueArgument: ResolvedValueArgument): IrExpression? {
        when (valueArgument) {
            is DefaultValueArgument ->
                return null
            is ExpressionValueArgument ->
                return irExpressionGenerator.generateExpression(valueArgument.valueArgument!!.getArgumentExpression()!!)
            is VarargValueArgument ->
                TODO("vararg")
            else ->
                TODO("Unexpected valueArgument: ${valueArgument.javaClass.simpleName}")
        }
    }
}
