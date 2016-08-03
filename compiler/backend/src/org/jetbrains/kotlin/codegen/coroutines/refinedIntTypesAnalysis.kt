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

package org.jetbrains.kotlin.codegen.coroutines

import org.jetbrains.kotlin.codegen.optimization.common.InsnSequence
import org.jetbrains.kotlin.codegen.optimization.common.OptimizationBasicInterpreter
import org.jetbrains.kotlin.codegen.optimization.common.isMeaningful
import org.jetbrains.kotlin.codegen.optimization.common.isStoreOperation
import org.jetbrains.kotlin.codegen.optimization.fixStack.peek
import org.jetbrains.kotlin.codegen.optimization.fixStack.top
import org.jetbrains.kotlin.codegen.optimization.transformer.MethodTransformer
import org.jetbrains.kotlin.utils.sure
import org.jetbrains.org.objectweb.asm.Opcodes
import org.jetbrains.org.objectweb.asm.Type
import org.jetbrains.org.objectweb.asm.tree.*
import org.jetbrains.org.objectweb.asm.tree.analysis.BasicValue
import org.jetbrains.org.objectweb.asm.tree.analysis.Frame
import org.jetbrains.org.objectweb.asm.tree.analysis.SourceInterpreter
import org.jetbrains.org.objectweb.asm.tree.analysis.SourceValue

// BasicValue interpreter from ASM does not distinct 'int' types from other int-like types like 'byte' or 'boolean',
// neither do HotSpot and JVM spec.
// But it seems like Dalvik does not follow it, and spilling boolean value into an 'int' field fails with VerifyError on Android 4,
// so this function calculates refined frames' markup.
// Note that type of some values is only possible to determine by their usages (e.g. ICONST_1, BALOAD both may push boolean or byte on stack)
internal fun performRefinedTypeAnalysis(methodNode: MethodNode, thisName: String): Array<out Frame<out BasicValue>> {
    val basicFrames = MethodTransformer.analyze(thisName, methodNode, OptimizationBasicInterpreter())
    val insnList = methodNode.instructions
    val sourceValueFrames = MethodTransformer.analyze(thisName, methodNode, MySourceInterpreter())

    val expectedTypeForInsn: Array<Type?> = arrayOfNulls(insnList.size())

    fun saveExpectedType(value: SourceValue?, expectedType: Type) {
        if (value == null) return
        if (expectedType.sort !in REFINED_INT_SORTS) return

        value.insns.forEach {
            val index = insnList.indexOf(it)
            assert(expectedTypeForInsn[index] == null || expectedTypeForInsn[index] == expectedType) {
                "Conflicting expected types: ${expectedTypeForInsn[index]}/$expectedType"
            }

            expectedTypeForInsn[index] = expectedType
        }
    }

    fun saveExpectedTypeForArrayStore(insn: AbstractInsnNode, sourceValueFrame: Frame<SourceValue>) {
        val arrayStoreType =
                when (insn.opcode) {
                    Opcodes.BASTORE -> Type.BYTE_TYPE
                    Opcodes.CASTORE -> Type.CHAR_TYPE
                    Opcodes.SASTORE -> Type.SHORT_TYPE
                    else -> return
                }

        val insnIndex = insnList.indexOf(insn)

        val arrayArg = basicFrames[insnIndex].peek(2)
        val expectedType =
                if (arrayArg?.type?.sort == Type.ARRAY)
                    arrayArg.type.elementType
                else
                    arrayStoreType

        saveExpectedType(sourceValueFrame.top(), expectedType)
    }

    fun saveExpectedTypeForFieldOrMethod(insn: AbstractInsnNode, sourceValueFrame: Frame<SourceValue>) {
        when (insn.opcode) {
            Opcodes.PUTFIELD, Opcodes.PUTSTATIC ->
                saveExpectedType(sourceValueFrame.top(), Type.getType((insn as FieldInsnNode).desc))

            Opcodes.INVOKESTATIC, Opcodes.INVOKEVIRTUAL, Opcodes.INVOKEINTERFACE, Opcodes.INVOKESPECIAL, Opcodes.INVOKEDYNAMIC -> {
                val argumentTypes = Type.getArgumentTypes((insn as MethodInsnNode).desc)
                argumentTypes.withIndex().forEach {
                    val (argIndex, type) = it
                    saveExpectedType(sourceValueFrame.peek(argumentTypes.size - argIndex - 1), type)
                }
            }
        }
    }

    fun saveExpectedTypeForVarStore(insn: AbstractInsnNode, sourceValueFrame: Frame<SourceValue>) {
        if (insn.isStoreOperation()) {
            val varIndex = (insn as VarInsnNode).`var`
            // Considering next insn is important because variable initializer is emitted just before
            // the beginning of variable
            val nextInsn = InsnSequence(insn.next, insnList.last).firstOrNull(AbstractInsnNode::isMeaningful)

            val variableNode =
                    methodNode.findContainingVariableFromTable(insn, varIndex)
                    ?: methodNode.findContainingVariableFromTable(nextInsn ?: return, varIndex)
                    ?: return

            saveExpectedType(sourceValueFrame.top(), Type.getType(variableNode.desc))
        }
    }

    for ((insnIndex, insn) in insnList.toArray().withIndex()) {
        val sourceValueFrame = sourceValueFrames[insnIndex] ?: continue

        saveExpectedTypeForArrayStore(insn, sourceValueFrame)
        saveExpectedTypeForFieldOrMethod(insn, sourceValueFrame)
        saveExpectedTypeForVarStore(insn, sourceValueFrame)
    }

    return MethodTransformer.analyze(thisName, methodNode, RefinedBasicInterpreter(expectedTypeForInsn, insnList))
}

private class MySourceInterpreter : SourceInterpreter() {
    override fun copyOperation(insn: AbstractInsnNode?, value: SourceValue) = value
}

private val REFINED_INT_SORTS = setOf(Type.BOOLEAN, Type.CHAR, Type.BYTE, Type.SHORT)

private class RefinedBasicInterpreter(
        private val expectedTypeByIndexInsn: Array<Type?>,
        private val insnList: InsnList
) : OptimizationBasicInterpreter() {
    // I2S, ...
    override fun unaryOperation(insn: AbstractInsnNode, value: BasicValue) =
            buildResultUsingExpectedType(super.unaryOperation(insn, value), insn)

    // ICONST, BIPUSH, ...
    override fun newOperation(insn: AbstractInsnNode) = buildResultUsingExpectedType(super.newOperation(insn), insn)

    // BALOAD, SALOAD, ...
    override fun binaryOperation(insn: AbstractInsnNode, value1: BasicValue, value2: BasicValue) =
            buildResultUsingExpectedType(super.binaryOperation(insn, value1, value2), insn)

    private fun buildResultUsingExpectedType(basicRes: BasicValue?, insn: AbstractInsnNode): BasicValue? {
        val expectedType = expectedTypeByIndexInsn[insnList.indexOf(insn)] ?: return basicRes

        basicRes ?: throw AssertionError("Basic result should not be null when $expectedType is expected")

        assert(basicRes.type.sort in REFINED_INT_SORTS || basicRes.type.sort == Type.INT) {
            "Unexpected basic newOperation type ${basicRes.type}"
        }

        if (expectedType == basicRes.type) return basicRes

        return BasicValue(expectedType)
    }
}

private fun MethodNode.findContainingVariableFromTable(insn: AbstractInsnNode, varIndex: Int): LocalVariableNode? {
    val insnIndex = instructions.indexOf(insn)
    return localVariables.firstOrNull {
        it.index == varIndex && it.rangeContainsInsn(insnIndex, instructions)
    }
}

private fun LocalVariableNode.rangeContainsInsn(insnIndex: Int, insnList: InsnList) =
        insnList.indexOf(start) < insnIndex && insnIndex < insnList.indexOf(end)
