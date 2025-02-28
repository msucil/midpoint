/*
 * Copyright (c) 2010-2017 Evolveum
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
import com.evolveum.midpoint.task.api.LightweightIdentifierGenerator;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.util.DebugUtil;
import com.evolveum.midpoint.xml.ns._public.common.common_3.CaseType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.EventCategoryType;

/**
 * @author mederly
 */
public class WorkflowProcessEvent extends WorkflowEvent {

    public WorkflowProcessEvent(LightweightIdentifierGenerator lightweightIdentifierGenerator, ChangeType changeType, CaseType aCase) {
        super(lightweightIdentifierGenerator, changeType, aCase.getApprovalContext(), aCase, null);
    }

    @Override
    public boolean isCategoryType(EventCategoryType eventCategoryType) {
        return eventCategoryType == EventCategoryType.WORKFLOW_PROCESS_EVENT || eventCategoryType == EventCategoryType.WORKFLOW_EVENT;
    }

	@Override
	protected String getOutcome() {
		return aCase.getOutcome();
	}

	@Override
    public String toString() {
        return "WorkflowProcessEvent{" +
                "workflowEvent=" + super.toString() +
                '}';

    }

	@Override
	public String debugDump(int indent) {
		StringBuilder sb = DebugUtil.createTitleStringBuilderLn(this.getClass(), indent);
		debugDumpCommon(sb, indent);
		return sb.toString();
	}

}
