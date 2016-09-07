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

import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.descriptors.CallableDescriptor
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import java.util.*

class BodyGenerator(val scopeOwner: CallableDescriptor, override val context: GeneratorContext): GeneratorWithScope {
    override val scope = Scope(scopeOwner)
    private val loopTable = HashMap<KtLoopExpression, IrLoop>()

    fun generateFunctionBody(ktBody: KtExpression): IrBody {
        val statementGenerator = createStatementGenerator()

        val irBlockBody = IrBlockBodyImpl(ktBody.startOffset, ktBody.endOffset)
        if (ktBody is KtBlockExpression) {
            statementGenerator.generateBlockBodyStatements(irBlockBody, ktBody)
        }
        else {
            statementGenerator.generateReturnExpression(ktBody, irBlockBody)
        }

        return irBlockBody
    }

    fun generatePropertyInitializerBody(ktInitializer: KtExpression): IrBody =
            IrExpressionBodyImpl(ktInitializer.startOffset, ktInitializer.endOffset,
                                 createStatementGenerator().generateExpression(ktInitializer))

    fun generateLambdaBody(ktFun: KtFunctionLiteral): IrBody {
        val statementGenerator = createStatementGenerator()

        val ktBody = ktFun.bodyExpression!!
        val irBlockBody = IrBlockBodyImpl(ktBody.startOffset, ktBody.endOffset)
        if (ktBody is KtBlockExpression) {
            for (ktStatement in ktBody.statements.subList(0, ktBody.statements.size - 1)) {
                irBlockBody.addStatement(statementGenerator.generateStatement(ktStatement))
            }
            val ktReturnedValue = ktBody.statements.last()
            statementGenerator.generateReturnExpression(ktReturnedValue, irBlockBody)
        }

        return irBlockBody
    }

    private fun StatementGenerator.generateBlockBodyStatements(irBlockBody: IrBlockBodyImpl, ktBody: KtBlockExpression) {
        for (ktStatement in ktBody.statements) {
            irBlockBody.addStatement(generateStatement(ktStatement))
        }
    }

    private fun StatementGenerator.generateReturnExpression(ktExpression: KtExpression, irBlockBody: IrBlockBodyImpl) {
        val irExpression = generateExpression(ktExpression)
        irBlockBody.addStatement(irExpression.wrapWithReturn())
    }

    private fun IrExpression.wrapWithReturn() =
            if (KotlinBuiltIns.isNothing(type))
                this
            else
                IrReturnImpl(startOffset, endOffset, context.builtIns.nothingType, scopeOwner, this)


    fun generateSecondaryConstructorBody(ktConstructor: KtSecondaryConstructor): IrBody {
        val statementGenerator = createStatementGenerator()

        val irBlockBody = IrBlockBodyImpl(ktConstructor.startOffset, ktConstructor.endOffset)

        val ktDelegatingConstructorCall = ktConstructor.getDelegationCall()
        val delegatingConstructorCall = statementGenerator.pregenerateCall(getResolvedCall(ktDelegatingConstructorCall)!!)
        val irDelegatingConstructorCall = CallGenerator(statementGenerator).generateCall(
                ktDelegatingConstructorCall, delegatingConstructorCall, IrOperator.DELEGATING_CONSTRUCTOR_CALL)
        irBlockBody.addStatement(irDelegatingConstructorCall)

        val ktBody = ktConstructor.bodyExpression
        if (ktBody != null) {
            statementGenerator.generateBlockBodyStatements(irBlockBody, ktBody)
        }

        return irBlockBody
    }

    private fun createStatementGenerator() =
            StatementGenerator(context, scopeOwner, this, scope)

    fun putLoop(expression: KtLoopExpression, irLoop: IrLoop) {
        loopTable[expression] = irLoop
    }

    fun getLoop(expression: KtExpression): IrLoop? =
            loopTable[expression]
}

