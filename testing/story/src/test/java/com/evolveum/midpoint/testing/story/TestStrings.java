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

package com.evolveum.midpoint.testing.story;

import static com.evolveum.midpoint.prism.util.PrismAsserts.assertReferenceValues;
import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertNotNull;
import static org.testng.AssertJUnit.assertNull;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.evolveum.midpoint.schema.util.CaseWorkItemUtil;
import com.evolveum.midpoint.schema.util.WorkItemId;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;
import org.apache.commons.collections4.multimap.ArrayListValuedHashMap;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.context.ContextConfiguration;
import org.testng.annotations.Test;

import com.evolveum.midpoint.audit.api.AuditEventRecord;
import com.evolveum.midpoint.audit.api.AuditEventStage;
import com.evolveum.midpoint.model.api.WorkflowService;
import com.evolveum.midpoint.model.test.DummyTransport;
import com.evolveum.midpoint.notifications.api.transports.Message;
import com.evolveum.midpoint.prism.PrismContext;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.delta.ItemDelta;
import com.evolveum.midpoint.prism.delta.ObjectDelta;
import com.evolveum.midpoint.prism.util.PrismAsserts;
import com.evolveum.midpoint.prism.xml.XmlTypeConverter;
import com.evolveum.midpoint.schema.ObjectDeltaOperation;
import com.evolveum.midpoint.schema.SearchResultList;
import com.evolveum.midpoint.schema.constants.ObjectTypes;
import com.evolveum.midpoint.schema.constants.SchemaConstants;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.schema.util.ObjectTypeUtil;
import com.evolveum.midpoint.schema.util.ApprovalContextUtil;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.util.DebugUtil;
import com.evolveum.midpoint.util.exception.ObjectNotFoundException;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.wf.api.WorkflowConstants;
import com.evolveum.midpoint.wf.util.ApprovalUtils;

/**
 *
 * @author mederly
 *
 */

@SuppressWarnings("FieldCanBeLocal")
@ContextConfiguration(locations = {"classpath:ctx-story-test-main.xml"})
@DirtiesContext(classMode = ClassMode.AFTER_CLASS)
public class TestStrings extends AbstractStoryTest {

	@Autowired private WorkflowService workflowService;
	@Autowired private DummyTransport dummyTransport;

	private static final String TEST_DIR = "src/test/resources/strings";
	private static final String ORG_DIR = TEST_DIR + "/orgs";
	private static final String ROLES_DIR = TEST_DIR + "/roles";
	private static final String ROLES_SPECIFIC_DIR = TEST_DIR + "/roles-specific";
	private static final String USERS_DIR = TEST_DIR + "/users";

	private static final File ORG_MONKEY_ISLAND_FILE = new File(ORG_DIR, "0-org-monkey-island-modified.xml");
	private static final File ORG_TEAMS_FILE = new File(ORG_DIR, "1-teams.xml");
	private static String orgTeamsOid;
	private static final File ORG_ROLE_CATALOG_FILE = new File(ORG_DIR, "2-role-catalog.xml");
	private static String orgRoleCatalogOid;
	private static final File ORG_SECURITY_APPROVERS_FILE = new File(ORG_DIR, "security-approvers.xml");
	private static String orgSecurityApproversOid;
	private static final File ORG_SOD_APPROVERS_FILE = new File(ORG_DIR, "sod-approvers.xml");
	private static String orgSodApproversOid;

	private static final File ROLE_END_USER_FILE = new File(ROLES_DIR, "role-end-user.xml");
	private static String roleEndUserOid;
	private static final File FORM_USER_DETAILS_FILE = new File(ROLES_DIR, "form-user-details.xml");
	private static String formUserDetailsOid;
	private static final File METAROLE_APPROVAL_ROLE_APPROVERS_FIRST_FILE = new File(ROLES_DIR, "metarole-approval-role-approvers-first.xml");
	private static String metaroleApprovalRoleApproversFirstOid;
	private static final File METAROLE_APPROVAL_ROLE_APPROVERS_FORM_FILE = new File(ROLES_DIR, "metarole-approval-role-approvers-form.xml");
	private static String metaroleApprovalRoleApproversFormOid;
	private static final File METAROLE_APPROVAL_SECURITY_FILE = new File(ROLES_DIR, "metarole-approval-security.xml");
	private static String metaroleApprovalSecurityOid;

	private static final File ROLE_A_TEST_1 = new File(ROLES_SPECIFIC_DIR, "a-test-1.xml");
	private static String roleATest1Oid;
	private static final File ROLE_A_TEST_2A = new File(ROLES_SPECIFIC_DIR, "a-test-2a.xml");
	private static String roleATest2aOid;
	private static final File ROLE_A_TEST_2B = new File(ROLES_SPECIFIC_DIR, "a-test-2b.xml");
	private static String roleATest2bOid;
	private static final File ROLE_A_TEST_3A = new File(ROLES_SPECIFIC_DIR, "a-test-3a.xml");
	private static String roleATest3aOid;
	private static final File ROLE_A_TEST_3B = new File(ROLES_SPECIFIC_DIR, "a-test-3b.xml");
	private static String roleATest3bOid;
	private static final File ROLE_A_TEST_3X = new File(ROLES_SPECIFIC_DIR, "a-test-3x.xml");
	private static String roleATest3xOid;
	private static final File ROLE_A_TEST_3Y = new File(ROLES_SPECIFIC_DIR, "a-test-3y.xml");
	private static String roleATest3yOid;
	private static final File ROLE_A_TEST_4 = new File(ROLES_SPECIFIC_DIR, "a-test-4.xml");
	private static String roleATest4Oid;

	private static final File USER_BARKEEPER_FILE = new File(USERS_DIR, "barkeeper.xml");
	private static String userBarkeeperOid;
	private static final File USER_BOB_FILE = new File(USERS_DIR, "bob.xml");
	private static String userBobOid;
	private static final File USER_CARLA_FILE = new File(USERS_DIR, "carla.xml");
	private static String userCarlaOid;
	private static final File USER_CHEESE_FILE = new File(USERS_DIR, "cheese.xml");
	private static String userCheeseOid;
	private static final File USER_CHEF_FILE = new File(USERS_DIR, "chef.xml");
	private static String userChefOid;
	private static final File USER_ELAINE_FILE = new File(USERS_DIR, "elaine.xml");
	private static String userElaineOid;
	private static final File USER_GUYBRUSH_FILE = new File(USERS_DIR, "guybrush.xml");
	private static String userGuybrushOid;
	private static final File USER_LECHUCK_FILE = new File(USERS_DIR, "lechuck.xml");
	private static String userLechuckOid;
	private static final File USER_LECHUCK_DEPUTY_FILE = new File(USERS_DIR, "lechuck-deputy.xml");
	private static String userLechuckDeputyOid;
	private static final File USER_LECHUCK_DEPUTY_DEPUTY_FILE = new File(USERS_DIR, "lechuck-deputy-deputy.xml");
	private static String userLechuckDeputyDeputyOid;
	private static final File USER_LECHUCK_DEPUTY_LIMITED_FILE = new File(USERS_DIR, "lechuck-deputy-limited.xml");
	private static String userLechuckDeputyLimitedOid;

	private static final File CONFIG_WITH_GLOBAL_RULES_FILE = new File(ROLES_DIR, "global-policy-rules.xml");

	public static final String NS_STRINGS_EXT = "http://midpoint.evolveum.com/xml/ns/strings";

	private static final String DUMMY_WORK_ITEM_LIFECYCLE = "dummy:workItemLifecycle";
	private static final String DUMMY_WORK_ITEM_ALLOCATION = "dummy:workItemAllocation";
	private static final String DUMMY_WORK_ITEM_CUSTOM = "dummy:workItemCustom";
	private static final String DUMMY_PROCESS = "dummy:process";

	protected static final int TASK_WAIT_TIMEOUT = 40000;

	@Override
	public void initSystem(Task initTask, OperationResult initResult) throws Exception {
		super.initSystem(initTask, initResult);

		transplantGlobalPolicyRulesAdd(CONFIG_WITH_GLOBAL_RULES_FILE, initTask, initResult);

		// we prefer running trigger scanner by hand
		resetTriggerTask(TASK_TRIGGER_SCANNER_OID, TASK_TRIGGER_SCANNER_FILE, initResult);
		// and we don't need validity scanner
		taskManager.suspendAndDeleteTasks(Collections.singletonList(TASK_VALIDITY_SCANNER_OID), 60000L, true, initResult);

		Task triggerScanner = taskManager.getTask(TASK_TRIGGER_SCANNER_OID, initResult);
		display("triggerScanner", triggerScanner);

		// import of story objects
		repoAddObjectsFromFile(ORG_MONKEY_ISLAND_FILE, OrgType.class, initResult);
		orgTeamsOid = repoAddObjectFromFile(ORG_TEAMS_FILE, initResult).getOid();
		orgRoleCatalogOid = repoAddObjectFromFile(ORG_ROLE_CATALOG_FILE, initResult).getOid();
		orgSecurityApproversOid = repoAddObjectFromFile(ORG_SECURITY_APPROVERS_FILE, initResult).getOid();
		orgSodApproversOid = repoAddObjectFromFile(ORG_SOD_APPROVERS_FILE, initResult).getOid();

		roleEndUserOid = repoAddObjectFromFile(ROLE_END_USER_FILE, initResult).getOid();
		formUserDetailsOid = repoAddObjectFromFile(FORM_USER_DETAILS_FILE, initResult).getOid();
		metaroleApprovalRoleApproversFirstOid = repoAddObjectFromFile(METAROLE_APPROVAL_ROLE_APPROVERS_FIRST_FILE, initResult).getOid();
		metaroleApprovalRoleApproversFormOid = repoAddObjectFromFile(METAROLE_APPROVAL_ROLE_APPROVERS_FORM_FILE, initResult).getOid();
		metaroleApprovalSecurityOid = repoAddObjectFromFile(METAROLE_APPROVAL_SECURITY_FILE, initResult).getOid();

		roleATest1Oid = addAndRecompute(ROLE_A_TEST_1, initTask, initResult);
		roleATest2aOid = addAndRecompute(ROLE_A_TEST_2A, initTask, initResult);
		roleATest2bOid = addAndRecompute(ROLE_A_TEST_2B, initTask, initResult);
		roleATest3aOid = addAndRecompute(ROLE_A_TEST_3A, initTask, initResult);
		roleATest3bOid = addAndRecompute(ROLE_A_TEST_3B, initTask, initResult);
		roleATest3xOid = addAndRecompute(ROLE_A_TEST_3X, initTask, initResult);
		roleATest3yOid = addAndRecompute(ROLE_A_TEST_3Y, initTask, initResult);
		roleATest4Oid = addAndRecompute(ROLE_A_TEST_4, initTask, initResult);

		userBarkeeperOid = addAndRecomputeUser(USER_BARKEEPER_FILE, initTask, initResult);
		userBobOid = addAndRecomputeUser(USER_BOB_FILE, initTask, initResult);
		userCarlaOid = addAndRecomputeUser(USER_CARLA_FILE, initTask, initResult);
		userCheeseOid = addAndRecomputeUser(USER_CHEESE_FILE, initTask, initResult);
		userChefOid = addAndRecomputeUser(USER_CHEF_FILE, initTask, initResult);
		userElaineOid = addAndRecomputeUser(USER_ELAINE_FILE, initTask, initResult);
		userGuybrushOid = addAndRecomputeUser(USER_GUYBRUSH_FILE, initTask, initResult);
		userLechuckOid = addAndRecomputeUser(USER_LECHUCK_FILE, initTask, initResult);
		userLechuckDeputyOid = addAndRecomputeUser(USER_LECHUCK_DEPUTY_FILE, initTask, initResult);
		userLechuckDeputyDeputyOid = addAndRecomputeUser(USER_LECHUCK_DEPUTY_DEPUTY_FILE, initTask, initResult);
		userLechuckDeputyLimitedOid = addAndRecomputeUser(USER_LECHUCK_DEPUTY_LIMITED_FILE, initTask, initResult);

		DebugUtil.setPrettyPrintBeansAs(PrismContext.LANG_YAML);
	}

	@Override
	protected PrismObject<UserType> getDefaultActor() {
		return userAdministrator;
	}

	@Test
    public void test000Sanity() throws Exception {
		final String TEST_NAME = "test000Sanity";
        displayTestTitle(TEST_NAME);
		Task task = createTask(TEST_NAME);

        // TODO
	}

	//region Basic approval
	@Test
    public void test100SimpleAssignmentStart() throws Exception {
		final String TEST_NAME = "test100SimpleAssignmentStart";
		displayTestTitle(TEST_NAME);
		Task task = createTask(TEST_NAME);
		OperationResult result = task.getResult();

		dummyAuditService.clear();
		dummyTransport.clearMessages();

		// WHEN
		displayWhen(TEST_NAME);
		assignRole(userBobOid, roleATest1Oid, task, task.getResult());

		// THEN
		displayThen(TEST_NAME);
		assertNotAssignedRole(getUser(userBobOid), roleATest1Oid);

		CaseWorkItemType workItem = getWorkItem(task, result);
		display("Work item", workItem);
		CaseType aCase = CaseWorkItemUtil.getCaseRequired(workItem);
		display("wfTask", aCase);

		assertTriggers(aCase, 2);

		ApprovalContextType actx = aCase.getApprovalContext();
		ApprovalSchemaType schema = actx.getApprovalSchema();
		assertEquals("Wrong # of approval levels", 3, schema.getStage().size());
		assertApprovalLevel(schema, 1, "Line managers", "P5D", 2);
		assertApprovalLevel(schema, 2, "Security", "P7D", 1);
		assertApprovalLevel(schema, 3, "Role approvers (all)", "P5D", 2);
		assertStage(aCase, 1, 3, "Line managers", null);
		assertAssignee(workItem, userLechuckOid, userLechuckOid);

		List<Message> lifecycleMessages = dummyTransport.getMessages(DUMMY_WORK_ITEM_LIFECYCLE);
		List<Message> allocationMessages = dummyTransport.getMessages(DUMMY_WORK_ITEM_ALLOCATION);
		List<Message> processMessages = dummyTransport.getMessages(DUMMY_PROCESS);
		display("work items lifecycle notifications", lifecycleMessages);
		display("work items allocation notifications", allocationMessages);
		display("processes notifications", processMessages);

		assertEquals("Wrong # of work items lifecycle messages", 3, lifecycleMessages.size());
		Map<String,Message> sorted = sortByRecipientsSingle(lifecycleMessages);
		assertMessage(sorted.get("lechuck@evolveum.com"), "lechuck@evolveum.com", "A new work item has been created",
				"Stage: Line managers (1/3)", "Allocated to: Captain LeChuck (lechuck)", "(in 5 days)");
		assertMessage(sorted.get("lechuck-deputy@evolveum.com"), "lechuck-deputy@evolveum.com", "A new work item has been created",
				"Stage: Line managers (1/3)", "Allocated to: Captain LeChuck (lechuck)", "(in 5 days)");
		assertMessage(sorted.get("lechuck-deputy-deputy@evolveum.com"), "lechuck-deputy-deputy@evolveum.com", "A new work item has been created",
				"Stage: Line managers (1/3)", "Allocated to: Captain LeChuck (lechuck)", "(in 5 days)");

		assertEquals("Wrong # of work items allocation messages", 3, allocationMessages.size());
		Map<String,Message> sorted2 = sortByRecipientsSingle(allocationMessages);
		assertMessage(sorted2.get("lechuck@evolveum.com"), "lechuck@evolveum.com", "Work item has been allocated to you",
				"Stage: Line managers (1/3)", "Allocated to: Captain LeChuck (lechuck)", "(in 5 days)");
		assertMessage(sorted2.get("lechuck-deputy@evolveum.com"), "lechuck-deputy@evolveum.com", "Work item has been allocated to you",
				"Stage: Line managers (1/3)", "Allocated to: Captain LeChuck (lechuck)", "(in 5 days)");
		assertMessage(sorted2.get("lechuck-deputy-deputy@evolveum.com"), "lechuck-deputy-deputy@evolveum.com", "Work item has been allocated to you",
				"Stage: Line managers (1/3)", "Allocated to: Captain LeChuck (lechuck)", "(in 5 days)");

		assertEquals("Wrong # of process messages", 1, processMessages.size());
		assertMessage(processMessages.get(0), "administrator@evolveum.com", "Workflow process instance has been started",
				"Process instance name: Assigning role \"a-test-1\" to user \"bob\"", "Stage: Line managers (1/3)");

		display("audit", dummyAuditService);
	}

	@Test
	public void test102SimpleAssignmentApproveByLechuck() throws Exception {
		final String TEST_NAME = "test102SimpleAssignmentApproveByLechuck";
		displayTestTitle(TEST_NAME);
		Task task = createTask(TEST_NAME);
		OperationResult result = task.getResult();

		dummyAuditService.clear();
		dummyTransport.clearMessages();

		// GIVEN
		login(userAdministrator);
		CaseWorkItemType workItem = getWorkItem(task, result);

		// WHEN
		displayWhen(TEST_NAME);
		PrismObject<UserType> lechuck = getUserFromRepo(userLechuckOid);
		login(lechuck);
		workflowService.completeWorkItem(WorkItemId.of(workItem), true, "OK. LeChuck", null, task, result);

		// THEN
		displayThen(TEST_NAME);
		login(userAdministrator);

		List<CaseWorkItemType> workItems = getWorkItems(task, result);
		displayWorkItems("Work item after 1st approval", workItems);
		assertEquals("Wrong # of work items on level 2", 2, workItems.size());
		CaseType aCase = CaseWorkItemUtil.getCaseRequired(workItem);
		display("wfTask after 1st approval", aCase);

		assertStage(aCase, 2, 3, "Security", null);
		assertTriggers(aCase, 4);

		// notifications
		List<Message> lifecycleMessages = dummyTransport.getMessages(DUMMY_WORK_ITEM_LIFECYCLE);
		List<Message> allocationMessages = dummyTransport.getMessages(DUMMY_WORK_ITEM_ALLOCATION);
		List<Message> processMessages = dummyTransport.getMessages(DUMMY_PROCESS);
		display("work items lifecycle notifications", lifecycleMessages);
		display("work items allocation notifications", allocationMessages);
		display("processes notifications", processMessages);
		dummyTransport.clearMessages();

		assertEquals("Wrong # of work items lifecycle messages", 5, lifecycleMessages.size());
		assertEquals("Wrong # of work items allocation messages", 5, allocationMessages.size());
		assertNull("process messages", processMessages);

		Map<String,Message> sorted = sortByRecipientsSingle(lifecycleMessages);
		assertMessage(sorted.get("lechuck@evolveum.com"), "lechuck@evolveum.com", "Work item has been completed",
				"Work item: Assigning role \"a-test-1\" to user \"bob\"", "Stage: Line managers (1/3)",
				"Allocated to: Captain LeChuck (lechuck)", "Result: APPROVED", "^Deadline:");
		assertMessage(sorted.get("lechuck-deputy@evolveum.com"), "lechuck-deputy@evolveum.com", "Work item has been completed",
				"Work item: Assigning role \"a-test-1\" to user \"bob\"", "Stage: Line managers (1/3)",
				"Allocated to: Captain LeChuck (lechuck)", "Result: APPROVED", "^Deadline:");
		assertMessage(sorted.get("lechuck-deputy-deputy@evolveum.com"), "lechuck-deputy-deputy@evolveum.com", "Work item has been completed",
				"Work item: Assigning role \"a-test-1\" to user \"bob\"", "Stage: Line managers (1/3)",
				"Allocated to: Captain LeChuck (lechuck)", "Result: APPROVED", "^Deadline:");
		assertMessage(sorted.get("elaine@evolveum.com"), "elaine@evolveum.com", "A new work item has been created",
				"Work item: Assigning role \"a-test-1\" to user \"bob\"", "Stage: Security (2/3)",
				"Allocated to: Elaine Marley (elaine)", "(in 7 days)", "^Result:");
		assertMessage(sorted.get("barkeeper@evolveum.com"), "barkeeper@evolveum.com", "A new work item has been created",
				"Work item: Assigning role \"a-test-1\" to user \"bob\"", "Stage: Security (2/3)",
				"Allocated to: Horridly Scarred Barkeep (barkeeper)", "(in 7 days)", "^Result:");

		Map<String,Message> sorted2 = sortByRecipientsSingle(allocationMessages);
		assertMessage(sorted2.get("lechuck@evolveum.com"), "lechuck@evolveum.com", "Work item has been completed",
				"Work item: Assigning role \"a-test-1\" to user \"bob\"", "Stage: Line managers (1/3)",
				"Allocated to: Captain LeChuck (lechuck)", "Result: APPROVED", "^Deadline:");
		assertMessage(sorted2.get("lechuck-deputy@evolveum.com"), "lechuck-deputy@evolveum.com", "Work item has been completed",
				"Work item: Assigning role \"a-test-1\" to user \"bob\"", "Stage: Line managers (1/3)",
				"Allocated to: Captain LeChuck (lechuck)", "Result: APPROVED", "^Deadline:");
		assertMessage(sorted2.get("lechuck-deputy-deputy@evolveum.com"), "lechuck-deputy-deputy@evolveum.com", "Work item has been completed",
				"Work item: Assigning role \"a-test-1\" to user \"bob\"", "Stage: Line managers (1/3)",
				"Allocated to: Captain LeChuck (lechuck)", "Result: APPROVED", "^Deadline:");
		assertMessage(sorted2.get("elaine@evolveum.com"), "elaine@evolveum.com", "Work item has been allocated to you",
				"Work item: Assigning role \"a-test-1\" to user \"bob\"", "Stage: Security (2/3)", "Allocated to: Elaine Marley (elaine)",
				"(in 7 days)", "^Result:");
		assertMessage(sorted2.get("barkeeper@evolveum.com"), "barkeeper@evolveum.com", "Work item has been allocated to you",
				"Work item: Assigning role \"a-test-1\" to user \"bob\"", "Stage: Security (2/3)",
				"Allocated to: Horridly Scarred Barkeep (barkeeper)", "(in 7 days)", "^Result:");

		// events
		List<CaseEventType> events = assertEvents(aCase, 2);
		assertCompletionEvent(events.get(1), userLechuckOid, userLechuckOid, 1, "Line managers", WorkItemOutcomeType.APPROVE, "OK. LeChuck");

		display("audit", dummyAuditService);
	}

	@Test
	public void test104SimpleAssignmentApproveByAdministrator() throws Exception {
		final String TEST_NAME = "test104SimpleAssignmentApproveByAdministrator";
		displayTestTitle(TEST_NAME);
		Task task = createTask(TEST_NAME);
		OperationResult result = task.getResult();

		dummyAuditService.clear();
		dummyTransport.clearMessages();

		// GIVEN
		login(userAdministrator);
		List<CaseWorkItemType> workItems = getWorkItems(task, result);
		displayWorkItems("Work item after 1st approval", workItems);
		CaseWorkItemType elaineWorkItem = workItems.stream()
				.filter(wi -> userElaineOid.equals(wi.getOriginalAssigneeRef().getOid()))
				.findFirst().orElseThrow(() -> new AssertionError("No work item for elaine"));

		// WHEN
		// Second approval
		workflowService.completeWorkItem(WorkItemId.of(elaineWorkItem), true, "OK. Security.", null, task, result);

		// THEN
		workItems = getWorkItems(task, result);
		displayWorkItems("Work item after 2nd approval", workItems);
		assertEquals("Wrong # of work items on level 3", 2, workItems.size());
		CaseType aCase = CaseWorkItemUtil.getCaseRequired(workItems.get(0));
		display("wfTask after 2nd approval", aCase);

		assertStage(aCase, 3, 3, "Role approvers (all)", null);
		assertTriggers(aCase, 4);

		Map<String, CaseWorkItemType> workItemsMap = sortByOriginalAssignee(workItems);
		assertNotNull("chef is not an approver", workItemsMap.get(userChefOid));
		assertNotNull("cheese is not an approver", workItemsMap.get(userCheeseOid));

		List<Message> lifecycleMessages = dummyTransport.getMessages(DUMMY_WORK_ITEM_LIFECYCLE);
		List<Message> allocationMessages = dummyTransport.getMessages(DUMMY_WORK_ITEM_ALLOCATION);
		List<Message> processMessages = dummyTransport.getMessages(DUMMY_PROCESS);
		display("work items lifecycle notifications", lifecycleMessages);
		display("work items allocation notifications", allocationMessages);
		display("processes notifications", processMessages);
		dummyTransport.clearMessages();

		assertEquals("Wrong # of work items lifecycle messages", 4, lifecycleMessages.size());
		assertEquals("Wrong # of work items allocation messages", 4, allocationMessages.size());
		assertNull("process messages", processMessages);

		Map<String,Message> sorted = sortByRecipientsSingle(lifecycleMessages);
		assertMessage(sorted.get("elaine@evolveum.com"), "elaine@evolveum.com", "Work item has been completed",
				"Work item: Assigning role \"a-test-1\" to user \"bob\"", "Stage: Security (2/3)", "Allocated to: Elaine Marley (elaine)",
				"Carried out by: midPoint Administrator (administrator)", "Result: APPROVED", "^Deadline:");
		assertMessage(sorted.get("barkeeper@evolveum.com"), "barkeeper@evolveum.com", "Work item has been cancelled",
				"Work item: Assigning role \"a-test-1\" to user \"bob\"", "Stage: Security (2/3)",
				"Allocated to: Horridly Scarred Barkeep (barkeeper)", "^Result:", "^Deadline:", "^Carried out by:");
		assertMessage(sorted.get("cheese@evolveum.com"), "cheese@evolveum.com", "A new work item has been created",
				"Work item: Assigning role \"a-test-1\" to user \"bob\"", "Role approvers (all) (3/3)",
				"Allocated to: Ignatius Cheese (cheese)", "^Result:", "(in 5 days)");
		assertMessage(sorted.get("chef@evolveum.com"), "chef@evolveum.com", "A new work item has been created",
				"Work item: Assigning role \"a-test-1\" to user \"bob\"", "Role approvers (all) (3/3)",
				"Allocated to: Scumm Bar Chef (chef)", "^Result:", "(in 5 days)");

		Map<String,Message> sorted2 = sortByRecipientsSingle(allocationMessages);
		assertMessage(sorted2.get("elaine@evolveum.com"), "elaine@evolveum.com", "Work item has been completed",
				"Work item: Assigning role \"a-test-1\" to user \"bob\"", "Stage: Security (2/3)", "Allocated to: Elaine Marley (elaine)",
				"Carried out by: midPoint Administrator (administrator)", "Result: APPROVED", "^Deadline:");
		assertMessage(sorted2.get("barkeeper@evolveum.com"), "barkeeper@evolveum.com", "Work item has been cancelled",
				"Work item: Assigning role \"a-test-1\" to user \"bob\"", "Stage: Security (2/3)",
				"Allocated to: Horridly Scarred Barkeep (barkeeper)", "^Result:", "^Deadline:", "^Carried out by:");
		assertMessage(sorted2.get("cheese@evolveum.com"), "cheese@evolveum.com", "Work item has been allocated to you",
				"Work item: Assigning role \"a-test-1\" to user \"bob\"", "Role approvers (all) (3/3)",
				"Allocated to: Ignatius Cheese (cheese)", "^Result:", "(in 5 days)");
		assertMessage(sorted2.get("chef@evolveum.com"), "chef@evolveum.com", "Work item has been allocated to you",
				"Work item: Assigning role \"a-test-1\" to user \"bob\"", "Role approvers (all) (3/3)",
				"Allocated to: Scumm Bar Chef (chef)", "^Result:", "(in 5 days)");

		display("audit", dummyAuditService);
	}

	@Test
	public void test106SimpleAssignmentApproveByCheese() throws Exception {
		final String TEST_NAME = "test106SimpleAssignmentApproveByCheese";
		displayTestTitle(TEST_NAME);
		Task task = createTask(TEST_NAME);
		OperationResult result = task.getResult();

		dummyAuditService.clear();
		dummyTransport.clearMessages();

		// GIVEN
		login(userAdministrator);
		List<CaseWorkItemType> workItems = getWorkItems(task, result);
		Map<String, CaseWorkItemType> workItemsMap = sortByOriginalAssignee(workItems);

		// WHEN
		login(getUser(userCheeseOid));
		workflowService.completeWorkItem(WorkItemId.of(workItemsMap.get(userCheeseOid)), true, "OK. Cheese.", null, task, result);

		// THEN
		login(userAdministrator);
		workItems = getWorkItems(task, result);
		displayWorkItems("Work item after 3rd approval", workItems);
		assertEquals("Wrong # of work items on level 3", 1, workItems.size());
		workItemsMap = sortByOriginalAssignee(workItems);
		CaseType aCase = CaseWorkItemUtil.getCaseRequired(workItems.get(0));
		display("case after 3rd approval", aCase);

		assertStage(aCase, 3, 3, "Role approvers (all)", null);
		assertTriggers(aCase, 2);

		assertNotNull("chef is not an approver", workItemsMap.get(userChefOid));

		List<Message> lifecycleMessages = dummyTransport.getMessages(DUMMY_WORK_ITEM_LIFECYCLE);
		List<Message> allocationMessages = dummyTransport.getMessages(DUMMY_WORK_ITEM_ALLOCATION);
		List<Message> processMessages = dummyTransport.getMessages(DUMMY_PROCESS);
		display("work items lifecycle notifications", lifecycleMessages);
		display("work items allocation notifications", allocationMessages);
		display("processes notifications", processMessages);
		dummyTransport.clearMessages();

		assertEquals("Wrong # of work items lifecycle messages", 1, lifecycleMessages.size());
		assertEquals("Wrong # of work items allocation messages", 1, allocationMessages.size());
		assertNull("process messages", processMessages);

		assertMessage(lifecycleMessages.get(0), "cheese@evolveum.com", "Work item has been completed",
				"Work item: Assigning role \"a-test-1\" to user \"bob\"", "Role approvers (all) (3/3)",
				"Allocated to: Ignatius Cheese (cheese)", "Carried out by: Ignatius Cheese (cheese)",
				"Result: APPROVED", "^Deadline:");
		assertMessage(allocationMessages.get(0), "cheese@evolveum.com", "Work item has been completed",
				"Work item: Assigning role \"a-test-1\" to user \"bob\"", "Role approvers (all) (3/3)",
				"Allocated to: Ignatius Cheese (cheese)", "Carried out by: Ignatius Cheese (cheese)",
				"Result: APPROVED", "^Deadline:");

		display("audit", dummyAuditService);
	}

	@Test
	public void test108SimpleAssignmentApproveByChef() throws Exception {
		final String TEST_NAME = "test108SimpleAssignmentApproveByChef";
		displayTestTitle(TEST_NAME);
		Task task = createTask(TEST_NAME);
		OperationResult result = task.getResult();

		// GIVEN
		login(userAdministrator);
		List<CaseWorkItemType> workItems = getWorkItems(task, result);
		Map<String, CaseWorkItemType> workItemsMap = sortByOriginalAssignee(workItems);

		// WHEN
		login(getUser(userChefOid));
		WorkItemId workItemId = WorkItemId.of(workItemsMap.get(userChefOid));
		workflowService.completeWorkItem(workItemId, true, "OK. Chef.", null, task, result);

		// THEN
		login(userAdministrator);
		workItems = getWorkItems(task, result);
		displayWorkItems("Work item after 4th approval", workItems);
		assertEquals("Wrong # of work items on level 3", 0, workItems.size());
		CaseType aCase = CaseWorkItemUtil.getCaseRequired(workItems.get(0));
		display("wfTask after 4th approval", aCase);

		CaseType parentCase = getCase(aCase.getParentRef().getOid());
		waitForCaseClose(parentCase);

		assertAssignedRole(getUser(userBobOid), roleATest1Oid);

		assertTriggers(aCase, 0);

		List<Message> lifecycleMessages = dummyTransport.getMessages(DUMMY_WORK_ITEM_LIFECYCLE);
		List<Message> allocationMessages = dummyTransport.getMessages(DUMMY_WORK_ITEM_ALLOCATION);
		List<Message> processMessages = dummyTransport.getMessages(DUMMY_PROCESS);
		display("work items lifecycle notifications", lifecycleMessages);
		display("work items allocation notifications", allocationMessages);
		display("processes notifications", processMessages);
		dummyTransport.clearMessages();

		assertEquals("Wrong # of work items lifecycle messages", 1, lifecycleMessages.size());
		assertEquals("Wrong # of work items allocation messages", 1, allocationMessages.size());
		assertEquals("Wrong # of process messages", 1, processMessages.size());

		assertMessage(lifecycleMessages.get(0), "chef@evolveum.com", "Work item has been completed",
				"Work item: Assigning role \"a-test-1\" to user \"bob\"", "Role approvers (all) (3/3)",
				"Allocated to: Scumm Bar Chef (chef)", "Carried out by: Scumm Bar Chef (chef)",
				"Result: APPROVED", "^Deadline:");
		assertMessage(allocationMessages.get(0), "chef@evolveum.com", "Work item has been completed",
				"Work item: Assigning role \"a-test-1\" to user \"bob\"", "Role approvers (all) (3/3)",
				"Allocated to: Scumm Bar Chef (chef)", "Carried out by: Scumm Bar Chef (chef)",
				"Result: APPROVED", "^Deadline:");
		assertMessage(processMessages.get(0), "administrator@evolveum.com", "Workflow process instance has finished",
				"Process instance name: Assigning role \"a-test-1\" to user \"bob\"", "Result: APPROVED");

		display("audit", dummyAuditService);

		List<AuditEventRecord> workItemEvents = filter(getParamAuditRecords(
				WorkflowConstants.AUDIT_WORK_ITEM_ID, workItemId.asString(), task, result), AuditEventStage.EXECUTION);
		assertAuditReferenceValue(workItemEvents, WorkflowConstants.AUDIT_OBJECT, userBobOid, UserType.COMPLEX_TYPE, "bob");
		assertAuditTarget(workItemEvents.get(0), userBobOid, UserType.COMPLEX_TYPE, "bob");
		assertAuditReferenceValue(workItemEvents.get(0), WorkflowConstants.AUDIT_TARGET, roleATest1Oid, RoleType.COMPLEX_TYPE, "a-test-1");
		// TODO other items
		// TODO-WF
//		List<AuditEventRecord> processEvents = filter(getParamAuditRecords(
//				WorkflowConstants.AUDIT_PROCESS_INSTANCE_ID, wfTask.asObjectable().getApprovalContext().getCaseOid(), task, result),
//				AuditEventType.WORKFLOW_PROCESS_INSTANCE, AuditEventStage.EXECUTION);
//		assertAuditReferenceValue(processEvents, WorkflowConstants.AUDIT_OBJECT, userBobOid, UserType.COMPLEX_TYPE, "bob");
//		assertAuditTarget(processEvents.get(0), userBobOid, UserType.COMPLEX_TYPE, "bob");
//		assertAuditReferenceValue(processEvents.get(0), WorkflowConstants.AUDIT_TARGET, roleATest1Oid, RoleType.COMPLEX_TYPE, "a-test-1");
		// TODO other items
	}

	//endregion

	//region Testing escalation
	@Test
	public void test200EscalatedApprovalStart() throws Exception {
		final String TEST_NAME = "test200EscalatedApprovalStart";
		displayTestTitle(TEST_NAME);
		Task task = createTask(TEST_NAME);
		OperationResult result = task.getResult();

		dummyAuditService.clear();
		dummyTransport.clearMessages();

		// WHEN
		assignRole(userCarlaOid, roleATest1Oid, task, task.getResult());

		// THEN
		assertNotAssignedRole(getUser(userCarlaOid), roleATest1Oid);

		CaseWorkItemType workItem = getWorkItem(task, result);
		display("Work item", workItem);
		CaseType aCase = CaseWorkItemUtil.getCaseRequired(workItem);
		display("case", aCase);

		assertTriggers(aCase, 2);

		assertStage(aCase, 1, 3, "Line managers", null);
		assertAssignee(workItem, userGuybrushOid, userGuybrushOid);

		List<Message> lifecycleMessages = dummyTransport.getMessages(DUMMY_WORK_ITEM_LIFECYCLE);
		List<Message> allocationMessages = dummyTransport.getMessages(DUMMY_WORK_ITEM_ALLOCATION);
		List<Message> processMessages = dummyTransport.getMessages(DUMMY_PROCESS);
		display("work items lifecycle notifications", lifecycleMessages);
		display("work items allocation notifications", allocationMessages);
		display("processes notifications", processMessages);
		dummyTransport.clearMessages();

		assertEquals("Wrong # of work items messages", 1, lifecycleMessages.size());
		assertMessage(lifecycleMessages.get(0), "guybrush@evolveum.com", "A new work item has been created", "Stage: Line managers (1/3)", "Allocated to: Guybrush Threepwood (guybrush)");
		assertMessage(allocationMessages.get(0), "guybrush@evolveum.com", "Work item has been allocated to you", "Stage: Line managers (1/3)", "Allocated to: Guybrush Threepwood (guybrush)");

		assertEquals("Wrong # of work items allocation messages", 1, allocationMessages.size());
		//assertMessage(lifecycleMessages.get(0), "guybrush@evolveum.com", "A new work item has been created", "Stage: Line managers (1/3)", "Guybrush Threepwood (guybrush)");

		assertEquals("Wrong # of process messages", 1, processMessages.size());
		assertMessage(processMessages.get(0), "administrator@evolveum.com", "Workflow process instance has been started",
				"Process instance name: Assigning role \"a-test-1\" to user \"carla\"", "Stage: Line managers (1/3)");

		display("audit", dummyAuditService);
	}

	@Test
	public void test202FourDaysLater() throws Exception {
		final String TEST_NAME = "test202FourDaysLater";
		displayTestTitle(TEST_NAME);
		Task task = createTask(TEST_NAME);
		OperationResult result = task.getResult();

		dummyAuditService.clear();
		dummyTransport.clearMessages();

		// WHEN
		clock.overrideDuration("P4D");
		waitForTaskNextRun(TASK_TRIGGER_SCANNER_OID, true, 20000, true);

		// THEN
		List<Message> lifecycleMessages = dummyTransport.getMessages(DUMMY_WORK_ITEM_LIFECYCLE);
		List<Message> allocationMessages = dummyTransport.getMessages(DUMMY_WORK_ITEM_ALLOCATION);
		List<Message> processMessages = dummyTransport.getMessages(DUMMY_PROCESS);
		display("work items lifecycle notifications", lifecycleMessages);
		display("work items allocation notifications", allocationMessages);
		display("processes notifications", processMessages);
		dummyTransport.clearMessages();

		assertNull("lifecycle messages", lifecycleMessages);
		assertEquals("Wrong # of work items allocation messages", 1, allocationMessages.size());
		assertMessage(allocationMessages.get(0), "guybrush@evolveum.com", "Work item will be automatically escalated in 1 day",
				"Stage: Line managers (1/3)", "Allocated to (before escalation): Guybrush Threepwood (guybrush)");
		assertNull("process messages", processMessages);

		display("audit", dummyAuditService);
	}

	// escalation should occur here
	@Test
	public void test204SixDaysLater() throws Exception {
		final String TEST_NAME = "test204SixDaysLater";
		displayTestTitle(TEST_NAME);
		Task task = createTask(TEST_NAME);
		OperationResult result = task.getResult();

		dummyAuditService.clear();
		dummyTransport.clearMessages();

		// WHEN
		clock.resetOverride();
		clock.overrideDuration("P6D");
		waitForTaskNextRun(TASK_TRIGGER_SCANNER_OID, true, 20000, true);

		// THEN
		List<CaseWorkItemType> workItems = getWorkItems(task, result);
		displayWorkItems("Work items after timed escalation", workItems);
		assertEquals("Wrong # of work items after timed escalation", 1, workItems.size());
		CaseType aCase = CaseWorkItemUtil.getCaseRequired(workItems.get(0));
		display("aCase after timed escalation", aCase);

		List<Message> lifecycleMessages = dummyTransport.getMessages(DUMMY_WORK_ITEM_LIFECYCLE);
		List<Message> allocationMessages = dummyTransport.getMessages(DUMMY_WORK_ITEM_ALLOCATION);
		List<Message> processMessages = dummyTransport.getMessages(DUMMY_PROCESS);
		display("work items lifecycle notifications", lifecycleMessages);
		display("work items allocation notifications", allocationMessages);
		display("processes notifications", processMessages);
		dummyTransport.clearMessages();

		// asserts - work item
		CaseWorkItemType workItem = workItems.get(0);
		PrismAsserts.assertReferenceValues(ref(workItem.getAssigneeRef()), userGuybrushOid, userCheeseOid);
		PrismAsserts.assertDuration("Wrong duration between now and deadline", "P9D", System.currentTimeMillis(), workItem.getDeadline(), null);
		PrismAsserts.assertReferenceValue(ref(workItem.getOriginalAssigneeRef()), userGuybrushOid);
		assertEquals("Wrong stage #", (Integer) 1, workItem.getStageNumber());
		assertEquals("Wrong escalation level #", 1, ApprovalContextUtil.getEscalationLevelNumber(workItem));
		assertEquals("Wrong escalation level name", "Line manager escalation", ApprovalContextUtil.getEscalationLevelName(workItem));

		List<CaseEventType> events = assertEvents(aCase, 2);
		assertEscalationEvent(events.get(1), userAdministrator.getOid(), userGuybrushOid, 1, "Line managers",
				Collections.singletonList(userGuybrushOid), Collections.singletonList(userCheeseOid), WorkItemDelegationMethodType.ADD_ASSIGNEES,
				1, "Line manager escalation");

		// asserts - notifications
		assertNull("lifecycle messages", lifecycleMessages);
		assertNull("process messages", processMessages);
		assertEquals("Wrong # of work items allocation messages", 3, allocationMessages.size());

		ArrayListValuedHashMap<String, Message> sorted = sortByRecipients(allocationMessages);
		assertMessage(sorted.get("guybrush@evolveum.com").get(0), "guybrush@evolveum.com", "Work item has been escalated",
				"Work item: Assigning role \"a-test-1\" to user \"carla\"", "Stage: Line managers (1/3)",
				"Allocated to (before escalation): Guybrush Threepwood (guybrush)",
				"(in 5 days)");
		assertMessage(sorted.get("guybrush@evolveum.com").get(1), "guybrush@evolveum.com", "Work item has been allocated to you",
				"Work item: Assigning role \"a-test-1\" to user \"carla\"", "Stage: Line managers (1/3)",
				"Escalation level: Line manager escalation (1)",
				"|Allocated to (after escalation): Guybrush Threepwood (guybrush), Ignatius Cheese (cheese)|Allocated to (after escalation): Ignatius Cheese (cheese), Guybrush Threepwood (guybrush)",
				"(in 9 days)");
		assertMessage(sorted.get("cheese@evolveum.com").get(0), "cheese@evolveum.com", "Work item has been allocated to you",
				"Work item: Assigning role \"a-test-1\" to user \"carla\"", "Stage: Line managers (1/3)",
				"Escalation level: Line manager escalation (1)",
				"|Allocated to (after escalation): Guybrush Threepwood (guybrush), Ignatius Cheese (cheese)|Allocated to (after escalation): Ignatius Cheese (cheese), Guybrush Threepwood (guybrush)",
				"(in 9 days)");

		display("audit", dummyAuditService);
	}

	@Test
	public void test205EightDaysLater() throws Exception {
		final String TEST_NAME = "test205EightDaysLater";
		displayTestTitle(TEST_NAME);
		Task task = createTask(TEST_NAME);
		OperationResult result = task.getResult();

		dummyAuditService.clear();
		dummyTransport.clearMessages();

		// WHEN
		clock.resetOverride();
		clock.overrideDuration("P8D");
		waitForTaskNextRun(TASK_TRIGGER_SCANNER_OID, true, 20000, true);

		// THEN
		List<Message> lifecycleMessages = dummyTransport.getMessages(DUMMY_WORK_ITEM_LIFECYCLE);
		List<Message> allocationMessages = dummyTransport.getMessages(DUMMY_WORK_ITEM_ALLOCATION);
		List<Message> processMessages = dummyTransport.getMessages(DUMMY_PROCESS);
		display("work items lifecycle notifications", lifecycleMessages);
		display("work items allocation notifications", allocationMessages);
		display("processes notifications", processMessages);
		dummyTransport.clearMessages();

		assertNull("lifecycle messages", lifecycleMessages);
		assertNull("process messages", processMessages);
		assertEquals("Wrong # of work items allocation messages", 4, allocationMessages.size());
		ArrayListValuedHashMap<String, Message> sorted = sortByRecipients(allocationMessages);
		assertMessage(sorted.get("guybrush@evolveum.com").get(0), "guybrush@evolveum.com", "Work item will be automatically completed in 2 days 12 hours",
				"Work item: Assigning role \"a-test-1\" to user \"carla\"", "Stage: Line managers (1/3)",
				"Escalation level: Line manager escalation (1)",
				"|Allocated to: Guybrush Threepwood (guybrush), Ignatius Cheese (cheese)|Allocated to: Ignatius Cheese (cheese), Guybrush Threepwood (guybrush)",
				"(in 9 days)");
		assertMessage(sorted.get("guybrush@evolveum.com").get(1), "guybrush@evolveum.com", "Work item will be automatically completed in 2 days",
				"Work item: Assigning role \"a-test-1\" to user \"carla\"", "Stage: Line managers (1/3)",
				"Escalation level: Line manager escalation (1)",
				"|Allocated to: Guybrush Threepwood (guybrush), Ignatius Cheese (cheese)|Allocated to: Ignatius Cheese (cheese), Guybrush Threepwood (guybrush)",
				"(in 9 days)");
		assertMessage(sorted.get("cheese@evolveum.com").get(0), "cheese@evolveum.com", "Work item will be automatically completed in 2 days 12 hours",
				"Work item: Assigning role \"a-test-1\" to user \"carla\"", "Stage: Line managers (1/3)",
				"Escalation level: Line manager escalation (1)",
				"|Allocated to: Guybrush Threepwood (guybrush), Ignatius Cheese (cheese)|Allocated to: Ignatius Cheese (cheese), Guybrush Threepwood (guybrush)",
				"(in 9 days)");
		assertMessage(sorted.get("cheese@evolveum.com").get(1), "cheese@evolveum.com", "Work item will be automatically completed in 2 days",
				"Work item: Assigning role \"a-test-1\" to user \"carla\"", "Stage: Line managers (1/3)",
				"Escalation level: Line manager escalation (1)",
				"|Allocated to: Guybrush Threepwood (guybrush), Ignatius Cheese (cheese)|Allocated to: Ignatius Cheese (cheese), Guybrush Threepwood (guybrush)",
				"(in 9 days)");

		display("audit", dummyAuditService);
	}

	@Test
	public void test206ApproveByCheese() throws Exception {
		final String TEST_NAME = "test206ApproveByCheese";
		displayTestTitle(TEST_NAME);
		Task task = createTask(TEST_NAME);
		OperationResult result = task.getResult();

		dummyAuditService.clear();
		dummyTransport.clearMessages();

		// GIVEN
		login(userAdministrator);
		clock.resetOverride();
		CaseWorkItemType workItem = getWorkItem(task, result);
		PrismObject<UserType> cheese = getUserFromRepo(userCheeseOid);
		login(cheese);

		// WHEN
		workflowService.completeWorkItem(WorkItemId.of(workItem), true, "OK. Cheese.", null, task, result);

		// THEN
		login(userAdministrator);

		List<CaseWorkItemType> workItems = getWorkItems(task, result);
		assertEquals("Wrong # of work items on level 2", 2, workItems.size());
		displayWorkItems("Work item after 1st approval", workItems);
		CaseType aCase = CaseWorkItemUtil.getCaseRequired(workItems.get(0));
		display("aCase after 1st approval", aCase);

		assertStage(aCase, 2, 3, "Security", null);
		assertTriggers(aCase, 4);

		List<Message> lifecycleMessages = dummyTransport.getMessages(DUMMY_WORK_ITEM_LIFECYCLE);
		List<Message> allocationMessages = dummyTransport.getMessages(DUMMY_WORK_ITEM_ALLOCATION);
		List<Message> processMessages = dummyTransport.getMessages(DUMMY_PROCESS);
		display("work items lifecycle notifications", lifecycleMessages);
		display("work items allocation notifications", allocationMessages);
		display("processes notifications", processMessages);
		dummyTransport.clearMessages();

		assertEquals("Wrong # of work items lifecycle messages", 4, lifecycleMessages.size());
		assertEquals("Wrong # of work items allocation messages", 4, allocationMessages.size());
		assertNull("process messages", processMessages);

		Map<String,Message> sorted = sortByRecipientsSingle(lifecycleMessages);
		assertMessage(sorted.get("guybrush@evolveum.com"), "guybrush@evolveum.com", "Work item has been completed",
				"Work item: Assigning role \"a-test-1\" to user \"carla\"", "Stage: Line managers (1/3)",
				"Escalation level: Line manager escalation (1)",
				"Originally allocated to: Guybrush Threepwood (guybrush)",
				"|Allocated to: Guybrush Threepwood (guybrush), Ignatius Cheese (cheese)|Allocated to: Ignatius Cheese (cheese), Guybrush Threepwood (guybrush)",
				"Carried out by: Ignatius Cheese (cheese)",
				"Result: APPROVED", "^Deadline:");
		assertMessage(sorted.get("cheese@evolveum.com"), "cheese@evolveum.com", "Work item has been completed",
				"Work item: Assigning role \"a-test-1\" to user \"carla\"", "Stage: Line managers (1/3)",
				"Escalation level: Line manager escalation (1)",
				"Originally allocated to: Guybrush Threepwood (guybrush)",
				"|Allocated to: Guybrush Threepwood (guybrush), Ignatius Cheese (cheese)|Allocated to: Ignatius Cheese (cheese), Guybrush Threepwood (guybrush)",
				"Carried out by: Ignatius Cheese (cheese)",
				"Result: APPROVED", "^Deadline:");
		assertMessage(sorted.get("elaine@evolveum.com"), "elaine@evolveum.com", "A new work item has been created",
				"Work item: Assigning role \"a-test-1\" to user \"carla\"", "Stage: Security (2/3)",
				"Allocated to: Elaine Marley (elaine)", "(in 7 days)", "^Result:");
		assertMessage(sorted.get("barkeeper@evolveum.com"), "barkeeper@evolveum.com", "A new work item has been created",
				"Work item: Assigning role \"a-test-1\" to user \"carla\"", "Stage: Security (2/3)",
				"Allocated to: Horridly Scarred Barkeep (barkeeper)", "(in 7 days)", "^Result:");

		Map<String,Message> sorted2 = sortByRecipientsSingle(allocationMessages);
		assertMessage(sorted2.get("guybrush@evolveum.com"), "guybrush@evolveum.com", "Work item has been completed",
				"Work item: Assigning role \"a-test-1\" to user \"carla\"", "Stage: Line managers (1/3)",
				"Escalation level: Line manager escalation (1)",
				"Originally allocated to: Guybrush Threepwood (guybrush)",
				"|Allocated to: Guybrush Threepwood (guybrush), Ignatius Cheese (cheese)|Allocated to: Ignatius Cheese (cheese), Guybrush Threepwood (guybrush)",
				"Carried out by: Ignatius Cheese (cheese)",
				"Result: APPROVED", "^Deadline:");
		assertMessage(sorted2.get("cheese@evolveum.com"), "cheese@evolveum.com", "Work item has been completed",
				"Work item: Assigning role \"a-test-1\" to user \"carla\"", "Stage: Line managers (1/3)",
				"Escalation level: Line manager escalation (1)",
				"Originally allocated to: Guybrush Threepwood (guybrush)",
				"|Allocated to: Guybrush Threepwood (guybrush), Ignatius Cheese (cheese)|Allocated to: Ignatius Cheese (cheese), Guybrush Threepwood (guybrush)",
				"Carried out by: Ignatius Cheese (cheese)",
				"Result: APPROVED", "^Deadline:");
		assertMessage(sorted2.get("elaine@evolveum.com"), "elaine@evolveum.com", "Work item has been allocated to you",
				"Work item: Assigning role \"a-test-1\" to user \"carla\"", "Stage: Security (2/3)",
				"Allocated to: Elaine Marley (elaine)", "(in 7 days)", "^Result:");
		assertMessage(sorted2.get("barkeeper@evolveum.com"), "barkeeper@evolveum.com", "Work item has been allocated to you",
				"Work item: Assigning role \"a-test-1\" to user \"carla\"", "Stage: Security (2/3)",
				"Allocated to: Horridly Scarred Barkeep (barkeeper)", "(in 7 days)", "^Result:");

		display("audit", dummyAuditService);
	}

	// notification should be send
	@Test
	public void test208SixDaysLater() throws Exception {
		final String TEST_NAME = "test208SixDaysLater";
		displayTestTitle(TEST_NAME);
		Task task = createTask(TEST_NAME);
		OperationResult result = task.getResult();

		dummyAuditService.clear();
		dummyTransport.clearMessages();

		// GIVEN
		clock.resetOverride();
		resetTriggerTask(TASK_TRIGGER_SCANNER_OID, TASK_TRIGGER_SCANNER_FILE, result);
		clock.overrideDuration("P6D");

		// WHEN
		waitForTaskNextRun(TASK_TRIGGER_SCANNER_OID, true, 20000, true);

		// THEN
		List<Message> lifecycleMessages = dummyTransport.getMessages(DUMMY_WORK_ITEM_LIFECYCLE);
		List<Message> allocationMessages = dummyTransport.getMessages(DUMMY_WORK_ITEM_ALLOCATION);
		List<Message> processMessages = dummyTransport.getMessages(DUMMY_PROCESS);
		display("work items lifecycle notifications", lifecycleMessages);
		display("work items allocation notifications", allocationMessages);
		display("processes notifications", processMessages);
		dummyTransport.clearMessages();

		assertNull("lifecycle messages", lifecycleMessages);
		assertNull("process messages", processMessages);
		assertEquals("Wrong # of work items allocation messages", 2, allocationMessages.size());
		Map<String, Message> sorted = sortByRecipientsSingle(allocationMessages);

		assertMessage(sorted.get("elaine@evolveum.com"), "elaine@evolveum.com",
				"Work item will be automatically completed in 2 days",
				"Security (2/3)", "Allocated to: Elaine Marley (elaine)", "(in 7 days)");
		assertMessage(sorted.get("barkeeper@evolveum.com"), "barkeeper@evolveum.com",
				"Work item will be automatically completed in 2 days",
				"Security (2/3)", "Allocated to: Horridly Scarred Barkeep (barkeeper)", "(in 7 days)");

		display("audit", dummyAuditService);
	}

	@Test
	public void test209EightDaysLater() throws Exception {
		final String TEST_NAME = "test209EightDaysLater";
		displayTestTitle(TEST_NAME);
		Task task = createTask(TEST_NAME);
		OperationResult result = task.getResult();

		dummyAuditService.clear();
		dummyTransport.clearMessages();

		// GIVEN
		clock.resetOverride();
		clock.overrideDuration("P8D");

		// WHEN
		waitForTaskNextRun(TASK_TRIGGER_SCANNER_OID, true, 20000, true);

		// THEN
		List<Message> lifecycleMessages = dummyTransport.getMessages(DUMMY_WORK_ITEM_LIFECYCLE);
		List<Message> allocationMessages = dummyTransport.getMessages(DUMMY_WORK_ITEM_ALLOCATION);
		List<Message> processMessages = dummyTransport.getMessages(DUMMY_PROCESS);
		display("work items lifecycle notifications", lifecycleMessages);
		display("work items allocation notifications", allocationMessages);
		display("processes notifications", processMessages);
		dummyTransport.clearMessages();

		assertEquals("Wrong # of work items lifecycle messages", 2, lifecycleMessages.size());
		assertEquals("Wrong # of work items allocation messages", 2, allocationMessages.size());
		assertEquals("Wrong # of process messages", 1, processMessages.size());
		checkTwoCompleted(lifecycleMessages);
		checkTwoCompleted(allocationMessages);
		assertMessage(processMessages.get(0), "administrator@evolveum.com", "Workflow process instance has finished",
				"Process instance name: Assigning role \"a-test-1\" to user \"carla\"", "Result: REJECTED");

		display("audit", dummyAuditService);
	}

	private void checkTwoCompleted(List<Message> lifecycleMessages) {
		Map<String, Message> sorted = sortByRecipientsSingle(lifecycleMessages);

		assertMessage(sorted.get("elaine@evolveum.com"), "elaine@evolveum.com",
				null,
				"Security (2/3)", "Allocated to: Elaine Marley (elaine)");
		assertMessage(sorted.get("barkeeper@evolveum.com"), "barkeeper@evolveum.com",
				null,
				"Security (2/3)", "Allocated to: Horridly Scarred Barkeep (barkeeper)");
		int completed;
		assertMessage(lifecycleMessages.get(0), null, "Work item has been completed",
				"^Carried out by:",
				"Reason: Timed action",
				"Result: REJECTED");
		assertMessage(lifecycleMessages.get(1), null, "Work item has been completed",
				"^Carried out by:",
				"Reason: Timed action",
				"Result: REJECTED");
	}

	@Test
	public void test220FormRoleAssignmentStart() throws Exception {
		final String TEST_NAME = "test220FormRoleAssignmentStart";
		displayTestTitle(TEST_NAME);
		PrismObject<UserType> bob = getUserFromRepo(userBobOid);
		login(bob);

		Task task = createTask(TEST_NAME);
		task.setOwner(bob);
		OperationResult result = task.getResult();

		dummyAuditService.clear();
		dummyTransport.clearMessages();

		// WHEN
		assignRole(userBobOid, roleATest4Oid, task, task.getResult());

		// THEN
		login(userAdministrator);
		assertNotAssignedRole(getUser(userBobOid), roleATest4Oid);

		List<CaseWorkItemType> workItems = getWorkItems(task, result);
		displayWorkItems("Work item after start", workItems);
		CaseType aCase = CaseWorkItemUtil.getCaseRequired(workItems.get(0));
		display("aCase", aCase);

//		assertTargetTriggers(aCase, 2);

		ApprovalContextType actx = aCase.getApprovalContext();
		ApprovalSchemaType schema = actx.getApprovalSchema();
		assertEquals("Wrong # of approval levels", 2, schema.getStage().size());
		assertApprovalLevel(schema, 1, "Line managers", "P5D", 2);
		assertApprovalLevel(schema, 2, "Role approvers (first)", "P5D", 2);
		assertStage(aCase, 1, 2, "Line managers", null);

		List<Message> lifecycleMessages = dummyTransport.getMessages(DUMMY_WORK_ITEM_LIFECYCLE);
		List<Message> allocationMessages = dummyTransport.getMessages(DUMMY_WORK_ITEM_ALLOCATION);
		List<Message> processMessages = dummyTransport.getMessages(DUMMY_PROCESS);
		display("work items lifecycle notifications", lifecycleMessages);
		display("work items allocation notifications", allocationMessages);
		display("processes notifications", processMessages);

		display("audit", dummyAuditService);
	}

	@Test
	public void test221FormApproveByLechuck() throws Exception {
		final String TEST_NAME = "test221FormApproveByLechuck";
		displayTestTitle(TEST_NAME);
		Task task = createTask(TEST_NAME);
		OperationResult result = task.getResult();

		dummyAuditService.clear();
		dummyTransport.clearMessages();

		// GIVEN
		login(userAdministrator);
		CaseWorkItemType workItem = getWorkItem(task, result);

		// WHEN
		PrismObject<UserType> lechuck = getUserFromRepo(userLechuckOid);
		login(lechuck);

		workflowService.completeWorkItem(WorkItemId.of(workItem), true, "OK. LeChuck", null, task, result);

		// THEN
		login(userAdministrator);
		assertNotAssignedRole(getUser(userBobOid), roleATest4Oid);
		List<CaseWorkItemType> workItems = getWorkItems(task, result);
		displayWorkItems("Work item after 1st approval", workItems);
		CaseType aCase = CaseWorkItemUtil.getCaseRequired(workItems.get(0));
		assertStage(aCase, 2, 2, "Role approvers (first)", null);

		ApprovalStageDefinitionType level = ApprovalContextUtil.getCurrentStageDefinition(aCase);
		assertEquals("Wrong evaluation strategy", LevelEvaluationStrategyType.FIRST_DECIDES, level.getEvaluationStrategy());

		// notifications
		List<Message> lifecycleMessages = dummyTransport.getMessages(DUMMY_WORK_ITEM_LIFECYCLE);
		List<Message> allocationMessages = dummyTransport.getMessages(DUMMY_WORK_ITEM_ALLOCATION);
		List<Message> processMessages = dummyTransport.getMessages(DUMMY_PROCESS);
		display("work items lifecycle notifications", lifecycleMessages);
		display("work items allocation notifications", allocationMessages);
		display("processes notifications", processMessages);
		dummyTransport.clearMessages();

		display("audit", dummyAuditService);
	}


	@Test
	public void test222FormApproveByCheese() throws Exception {
		final String TEST_NAME = "test222FormApproveByCheese";
		displayTestTitle(TEST_NAME);
		Task task = createTask(TEST_NAME);
		OperationResult result = task.getResult();

		dummyAuditService.clear();
		dummyTransport.clearMessages();

		// GIVEN
		login(userAdministrator);
		SearchResultList<CaseWorkItemType> workItems = getWorkItems(task, result);
		CaseWorkItemType workItem = sortByOriginalAssignee(workItems).get(userCheeseOid);
		assertNotNull("No work item for cheese", workItem);

		// WHEN
		PrismObject<UserType> cheese = getUserFromRepo(userCheeseOid);
		login(cheese);
		ObjectDelta formDelta = prismContext.deltaFor(UserType.class)
				.item(UserType.F_DESCRIPTION).replace("Hello")
				.asObjectDelta(userBobOid);
		workflowService.completeWorkItem(WorkItemId.of(workItem), true, "OK. LeChuck", formDelta, task, result);

		// THEN
		login(userAdministrator);

		workItems = getWorkItems(task, result);
		displayWorkItems("Work item after 2nd approval", workItems);
		assertEquals("Wrong # of work items after 2nd approval", 0, workItems.size());

		CaseType aCase = CaseWorkItemUtil.getCaseRequired(workItem);
		display("aCase after 2nd approval", aCase);

		assertStage(aCase, 2, 2, "Role approvers (first)", null);
		// assertTargetTriggers(aCase, 4);

		// notifications
		List<Message> lifecycleMessages = dummyTransport.getMessages(DUMMY_WORK_ITEM_LIFECYCLE);
		List<Message> allocationMessages = dummyTransport.getMessages(DUMMY_WORK_ITEM_ALLOCATION);
		List<Message> processMessages = dummyTransport.getMessages(DUMMY_PROCESS);
		display("work items lifecycle notifications", lifecycleMessages);
		display("work items allocation notifications", allocationMessages);
		display("processes notifications", processMessages);

		// audit
		display("audit", dummyAuditService);
		List<AuditEventRecord> records = dummyAuditService.getRecords();
		assertEquals("Wrong # of audit records", 4, records.size());
		AuditEventRecord record = records.get(0);
		Collection<ObjectDeltaOperation<? extends ObjectType>> deltas = record.getDeltas();
		assertEquals("Wrong # of deltas in audit record", 1, deltas.size());
		ObjectDeltaOperation<? extends ObjectType> delta = deltas.iterator().next();
		assertEquals("Wrong # of modifications in audit record delta", 1, delta.getObjectDelta().getModifications().size());
		ItemDelta<?, ?> itemDelta = delta.getObjectDelta().getModifications().iterator().next();
		if (!UserType.F_DESCRIPTION.equivalent(itemDelta.getPath())) {
			fail("Wrong item path in delta: expected: "+UserType.F_DESCRIPTION+", found: "+itemDelta.getPath());
		}
		assertEquals("Wrong value in delta", "Hello", itemDelta.getValuesToReplace().iterator().next().getRealValue());

		// record #1, #2: cancellation of work items of other approvers
		// record #3: finishing process execution

		CaseType rootCase = getCase(aCase.getParentRef().getOid());
		waitForTaskCloseOrSuspend(rootCase.getOid(), TASK_WAIT_TIMEOUT);
		assertAssignedRole(getUser(userBobOid), roleATest4Oid);
	}

	@Test
	public void test250ApproverAssignment() throws Exception {
		final String TEST_NAME = "test250ApproverAssignment";
		displayTestTitle(TEST_NAME);
		Task task = createTask(TEST_NAME);
		OperationResult result = task.getResult();

		dummyAuditService.clear();
		dummyTransport.clearMessages();

		// WHEN
		assignRole(userBobOid, roleATest1Oid, SchemaConstants.ORG_APPROVER, task, task.getResult());

		// THEN
		assertNull("bob has assigned role \"a-test-1\" as an approver", getUserAssignment(userBobOid, roleATest1Oid, SchemaConstants.ORG_APPROVER));

		List<CaseWorkItemType> workItems = getWorkItems(task, result);
		displayWorkItems("Work item after start", workItems);
		CaseType aCase = CaseWorkItemUtil.getCaseRequired(workItems.get(0));
		display("aCase", aCase);

		ApprovalSchemaType schema = aCase.getApprovalContext().getApprovalSchema();
		assertEquals("Wrong # of approval levels", 3, schema.getStage().size());
		assertApprovalLevel(schema, 1, "Line managers", "P5D", 2);
		assertApprovalLevel(schema, 2, "Security", "P7D", 1);
		assertApprovalLevel(schema, 3, "Role approvers (all)", "P5D", 2);
		assertStage(aCase, 1, 3, "Line managers", null);

		List<Message> lifecycleMessages = dummyTransport.getMessages(DUMMY_WORK_ITEM_LIFECYCLE);
		List<Message> allocationMessages = dummyTransport.getMessages(DUMMY_WORK_ITEM_ALLOCATION);
		List<Message> processMessages = dummyTransport.getMessages(DUMMY_PROCESS);
		display("work items lifecycle notifications", lifecycleMessages);
		display("work items allocation notifications", allocationMessages);
		display("processes notifications", processMessages);

		display("audit", dummyAuditService);
	}


	//endregion

	private void assertMessage(Message message, String recipient, String subject, String... texts) {
		assertNotNull("No message for " + recipient, message);
		assertEquals("Wrong # of recipients", 1, message.getTo().size());
		if (recipient != null) {
			assertEquals("Wrong recipient", recipient, message.getTo().get(0));
		}
		if (subject != null) {
			assertEquals("Wrong subject", subject, message.getSubject());
		}
		condition: for (String text : texts) {
			if (text.startsWith("^")) {
				String pureText = text.substring(1);
				if (message.getBody().contains(pureText)) {
					fail("Message body does contain '" + pureText + "' even if it shouldn't: " + message.getBody());
				}
			} else if (text.startsWith("|")) {
				String[] strings = StringUtils.split(text, "|");
				for (String string : strings) {
					if (message.getBody().contains(string)) {
						continue condition;
					}
				}
				fail("Message body does not contain any of " + Arrays.asList(strings) + ": " + message.getBody());
			} else {
				if (!message.getBody().contains(text)) {
					fail("Message body doesn't contain '" + text + "': " + message.getBody());
				}
			}
		}
	}

	private ArrayListValuedHashMap<String, Message> sortByRecipients(Collection<Message> messages) {
		ArrayListValuedHashMap<String, Message> rv = new ArrayListValuedHashMap<>();
		messages.forEach(m ->
				m.getTo().forEach(
						to -> rv.put(to, m)));
		return rv;
	}

	private Map<String, Message> sortByRecipientsSingle(Collection<Message> messages) {
		Map<String, Message> rv = new HashMap<>();
		messages.forEach(m ->
				m.getTo().forEach(
						to -> rv.put(to, m)));
		return rv;
	}

	private Task getParentTask(PrismObject<TaskType> task, OperationResult result)
			throws SchemaException, ObjectNotFoundException {
		return taskManager.getTaskByIdentifier(task.asObjectable().getParent(), result);
	}

	private void assertTriggers(CaseType aCase, int count) {
		assertEquals("Wrong # of triggers", count, aCase.getTrigger().size());
	}

	private void assertAssignee(CaseWorkItemType workItem, String originalAssignee, String... currentAssignee) throws SchemaException {
		assertRefEquals("Wrong original assignee", ObjectTypeUtil.createObjectRef(originalAssignee, ObjectTypes.USER), workItem.getOriginalAssigneeRef());
		assertReferenceValues(ref(workItem.getAssigneeRef()), currentAssignee);
	}


	private void assertStage(CaseType aCase, Integer stageNumber, Integer stageCount, String stageName, String stageDisplayName) {
		ApprovalContextType wfc = aCase.getApprovalContext();
		assertEquals("Wrong stage number", stageNumber, aCase.getStageNumber());
		assertEquals("Wrong stage count", stageCount, ApprovalContextUtil.getStageCount(wfc));
		assertEquals("Wrong stage name", stageName, ApprovalContextUtil.getStageName(aCase));
		assertEquals("Wrong stage name", stageDisplayName, ApprovalContextUtil.getStageDisplayName(aCase));
	}

	private void assertApprovalLevel(ApprovalSchemaType schema, int number, String name, String duration, int timedActions) {
		ApprovalStageDefinitionType level = schema.getStage().get(number-1);
		assertEquals("Wrong level number", number, (int) level.getNumber());
		assertEquals("Wrong level name", name, level.getName());
		assertEquals("Wrong level duration", XmlTypeConverter.createDuration(duration), level.getDuration());
		assertEquals("Wrong # of timed actions", timedActions, level.getTimedActions().size());
	}

	private List<CaseEventType> assertEvents(CaseType aCase, int expectedCount) {
		assertEquals("Wrong # of wf events", expectedCount, aCase.getEvent().size());
		return aCase.getEvent();
	}

	private void assertEscalationEvent(CaseEventType wfProcessEventType, String initiator, String originalAssignee,
			int stageNumber, String stageName, List<String> assigneesBefore, List<String> delegatedTo,
			WorkItemDelegationMethodType methodType, int newEscalationLevelNumber, String newEscalationLevelName) throws SchemaException {
		if (!(wfProcessEventType instanceof WorkItemEscalationEventType)) {
			fail("Wrong event class: expected: " + WorkItemEscalationEventType.class + ", real: " + wfProcessEventType.getClass());
		}
		WorkItemEscalationEventType event = (WorkItemEscalationEventType) wfProcessEventType;
		assertEvent(event, initiator, originalAssignee, stageNumber, stageName);
		PrismAsserts.assertReferenceValues(ref(event.getAssigneeBefore()), assigneesBefore.toArray(new String[0]));
		PrismAsserts.assertReferenceValues(ref(event.getDelegatedTo()), delegatedTo.toArray(new String[0]));
		assertEquals("Wrong delegation method", methodType, event.getDelegationMethod());
		assertEquals("Wrong escalation level #", (Integer) newEscalationLevelNumber, event.getNewEscalationLevel().getNumber());
		assertEquals("Wrong escalation level name", newEscalationLevelName, event.getNewEscalationLevel().getName());
	}

	private void assertCompletionEvent(CaseEventType wfProcessEventType, String initiator, String originalAssignee,
			int stageNumber, String stageName, WorkItemOutcomeType outcome, String comment) throws SchemaException {
		if (!(wfProcessEventType instanceof WorkItemCompletionEventType)) {
			fail("Wrong event class: expected: " + WorkItemCompletionEventType.class + ", real: " + wfProcessEventType.getClass());
		}
		WorkItemCompletionEventType event = (WorkItemCompletionEventType) wfProcessEventType;
		assertEvent(event, initiator, originalAssignee, stageNumber, stageName);
		assertEquals("Wrong outcome", outcome, ApprovalUtils.fromUri(event.getOutput().getOutcome()));
		assertEquals("Wrong comment", comment, event.getOutput().getComment());
	}

	private void assertEvent(CaseEventType processEvent, String initiator, String originalAssignee, Integer stageNumber,
			String stageName) throws SchemaException {
		if (!(processEvent instanceof WorkItemEventType)) {
			fail("Wrong event class: expected: " + WorkItemEventType.class + ", real: " + processEvent.getClass());
		}
		WorkItemEventType event = (WorkItemEventType) processEvent;
		PrismAsserts.assertReferenceValue(ref(event.getInitiatorRef()), initiator);
		assertEquals("Wrong stage #", stageNumber, event.getStageNumber());
		//assertEquals("Wrong stage name", stageName, event.getStageName());
		if (originalAssignee != null) {
			assertNotNull("Null original assignee", event.getOriginalAssigneeRef());
			PrismAsserts.assertReferenceValue(ref(event.getOriginalAssigneeRef()), originalAssignee);
		}
	}

}
