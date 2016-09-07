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

import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.IrElementBase
import org.jetbrains.kotlin.ir.SourceLocation

interface IrDeclaration : IrElement {
    override val parent: IrDeclarationOwner?
    val indexInParent: Int

    val descriptor: DeclarationDescriptor?

    val declarationKind: IrDeclarationKind
    val originKind: IrDeclarationOriginKind

    companion object {
        const val DETACHED_INDEX = Int.MIN_VALUE
    }
}

enum class IrDeclarationKind {
    MODULE,
    FILE,
    FUNCTION,
    PROPERTY,
    PROPERTY_GETTER,
    PROPERTY_SETTER,
    CLASS
}

enum class IrDeclarationOriginKind {
    DEFINED,
    DEFAULT_PROPERTY_ACCESSOR,
}

abstract class IrDeclarationBase(
        sourceLocation: SourceLocation,
        override val originKind: IrDeclarationOriginKind
) : IrElementBase(sourceLocation), IrDeclaration {
    override var indexInParent: Int = IrDeclaration.DETACHED_INDEX
}
