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

import org.jetbrains.kotlin.ir.declarations.IrFileImpl
import org.jetbrains.kotlin.resolve.BindingContext

class IrModuleGenerator(override val context: IrGeneratorContext) : IrDeclarationGenerator {
    fun generateModuleContent() {
        for (ktFile in context.inputFiles) {
            val packageFragmentDescriptor = getOrFail(BindingContext.FILE_TO_PACKAGE_FRAGMENT, ktFile) { "no package fragment for file" }
            val fileEntry = context.sourceManager.getOrCreateFileEntry(ktFile)
            val fileName = fileEntry.getRecognizableName()
            val irFile = IrFileImpl(fileEntry, fileName, packageFragmentDescriptor)
            context.sourceManager.putFileEntry(irFile, fileEntry)
            context.irModule.addFile(irFile)
            val irFileElementFactory = IrDeclarationFactory()
            val generator = IrFileGenerator(ktFile, context, irFile, this, irFileElementFactory)
            generator.generateFileContent()
        }
    }
}