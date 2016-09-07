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

package org.jetbrains.kotlin.psi2ir.intermediate

import org.jetbrains.kotlin.ir.expressions.IrBlockImpl
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrIfThenElseImpl
import org.jetbrains.kotlin.ir.expressions.IrOperator
import org.jetbrains.kotlin.psi2ir.generators.GeneratorWithScope
import org.jetbrains.kotlin.psi2ir.generators.constNull
import org.jetbrains.kotlin.psi2ir.generators.equalsNull
import org.jetbrains.kotlin.types.typeUtil.makeNullable


class SafeCallReceiver(
        val generator: GeneratorWithScope,
        val startOffset: Int,
        val endOffset: Int,
        val explicitReceiver: IrExpression,
        val implicitDispatchReceiverValue: Value?
) : CallReceiver {
    override fun call(withDispatchAndExtensionReceivers: (Value?, Value?) -> IrExpression): IrExpression {
        val irTmp = generator.scope.createTemporaryVariable(explicitReceiver, "safe_receiver")
        val safeReceiverValue = VariableLValue(irTmp)

        val dispatchReceiverValue: Value
        val extensionReceiverValue: Value?
        if (implicitDispatchReceiverValue != null) {
            dispatchReceiverValue = implicitDispatchReceiverValue
            extensionReceiverValue = safeReceiverValue
        }
        else {
            dispatchReceiverValue = safeReceiverValue
            extensionReceiverValue = null
        }

        val irResult = withDispatchAndExtensionReceivers(dispatchReceiverValue, extensionReceiverValue)
        val resultType = irResult.type?.makeNullable()

        val irBlock = IrBlockImpl(startOffset, endOffset, resultType, resultType != null, IrOperator.SAFE_CALL)

        irBlock.addStatement(irTmp)

        val irIfThenElse = IrIfThenElseImpl(startOffset, endOffset, resultType,
                                            generator.equalsNull(startOffset, endOffset, safeReceiverValue.load()),
                                            generator.constNull(startOffset, endOffset),
                                            irResult,
                                            IrOperator.SAFE_CALL)
        irBlock.addStatement(irIfThenElse)

        return irBlock
    }
}