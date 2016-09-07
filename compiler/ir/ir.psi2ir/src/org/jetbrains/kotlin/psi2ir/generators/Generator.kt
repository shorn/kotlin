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

import org.jetbrains.kotlin.descriptors.CallableDescriptor
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.ir.expressions.IrDummyExpression
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.calls.callUtil.getResolvedCall
import org.jetbrains.kotlin.resolve.calls.callUtil.isSafeCall
import org.jetbrains.kotlin.resolve.calls.model.ResolvedCall
import org.jetbrains.kotlin.resolve.descriptorUtil.classValueType
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.typeUtil.makeNullable
import org.jetbrains.kotlin.util.slicedMap.ReadOnlySlice

interface IrGenerator {
    val context: GeneratorContext
}

fun <K, V : Any> IrGenerator.get(slice: ReadOnlySlice<K, V>, key: K): V? =
        context.bindingContext[slice, key]

fun <K, V : Any> IrGenerator.getOrFail(slice: ReadOnlySlice<K, V>, key: K): V =
        context.bindingContext[slice, key] ?: throw RuntimeException("No $slice for $key")

inline fun <K, V : Any> IrGenerator.getOrFail(slice: ReadOnlySlice<K, V>, key: K, message: (K) -> String): V =
        context.bindingContext[slice, key] ?: throw RuntimeException(message(key))

fun IrGenerator.getInferredTypeWithSmartcasts(key: KtExpression): KotlinType? =
        context.bindingContext.getType(key)

fun IrGenerator.getExpectedTypeForLastInferredCall(key: KtExpression): KotlinType? =
        get(BindingContext.EXPECTED_EXPRESSION_TYPE, key)

fun IrGenerator.getInferredTypeWithSmarcastsOrFail(key: KtExpression): KotlinType =
        getInferredTypeWithSmartcasts(key) ?: TODO("No type for expression: ${key.text}")

fun IrGenerator.isUsedAsExpression(ktExpression: KtExpression) =
        get(BindingContext.USED_AS_EXPRESSION, ktExpression) ?: false

fun IrGenerator.getResolvedCall(key: KtExpression): ResolvedCall<out CallableDescriptor>? =
        key.getResolvedCall(context.bindingContext)

fun IrGenerator.getReturnType(key: KtExpression): KotlinType? {
    val resolvedCall = getResolvedCall(key)
    if (resolvedCall != null) {
        return getReturnType(resolvedCall)
    }

    if (key is KtBlockExpression) {
        if (!isUsedAsExpression(key)) return null
        return getReturnType(key.statements.last())
    }

    throw AssertionError("Unexpected expression: $key")
}

fun getReturnType(resolvedCall: ResolvedCall<*>): KotlinType {
    val descriptor = resolvedCall.resultingDescriptor
    return when (descriptor) {
        is ClassDescriptor ->
            descriptor.classValueType ?: throw AssertionError("Class descriptor without companion object: $descriptor")
        is CallableDescriptor -> {
            val returnType = descriptor.returnType ?: throw AssertionError("Callable descriptor without return type: $descriptor")
            if (resolvedCall.call.isSafeCall())
                returnType.makeNullable()
            else
                returnType
        }
        else ->
            throw AssertionError("Unexpected desciptor in resolved call: $descriptor")
    }
}

fun IrGenerator.createDummyExpression(ktExpression: KtExpression, description: String): IrDummyExpression =
        IrDummyExpression(ktExpression.startOffset, ktExpression.endOffset, getInferredTypeWithSmartcasts(ktExpression), description)