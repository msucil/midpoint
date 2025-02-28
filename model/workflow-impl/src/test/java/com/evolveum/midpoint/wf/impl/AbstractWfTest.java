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

package com.evolveum.midpoint.wf.impl;

import com.evolveum.midpoint.model.common.SystemObjectCache;
import com.evolveum.midpoint.model.impl.AbstractModelImplementationIntegrationTest;
import com.evolveum.midpoint.model.impl.lens.Clockwork;
import com.evolveum.midpoint.prism.Item;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.PrismReference;
import com.evolveum.midpoint.prism.PrismReferenceValue;
import com.evolveum.midpoint.prism.delta.ObjectDelta;
import com.evolveum.midpoint.prism.query.ObjectQuery;
import com.evolveum.midpoint.prism.query.builder.S_AtomicFilterExit;
import com.evolveum.midpoint.prism.util.PrismUtil;
import com.evolveum.midpoint.schema.RelationRegistry;
import com.evolveum.midpoint.schema.SearchResultList;
import com.evolveum.midpoint.schema.constants.ObjectTypes;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.schema.util.CaseWorkItemUtil;
import com.evolveum.midpoint.schema.util.ObjectTypeUtil;
import com.evolveum.midpoint.security.api.SecurityUtil;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.task.api.TaskExecutionStatus;
import com.evolveum.midpoint.task.api.TaskManager;
import com.evolveum.midpoint.test.AbstractIntegrationTest;
import com.evolveum.midpoint.test.Checker;
import com.evolveum.midpoint.test.IntegrationTestTools;
import com.evolveum.midpoint.util.exception.*;
import com.evolveum.midpoint.wf.api.WorkflowManager;
import com.evolveum.midpoint.wf.impl.access.WorkItemManager;
import com.evolveum.midpoint.wf.impl.engine.WorkflowEngine;
import com.evolveum.midpoint.wf.impl.policy.ExpectedWorkItem;
import com.evolveum.midpoint.wf.impl.processors.general.GeneralChangeProcessor;
import com.evolveum.midpoint.wf.impl.processors.primary.PrimaryChangeProcessor;
import com.evolveum.midpoint.wf.impl.util.MiscHelper;
import com.evolveum.midpoint.wf.util.QueryUtils;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.context.ContextConfiguration;

import javax.xml.namespace.QName;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.testng.AssertJUnit.*;

/**
 * @author mederly
 *
 */
@ContextConfiguration(locations = {"classpath:ctx-workflow-test-main.xml"})
@DirtiesContext(classMode = ClassMode.AFTER_CLASS)
public abstract class AbstractWfTest extends AbstractModelImplementationIntegrationTest {

	public static final File ROLE_SUPERUSER_FILE = new File(COMMON_DIR, "role-superuser.xml");
	public static final File USER_ADMINISTRATOR_FILE = new File(COMMON_DIR, "user-administrator.xml");

	protected static final File USER_JACK_FILE = new File(COMMON_DIR, "user-jack.xml");

	protected static final File ROLE_APPROVER_FILE = new File(COMMON_DIR, "041-role-approver.xml");
	protected static final File ARCHETYPE_OPERATION_REQUEST_FILE = new File(COMMON_DIR, "024-archetype-operation-request.xml");
	protected static final File ARCHETYPE_APPROVAL_CASE_FILE = new File(COMMON_DIR, "025-archetype-approval-case.xml");

	protected static final String USER_ADMINISTRATOR_OID = SystemObjectsType.USER_ADMINISTRATOR.value();

	protected String userJackOid;

	@Autowired protected Clockwork clockwork;
	@Autowired protected TaskManager taskManager;
	@Autowired protected WorkflowManager workflowManager;
	@Autowired protected WorkflowEngine workflowEngine;
	@Autowired protected WorkItemManager workItemManager;
	@Autowired protected PrimaryChangeProcessor primaryChangeProcessor;
	@Autowired protected GeneralChangeProcessor generalChangeProcessor;
	@Autowired protected SystemObjectCache systemObjectCache;
	@Autowired protected RelationRegistry relationRegistry;
	@Autowired protected WfTestHelper testHelper;
	@Autowired protected MiscHelper miscHelper;

	protected PrismObject<UserType> userAdministrator;

	@Override
	public void initSystem(Task initTask, OperationResult initResult) throws Exception {
		super.initSystem(initTask, initResult);
		modelService.postInit(initResult);

		PrismObject<SystemConfigurationType> sysconfig = prismContext.parseObject(getSystemConfigurationFile());
		updateSystemConfiguration(sysconfig.asObjectable());
		repoAddObject(sysconfig, initResult);

		repoAddObjectFromFile(ROLE_SUPERUSER_FILE, initResult);
		userAdministrator = repoAddObjectFromFile(USER_ADMINISTRATOR_FILE, initResult);
		login(userAdministrator);

		repoAddObjectFromFile(ROLE_APPROVER_FILE, initResult).getOid();
		repoAddObjectFromFile(ARCHETYPE_OPERATION_REQUEST_FILE, initResult).getOid();
		repoAddObjectFromFile(ARCHETYPE_APPROVAL_CASE_FILE, initResult).getOid();

		userJackOid = repoAddObjectFromFile(USER_JACK_FILE, initResult).getOid();
	}

	@Override
	protected PrismObject<UserType> getDefaultActor() {
		return userAdministrator;
	}

	protected void updateSystemConfiguration(SystemConfigurationType systemConfiguration) throws SchemaException, IOException {
		// nothing to do by default
	}

	protected abstract File getSystemConfigurationFile();

	protected Map<String, WorkflowResult> createResultMap(String oid, WorkflowResult result) {
		Map<String, WorkflowResult> retval = new HashMap<>();
		retval.put(oid, result);
		return retval;
	}

	protected Map<String, WorkflowResult> createResultMap(String oid, WorkflowResult approved, String oid2,
			WorkflowResult approved2) {
		Map<String, WorkflowResult> retval = new HashMap<>();
		retval.put(oid, approved);
		retval.put(oid2, approved2);
		return retval;
	}

	protected Map<String, WorkflowResult> createResultMap(String oid, WorkflowResult approved, String oid2,
			WorkflowResult approved2, String oid3, WorkflowResult approved3) {
		Map<String, WorkflowResult> retval = new HashMap<>();
		retval.put(oid, approved);
		retval.put(oid2, approved2);
		retval.put(oid3, approved3);
		return retval;
	}

	protected void checkAuditRecords(Map<String, WorkflowResult> expectedResults) {
		checkWorkItemAuditRecords(expectedResults);
		checkWfProcessAuditRecords(expectedResults);
	}

	protected void checkWorkItemAuditRecords(Map<String, WorkflowResult> expectedResults) {
		WfTestUtil.checkWorkItemAuditRecords(expectedResults, dummyAuditService);
	}

	protected void checkWfProcessAuditRecords(Map<String, WorkflowResult> expectedResults) {
		WfTestUtil.checkWfProcessAuditRecords(expectedResults, dummyAuditService);
	}

	protected void removeAllAssignments(String oid, OperationResult result) throws Exception {
		PrismObject<UserType> user = repositoryService.getObject(UserType.class, oid, null, result);
		for (AssignmentType at : user.asObjectable().getAssignment()) {
			ObjectDelta delta = prismContext.deltaFactory().object()
					.createModificationDeleteContainer(UserType.class, oid, UserType.F_ASSIGNMENT,
							at.asPrismContainerValue().clone());
			repositoryService.modifyObject(UserType.class, oid, delta.getModifications(), result);
			display("Removed assignment " + at + " from " + user);
		}
	}

	protected CaseWorkItemType getWorkItem(Task task, OperationResult result)
			throws SchemaException, SecurityViolationException, ConfigurationException, ObjectNotFoundException, ExpressionEvaluationException, CommunicationException {
		//Collection<SelectorOptions<GetOperationOptions>> options = GetOperationOptions.resolveItemsNamed(CaseWorkItemType.F_TASK_REF);
		SearchResultList<CaseWorkItemType> itemsAll = modelService.searchContainers(CaseWorkItemType.class, getOpenItemsQuery(), null, task, result);
		if (itemsAll.size() != 1) {
			System.out.println("Unexpected # of work items: " + itemsAll.size());
			for (CaseWorkItemType workItem : itemsAll) {
				System.out.println(PrismUtil.serializeQuietly(prismContext, workItem));
			}
		}
		assertEquals("Wrong # of total work items", 1, itemsAll.size());
		return itemsAll.get(0);
	}

	protected SearchResultList<CaseWorkItemType> getWorkItems(Task task, OperationResult result) throws Exception {
		return modelService.searchContainers(CaseWorkItemType.class, getOpenItemsQuery(), null, task, result);
	}

	protected void displayWorkItems(String title, List<CaseWorkItemType> workItems) {
		workItems.forEach(wi -> display(title, wi));
	}

	protected ObjectReferenceType ort(String oid) {
		return ObjectTypeUtil.createObjectRef(oid, ObjectTypes.USER);
	}

	protected PrismReferenceValue prv(String oid) {
		return ObjectTypeUtil.createObjectRef(oid, ObjectTypes.USER).asReferenceValue();
	}

	protected PrismReference ref(List<ObjectReferenceType> orts) {
		PrismReference rv = prismContext.itemFactory().createReference(new QName("dummy"));
		orts.forEach(ort -> {
			try {
				rv.add(ort.asReferenceValue().clone());
			} catch (SchemaException e) {
				throw new IllegalStateException(e);
			}
		});
		return rv;
	}

	protected PrismReference ref(ObjectReferenceType ort) {
		return ref(Collections.singletonList(ort));
	}

	protected void assertObjectInTaskTree(Task rootTask, String oid, boolean checkObjectOnSubtasks, OperationResult result)
			throws SchemaException {
		assertObjectInTask(rootTask, oid);
		if (checkObjectOnSubtasks) {
			for (Task task : rootTask.listSubtasks(result)) {
				assertObjectInTask(task, oid);
			}
		}
	}

	protected void assertObjectInTask(Task task, String oid) {
		assertEquals("Missing or wrong object OID in task " + task, oid, task.getObjectOid());
	}

	protected void waitForTaskClose(final Task task, final int timeout) throws Exception {
		final OperationResult waitResult = new OperationResult(AbstractIntegrationTest.class + ".waitForTaskClose");
		Checker checker = new Checker() {
			@Override
			public boolean check() throws CommonException {
				task.refresh(waitResult);
				OperationResult result = task.getResult();
				if (verbose)
					display("Check result", result);
				return task.getExecutionStatus() == TaskExecutionStatus.CLOSED;
			}

			@Override
			public void timeout() {
				try {
					task.refresh(waitResult);
				} catch (Throwable e) {
					display("Exception during task refresh", e);
				}
				OperationResult result = task.getResult();
				display("Result of timed-out task", result);
				assert false : "Timeout (" + timeout + ") while waiting for " + task + " to finish. Last result " + result;
			}
		};
		IntegrationTestTools.waitFor("Waiting for " + task + " finish", checker, timeout, 1000);
	}

	protected String getTargetOid(CaseWorkItemType caseWorkItem) {
		ObjectReferenceType targetRef = CaseWorkItemUtil.getCaseRequired(caseWorkItem).getTargetRef();
		assertNotNull("targetRef not found", targetRef);
		String roleOid = targetRef.getOid();
		assertNotNull("requested role OID not found", roleOid);
		return roleOid;
	}

	protected void checkTargetOid(CaseWorkItemType caseWorkItem, String expectedOid)
			throws ObjectNotFoundException, SchemaException, CommunicationException, ConfigurationException,
			SecurityViolationException {
		String realOid = getTargetOid(caseWorkItem);
		assertEquals("Unexpected target OID", expectedOid, realOid);
	}


	protected void assertDeltasEqual(String message, ObjectDelta expectedDelta, ObjectDelta realDelta) {
//		removeOldValues(expectedDelta);
//		removeOldValues(realDelta);
		if (!expectedDelta.equivalent(realDelta)) {
			fail(message + "\nExpected:\n" + expectedDelta.debugDump() + "\nReal:\n" + realDelta.debugDump());
		}
	}

//	private void removeOldValues(ObjectDelta<?> delta) {
//		if (delta.isModify()) {
//			delta.getModifications().forEach(mod -> mod.setEstimatedOldValues(null));
//		}
//	}

	protected void assertNoObject(ObjectType object) throws SchemaException, ObjectNotFoundException, SecurityViolationException, CommunicationException, ConfigurationException, ExpressionEvaluationException {
		assertNull("Object was created but it shouldn't be",
				searchObjectByName(object.getClass(), object.getName().getOrig()));
	}

	protected void assertNoObject(PrismObject<? extends ObjectType> object) throws SchemaException, ObjectNotFoundException, SecurityViolationException, CommunicationException, ConfigurationException, ExpressionEvaluationException {
		assertNoObject(object.asObjectable());
	}

	protected <T extends ObjectType> void assertObject(T object) throws SchemaException, ObjectNotFoundException, SecurityViolationException, CommunicationException, ConfigurationException, ExpressionEvaluationException {
		PrismObject<T> objectFromRepo = searchObjectByName((Class<T>) object.getClass(), object.getName().getOrig());
		assertNotNull("Object " + object + " was not created", objectFromRepo);
		objectFromRepo.removeItem(ObjectType.F_METADATA, Item.class);
		objectFromRepo.removeItem(ObjectType.F_OPERATION_EXECUTION, Item.class);
		if (!object.equals(objectFromRepo.asObjectable())) {
			System.out.println("Expected:\n" + prismContext.xmlSerializer().serialize(object.asPrismObject()));
			System.out.println("Actual:\n" + prismContext.xmlSerializer().serialize(objectFromRepo));
		}
		assertEquals("Object is different from the one that was expected", object, objectFromRepo.asObjectable());
	}

	protected void checkVisibleWorkItem(ExpectedWorkItem expectedWorkItem, int count, Task task, OperationResult result)
			throws SchemaException, ObjectNotFoundException, ConfigurationException, SecurityViolationException, ExpressionEvaluationException, CommunicationException {
		S_AtomicFilterExit q = QueryUtils
				.filterForAssignees(prismContext.queryFor(CaseWorkItemType.class), SecurityUtil.getPrincipal(),
						OtherPrivilegesLimitationType.F_APPROVAL_WORK_ITEMS, relationRegistry);
		q = q.and().item(CaseWorkItemType.F_CLOSE_TIMESTAMP).isNull();
		List<CaseWorkItemType> currentWorkItems = modelService.searchContainers(CaseWorkItemType.class, q.build(), null, task, result);
		long found = currentWorkItems.stream().filter(wi -> expectedWorkItem == null || expectedWorkItem.matches(wi)).count();
		assertEquals("Wrong # of matching work items", count, found);
	}

	protected ObjectQuery getOpenItemsQuery() {
		return prismContext.queryFor(CaseWorkItemType.class)
				.item(CaseWorkItemType.F_CLOSE_TIMESTAMP).isNull()
				.build();
	}
}
