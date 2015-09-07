/*
 * Copyright 2010-2015 JetBrains s.r.o.
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

package org.jetbrains.kotlin.types.expressions;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.descriptors.impl.LocalVariableDescriptor;
import org.jetbrains.kotlin.psi.*;
import org.jetbrains.kotlin.resolve.calls.smartcasts.DataFlowInfo;
import org.jetbrains.kotlin.resolve.calls.smartcasts.DataFlowValue;
import org.jetbrains.kotlin.resolve.calls.smartcasts.Nullability;

import java.util.*;

/**
 * The purpose of this class is to find all variable assignments
 * <b>before</b> loop analysis
 */
class PreliminaryLoopVisitor extends AssignedVariablesSearcher {

    @NotNull
    static public PreliminaryLoopVisitor visitLoop(@NotNull JetLoopExpression loopExpression) {
        PreliminaryLoopVisitor visitor = new PreliminaryLoopVisitor();
        loopExpression.accept(visitor, null);
        return visitor;
    }

    @NotNull
    public DataFlowInfo clearDataFlowInfoForAssignedLocalVariables(@NotNull DataFlowInfo dataFlowInfo) {
        Map<DataFlowValue, Nullability> nullabilityMap = dataFlowInfo.getCompleteNullabilityInfo();
        Set<DataFlowValue> valueSetToClear = new LinkedHashSet<DataFlowValue>();
        for (DataFlowValue value: nullabilityMap.keySet()) {
            // Only predictable variables are under interest here
            if (value.getKind() == DataFlowValue.Kind.PREDICTABLE_VARIABLE && value.getId() instanceof LocalVariableDescriptor) {
                LocalVariableDescriptor descriptor = (LocalVariableDescriptor)value.getId();
                if (getAssignedNames().contains(descriptor.getName())) {
                    valueSetToClear.add(value);
                }
            }
        }
        for (DataFlowValue valueToClear: valueSetToClear) {
            dataFlowInfo = dataFlowInfo.clearValueInfo(valueToClear);
        }
        return dataFlowInfo;
    }
}
