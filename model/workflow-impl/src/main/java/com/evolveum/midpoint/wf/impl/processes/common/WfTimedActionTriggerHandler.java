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

package com.evolveum.midpoint.wf.impl.processes.common;

import com.evolveum.midpoint.model.impl.trigger.MultipleTriggersHandler;
import com.evolveum.midpoint.model.impl.trigger.TriggerHandlerRegistry;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.util.CloneUtil;
import com.evolveum.midpoint.repo.common.expression.ExpressionVariables;
import com.evolveum.midpoint.schema.constants.ExpressionConstants;
import com.evolveum.midpoint.schema.constants.SchemaConstants;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.schema.result.OperationResultStatus;
import com.evolveum.midpoint.schema.util.*;
import com.evolveum.midpoint.task.api.RunningTask;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.task.api.TaskManager;
import com.evolveum.midpoint.util.Holder;
import com.evolveum.midpoint.util.exception.*;
import com.evolveum.midpoint.util.logging.LoggingUtils;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.wf.api.WorkItemAllocationChangeOperationInfo;
import com.evolveum.midpoint.wf.api.WorkItemOperationSourceInfo;
import com.evolveum.midpoint.wf.api.WorkflowConstants;
import com.evolveum.midpoint.wf.api.request.CompleteWorkItemsRequest;
import com.evolveum.midpoint.wf.impl.access.WorkItemManager;
import com.evolveum.midpoint.wf.impl.engine.helpers.NotificationHelper;
import com.evolveum.midpoint.wf.impl.engine.WorkflowEngine;
import com.evolveum.midpoint.wf.impl.util.MiscHelper;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;
import org.apache.commons.lang.BooleanUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.xml.datatype.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.apache.commons.lang3.ObjectUtils.defaultIfNull;

/**
 * @author mederly
 */
@Component
public class WfTimedActionTriggerHandler implements MultipleTriggersHandler {

	public static final String HANDLER_URI = WorkflowConstants.NS_WORKFLOW_TRIGGER_PREFIX + "/timed-action/handler-3";

	private static final transient Trace LOGGER = TraceManager.getTrace(WfTimedActionTriggerHandler.class);

	@Autowired private TriggerHandlerRegistry triggerHandlerRegistry;
	@Autowired private WorkflowEngine workflowEngine;
	@Autowired private WorkItemManager workItemManager;
	@Autowired private NotificationHelper notificationHelper;
	@Autowired private TaskManager taskManager;
	@Autowired private ExpressionEvaluationHelper evaluationHelper;
	@Autowired private StageComputeHelper stageComputeHelper;
	@Autowired private MiscHelper miscHelper;

	@PostConstruct
	private void initialize() {
		triggerHandlerRegistry.register(HANDLER_URI, this);
	}

	@Override
	public <O extends ObjectType> Collection<TriggerType> handle(PrismObject<O> object, Collection<TriggerType> triggers, RunningTask opTask, OperationResult parentResult) {
		if (!(object.asObjectable() instanceof CaseType)) {
			throw new IllegalArgumentException("Unexpected object type: should be CaseType: " + object);
		}
		CaseType aCase = (CaseType) object.asObjectable();
		ApprovalContextType wfc = aCase.getApprovalContext();
		if (wfc == null) {
			LOGGER.warn("Task without workflow context; ignoring it: " + object);
			return triggers;
		}

		OperationResult triggersResult = parentResult.createSubresult(WfTimedActionTriggerHandler.class.getName() + ".handle");
		try {
			List<TriggerType> processedTriggers = new ArrayList<>();

			/*
			 * "Complete" action has to be executed for all the work items at once. Otherwise the first work item will be regularly
			 * completed and the other ones cancelled because the stage is being closed. (Before midPoint 4.0 there has to be
			 * a special code to treat this.)
			 */
			List<CompleteWorkItemsRequest.SingleCompletion> completeActions = new ArrayList<>();
			Holder<WorkItemEventCauseInformationType> causeHolder = new Holder<>();
			for (TriggerType trigger : triggers) {
				boolean ok = true;
				OperationResult opResult = triggersResult
						.createSubresult(WfTimedActionTriggerHandler.class.getName() + ".handleTrigger");
				Long workItemId = ObjectTypeUtil
						.getExtensionItemRealValue(trigger.getExtension(), SchemaConstants.MODEL_EXTENSION_WORK_ITEM_ID);
				if (workItemId == null) {
					LOGGER.warn("Trigger without workItemId; ignoring it: " + trigger);
					opResult.recordStatus(OperationResultStatus.NOT_APPLICABLE, "No work item ID");
				} else {
					try {
						CaseWorkItemType workItem = CaseWorkItemUtil.getWorkItem(aCase, workItemId);
						if (workItem == null) {
							LOGGER.warn("Work item {} couldn't be found; ignoring the trigger: {}", workItemId, trigger);
							opResult.recordStatus(OperationResultStatus.NOT_APPLICABLE, "No work item with given ID");
						} else {
							Duration timeBeforeAction = ObjectTypeUtil
									.getExtensionItemRealValue(trigger.getExtension(),
											SchemaConstants.MODEL_EXTENSION_TIME_BEFORE_ACTION);
							if (timeBeforeAction != null) {
								AbstractWorkItemActionType action = ObjectTypeUtil
										.getExtensionItemRealValue(trigger.getExtension(),
												SchemaConstants.MODEL_EXTENSION_WORK_ITEM_ACTION);
								if (action == null) {
									LOGGER.warn("Notification trigger without workItemAction; ignoring it: {}", trigger);
									continue;
								}
								executeNotifications(timeBeforeAction, action, workItem, aCase, opTask, opResult);
							} else {
								WorkItemActionsType actions = ObjectTypeUtil
										.getExtensionItemRealValue(trigger.getExtension(),
												SchemaConstants.MODEL_EXTENSION_WORK_ITEM_ACTIONS);
								if (actions == null) {
									LOGGER.warn("Trigger without workItemActions; ignoring it: " + trigger);
									continue;
								}
								executeActions(actions, workItem, aCase, completeActions, causeHolder, opTask, opResult);
							}
							opResult.computeStatusIfUnknown();
						}
					} catch (RuntimeException | ObjectNotFoundException | SchemaException | SecurityViolationException | ExpressionEvaluationException | CommunicationException | ConfigurationException | ObjectAlreadyExistsException e) {
						String message =
								"Exception while handling work item trigger for ID " + workItemId + ": " + e.getMessage();
						opResult.recordPartialError(message, e);
						ok = false;
					}
				}
				if (ok) {
					processedTriggers.add(trigger);
				}
			}

			if (!completeActions.isEmpty()) {
				OperationResult result = triggersResult
						.createSubresult(WfTimedActionTriggerHandler.class.getName() + ".handleCompletions");
				try {
					CompleteWorkItemsRequest request = new CompleteWorkItemsRequest(aCase.getOid(), causeHolder.getValue());
					request.getCompletions().addAll(completeActions);
					workItemManager.completeWorkItems(request, opTask, result);
					result.recordSuccessIfUnknown();
				} catch (Throwable t) {
					LoggingUtils.logUnexpectedException(LOGGER, "Couldn't handler work item completion", t);
					result.recordFatalError("Couldn't handle work item completion", t);
					// but let's ignore this fact and mark all triggers as processed
				}
			}
			triggersResult.computeStatus();
			return processedTriggers;
		} catch (Throwable t) {
			triggersResult.recordFatalError("Couldn't process triggers: " + t.getMessage(), t);
			throw t;
		}
	}

	private void executeNotifications(Duration timeBeforeAction, AbstractWorkItemActionType action, CaseWorkItemType workItem,
			CaseType aCase, RunningTask opTask, OperationResult result) throws SchemaException {
		WorkItemOperationKindType operationKind = ApprovalContextUtil.getOperationKind(action);
		WorkItemEventCauseInformationType cause = ApprovalContextUtil.createCause(action);
		List<ObjectReferenceType> assigneesAndDeputies = miscHelper.getAssigneesAndDeputies(workItem, opTask, result);
		WorkItemAllocationChangeOperationInfo operationInfo =
				new WorkItemAllocationChangeOperationInfo(operationKind, assigneesAndDeputies, null);
		WorkItemOperationSourceInfo sourceInfo = new WorkItemOperationSourceInfo(null, cause, action);
		notificationHelper.notifyWorkItemAllocationChangeCurrentActors(workItem, operationInfo, sourceInfo, timeBeforeAction,
				aCase, result);
	}

	private void executeActions(WorkItemActionsType actions, CaseWorkItemType workItem, CaseType aCase,
			List<CompleteWorkItemsRequest.SingleCompletion> completeActions,
			Holder<WorkItemEventCauseInformationType> causeHolder,
			Task opTask, OperationResult result)
			throws SchemaException, SecurityViolationException, ObjectNotFoundException, ExpressionEvaluationException,
			CommunicationException, ConfigurationException, ObjectAlreadyExistsException {
		for (WorkItemNotificationActionType notificationAction : actions.getNotify()) {
			executeNotificationAction(workItem, notificationAction, aCase, opTask, result);
		}
		if (actions.getDelegate() != null) {
			executeDelegateAction(workItem, actions.getDelegate(), false, aCase, opTask, result);
		}
		if (actions.getEscalate() != null) {
			executeDelegateAction(workItem, actions.getEscalate(), true, aCase, opTask, result);
		}
		CompleteWorkItemActionType complete = actions.getComplete();
		if (complete != null) {
			completeActions.add(new CompleteWorkItemsRequest.SingleCompletion(workItem.getId(),
					defaultIfNull(complete.getOutcome(), SchemaConstants.MODEL_APPROVAL_OUTCOME_REJECT), null, null));
			causeHolder.setValue(ApprovalContextUtil.createCause(complete));
		}
	}

//	private void executeCompleteAction(CaseWorkItemType workItem, CompleteWorkItemActionType completeAction,
//			OperationResult result)
//			throws SchemaException, SecurityViolationException, ObjectNotFoundException, ExpressionEvaluationException,
//			CommunicationException, ConfigurationException, ObjectAlreadyExistsException {
//		WorkItemOutcomeType outcome = completeAction.getOutcome() != null ? ApprovalUtils.fromUri(completeAction.getOutcome()) : WorkItemOutcomeType.REJECT;
//		workItemManager.completeWorkItem(WorkItemId.of(workItem), ApprovalUtils.toUri(outcome),
//				null, null, WfContextUtil.createCause(completeAction), result);
//	}

	private void executeDelegateAction(CaseWorkItemType workItem, DelegateWorkItemActionType delegateAction, boolean escalate,
			CaseType aCase, Task opTask, OperationResult result)
			throws SecurityViolationException, ObjectNotFoundException, SchemaException, ExpressionEvaluationException, CommunicationException, ConfigurationException {
		WorkItemEscalationLevelType escLevel = escalate ? ApprovalContextUtil.createEscalationLevelInformation(delegateAction) : null;
		List<ObjectReferenceType> delegates = computeDelegateTo(delegateAction, workItem, aCase, opTask, result);
		workItemManager.delegateWorkItem(WorkItemId.of(workItem), delegates,
				delegateAction.getDelegationMethod(), escLevel,
				delegateAction.getDuration(), ApprovalContextUtil.createCause(delegateAction), opTask, result);
	}

	private List<ObjectReferenceType> computeDelegateTo(DelegateWorkItemActionType delegateAction, CaseWorkItemType workItem,
			CaseType aCase, Task opTask, OperationResult result)
			throws SchemaException, ObjectNotFoundException, ExpressionEvaluationException, CommunicationException, ConfigurationException, SecurityViolationException {
		List<ObjectReferenceType> rv = new ArrayList<>();
		rv.addAll(CloneUtil.cloneCollectionMembers(delegateAction.getApproverRef()));
		if (!delegateAction.getApproverExpression().isEmpty()) {
			ExpressionVariables variables = stageComputeHelper.getDefaultVariables(aCase, aCase.getApprovalContext(), getChannel(opTask), result);
			variables.put(ExpressionConstants.VAR_WORK_ITEM, workItem, CaseWorkItemType.class);
			rv.addAll(evaluationHelper.evaluateRefExpressions(delegateAction.getApproverExpression(),
					variables, "computing delegates", opTask, result));
		}
//		if (!delegateAction.getApproverRelation().isEmpty()) {
//			throw new UnsupportedOperationException("Approver relation in delegate/escalate action is not supported yet.");
//		}
		return rv;
	}

	private String getChannel(Task opTask) {
		// TODO TODO TODO here we should put the original channel (determined from the root model context!)
		return opTask.getChannel();
	}

	private void executeNotificationAction(CaseWorkItemType workItem, @NotNull WorkItemNotificationActionType notificationAction,
			CaseType aCase, Task opTask,
			OperationResult result) throws SchemaException {
		WorkItemTypeUtil.assertHasCaseOid(workItem);
		WorkItemEventCauseInformationType cause = ApprovalContextUtil.createCause(notificationAction);
		if (BooleanUtils.isNotFalse(notificationAction.isPerAssignee())) {
			List<ObjectReferenceType> assigneesAndDeputies = miscHelper.getAssigneesAndDeputies(workItem, opTask, result);
			for (ObjectReferenceType assigneeOrDeputy : assigneesAndDeputies) {
				notificationHelper.notifyWorkItemCustom(assigneeOrDeputy, workItem, cause, aCase, notificationAction, result);
			}
		} else {
			notificationHelper.notifyWorkItemCustom(null, workItem, cause, aCase, notificationAction, result);
		}

	}

}
