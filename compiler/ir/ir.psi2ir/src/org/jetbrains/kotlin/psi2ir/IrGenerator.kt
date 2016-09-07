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

import org.jetbrains.kotlin.descriptors.CallableDescriptor
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.calls.callUtil.getResolvedCall
import org.jetbrains.kotlin.resolve.calls.model.ResolvedCall
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.util.slicedMap.ReadOnlySlice

interface IrGenerator {
    val context: IrGeneratorContext
}

fun IrGenerator.getType(key: KtExpression): KotlinType? =
        context.bindingContext.getType(key)

fun IrGenerator.getTypeOrFail(key: KtExpression): KotlinType =
        getType(key) ?: TODO("No type for expression: ${key.text}")

fun <K, V : Any> IrGenerator.get(slice: ReadOnlySlice<K, V>, key: K): V? =
        context.bindingContext[slice, key]

inline fun <K, V : Any> IrGenerator.getOrFail(slice: ReadOnlySlice<K, V>, key: K, message: (K) -> String): V =
        context.bindingContext[slice, key] ?: throw RuntimeException(message(key))

fun IrGenerator.isUsedAsExpression(ktExpression: KtExpression) =
        get(BindingContext.USED_AS_EXPRESSION, ktExpression) ?: false

inline fun <K, V : Any> IrGenerator.getOrElse(slice: ReadOnlySlice<K, V>, key: K, otherwise: (K) -> V): V =
        context.bindingContext[slice, key] ?: otherwise(key)

fun IrGenerator.getResolvedCall(key: KtExpression): ResolvedCall<out CallableDescriptor>? =
        key.getResolvedCall(context.bindingContext)