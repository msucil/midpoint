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

package com.evolveum.midpoint.wf.impl.policy.other;

import com.evolveum.midpoint.audit.api.AuditEventRecord;
import com.evolveum.midpoint.audit.api.AuditEventType;
import com.evolveum.midpoint.model.api.WorkflowService;
import com.evolveum.midpoint.notifications.api.transports.Message;
import com.evolveum.midpoint.prism.PrismContext;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.util.PrismAsserts;
import com.evolveum.midpoint.schema.SearchResultList;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.schema.util.CaseWorkItemUtil;
import com.evolveum.midpoint.schema.util.ApprovalContextUtil;
import com.evolveum.midpoint.schema.util.WorkItemId;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.test.util.TestUtil;
import com.evolveum.midpoint.util.DebugUtil;
import com.evolveum.midpoint.wf.api.WorkflowConstants;
import com.evolveum.midpoint.wf.impl.policy.AbstractWfTestPolicy;
import com.evolveum.midpoint.wf.util.ApprovalUtils;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.testng.annotations.Test;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.testng.AssertJUnit.*;

/**
 * @author mederly
 */
@ContextConfiguration(locations = {"classpath:ctx-workflow-test-main.xml"})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class TestEscalation extends AbstractWfTestPolicy {

	@Override
	protected PrismObject<UserType> getDefaultActor() {
		return userAdministrator;
	}

	@Autowired
	private WorkflowService workflowService;

	protected static final File TASK_TRIGGER_SCANNER_FILE = new File(COMMON_DIR, "task-trigger-scanner.xml");
	protected static final String TASK_TRIGGER_SCANNER_OID = "00000000-0000-0000-0000-000000000007";

	protected static final File TEST_ESCALATION_RESOURCE_DIR = new File("src/test/resources/policy/escalation");
	protected static final File METAROLE_ESCALATED_FILE = new File(TEST_ESCALATION_RESOURCE_DIR, "metarole-escalated.xml");
	protected static final File ROLE_E1_FILE = new File(TEST_ESCALATION_RESOURCE_DIR, "role-e1.xml");
	protected static final File ROLE_E2_FILE = new File(TEST_ESCALATION_RESOURCE_DIR, "role-e2.xml");

	protected String metaroleEscalatedOid;
	protected String roleE1Oid;
	protected String roleE2Oid;
	private PrismObject<UserType> userLead1, userLead2;
	private WorkItemId workItemId;
	private String approvalCaseOid;

	@Override
	public void initSystem(Task initTask, OperationResult initResult) throws Exception {
		super.initSystem(initTask, initResult);

		DebugUtil.setPrettyPrintBeansAs(PrismContext.LANG_YAML);

		metaroleEscalatedOid = repoAddObjectFromFile(METAROLE_ESCALATED_FILE, initResult).getOid();
		roleE1Oid = repoAddObjectFromFile(ROLE_E1_FILE, initResult).getOid();
		roleE2Oid = repoAddObjectFromFile(ROLE_E2_FILE, initResult).getOid();

		importObjectFromFile(TASK_TRIGGER_SCANNER_FILE);

		userLead1 = getUser(userLead1Oid);
		userLead2 = getUser(userLead2Oid);

		importLead1Deputies(initTask, initResult);
	}

	@Test
	public void test100CreateTask() throws Exception {
		final String TEST_NAME = "test100CreateTask";
		TestUtil.displayTestTitle(this, TEST_NAME);
		login(userAdministrator);

		Task task = createTask(TEST_NAME);
		OperationResult result = task.getResult();

		assignRole(userJackOid, roleE1Oid, task, result);				// should start approval process
		assertNotAssignedRole(userJackOid, roleE1Oid, task, result);

		CaseWorkItemType workItem = getWorkItem(task, result);
		workItemId = WorkItemId.of(workItem);

		approvalCaseOid = CaseWorkItemUtil.getCaseRequired(workItem).getOid();
		CaseType aCase = getCase(approvalCaseOid);

		display("work item", workItem);
		display("workflow task", aCase);

		// 5 days: notification
		// D-2 days: escalate
		// D-0 days: approve
		assertEquals("Wrong # of triggers", 3, aCase.getTrigger().size());

		PrismAsserts.assertReferenceValues(ref(workItem.getAssigneeRef()), userLead1Oid);
		PrismAsserts.assertReferenceValue(ref(workItem.getOriginalAssigneeRef()), userLead1Oid);
	}

	@Test
	public void test110Notify() throws Exception {
		final String TEST_NAME = "test110Notify";
		TestUtil.displayTestTitle(this, TEST_NAME);
		login(userAdministrator);

		Task task = createTask(TEST_NAME);
		OperationResult result = task.getResult();

		clock.overrideDuration("P6D");			// at P5D there's a notify action
		waitForTaskNextRun(TASK_TRIGGER_SCANNER_OID, true, 20000, true);

		// TODO assert notifications

		CaseWorkItemType workItem = getWorkItem(task, result);
		display("work item", workItem);
		CaseType aCase = CaseWorkItemUtil.getCaseRequired(workItem);
		display("case", aCase);
		assertEquals("Wrong # of triggers", 2, aCase.getTrigger().size());

		PrismAsserts.assertReferenceValues(ref(workItem.getAssigneeRef()), userLead1Oid);
		PrismAsserts.assertReferenceValue(ref(workItem.getOriginalAssigneeRef()), userLead1Oid);
	}

	@Test
	public void test120Escalate() throws Exception {
		final String TEST_NAME = "test120Escalate";
		TestUtil.displayTestTitle(this, TEST_NAME);
		login(userAdministrator);

		Task task = createTask(TEST_NAME);
		OperationResult result = task.getResult();

		clock.resetOverride();
		clock.overrideDuration("P13D");		// at -P2D (i.e. P12D) there is a delegate action
		waitForTaskNextRun(TASK_TRIGGER_SCANNER_OID, true, 20000, true);

		CaseWorkItemType workItem = getWorkItem(task, result);
		display("work item", workItem);
		CaseType aCase = CaseWorkItemUtil.getCaseRequired(workItem);
		display("case", aCase);
		assertEquals("Wrong # of triggers", 1, aCase.getTrigger().size());

		PrismAsserts.assertReferenceValues(ref(workItem.getAssigneeRef()), userLead1Oid, userLead2Oid);
		PrismAsserts.assertReferenceValue(ref(workItem.getOriginalAssigneeRef()), userLead1Oid);
		assertEquals("Wrong escalation level number", 1, ApprovalContextUtil.getEscalationLevelNumber(workItem));

	}

	@Test
	public void test130Complete() throws Exception {
		final String TEST_NAME = "test130Complete";
		TestUtil.displayTestTitle(this, TEST_NAME);
		login(userAdministrator);

		Task task = createTask(TEST_NAME);
		OperationResult result = task.getResult();

		clock.resetOverride();
		clock.overrideDuration("P15D");		// at 0 (i.e. P14D) there is a delegate action
		waitForTaskNextRun(TASK_TRIGGER_SCANNER_OID, true, 20000, true);

		CaseType aCase = getCase(approvalCaseOid);
		display("task", aCase);
		assertEquals("Wrong # of triggers", 0, aCase.getTrigger().size());

		CaseType rootCase = getCase(aCase.getParentRef().getOid());
		display("rootTask", rootCase);
		waitForCaseClose(rootCase, 60000);

		assertAssignedRole(userJackOid, roleE1Oid, task, result);
	}

	@Test
	public void test200CreateTaskE2() throws Exception {
		final String TEST_NAME = "test200CreateTaskE2";
		TestUtil.displayTestTitle(this, TEST_NAME);
		login(userAdministrator);

		Task task = createTask(TEST_NAME);
		OperationResult result = task.getResult();

		clock.resetOverride();
		resetTriggerTask(TASK_TRIGGER_SCANNER_OID, TASK_TRIGGER_SCANNER_FILE, result);

		// WHEN

		assignRole(userJackOid, roleE2Oid, task, result);				// should start approval process

		// THEN
		assertNotAssignedRole(userJackOid, roleE2Oid, task, result);

		List<CaseWorkItemType> workItems = getWorkItems(task, result);
		displayWorkItems("Work items", workItems);

		approvalCaseOid = CaseWorkItemUtil.getCaseRequired(workItems.get(0)).getOid();
		CaseType aCase = getCase(approvalCaseOid);

		display("workflow case", aCase);

		// D-0 days: escalate (twice)
		assertEquals("Wrong # of triggers", 2, aCase.getTrigger().size());
	}

	@Test
	public void test210Escalate() throws Exception {
		final String TEST_NAME = "test210Escalate";
		TestUtil.displayTestTitle(this, TEST_NAME);
		login(userAdministrator);

		Task task = createTask(TEST_NAME);
		OperationResult result = task.getResult();

		dummyAuditService.clear();
		dummyTransport.clearMessages();

		// WHEN

		clock.overrideDuration("P3DT10M");		// at 3D there's a deadline with escalation
		waitForTaskNextRun(TASK_TRIGGER_SCANNER_OID, true, 20000, true);

		// THEN

		SearchResultList<CaseWorkItemType> workItems = getWorkItems(task, result);
		displayWorkItems("Work items after deadline", workItems);

		CaseType aCase = getCase(approvalCaseOid);
		display("workflow case", aCase);

		// D-0 days: reject (twice)
		assertEquals("Wrong # of triggers", 2, aCase.getTrigger().size());

		displayCollection("audit records", dummyAuditService.getRecords());
		display("dummy transport", dummyTransport);
	}

	@Test
	public void test220Reject() throws Exception {
		final String TEST_NAME = "test220Reject";
		TestUtil.displayTestTitle(this, TEST_NAME);
		login(userAdministrator);

		Task task = createTask(TEST_NAME);
		OperationResult result = task.getResult();

		dummyAuditService.clear();
		dummyTransport.clearMessages();

		// WHEN

		clock.resetOverride();
		clock.overrideDuration("P8DT20M");		// at 5D there's a deadline with auto-rejection
		waitForTaskNextRun(TASK_TRIGGER_SCANNER_OID, true, 20000, true);

		// THEN

		SearchResultList<CaseWorkItemType> workItems = getWorkItems(task, result);
		displayWorkItems("Work items after deadline", workItems);
		assertEquals("Wrong # of work items", 0, workItems.size());

		CaseType aCase = getCase(approvalCaseOid);
		display("workflow case", aCase);
		assertEquals("Wrong # of triggers", 0, aCase.getTrigger().size());
		Map<String, WorkItemCompletionEventType> eventMap = new HashMap<>();
		for (CaseEventType event : aCase.getEvent()) {
			if (event instanceof WorkItemCompletionEventType) {
				WorkItemCompletionEventType c = (WorkItemCompletionEventType) event;
				eventMap.put(c.getExternalWorkItemId(), c);
				assertNotNull("No result in "+c, c.getOutput());
				assertEquals("Wrong outcome in "+c, WorkItemOutcomeType.REJECT, ApprovalUtils.fromUri(c.getOutput().getOutcome()));
				assertNotNull("No cause in "+c, c.getCause());
				assertEquals("Wrong cause type in "+c, WorkItemEventCauseTypeType.TIMED_ACTION, c.getCause().getType());
				assertEquals("Wrong cause name in "+c, "auto-reject", c.getCause().getName());
				assertEquals("Wrong cause display name in "+c, "Automatic rejection at deadline", c.getCause().getDisplayName());
			}
		}
		display("completion event map", eventMap);
		assertEquals("Wrong # of completion events", 2, eventMap.size());

		displayCollection("audit records", dummyAuditService.getRecords());
		List<AuditEventRecord> workItemAuditRecords = dummyAuditService.getRecordsOfType(AuditEventType.WORK_ITEM);
		assertEquals("Wrong # of work item audit records", 2, workItemAuditRecords.size());
		for (AuditEventRecord r : workItemAuditRecords) {
			assertEquals("Wrong causeType in "+r, Collections.singleton("timedAction"), r.getPropertyValues(WorkflowConstants.AUDIT_CAUSE_TYPE));
			assertEquals("Wrong causeName in "+r, Collections.singleton("auto-reject"), r.getPropertyValues(WorkflowConstants.AUDIT_CAUSE_NAME));
			assertEquals("Wrong causeDisplayName in "+r, Collections.singleton("Automatic rejection at deadline"), r.getPropertyValues(WorkflowConstants.AUDIT_CAUSE_DISPLAY_NAME));
			assertEquals("Wrong result in "+r, "Rejected", r.getResult());
		}
		displayCollection("notifications - process", dummyTransport.getMessages("dummy:simpleWorkflowNotifier-Processes"));
		List<Message> notifications = dummyTransport.getMessages("dummy:simpleWorkflowNotifier-WorkItems");
		displayCollection("notifications - work items", notifications);
		for (Message notification : notifications) {
			assertContains(notification, "Reason: Automatic rejection at deadline (timed action)");
			assertContains(notification, "Result: REJECTED");
		}
	}

	private void assertContains(Message notification, String text) {
		if (!notification.getBody().contains(text)) {
			fail("No '"+text+"' in "+notification);
		}
	}

}
