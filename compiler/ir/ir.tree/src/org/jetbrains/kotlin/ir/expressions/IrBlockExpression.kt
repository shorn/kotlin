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

package org.jetbrains.kotlin.ir.expressions

import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.assertCast
import org.jetbrains.kotlin.ir.assertDetached
import org.jetbrains.kotlin.ir.visitors.IrElementVisitor
import org.jetbrains.kotlin.types.KotlinType
import java.util.*


interface IrBlockExpression : IrExpression {
    val hasResult: Boolean
    val operator: IrOperator?

    val statements: List<IrStatement>
    fun addStatement(statement: IrStatement)
}

fun IrBlockExpression.addIfNotNull(statement: IrStatement?) {
    if (statement != null) addStatement(statement)
}

class IrBlockExpressionImpl(
        startOffset: Int,
        endOffset: Int,
        type: KotlinType?,
        override val hasResult: Boolean,
        override val operator: IrOperator? = null
) : IrExpressionBase(startOffset, endOffset, type), IrBlockExpression {
    override val statements: MutableList<IrStatement> = ArrayList()

    override fun addStatement(statement: IrStatement) {
        statement.assertDetached()
        statement.setTreeLocation(this, statements.size)
        statements.add(statement)
    }

    override fun getChild(slot: Int): IrElement? =
            statements.getOrNull(slot)

    override fun replaceChild(slot: Int, newChild: IrElement) {
        if (0 <= slot && slot < statements.size) {
            statements[slot] = newChild.assertCast()
        }
    }

    override fun <R, D> accept(visitor: IrElementVisitor<R, D>, data: D): R =
            visitor.visitBlockExpression(this, data)

    override fun <D> acceptChildren(visitor: IrElementVisitor<Unit, D>, data: D) {
        statements.forEach { it.accept(visitor, data) }
    }
}