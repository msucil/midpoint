/*
 * Copyright (c) 2013-2018 Evolveum
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

import com.evolveum.midpoint.model.test.AbstractModelIntegrationTest;
import com.evolveum.midpoint.prism.*;
import com.evolveum.midpoint.prism.match.MatchingRule;
import com.evolveum.midpoint.prism.match.MatchingRuleRegistry;
import com.evolveum.midpoint.prism.path.ItemPath;
import com.evolveum.midpoint.prism.util.PrismUtil;
import com.evolveum.midpoint.schema.SearchResultList;
import com.evolveum.midpoint.schema.constants.ObjectTypes;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.schema.util.ObjectTypeUtil;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.util.DOMUtil;
import com.evolveum.midpoint.util.exception.ObjectAlreadyExistsException;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.context.ContextConfiguration;

import javax.xml.namespace.QName;

import static com.evolveum.midpoint.test.IntegrationTestTools.display;
import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertNotNull;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.*;

/**
 * @author Radovan Semancik
 *
 */
@ContextConfiguration(locations = {"classpath:ctx-story-test-main.xml"})
@DirtiesContext(classMode = ClassMode.AFTER_CLASS)
public class AbstractStoryTest extends AbstractModelIntegrationTest {

	public static final File SYSTEM_CONFIGURATION_FILE = new File(COMMON_DIR, "system-configuration.xml");
	public static final String SYSTEM_CONFIGURATION_OID = SystemObjectsType.SYSTEM_CONFIGURATION.value();

	protected static final File USER_ADMINISTRATOR_FILE = new File(COMMON_DIR, "user-administrator.xml");
	protected static final String USER_ADMINISTRATOR_OID = "00000000-0000-0000-0000-000000000002";
	protected static final String USER_ADMINISTRATOR_USERNAME = "administrator";

	protected static final File USER_JACK_FILE = new File(COMMON_DIR, "user-jack.xml");
	protected static final String USER_JACK_OID = "c0c010c0-d34d-b33f-f00d-111111111111";
	protected static final String USER_JACK_USERNAME = "jack";
	protected static final String USER_JACK_FULL_NAME = "Jack Sparrow";

	protected static final File ROLE_SUPERUSER_FILE = new File(COMMON_DIR, "role-superuser.xml");
	protected static final String ROLE_SUPERUSER_OID = "00000000-0000-0000-0000-000000000004";

	protected static final File TASK_TRIGGER_SCANNER_FILE = new File(COMMON_DIR, "task-trigger-scanner.xml");
	protected static final String TASK_TRIGGER_SCANNER_OID = "00000000-0000-0000-0000-000000000007";

	protected static final File TASK_VALIDITY_SCANNER_FILE = new File(COMMON_DIR, "task-validity-scanner.xml");
	protected static final String TASK_VALIDITY_SCANNER_OID = "00000000-0000-0000-0000-000000000006";
	
	protected static final String NS_PIRACY = "http://midpoint.evolveum.com/xml/ns/samples/piracy";
	protected static final QName PIRACY_SHIP_QNAME = new QName(NS_PIRACY, "ship");
	protected static final ItemPath PATH_EXTENSION_SHIP = ItemPath.create(ObjectType.F_EXTENSION, PIRACY_SHIP_QNAME);

	protected MatchingRule<String> caseIgnoreMatchingRule;

	@Autowired
	protected MatchingRuleRegistry matchingRuleRegistry;

	protected PrismObject<UserType> userAdministrator;

	@Override
	public void initSystem(Task initTask, OperationResult initResult) throws Exception {
		super.initSystem(initTask, initResult);
		modelService.postInit(initResult);

		// System Configuration
		try {
			repoAddObjectFromFile(getSystemConfigurationFile(), initResult);
		} catch (ObjectAlreadyExistsException e) {
			throw new ObjectAlreadyExistsException("System configuration already exists in repository;" +
					"looks like the previous test haven't cleaned it up", e);
		}

		// User administrator
		userAdministrator = repoAddObjectFromFile(USER_ADMINISTRATOR_FILE, initResult);
		repoAddObjectFromFile(USER_JACK_FILE, true, initResult);
		repoAddObjectFromFile(ROLE_SUPERUSER_FILE, initResult);
		login(userAdministrator);

		assumeAssignmentPolicy(AssignmentPolicyEnforcementType.RELATIVE);

		caseIgnoreMatchingRule = matchingRuleRegistry.getMatchingRule(PrismConstants.STRING_IGNORE_CASE_MATCHING_RULE_NAME, DOMUtil.XSD_STRING);

		importSystemTasks(initResult);
	}
	
	protected int getNumberOfUsers() {
		return 2; // Administrator, jack
	}
	
	protected int getNumberOfRoles() {
		return 1; // Superuser role
	}

	protected File getSystemConfigurationFile() {
		return SYSTEM_CONFIGURATION_FILE;
	}

	protected void importSystemTasks(OperationResult initResult) throws FileNotFoundException {
		importObjectFromFile(TASK_TRIGGER_SCANNER_FILE, initResult);
		importObjectFromFile(TASK_VALIDITY_SCANNER_FILE, initResult);
	}


	protected void assertUserJack(PrismObject<UserType> userJack) {
		assertUser(userJack, USER_JACK_OID, USER_JACK_USERNAME, "Jack Sparrow", "Jack", "Sparrow");
	}

	//region TODO deduplicate with AbstractWfTestPolicy

	public void displayWorkItems(String title, List<CaseWorkItemType> workItems) {
		workItems.forEach(wi -> display(title, wi));
	}

	protected CaseWorkItemType getWorkItem(Task task, OperationResult result) throws Exception {
		SearchResultList<CaseWorkItemType> itemsAll = getWorkItems(task, result);
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
		return modelService.searchContainers(CaseWorkItemType.class, null, null, task, result);
	}

	protected ObjectReferenceType ort(String oid) {
		return ObjectTypeUtil.createObjectRef(oid, ObjectTypes.USER);
	}

	protected PrismReferenceValue prv(String oid) {
		return ObjectTypeUtil.createObjectRef(oid, ObjectTypes.USER).asReferenceValue();
	}

	protected PrismReference ref(List<ObjectReferenceType> orts) throws SchemaException {
		PrismReference rv = prismContext.itemFactory().createReference(new QName("dummy"));
		
		for (ObjectReferenceType ort : orts) {
			rv.add(ort.asReferenceValue().clone());
		}
		
		return rv;
	}

	protected PrismReference ref(ObjectReferenceType ort) throws SchemaException {
		return ref(Collections.singletonList(ort));
	}

	protected Map<String, CaseWorkItemType> sortByOriginalAssignee(Collection<CaseWorkItemType> workItems) {
		Map<String, CaseWorkItemType> rv = new HashMap<>();
		workItems.forEach(wi -> rv.put(wi.getOriginalAssigneeRef().getOid(), wi));
		return rv;
	}
	//endregion

}
