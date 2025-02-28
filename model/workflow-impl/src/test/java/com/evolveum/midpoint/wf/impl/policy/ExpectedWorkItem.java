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

package com.evolveum.midpoint.wf.impl.policy;

import com.evolveum.midpoint.schema.util.CaseWorkItemUtil;
import com.evolveum.midpoint.schema.util.ApprovalContextUtil;
import com.evolveum.midpoint.xml.ns._public.common.common_3.CaseType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.CaseWorkItemType;

/**
 * @author mederly
 */
public class ExpectedWorkItem {
	final String assigneeOid;
	final String targetOid;
	final ExpectedTask task;

	public ExpectedWorkItem(String assigneeOid, String targetOid, ExpectedTask task) {
		this.assigneeOid = assigneeOid;
		this.targetOid = targetOid;
		this.task = task;
	}

	public boolean matches(CaseWorkItemType actualWorkItem) {
		if (!assigneeOid.equals(actualWorkItem.getOriginalAssigneeRef().getOid())) {
			return false;
		}
		if (targetOid != null && !targetOid.equals(ApprovalContextUtil.getTargetRef(actualWorkItem).getOid())) {
			return false;
		}
		CaseType actualCase = CaseWorkItemUtil.getCaseRequired(actualWorkItem);
		return task.processName.equals(actualCase.getName().getOrig());
	}

	@Override
	public String toString() {
		return "ExpectedWorkItem{" +
				"assigneeOid='" + assigneeOid + '\'' +
				", targetOid='" + targetOid + '\'' +
				", task=" + task +
				'}';
	}
}
