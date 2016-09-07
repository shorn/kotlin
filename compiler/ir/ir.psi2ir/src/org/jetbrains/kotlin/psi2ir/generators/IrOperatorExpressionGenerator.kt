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

package org.jetbrains.kotlin.psi2ir.generators

import com.intellij.psi.tree.IElementType
import org.jetbrains.kotlin.descriptors.PropertyDescriptor
import org.jetbrains.kotlin.descriptors.impl.LocalVariableDescriptor
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrOperator
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.calls.callUtil.isSafeCall
import org.jetbrains.kotlin.resolve.calls.model.ResolvedCall
import org.jetbrains.kotlin.types.KotlinType

val KT_OPERATOR_TO_IR_OPERATOR = hashMapOf(
        KtTokens.PLUSEQ to IrOperator.PLUSEQ,
        KtTokens.MINUSEQ to IrOperator.MINUSEQ,
        KtTokens.MULTEQ to IrOperator.MULTEQ,
        KtTokens.DIVEQ to IrOperator.DIVEQ,
        KtTokens.PERCEQ to IrOperator.PERCEQ
)

class IrOperatorExpressionGenerator(val irStatementGenerator: IrStatementGenerator): IrGenerator {
    override val context: IrGeneratorContext get() = irStatementGenerator.context

    fun generateBinaryExpression(expression: KtBinaryExpression): IrExpression {
        val ktOperator = expression.operationReference.getReferencedNameElementType()

        return when (ktOperator) {
            KtTokens.EQ -> generateAssignment(expression)
            in KtTokens.AUGMENTED_ASSIGNMENTS -> generateAugmentedAssignment(expression, ktOperator)
            else -> createDummyExpression(expression, ktOperator.toString())
        }
    }

    private fun generateAugmentedAssignment(expression: KtBinaryExpression, ktOperator: IElementType): IrExpression {
        val ktLeft = expression.left!!

        val irOperator = getIrOperator(ktOperator)
        val lhs = generateReferenceForAssignmentLhs(ktLeft, irOperator)

        val isSimpleAssignment = get(BindingContext.VARIABLE_REASSIGNMENT, expression) ?: false

        val operatorCall = getResolvedCall(expression)!!

        val opCallGenerator = IrCallGenerator(irStatementGenerator).apply { putValue(ktLeft, lhs) }
        val irOpCall = opCallGenerator.generateCall(expression, operatorCall, irOperator)

        return if (isSimpleAssignment) {
            // Set( Op( Get(), RHS ) )
            lhs.store(irOpCall)
        }
        else {
            // Op( Get(), RHS )
            irOpCall
        }
    }

    private fun getIrOperator(ktOperator: IElementType): IrOperator {
        return KT_OPERATOR_TO_IR_OPERATOR[ktOperator] ?: TODO("Operator: $ktOperator")
    }

    private fun generateAssignment(expression: KtBinaryExpression): IrExpression {
        val ktLeft = expression.left!!
        val ktRight = expression.right!!
        val lhsReference = generateReferenceForAssignmentLhs(ktLeft, IrOperator.EQ)
        return lhsReference.store(irStatementGenerator.generateExpression(ktRight))
    }

    private fun generateReferenceForAssignmentLhs(ktLeft: KtExpression, irOperator: IrOperator?): IrReference {
        val resolvedCall = getResolvedCall(ktLeft) ?: TODO("no resolved call for LHS")
        val descriptor = resolvedCall.candidateDescriptor

        return when (descriptor) {
            is LocalVariableDescriptor ->
                if (descriptor.isDelegated)
                    TODO("Delegated local variable")
                else
                    IrVariableReferenceValue(ktLeft, irOperator, descriptor)
            is PropertyDescriptor ->
                IrCallGenerator(irStatementGenerator).run {
                    IrPropertyReferenceValue(
                            ktLeft, irOperator, descriptor,
                            generateReceiver(ktLeft, resolvedCall.dispatchReceiver, descriptor.dispatchReceiverParameter),
                            generateReceiver(ktLeft, resolvedCall.extensionReceiver, descriptor.extensionReceiverParameter),
                            resolvedCall.call.isSafeCall()
                    )
                }
            else ->
                TODO("Other cases of LHS")
        }
    }

}
