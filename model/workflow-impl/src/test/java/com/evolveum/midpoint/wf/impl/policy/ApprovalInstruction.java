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

import com.evolveum.midpoint.xml.ns._public.common.common_3.CaseWorkItemType;

/**
 * @author mederly
 */
public class ApprovalInstruction {

	public final ExpectedWorkItem expectedWorkItem;
	public final boolean approval;
	public final String approverOid;
	public final String comment;
	public final CheckedRunnable beforeApproval, afterApproval;

	public ApprovalInstruction(ExpectedWorkItem expectedWorkItem, boolean approval, String approverOid, String comment,
			CheckedRunnable beforeApproval, CheckedRunnable afterApproval) {
		this.expectedWorkItem = expectedWorkItem;
		this.approval = approval;
		this.approverOid = approverOid;
		this.comment = comment;
		this.beforeApproval = beforeApproval;
		this.afterApproval = afterApproval;
	}

	public ApprovalInstruction(ExpectedWorkItem expectedWorkItem, boolean approval, String approverOid, String comment) {
		this(expectedWorkItem, approval, approverOid, comment, null, null);
	}

	public boolean matches(CaseWorkItemType actualWorkItem) {
		return expectedWorkItem == null || expectedWorkItem.matches(actualWorkItem);
	}

	@Override
	public String toString() {
		return "ApprovalInstruction{" +
				"expectedWorkItem=" + expectedWorkItem +
				", approval=" + approval +
				", approverOid='" + approverOid + '\'' +
				", comment='" + comment + '\'' +
				'}';
	}

	@FunctionalInterface
	public interface CheckedRunnable {
		void run() throws Exception;
	}
}
