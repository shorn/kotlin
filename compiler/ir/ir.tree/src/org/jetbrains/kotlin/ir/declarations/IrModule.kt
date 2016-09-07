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

import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.ir.*
import org.jetbrains.kotlin.ir.descriptors.IrBuiltIns
import org.jetbrains.kotlin.ir.visitors.IrElementVisitor
import java.util.*

interface IrModule : IrElement {
    override val startOffset: Int get() = UNDEFINED_OFFSET
    override val endOffset: Int get() = UNDEFINED_OFFSET

    override val parent: IrElement? get() = null
    override val slot: Int get() = MODULE_SLOT
    override fun setTreeLocation(newParent: IrElement?, newSlot: Int) {
        throw AssertionError("IrModule can't have a parent element")
    }

    val descriptor: ModuleDescriptor
    val irBuiltins: IrBuiltIns
    val files: List<IrFile>
}

class IrModuleImpl(
        override val descriptor: ModuleDescriptor,
        override val irBuiltins: IrBuiltIns
) : IrModule {
    override val files: MutableList<IrFile> = ArrayList()

    fun addFile(file: IrFile) {
        file.assertDetached()
        file.setTreeLocation(this, files.size)
        files.add(file)
    }

    override fun getChild(slot: Int): IrElement? =
            files.getOrNull(slot)

    override fun replaceChild(slot: Int, newChild: IrElement) {
        newChild.assertDetached()
        files.getOrNull(slot)?.detach() ?: throwNoSuchSlot(slot)
    }

    override fun <R, D> accept(visitor: IrElementVisitor<R, D>, data: D): R =
            visitor.visitModule(this, data)

    override fun <D> acceptChildren(visitor: IrElementVisitor<Unit, D>, data: D) {
        files.forEach { it.accept(visitor, data) }
    }
}