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

import org.jetbrains.kotlin.ir.declarations.IrModule
import org.jetbrains.kotlin.resolve.BindingContext

class IrModuleGenerator(override val context: IrGeneratorContext) : IrDeclarationGenerator {
    override val irDeclaration: IrModule get() = context.irModule
    override val parent: IrDeclarationGenerator? get() = null

    fun generateModuleContent() {
        for (ktFile in context.inputFiles) {
            val packageFragmentDescriptor = getOrFail(BindingContext.FILE_TO_PACKAGE_FRAGMENT, ktFile) { "no package fragment for file" }
            val irFileElementFactory = IrDeclarationFactory.create(context.irModule, context.sourceManager, ktFile, packageFragmentDescriptor)
            val irFile = irFileElementFactory.irFileImpl
            context.irModule.addFile(irFile)
            val generator = IrFileGenerator(ktFile, context, irFile, this, irFileElementFactory)
            generator.generateFileContent()
        }
    }
}