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

import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.IrModule
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi2ir.generators.IrGeneratorContext
import org.jetbrains.kotlin.psi2ir.generators.IrModuleGenerator
import org.jetbrains.kotlin.psi2ir.transformations.foldStringConcatenation
import org.jetbrains.kotlin.psi2ir.transformations.inlineDesugaredBlocks
import org.jetbrains.kotlin.resolve.BindingContext

class Psi2IrTranslator(val configuration: Configuration = Configuration()) {
    class Configuration(
            val shouldInlineDesugaredBlocks: Boolean = true,
            val shouldFoldStringConcatenation: Boolean = true
    )

    fun generateModule(moduleDescriptor: ModuleDescriptor, ktFiles: List<KtFile>, bindingContext: BindingContext): IrModule {
        val irGeneratorContext = IrGeneratorContext(moduleDescriptor, bindingContext)
        val irModule = IrModuleGenerator(irGeneratorContext).generateModule(ktFiles)
        postprocess(irModule)
        return irModule
    }

    private fun postprocess(irElement: IrElement) {
        if (configuration.shouldInlineDesugaredBlocks) inlineDesugaredBlocks(irElement)
        if (configuration.shouldFoldStringConcatenation) foldStringConcatenation(irElement)
    }
}
