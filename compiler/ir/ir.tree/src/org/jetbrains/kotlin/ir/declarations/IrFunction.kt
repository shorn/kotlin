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

package org.jetbrains.kotlin.ir.declarations

import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.ir.SourceLocation
import org.jetbrains.kotlin.ir.expressions.IrBody
import org.jetbrains.kotlin.ir.visitors.IrElementVisitor

interface IrFunction : IrMemberDeclaration {
    override val descriptor: FunctionDescriptor
    val body: IrBody
}

abstract class IrFunctionBase(
        sourceLocation: SourceLocation,
        kind: IrDeclarationKind
) : IrCompoundDeclarationBase(sourceLocation, kind), IrFunction {
    override var parent: IrCompoundDeclaration? = null

    override fun setTreeLocation(parent: IrCompoundDeclaration?, index: Int) {
        this.parent = parent
        this.index = index
    }

    override fun <D> acceptChildren(visitor: IrElementVisitor<Unit, D>, data: D) {
        super.acceptChildDeclarations(visitor, data)
        body.accept(visitor, data)
    }
}

class IrFunctionImpl(
        sourceLocation: SourceLocation,
        kind: IrDeclarationKind,
        override val descriptor: FunctionDescriptor,
        override val body: IrBody
) : IrFunctionBase(sourceLocation, kind) {
    override fun <R, D> accept(visitor: IrElementVisitor<R, D>, data: D): R =
            visitor.visitFunction(this, data)
}

