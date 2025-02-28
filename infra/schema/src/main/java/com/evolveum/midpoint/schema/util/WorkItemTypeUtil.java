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

package com.evolveum.midpoint.schema.util;

import com.evolveum.midpoint.xml.ns._public.common.common_3.*;

/**
 * @author mederly
 */
public class WorkItemTypeUtil {

	public static AbstractWorkItemOutputType getOutput(AbstractWorkItemType workItem) {
		return workItem != null ? workItem.getOutput() : null;
	}

	public static String getOutcome(AbstractWorkItemType workItem) {
		return getOutcome(getOutput(workItem));
	}

	public static String getComment(AbstractWorkItemType workItem) {
		return getComment(getOutput(workItem));
	}

	public static String getComment(AbstractWorkItemOutputType output) {
		return output != null ? output.getComment() : null;
	}

	public static byte[] getEvidence(AbstractWorkItemType workItem) {
		return getEvidence(getOutput(workItem));
	}

	public static byte[] getEvidence(AbstractWorkItemOutputType output) {
		return output != null ? output.getEvidence() : null;
	}

	public static String getEvidenceFilename(AbstractWorkItemType workItem) {
		return getEvidenceFilename(getOutput(workItem));
	}

	public static String getEvidenceFilename(AbstractWorkItemOutputType output) {
		return output != null ? output.getEvidenceFilename() : null;
	}

	public static String getEvidenceContentType(AbstractWorkItemType workItem) {
		return getEvidenceContentType(getOutput(workItem));
	}

	public static String getEvidenceContentType(AbstractWorkItemOutputType output) {
		return output != null ? output.getEvidenceContentType() : null;
	}

	public static String getOutcome(AbstractWorkItemOutputType output) {
		return output != null ? output.getOutcome() : null;
	}

	public static void assertHasCaseOid(CaseWorkItemType workItem) {
		if (CaseTypeUtil.getCase(workItem) == null) {
			throw new AssertionError("No parent case for work item " + workItem);
		}
	}

	public static ObjectReferenceType getRequestorReference(CaseWorkItemType workItem){
		CaseType caseType = CaseTypeUtil.getCase(workItem);
		if (caseType != null){
			return caseType.getRequestorRef();
		}
		return null;
	}

	public static ObjectReferenceType getObjectReference(CaseWorkItemType workItem){
		CaseType caseType = CaseTypeUtil.getCase(workItem);
		if (caseType != null){
			return caseType.getObjectRef();
		}
		return null;
	}


	public static ObjectReferenceType getTargetReference(CaseWorkItemType workItem){
		CaseType caseType = CaseTypeUtil.getCase(workItem);
		if (caseType != null){
			return caseType.getTargetRef();
		}
		return null;
	}
}
