/*
 * Copyright (c) 2010-2019 Evolveum
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.evolveum.midpoint.notifications.api.events;

import com.evolveum.midpoint.prism.delta.ChangeType;
import com.evolveum.midpoint.schema.expression.VariablesMap;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.task.api.LightweightIdentifierGenerator;
import com.evolveum.midpoint.wf.api.WorkItemOperationInfo;
import com.evolveum.midpoint.wf.api.WorkItemOperationSourceInfo;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.xml.datatype.Duration;

/**
 * @author mederly
 */
public class WorkItemAllocationEvent extends WorkItemEvent {

	public WorkItemAllocationEvent(@NotNull LightweightIdentifierGenerator lightweightIdentifierGenerator,
			@NotNull ChangeType changeType,
			@NotNull CaseWorkItemType workItem, @Nullable SimpleObjectRef assignee, @Nullable SimpleObjectRef initiator,
			@Nullable WorkItemOperationInfo operationInfo, @Nullable WorkItemOperationSourceInfo sourceInfo,
			@NotNull ApprovalContextType approvalContext, @NotNull CaseType aCase,
			@Nullable Duration timeBefore) {
        super(lightweightIdentifierGenerator, changeType, workItem, assignee, initiator, operationInfo, sourceInfo,
				approvalContext, aCase, null, timeBefore);
	}

	@Override
    public boolean isCategoryType(EventCategoryType eventCategoryType) {
        return eventCategoryType == EventCategoryType.WORK_ITEM_ALLOCATION_EVENT
        		|| eventCategoryType == EventCategoryType.WORK_ITEM_EVENT
				|| eventCategoryType == EventCategoryType.WORKFLOW_EVENT;
    }

	@Override
    public void createExpressionVariables(VariablesMap variables, OperationResult result) {
        super.createExpressionVariables(variables, result);
    }

	@Override
	public String toString() {
		return "WorkItemAllocationEvent:" + super.toString();
	}

}
