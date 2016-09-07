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

package org.jetbrains.kotlin.ir.expressions

import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.ir.visitors.IrElementVisitor
import org.jetbrains.kotlin.types.KotlinType


interface IrDeclarationReference : IrExpression {
    val descriptor: DeclarationDescriptor
}

interface IrVariableReference : IrDeclarationReference {
    override val descriptor: VariableDescriptor
}

interface IrSingletonReference : IrDeclarationReference {
    override val descriptor: ClassDescriptor
}

interface IrExtensionReceiverReference : IrDeclarationReference {
    override val descriptor: CallableDescriptor
}

abstract class IrDeclarationReferenceBase<out D : DeclarationDescriptor>(
        startOffset: Int,
        endOffset: Int,
        type: KotlinType,
        override val descriptor: D
) : IrTerminalExpressionBase(startOffset, endOffset, type), IrDeclarationReference

class IrVariableReferenceImpl(
        startOffset: Int,
        endOffset: Int,
        type: KotlinType,
        descriptor: VariableDescriptor
) : IrDeclarationReferenceBase<VariableDescriptor>(startOffset, endOffset, type, descriptor), IrVariableReference {
    override fun <R, D> accept(visitor: IrElementVisitor<R, D>, data: D): R =
            visitor.visitVariableReference(this, data)
}

class IrSingletonReferenceImpl(
        startOffset: Int,
        endOffset: Int,
        type: KotlinType,
        descriptor: ClassDescriptor
) : IrDeclarationReferenceBase<ClassDescriptor>(startOffset, endOffset, type, descriptor), IrSingletonReference {
    override fun <R, D> accept(visitor: IrElementVisitor<R, D>, data: D): R =
            visitor.visitSingletonReference(this, data)
}


class IrExtensionReceiverReferenceImpl(
        startOffset: Int,
        endOffset: Int,
        type: KotlinType,
        override val descriptor: CallableDescriptor
) : IrTerminalExpressionBase(startOffset, endOffset, type), IrExtensionReceiverReference {
    override fun <R, D> accept(visitor: IrElementVisitor<R, D>, data: D): R {
        return visitor.visitExtensionReceiverReference(this, data)
    }
}
