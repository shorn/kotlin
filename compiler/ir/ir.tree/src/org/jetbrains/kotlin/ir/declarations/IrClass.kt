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

import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.ir.*
import org.jetbrains.kotlin.ir.expressions.IrBody
import org.jetbrains.kotlin.ir.visitors.IrElementVisitor
import java.util.*

interface IrClass : IrDeclaration {
    override val declarationKind: IrDeclarationKind
        get() = IrDeclarationKind.CLASS

    override val descriptor: ClassDescriptor

    val members: List<IrDeclaration>

    var nestedInitializers: IrBody?
}

class IrClassImpl(
        startOffset: Int,
        endOffset: Int,
        origin: IrDeclarationOrigin,
        override val descriptor: ClassDescriptor
) : IrDeclarationBase(startOffset, endOffset, origin), IrClass {
    override val members: MutableList<IrDeclaration> = ArrayList()

    fun addMember(member: IrDeclaration) {
        member.assertDetached()
        member.setTreeLocation(this, members.size)
        members.add(member)
    }

    override var nestedInitializers: IrBody? = null
        set(value) {
            value?.assertDetached()
            field?.detach()
            field = value
            value?.setTreeLocation(this, NESTED_INITIALIZERS_SLOT)
        }

    override fun getChild(slot: Int): IrElement? =
        when (slot) {
            NESTED_INITIALIZERS_SLOT -> nestedInitializers
            else -> members.getOrNull(slot)
        }


    override fun replaceChild(slot: Int, newChild: IrElement) {
        when (slot) {
            NESTED_INITIALIZERS_SLOT ->
                nestedInitializers = newChild.assertCast()
            else -> {
                newChild.assertDetached()
                members.getOrNull(slot)?.detach() ?: throwNoSuchSlot(slot)
                members[slot] = newChild.assertCast()
                newChild.setTreeLocation(this, slot)
            }
        }

    }

    override fun <R, D> accept(visitor: IrElementVisitor<R, D>, data: D): R =
            visitor.visitClass(this, data)

    override fun <D> acceptChildren(visitor: IrElementVisitor<Unit, D>, data: D) {
        nestedInitializers?.accept(visitor, data)
        members.forEach { it.accept(visitor, data) }
    }

}
