/**
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

import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.assertCast
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOriginKind
import org.jetbrains.kotlin.ir.declarations.IrVariableImpl
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.jetbrains.kotlin.psi2ir.deparenthesize
import org.jetbrains.kotlin.psi2ir.intermediate.createRematerializableOrTemporary
import org.jetbrains.kotlin.psi2ir.intermediate.setExplicitReceiverValue
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.BindingContextUtils
import org.jetbrains.kotlin.resolve.DescriptorUtils
import org.jetbrains.kotlin.resolve.calls.model.ResolvedCall
import org.jetbrains.kotlin.resolve.calls.model.VariableAsFunctionResolvedCall
import org.jetbrains.kotlin.resolve.calls.util.FakeCallableDescriptorForObject
import org.jetbrains.kotlin.resolve.constants.BooleanValue
import org.jetbrains.kotlin.resolve.constants.IntValue
import org.jetbrains.kotlin.resolve.constants.NullValue
import org.jetbrains.kotlin.resolve.constants.StringValue
import org.jetbrains.kotlin.resolve.constants.evaluate.ConstantExpressionEvaluator
import org.jetbrains.kotlin.resolve.descriptorUtil.classValueType
import org.jetbrains.kotlin.types.expressions.ExpressionTypingUtils
import java.lang.AssertionError

class StatementGenerator(
        override val context: GeneratorContext,
        val scopeOwner: DeclarationDescriptor,
        val expressionBodyGenerator: ExpressionBodyGenerator,
        override val scope: Scope
) : KtVisitor<IrStatement, Nothing?>(), GeneratorWithScope {
    fun generateExpression(ktExpression: KtExpression): IrExpression =
            ktExpression.genExpr()

    private fun KtElement.genStmt(): IrStatement =
            deparenthesize().accept(this@StatementGenerator, null)

    private fun KtElement.genExpr(): IrExpression =
            genStmt().assertCast()

    override fun visitExpression(expression: KtExpression, data: Nothing?): IrStatement =
            createDummyExpression(expression, expression.javaClass.simpleName)

    override fun visitProperty(property: KtProperty, data: Nothing?): IrStatement {
        if (property.delegateExpression != null) TODO("Local delegated property")

        val variableDescriptor = getOrFail(BindingContext.VARIABLE, property)

        val irLocalVariable = IrVariableImpl(property.startOffset, property.endOffset, IrDeclarationOriginKind.DEFINED, variableDescriptor)
        irLocalVariable.initializer = property.initializer?.genExpr()
        return irLocalVariable
    }

    override fun visitDestructuringDeclaration(multiDeclaration: KtDestructuringDeclaration, data: Nothing?): IrStatement {
        // TODO use some special form that introduces multiple declarations into surrounding scope?

        val irBlock = IrBlockImpl(multiDeclaration.startOffset, multiDeclaration.endOffset, null, false, IrOperator.SYNTHETIC_BLOCK)
        val ktInitializer = multiDeclaration.initializer!!
        val irTmpInitializerValue = createRematerializableOrTemporary(scope, ktInitializer.genExpr(), irBlock, "container")

        val irCallGenerator = CallGenerator(this)

        for ((index, ktEntry) in multiDeclaration.entries.withIndex()) {
            val componentResolvedCall = getOrFail(BindingContext.COMPONENT_RESOLVED_CALL, ktEntry)

            val componentSubstitutedCall = pregenerateCall(componentResolvedCall)
            componentSubstitutedCall.setExplicitReceiverValue(irTmpInitializerValue)

            val componentVariable = getOrFail(BindingContext.VARIABLE, ktEntry)
            val irComponentCall = irCallGenerator.generateCall(ktEntry.startOffset, ktEntry.endOffset, componentSubstitutedCall,
                                                               IrOperator.COMPONENT_N.withIndex(index + 1))
            val irComponentVar = IrVariableImpl(ktEntry.startOffset, ktEntry.endOffset, IrDeclarationOriginKind.DEFINED,
                                                componentVariable, irComponentCall)
            irBlock.addStatement(irComponentVar)
        }

        return irBlock
    }

    override fun visitBlockExpression(expression: KtBlockExpression, data: Nothing?): IrStatement {
        val isBlockBody = expression.parent is KtNamedFunction
        val hasResult = if (isBlockBody) false else isUsedAsExpression(expression)
        val returnType = if (isBlockBody || !hasResult) null else getReturnType(expression)
        val irBlock = IrBlockImpl(expression.startOffset, expression.endOffset, returnType, hasResult)
        expression.statements.forEach { irBlock.addStatement(it.genStmt()) }
        return irBlock
    }

    override fun visitReturnExpression(expression: KtReturnExpression, data: Nothing?): IrStatement {
        val returnTarget = getReturnExpressionTarget(expression)
        val irReturnedExpression = expression.returnedExpression?.genExpr()
        return IrReturnImpl(expression.startOffset, expression.endOffset, returnTarget, irReturnedExpression)
    }

    private fun getReturnExpressionTarget(expression: KtReturnExpression): CallableDescriptor =
            if (!ExpressionTypingUtils.isFunctionLiteral(scopeOwner) && !ExpressionTypingUtils.isFunctionExpression(scopeOwner)) {
                scopeOwner as? CallableDescriptor ?: throw AssertionError("Return not in a callable: $scopeOwner")
            }
            else {
                val label = expression.getTargetLabel()
                if (label != null) {
                    val labelTarget = getOrFail(BindingContext.LABEL_TARGET, label)
                    val labelTargetDescriptor = getOrFail(BindingContext.DECLARATION_TO_DESCRIPTOR, labelTarget)
                    labelTargetDescriptor as CallableDescriptor
                }
                else if (ExpressionTypingUtils.isFunctionLiteral(scopeOwner)) {
                    BindingContextUtils.getContainingFunctionSkipFunctionLiterals(scopeOwner, true).first
                }
                else {
                    scopeOwner as? CallableDescriptor ?: throw AssertionError("Return not in a callable: $scopeOwner")
                }
            }

    override fun visitConstantExpression(expression: KtConstantExpression, data: Nothing?): IrExpression {
        val compileTimeConstant = ConstantExpressionEvaluator.getConstant(expression, context.bindingContext)
                                  ?: error("KtConstantExpression was not evaluated: ${expression.text}")
        val constantValue = compileTimeConstant.toConstantValue(getInferredTypeWithSmartcastsOrFail(expression))
        val constantType = constantValue.type

        return when (constantValue) {
            is StringValue ->
                IrConstImpl.string(expression.startOffset, expression.endOffset, constantType, constantValue.value)
            is IntValue ->
                IrConstImpl.int(expression.startOffset, expression.endOffset, constantType, constantValue.value)
            is NullValue ->
                IrConstImpl.constNull(expression.startOffset, expression.endOffset, constantType)
            is BooleanValue ->
                IrConstImpl.boolean(expression.startOffset, expression.endOffset, constantType, constantValue.value)
            else ->
                TODO("handle other literal types: ${constantValue.type}")
        }
    }

    override fun visitStringTemplateExpression(expression: KtStringTemplateExpression, data: Nothing?): IrStatement {
        val entries = expression.entries
        when {
            entries.size == 1 -> {
                val entry0 = entries[0]
                if (entry0 is KtLiteralStringTemplateEntry) {
                    return entry0.genExpr()
                }
            }
            entries.size == 0 -> return IrConstImpl.string(expression.startOffset, expression.endOffset, getInferredTypeWithSmartcastsOrFail(expression), "")
        }

        val irStringTemplate = IrStringConcatenationImpl(expression.startOffset, expression.endOffset, getInferredTypeWithSmartcasts(expression))
        entries.forEach { it.expression!!.let { irStringTemplate.addArgument(it.genExpr()) } }
        return irStringTemplate
    }

    override fun visitLiteralStringTemplateEntry(entry: KtLiteralStringTemplateEntry, data: Nothing?): IrStatement =
            IrConstImpl.string(entry.startOffset, entry.endOffset, context.builtIns.stringType, entry.text)

    override fun visitSimpleNameExpression(expression: KtSimpleNameExpression, data: Nothing?): IrExpression {
        val resolvedCall = getResolvedCall(expression) ?: throw AssertionError("No resolved call for ${expression.text}")

        if (resolvedCall is VariableAsFunctionResolvedCall) {
            TODO("Unexpected VariableAsFunctionResolvedCall")
        }

        val descriptor = resolvedCall.resultingDescriptor

        return generateExpressionForReferencedDescriptor(descriptor, expression, resolvedCall)
    }

    private fun generateExpressionForReferencedDescriptor(descriptor: DeclarationDescriptor, expression: KtExpression, resolvedCall: ResolvedCall<*>): IrExpression =
            when (descriptor) {
                is FakeCallableDescriptorForObject ->
                    generateExpressionForReferencedDescriptor(descriptor.getReferencedDescriptor(), expression, resolvedCall)
                is ClassDescriptor ->
                    when {
                        DescriptorUtils.isObject(descriptor) ->
                            IrGetObjectValueImpl(expression.startOffset, expression.endOffset, descriptor.classValueType, descriptor)
                        DescriptorUtils.isEnumEntry(descriptor) ->
                            IrGetEnumValueImpl(expression.startOffset, expression.endOffset, descriptor.classValueType, descriptor)
                        else -> {
                            IrGetObjectValueImpl(expression.startOffset, expression.endOffset, descriptor.classValueType,
                                                 descriptor.companionObjectDescriptor ?: throw AssertionError("Class value without companion object: $descriptor"))
                        }
                    }
                is PropertyDescriptor -> {
                    // TODO safe calls
                    CallGenerator(this).generateCall(expression.startOffset, expression.endOffset, pregenerateCall(resolvedCall))
                }
                is VariableDescriptor ->
                    IrGetVariableImpl(expression.startOffset, expression.endOffset, descriptor)
                else ->
                    IrDummyExpression(
                            expression.startOffset, expression.endOffset, getInferredTypeWithSmartcasts(expression),
                            expression.text + ": ${descriptor.name} ${descriptor.javaClass.simpleName}"
                    )
            }

    override fun visitCallExpression(expression: KtCallExpression, data: Nothing?): IrStatement {
        val resolvedCall = getResolvedCall(expression) ?: TODO("No resolved call for call expression")

        if (resolvedCall is VariableAsFunctionResolvedCall) {
            TODO("VariableAsFunctionResolvedCall = variable call + invoke call")
        }

        // TODO safe calls

        return CallGenerator(this).generateCall(expression.startOffset, expression.endOffset, pregenerateCall(resolvedCall))
    }

    override fun visitDotQualifiedExpression(expression: KtDotQualifiedExpression, data: Nothing?): IrStatement =
            expression.selectorExpression!!.accept(this, data)

    override fun visitSafeQualifiedExpression(expression: KtSafeQualifiedExpression, data: Nothing?): IrStatement =
            expression.selectorExpression!!.accept(this, data)

    override fun visitThisExpression(expression: KtThisExpression, data: Nothing?): IrExpression {
        val referenceTarget = getOrFail(BindingContext.REFERENCE_TARGET, expression.instanceReference) { "No reference target for this" }
        return when (referenceTarget) {
            is ClassDescriptor ->
                IrThisReferenceImpl(
                        expression.startOffset, expression.endOffset,
                        referenceTarget.defaultType, // TODO substituted type for 'this'?
                        referenceTarget
                )
            is CallableDescriptor -> {
                val extensionReceiver = referenceTarget.extensionReceiverParameter ?: TODO("No extension receiver: $referenceTarget")
                IrGetExtensionReceiverImpl(
                        expression.startOffset, expression.endOffset,
                        extensionReceiver.type,
                        extensionReceiver
                )
            }
            else ->
                error("Expected this or receiver: $referenceTarget")
        }
    }

    override fun visitBinaryExpression(expression: KtBinaryExpression, data: Nothing?): IrStatement =
            OperatorExpressionGenerator(this).generateBinaryExpression(expression)

    override fun visitPrefixExpression(expression: KtPrefixExpression, data: Nothing?): IrStatement =
            OperatorExpressionGenerator(this).generatePrefixExpression(expression)

    override fun visitPostfixExpression(expression: KtPostfixExpression, data: Nothing?): IrStatement =
            OperatorExpressionGenerator(this).generatePostfixExpression(expression)

    override fun visitBinaryWithTypeRHSExpression(expression: KtBinaryExpressionWithTypeRHS, data: Nothing?): IrStatement =
            OperatorExpressionGenerator(this).generateCastExpression(expression)

    override fun visitIsExpression(expression: KtIsExpression, data: Nothing?): IrStatement =
            OperatorExpressionGenerator(this).generateInstanceOfExpression(expression)

    override fun visitIfExpression(expression: KtIfExpression, data: Nothing?): IrStatement =
            BranchingExpressionGenerator(this).generateIfExpression(expression)

    override fun visitWhenExpression(expression: KtWhenExpression, data: Nothing?): IrStatement =
            BranchingExpressionGenerator(this).generateWhenExpression(expression)

    override fun visitWhileExpression(expression: KtWhileExpression, data: Nothing?): IrStatement =
            LoopExpressionGenerator(this).generateWhileExpression(expression)

    override fun visitDoWhileExpression(expression: KtDoWhileExpression, data: Nothing?): IrStatement =
            LoopExpressionGenerator(this).generateDoWhileExpression(expression)
}


