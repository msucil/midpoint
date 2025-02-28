/*
 * Copyright (c) 2010-2014 Evolveum
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.evolveum.midpoint.wf.impl.processors.primary;

import com.evolveum.midpoint.model.api.ModelExecuteOptions;
import com.evolveum.midpoint.model.api.context.ModelContext;
import com.evolveum.midpoint.model.impl.lens.LensContext;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.delta.ObjectDelta;
import com.evolveum.midpoint.prism.xml.XmlTypeConverter;
import com.evolveum.midpoint.schema.ObjectTreeDeltas;
import com.evolveum.midpoint.schema.util.ObjectTypeUtil;
import com.evolveum.midpoint.util.DebugUtil;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.wf.impl.processors.ChangeProcessor;
import com.evolveum.midpoint.wf.impl.processors.StartInstruction;
import com.evolveum.midpoint.wf.impl.processors.primary.aspect.PrimaryChangeAspect;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;
import org.jetbrains.annotations.NotNull;

import java.util.Date;

/**
 * Invariant: case.approvalContext != null
 */
public class PcpStartInstruction extends StartInstruction {

	@SuppressWarnings("unused")
	private static final Trace LOGGER = TraceManager.getTrace(PcpStartInstruction.class);

	private ObjectTreeDeltas<?> deltasToApprove;

	private PcpStartInstruction(@NotNull ChangeProcessor changeProcessor, @NotNull String archetypeOid) {
        super(changeProcessor, archetypeOid);
		aCase.setApprovalContext(new ApprovalContextType(changeProcessor.getPrismContext()));
    }

	public static PcpStartInstruction createItemApprovalInstruction(ChangeProcessor changeProcessor,
			@NotNull ApprovalSchemaType approvalSchemaType, SchemaAttachedPolicyRulesType attachedPolicyRules) {
		PcpStartInstruction instruction = new PcpStartInstruction(changeProcessor,
				SystemObjectsType.ARCHETYPE_APPROVAL_CASE.value());
		instruction.getApprovalContext().setApprovalSchema(approvalSchemaType);
		instruction.getApprovalContext().setPolicyRules(attachedPolicyRules);
		return instruction;
	}

	static PcpStartInstruction createEmpty(ChangeProcessor changeProcessor, @NotNull String archetypeOid) {
		return new PcpStartInstruction(changeProcessor, archetypeOid);
	}

	public boolean isExecuteApprovedChangeImmediately() {
		ApprovalContextType actx = aCase.getApprovalContext();
		return actx != null && Boolean.TRUE.equals(actx.isImmediateExecution());
    }

	void setExecuteApprovedChangeImmediately(ModelContext<?> modelContext) {
		aCase.getApprovalContext().setImmediateExecution(
				ModelExecuteOptions.isExecuteImmediatelyAfterApproval(modelContext.getOptions()));
	}

	public void prepareCommonAttributes(PrimaryChangeAspect aspect, ModelContext<?> modelContext, PrismObject<UserType> requester) {
		if (requester != null) {
			setRequesterRef(requester);
		}

		setExecuteApprovedChangeImmediately(modelContext);

	    getApprovalContext().setChangeAspect(aspect.getClass().getName());

		CaseCreationEventType event = new CaseCreationEventType(getPrismContext());
        event.setTimestamp(XmlTypeConverter.createXMLGregorianCalendar(new Date()));
        if (requester != null) {
			event.setInitiatorRef(ObjectTypeUtil.createObjectRef(requester, getPrismContext()));
			// attorney does not need to be set here (for now)
		}
		event.setBusinessContext(((LensContext) modelContext).getRequestBusinessContext());
        aCase.getEvent().add(event);
    }

	public void setDeltasToApprove(ObjectDelta<? extends ObjectType> delta) throws SchemaException {
        setDeltasToApprove(new ObjectTreeDeltas<>(delta, getChangeProcessor().getPrismContext()));
    }

    public void setDeltasToApprove(ObjectTreeDeltas objectTreeDeltas) throws SchemaException {
		deltasToApprove = objectTreeDeltas;
        getApprovalContext().setDeltasToApprove(ObjectTreeDeltas.toObjectTreeDeltasType(objectTreeDeltas));
    }

    void setResultingDeltas(ObjectTreeDeltas objectTreeDeltas) throws SchemaException {
	    getApprovalContext().setResultingDeltas(ObjectTreeDeltas.toObjectTreeDeltasType(objectTreeDeltas));
    }

	@Override
    public String debugDump(int indent) {
        StringBuilder sb = new StringBuilder();

        DebugUtil.indentDebugDump(sb, indent);
        sb.append("PrimaryChangeProcessor StartInstruction: (execute approved change immediately = ")
                .append(isExecuteApprovedChangeImmediately())
                .append(")\n");
        sb.append(super.debugDump(indent+1));
		return sb.toString();
    }

	boolean isObjectCreationInstruction() {
		return deltasToApprove != null && deltasToApprove.getFocusChange() != null && deltasToApprove.getFocusChange().isAdd();
	}
}
