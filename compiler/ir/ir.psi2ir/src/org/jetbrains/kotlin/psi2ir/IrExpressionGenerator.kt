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
import org.jetbrains.kotlin.resolve.constants.IntValue
import org.jetbrains.kotlin.resolve.constants.StringValue
import org.jetbrains.kotlin.resolve.constants.evaluate.ConstantExpressionEvaluator

class IrExpressionGenerator(
        override val context: IrGeneratorContext,
        val containingFile: PsiSourceManager.PsiFileEntry
) : KtVisitor<IrExpression, Nothing?>(), IrGenerator {
    fun generateExpression(ktExpression: KtExpression) = ktExpression.irExpr()

    private fun KtElement.irExpr(): IrExpression = accept(this@IrExpressionGenerator, null)
    private fun KtElement.loc() = this@IrExpressionGenerator.containingFile.getSourceLocationForElement(this)
    private fun KtExpression.type() = getType(this) ?: TODO("no type for expression")

    override fun visitExpression(expression: KtExpression, data: Nothing?): IrExpression =
            IrDummyExpression(expression.loc(), expression.type())

    override fun visitBlockExpression(expression: KtBlockExpression, data: Nothing?): IrExpression {
        val irBlock = IrBlockExpressionImpl(expression.loc(), expression.type())
        expression.statements.forEach { irBlock.addChildExpression(it.irExpr()) }
        return irBlock
    }

    override fun visitReturnExpression(expression: KtReturnExpression, data: Nothing?): IrExpression =
            IrReturnExpressionImpl(expression.loc(), expression.type(),
                                   expression.returnedExpression?.irExpr())

    override fun visitConstantExpression(expression: KtConstantExpression, data: Nothing?): IrExpression {
        val compileTimeConstant = ConstantExpressionEvaluator.getConstant(expression, context.bindingContext)
                                  ?: error("KtConstantExpression was not evaluated: ${expression.text}")
        val constantValue = compileTimeConstant.toConstantValue(expression.type())
        val constantType = constantValue.type

        return when (constantValue) {
            is StringValue ->
                IrStringLiteralImpl(expression.loc(), constantType, constantValue.value)
            is IntValue ->
                IrIntLiteralImpl(expression.loc(), constantType, constantValue.value)
            else ->
                TODO("handle other literal types: ${constantValue.type}")
        }
    }

    override fun visitStringTemplateExpression(expression: KtStringTemplateExpression, data: Nothing?): IrExpression {
        if (expression.entries.size == 1 && expression.entries[0] is KtLiteralStringTemplateEntry) {
            return expression.entries[0].irExpr()
        }

        val irStringTemplate = IrStringTemplateExpressionImpl(expression.loc(), expression.type())
        expression.entries.forEach { irStringTemplate.addStringTemplateEntry(it.irExpr()) }
        return irStringTemplate
    }

    override fun visitLiteralStringTemplateEntry(entry: KtLiteralStringTemplateEntry, data: Nothing?): IrExpression =
            IrStringLiteralImpl(entry.loc(), context.builtIns.stringType, entry.text)
}
