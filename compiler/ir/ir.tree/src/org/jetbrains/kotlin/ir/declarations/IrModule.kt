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
import org.jetbrains.kotlin.ir.NO_LOCATION
import org.jetbrains.kotlin.ir.visitors.IrElementVisitor
import java.util.*

interface IrModule : IrCompoundDeclaration {
    override val descriptor: ModuleDescriptor

    val files: List<IrFile>
    fun addFile(file: IrFile)
}

class IrModuleImpl(override val descriptor: ModuleDescriptor) : IrDeclarationBase(NO_LOCATION, null), IrModule {
    override val files: MutableList<IrFile> = ArrayList()
    override val childDeclarations: List<IrDeclaration> get() = files

    override fun addFile(file: IrFile) {
        files.add(file)
    }

    override fun addChildDeclaration(child: IrDeclaration) {
        if (child !is IrFile) throw IllegalArgumentException("Only IrFile can be contained in IrModule, got $child")
        addFile(child)
    }

    override fun <R, D> accept(visitor: IrElementVisitor<R, D>, data: D): R =
            visitor.visitModule(this, data)

    override fun <D> acceptChildren(visitor: IrElementVisitor<Unit, D>, data: D) {
        files.forEach { it.accept(visitor, data) }
    }
}