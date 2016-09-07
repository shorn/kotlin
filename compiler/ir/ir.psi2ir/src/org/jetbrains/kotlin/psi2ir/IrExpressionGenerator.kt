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

import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.jetbrains.kotlin.resolve.constants.IntValue
import org.jetbrains.kotlin.resolve.constants.StringValue
import org.jetbrains.kotlin.resolve.constants.evaluate.ConstantExpressionEvaluator

class IrExpressionGenerator(
        override val context: IrGeneratorContext,
        val fileElementFactory: IrFileElementFactory
) : KtVisitor<IrExpressionBase, Nothing?>(), IrGenerator {
    fun generateExpression(ktExpression: KtExpression) = ktExpression.generate()

    private fun KtElement.generate(): IrExpressionBase = accept(this@IrExpressionGenerator, null)
    private fun KtExpression.type() = getType(this) ?: TODO("no type for expression")

    override fun visitExpression(expression: KtExpression, data: Nothing?): IrExpressionBase =
            IrDummyExpression(expression.startOffset, expression.endOffset, expression.type())

    override fun visitBlockExpression(expression: KtBlockExpression, data: Nothing?): IrExpressionBase {
        val irBlock = IrBlockExpressionImpl(expression.startOffset, expression.endOffset, expression.type())
        expression.statements.forEach { irBlock.childExpressions.add(it.generate()) }
        return irBlock
    }

    override fun visitReturnExpression(expression: KtReturnExpression, data: Nothing?): IrExpressionBase =
            IrReturnExpressionImpl(expression.startOffset, expression.endOffset, expression.type())
                    .apply { this.childExpression = expression.returnedExpression?.generate() }

    override fun visitConstantExpression(expression: KtConstantExpression, data: Nothing?): IrExpressionBase {
        val compileTimeConstant = ConstantExpressionEvaluator.getConstant(expression, context.bindingContext)
                                  ?: error("KtConstantExpression was not evaluated: ${expression.text}")
        val constantValue = compileTimeConstant.toConstantValue(expression.type())
        val constantType = constantValue.type

        return when (constantValue) {
            is StringValue ->
                IrLiteralExpressionImpl.string(expression.startOffset, expression.endOffset, constantType, constantValue.value)
            is IntValue ->
                IrLiteralExpressionImpl.int(expression.startOffset, expression.endOffset, constantType, constantValue.value)
            else ->
                TODO("handle other literal types: ${constantValue.type}")
        }
    }

    override fun visitStringTemplateExpression(expression: KtStringTemplateExpression, data: Nothing?): IrExpressionBase {
        if (expression.entries.size == 1 && expression.entries[0] is KtLiteralStringTemplateEntry) {
            return expression.entries[0].generate()
        }

        val irStringTemplate = IrStringConcatenationExpressionImpl(expression.startOffset, expression.endOffset, expression.type())
        expression.entries.forEach { irStringTemplate.addChildExpression(it.generate()) }
        return irStringTemplate
    }

    override fun visitLiteralStringTemplateEntry(entry: KtLiteralStringTemplateEntry, data: Nothing?): IrExpressionBase =
            IrLiteralExpressionImpl.string(entry.startOffset, entry.endOffset, context.builtIns.stringType, entry.text)
}
