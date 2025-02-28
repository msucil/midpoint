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
package com.evolveum.midpoint.model.intest;

import com.evolveum.midpoint.model.api.ModelExecuteOptions;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.delta.ObjectDelta;
import com.evolveum.midpoint.prism.polystring.PolyString;
import com.evolveum.midpoint.prism.util.PrismAsserts;
import com.evolveum.midpoint.prism.xml.XmlTypeConverter;
import com.evolveum.midpoint.schema.constants.SchemaConstants;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.test.util.TestUtil;
import com.evolveum.midpoint.util.exception.*;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.context.ContextConfiguration;
import org.testng.annotations.Test;

import javax.xml.datatype.XMLGregorianCalendar;
import javax.xml.namespace.QName;
import java.io.File;

/**
 * User template with "mapping range" features.
 *
 * @author mederly
 *
 */
@ContextConfiguration(locations = {"classpath:ctx-model-intest-test-main.xml"})
@DirtiesContext(classMode = ClassMode.AFTER_CLASS)
public class TestUserTemplateWithRanges extends AbstractInitializedModelIntegrationTest {

	public static final File TEST_DIR = new File("src/test/resources/object-template-ranges");

	public static final QName MANAGER_ID_QNAME = new QName("http://sample.evolveum.com/xml/ns/sample-idm/extension", "managerId");

	protected static final File ROLE_BLOODY_NOSE_FILE = new File(TEST_DIR, "role-bloody-nose.xml");
	protected static final String ROLE_BLOODY_NOSE_OID = "ed34d3fe-1f0b-11e9-8e31-b3cfa585868a";
	protected static final String ROLE_BLOODY_NOSE_NAME = "Bloody Nose";
	
	protected static final File ORG_MONKEY_ISLAND_LOCAL_FILE = new File(TEST_DIR, "org-monkey-island-local.xml");

	protected static final File USER_TEMPLATE_RANGES_FILE = new File(TEST_DIR, "user-template-ranges.xml");
	protected static final String USER_TEMPLATE_RANGES_OID = "f486e3a7-6970-416e-8fe2-995358f59c46";

	private static final String BLOODY_ASSIGNMENT_SUBTYPE = "bloody";

	private static final XMLGregorianCalendar GUYBRUSH_FUNERAL_DATE_123456_CAL =
			XmlTypeConverter.createXMLGregorianCalendar(2222, 1, 2, 3, 4, 5);
	
	private static final XMLGregorianCalendar GUYBRUSH_FUNERAL_DATE_22222_CAL =
			XmlTypeConverter.createXMLGregorianCalendar(2222, 2, 2, 22, 22, 22);

	@Override
	protected boolean doAddOrgstruct() {
		return false; // we use our own
	}

	@Override
	public void initSystem(Task initTask, OperationResult initResult) throws Exception {
		super.initSystem(initTask, initResult);
        assumeAssignmentPolicy(AssignmentPolicyEnforcementType.FULL);

		repoAddObjectsFromFile(ORG_MONKEY_ISLAND_LOCAL_FILE, OrgType.class, initResult);

        repoAddObjectFromFile(USER_TEMPLATE_RANGES_FILE, initResult);
        repoAddObjectFromFile(ROLE_BLOODY_NOSE_FILE, initResult);
		setDefaultObjectTemplate(UserType.COMPLEX_TYPE, USER_TEMPLATE_RANGES_OID, initResult);

		changeEmployeeIdRaw("EM100", initTask, initResult);
	}

	/**
	 * Recomputation should give Elaine a manager-type assignment to Gov office.
	 */
	@Test
    public void test100RecomputeElaine() throws Exception {
		final String TEST_NAME = "test100RecomputeElaine";
        TestUtil.displayTestTitle(this, TEST_NAME);

        // GIVEN
        Task task = taskManager.createTaskInstance(TestUserTemplateWithRanges.class.getName() + "." + TEST_NAME);
        OperationResult result = task.getResult();

		// WHEN
		recomputeUser(USER_ELAINE_OID, task, result);

		// THEN
		result.computeStatus();
        TestUtil.assertSuccess(result);

		PrismObject<UserType> userElaine = getUser(USER_ELAINE_OID);
		display("elaine after recompute", userElaine);
		assertAssignedOrg(userElaine, ORG_GOVERNOR_OFFICE_OID, SchemaConstants.ORG_MANAGER);
        assertHasOrg(userElaine, ORG_GOVERNOR_OFFICE_OID, SchemaConstants.ORG_MANAGER);
	}

	/**
	 * After changing manager info in Gov office org,
	 * recomputation should remove Elaine a manager-type assignment to Gov office.
	 */
	@Test
	public void test110ChangeManagerAndRecomputeElaine() throws Exception {
		final String TEST_NAME = "test110ChangeManagerAndRecomputeElaine";
		TestUtil.displayTestTitle(this, TEST_NAME);

		// GIVEN
		Task task = taskManager.createTaskInstance(TestUserTemplateWithRanges.class.getName() + "." + TEST_NAME);
		OperationResult result = task.getResult();

		// WHEN
		changeManagerRaw("xxxxx", task, result);
		recomputeUser(USER_ELAINE_OID, task, result);

		// THEN
		result.computeStatus();
		TestUtil.assertSuccess(result);

		PrismObject<UserType> userElaine = getUser(USER_ELAINE_OID);
		display("elaine after change manager ID + recompute", userElaine);
		assertNotAssignedOrg(userElaine, ORG_GOVERNOR_OFFICE_OID, SchemaConstants.ORG_MANAGER);
		assertHasNoOrg(userElaine);		// this is even stronger
	}

	/**
	 * After setting manager in Gov office org back to EM100,
	 * recomputation should add Elaine a manager-type assignment to Gov office.
	 * In addition, we made her a member of Gov office as a preparation for test130.
	 */
	@Test
	public void test120RestoreManagerAndRecomputeElaineAgain() throws Exception {
		final String TEST_NAME = "test120RestoreManagerAndRecomputeElaineAgain";
		TestUtil.displayTestTitle(this, TEST_NAME);

		// GIVEN
		Task task = taskManager.createTaskInstance(TestUserTemplateWithRanges.class.getName() + "." + TEST_NAME);
		OperationResult result = task.getResult();

		assignOrg(USER_ELAINE_OID, ORG_GOVERNOR_OFFICE_OID, null);

		// WHEN
		changeManagerRaw("EM100", task, result);
		recomputeUser(USER_ELAINE_OID, task, result);

		// THEN
		result.computeStatus();
		TestUtil.assertSuccess(result);

		PrismObject<UserType> userElaine = getUser(USER_ELAINE_OID);
		display("elaine after restore of manager ID + recompute", userElaine);
		assertAssignedOrg(userElaine, ORG_GOVERNOR_OFFICE_OID, SchemaConstants.ORG_MANAGER);
		assertAssignedOrg(userElaine, ORG_GOVERNOR_OFFICE_OID, null);
		assertHasOrg(userElaine, ORG_GOVERNOR_OFFICE_OID, SchemaConstants.ORG_MANAGER);
		assertHasOrg(userElaine, ORG_GOVERNOR_OFFICE_OID, null);
	}

	/**
	 * After changing manager info in Gov office org again,
	 * recomputation should remove Elaine a manager-type assignment to Gov office.
	 * But it should keep her as a member.
	 */
	@Test
	public void test140ChangeManagerAndRecomputeElaineAgain() throws Exception {
		final String TEST_NAME = "test140ChangeManagerAndRecomputeElaineAgain";
		TestUtil.displayTestTitle(this, TEST_NAME);

		// GIVEN
		Task task = taskManager.createTaskInstance(TestUserTemplateWithRanges.class.getName() + "." + TEST_NAME);
		OperationResult result = task.getResult();

		// WHEN
		changeManagerRaw("xxxxx", task, result);
		recomputeUser(USER_ELAINE_OID, task, result);

		// THEN
		result.computeStatus();
		TestUtil.assertSuccess(result);

		PrismObject<UserType> userElaine = getUser(USER_ELAINE_OID);
		display("elaine after change of manager ID + recompute", userElaine);
		assertNotAssignedOrg(userElaine, ORG_GOVERNOR_OFFICE_OID, SchemaConstants.ORG_MANAGER);
		assertAssignedOrg(userElaine, ORG_GOVERNOR_OFFICE_OID);
		assertHasNoOrg(userElaine, ORG_GOVERNOR_OFFICE_OID, SchemaConstants.ORG_MANAGER);
		assertHasOrg(userElaine, ORG_GOVERNOR_OFFICE_OID);
	}


	/*
	 * 2xx series: testing ranges with conditions
	 * ==========================================
	 */

	/**
	 * Simple add values, nothing special. (Preparing ground for next test.)
	 */
	@Test
	public void test200SimpleOrgUnitAddition() throws Exception {
		final String TEST_NAME = "test200SimpleOrgUnitAddition";
		displayTestTitle(TEST_NAME);

		// GIVEN
		Task task = createTask(TEST_NAME);
		OperationResult result = task.getResult();

		// WHEN
		executeChanges(
				deltaFor(UserType.class)
						.item(UserType.F_ORGANIZATIONAL_UNIT).add(
								PolyString.fromOrig("U1"),
								PolyString.fromOrig("U2"),
								PolyString.fromOrig("U3"),
								PolyString.fromOrig("U4"))
						.item(UserType.F_ORGANIZATION).add(PolyString.fromOrig("O1"))
						.asObjectDelta(USER_JACK_OID),
				null, task, result);

		// THEN
		assertSuccess(result);

		PrismObject<UserType> userJack = getUser(USER_JACK_OID);
		display("jack", userJack);
		PrismAsserts.assertPropertyValue(userJack, UserType.F_ORGANIZATIONAL_UNIT,
				new PolyString("U1", "u1"),
				new PolyString("U2", "u2"),
				new PolyString("U3", "u3"),
				new PolyString("U4", "u4"));
		PrismAsserts.assertPropertyValue(userJack, UserType.F_ORGANIZATION,
				new PolyString("O1", "o1"),
				new PolyString("OU: U1 emp1234", "ou u1 emp1234"),
				new PolyString("OU: U2 emp1234", "ou u2 emp1234"),
				new PolyString("OU: U3 emp1234", "ou u3 emp1234"),
				new PolyString("OU: U4 emp1234", "ou u4 emp1234"));
	}

	/**
	 * OrganizationalUnit U1 disappearing + recompute. Computed value of 'OU: U1 ...' should be removed.
	 * Note that condition is true -> true.
	 */
	@Test
	public void test210RemoveUnit1() throws Exception {
		final String TEST_NAME = "test210RemoveUnit1";
		displayTestTitle(TEST_NAME);

		// GIVEN
		Task task = createTask(TEST_NAME);
		OperationResult result = task.getResult();

		// WHEN
		executeChanges(
				deltaFor(UserType.class)
						.item(UserType.F_ORGANIZATIONAL_UNIT).delete(PolyString.fromOrig("U1"))
						.asObjectDelta(USER_JACK_OID),
				ModelExecuteOptions.createRaw(), task, result);

		recomputeUser(USER_JACK_OID, task, result);

		// THEN
		assertSuccess(result);

		PrismObject<UserType> userJack = getUser(USER_JACK_OID);
		display("jack", userJack);
		PrismAsserts.assertPropertyValue(userJack, UserType.F_ORGANIZATIONAL_UNIT,
				new PolyString("U2", "u2"),
				new PolyString("U3", "u3"),
				new PolyString("U4", "u4"));
		PrismAsserts.assertPropertyValue(userJack, UserType.F_ORGANIZATION,
				new PolyString("O1", "o1"),
				new PolyString("OU: U2 emp1234", "ou u2 emp1234"),
				new PolyString("OU: U3 emp1234", "ou u3 emp1234"),
				new PolyString("OU: U4 emp1234", "ou u4 emp1234"));
	}

	/**
	 * OrganizationalUnit U2 disappearing.
	 * Then, along with recompute we erase employeeNumber.
	 * Mapping 'ceases to exist' and all of its range should be deleted.
	 * Condition: true -> false
	 */
	@Test
	public void test220RemoveUnit2AndNumber() throws Exception {
		final String TEST_NAME = "test220RemoveUnit2AndNumber";
		TestUtil.displayTestTitle(this, TEST_NAME);

		// GIVEN
		Task task = createTask(TEST_NAME);
		OperationResult result = task.getResult();

		// WHEN
		executeChanges(
				deltaFor(UserType.class)
						.item(UserType.F_ORGANIZATIONAL_UNIT).delete(PolyString.fromOrig("U2"))
						.asObjectDelta(USER_JACK_OID),
				ModelExecuteOptions.createRaw(), task, result);

		executeChanges(
				deltaFor(UserType.class)
						.item(UserType.F_EMPLOYEE_NUMBER).replace()
						.asObjectDelta(USER_JACK_OID),
				null, task, result);

		// THEN
		assertSuccess(result);

		PrismObject<UserType> userJack = getUser(USER_JACK_OID);
		display("jack", userJack);
		PrismAsserts.assertPropertyValue(userJack, UserType.F_ORGANIZATIONAL_UNIT,
				new PolyString("U3", "u3"),
				new PolyString("U4", "u4"));
		PrismAsserts.assertPropertyValue(userJack, UserType.F_ORGANIZATION,
				new PolyString("O1", "o1"));
	}

	/**
	 * Introduced OU: nonsense.
	 * Restoring employeeNumber.
	 * All missing 'OU: *' should be re-added, 'OU: nonsense' should be removed.
	 * Condition: false -> true
	 */
	@Test
	public void test230RestoreNumber() throws Exception {
		final String TEST_NAME = "test230RestoreNumber";
		displayTestTitle(TEST_NAME);

		// GIVEN
		Task task = createTask(TEST_NAME);
		OperationResult result = task.getResult();

		// WHEN
		executeChanges(
				deltaFor(UserType.class)
						.item(UserType.F_ORGANIZATION).add(PolyString.fromOrig("OU: nonsense"))
						.asObjectDelta(USER_JACK_OID),
				ModelExecuteOptions.createRaw(), task, result);

		executeChanges(
				deltaFor(UserType.class)
						.item(UserType.F_EMPLOYEE_NUMBER).replace("emp1234")
						.asObjectDelta(USER_JACK_OID),
				null, task, result);

		// THEN
		assertSuccess(result);

		PrismObject<UserType> userJack = getUser(USER_JACK_OID);
		display("jack", userJack);
		PrismAsserts.assertPropertyValue(userJack, UserType.F_ORGANIZATIONAL_UNIT,
				new PolyString("U3", "u3"),
				new PolyString("U4", "u4"));
		PrismAsserts.assertPropertyValue(userJack, UserType.F_ORGANIZATION,
				new PolyString("O1", "o1"),
				new PolyString("OU: U3 emp1234", "ou u3 emp1234"),
				new PolyString("OU: U4 emp1234", "ou u4 emp1234"));
	}
	
	@Test
	public void test300GuybrushBloodyNose() throws Exception {
		final String TEST_NAME = "test300GuybrushBloodyNose";
		displayTestTitle(TEST_NAME);

		// GIVEN
		Task task = createTask(TEST_NAME);
		OperationResult result = task.getResult();

		// WHEN
		displayWhen(TEST_NAME);
		executeChanges(
				deltaFor(UserType.class)
						.item(UserType.F_TITLE).add(PolyString.fromOrig(ROLE_BLOODY_NOSE_NAME))
						.asObjectDelta(USER_GUYBRUSH_OID),
				null, task, result);

		// THEN
		displayThen(TEST_NAME);
		assertSuccess(result);

		assertUserAfter(USER_GUYBRUSH_OID)
			.assertTitle(ROLE_BLOODY_NOSE_NAME)
			.assignments()
				.single()
					.assertTargetOid(ROLE_BLOODY_NOSE_OID)
					.assertSubtype(BLOODY_ASSIGNMENT_SUBTYPE)
					.activation()
						.assertNoValidTo();
		
	}
	
	@Test
	public void test309GuybrushNotBloody() throws Exception {
		final String TEST_NAME = "test309GuybrushNotBloody";
		displayTestTitle(TEST_NAME);

		// GIVEN
		Task task = createTask(TEST_NAME);
		OperationResult result = task.getResult();

		// WHEN
		displayWhen(TEST_NAME);
		executeChanges(
				deltaFor(UserType.class)
						.item(UserType.F_TITLE).delete(PolyString.fromOrig(ROLE_BLOODY_NOSE_NAME))
						.asObjectDelta(USER_GUYBRUSH_OID),
				null, task, result);

		// THEN
		displayThen(TEST_NAME);
		assertSuccess(result);

		assertUserAfter(USER_GUYBRUSH_OID)
			.assertNoTitle()
			.assignments()
				.assertNone();
		
	}
	
	@Test
	public void test310GuybrushBloodyNoseFuneral() throws Exception {
		final String TEST_NAME = "test310GuybrushBloodyNoseFuneral";
		displayTestTitle(TEST_NAME);

		// GIVEN
		Task task = createTask(TEST_NAME);
		OperationResult result = task.getResult();
		
		modifyUserReplace(USER_GUYBRUSH_OID, getExtensionPath(PIRACY_FUNERAL_TIMESTAMP), task, result,
				GUYBRUSH_FUNERAL_DATE_123456_CAL);

		// WHEN
		displayWhen(TEST_NAME);
		executeChanges(
				deltaFor(UserType.class)
						.item(UserType.F_TITLE).add(PolyString.fromOrig(ROLE_BLOODY_NOSE_NAME))
						.asObjectDelta(USER_GUYBRUSH_OID),
				null, task, result);

		// THEN
		displayThen(TEST_NAME);
		assertSuccess(result);

		assertUserAfter(USER_GUYBRUSH_OID)
			.assertTitle(ROLE_BLOODY_NOSE_NAME)
			.assignments()
				.single()
					.assertTargetOid(ROLE_BLOODY_NOSE_OID)
					.assertSubtype(BLOODY_ASSIGNMENT_SUBTYPE)
					.activation()
						.assertValidTo(GUYBRUSH_FUNERAL_DATE_123456_CAL);
		
	}
	
	@Test
	public void test319GuybrushNoBloodyNoseFuneral() throws Exception {
		final String TEST_NAME = "test319GuybrushNoBloodyNoseFuneral";
		displayTestTitle(TEST_NAME);

		// GIVEN
		Task task = createTask(TEST_NAME);
		OperationResult result = task.getResult();
		
		// WHEN
		displayWhen(TEST_NAME);
		executeChanges(
				deltaFor(UserType.class)
						.item(UserType.F_TITLE).delete(PolyString.fromOrig(ROLE_BLOODY_NOSE_NAME))
						.asObjectDelta(USER_GUYBRUSH_OID),
				null, task, result);

		// THEN
		displayThen(TEST_NAME);
		assertSuccess(result);

		assertUserAfter(USER_GUYBRUSH_OID)
			.assertNoTitle()
			.assignments()
				.assertNone();
		
	}
	
	@Test
	public void test320GuybrushBloodyNose() throws Exception {
		final String TEST_NAME = "test320GuybrushBloodyNose";
		displayTestTitle(TEST_NAME);

		// GIVEN
		Task task = createTask(TEST_NAME);
		OperationResult result = task.getResult();
		
		modifyUserReplace(USER_GUYBRUSH_OID, getExtensionPath(PIRACY_FUNERAL_TIMESTAMP), task, result
				/* no value */);

		// WHEN
		displayWhen(TEST_NAME);
		executeChanges(
				deltaFor(UserType.class)
						.item(UserType.F_TITLE).add(PolyString.fromOrig(ROLE_BLOODY_NOSE_NAME))
						.asObjectDelta(USER_GUYBRUSH_OID),
				null, task, result);

		// THEN
		displayThen(TEST_NAME);
		assertSuccess(result);

		assertUserAfter(USER_GUYBRUSH_OID)
			.assertTitle(ROLE_BLOODY_NOSE_NAME)
			.assignments()
				.single()
					.assertTargetOid(ROLE_BLOODY_NOSE_OID)
					.assertSubtype(BLOODY_ASSIGNMENT_SUBTYPE)
					.activation()
						.assertNoValidTo();
		
	}
	
	@Test
	public void test322GuybrushSetFuneral() throws Exception {
		final String TEST_NAME = "test322GuybrushSetFuneral";
		displayTestTitle(TEST_NAME);

		// GIVEN
		Task task = createTask(TEST_NAME);
		OperationResult result = task.getResult();

		// WHEN
		displayWhen(TEST_NAME);
		modifyUserReplace(USER_GUYBRUSH_OID, getExtensionPath(PIRACY_FUNERAL_TIMESTAMP), task, result,
				GUYBRUSH_FUNERAL_DATE_123456_CAL);

		// THEN
		displayThen(TEST_NAME);
		assertSuccess(result);

		assertUserAfter(USER_GUYBRUSH_OID)
			.assertTitle(ROLE_BLOODY_NOSE_NAME)
			.assignments()
				.single()
					.assertTargetOid(ROLE_BLOODY_NOSE_OID)
					.assertSubtype(BLOODY_ASSIGNMENT_SUBTYPE)
					.activation()
						.assertValidTo(GUYBRUSH_FUNERAL_DATE_123456_CAL);
		
	}
	
	@Test
	public void test324GuybrushSetFuneral22222() throws Exception {
		final String TEST_NAME = "test324GuybrushSetFuneral22222";
		displayTestTitle(TEST_NAME);

		// GIVEN
		Task task = createTask(TEST_NAME);
		OperationResult result = task.getResult();

		// WHEN
		displayWhen(TEST_NAME);
		modifyUserReplace(USER_GUYBRUSH_OID, getExtensionPath(PIRACY_FUNERAL_TIMESTAMP), task, result,
				GUYBRUSH_FUNERAL_DATE_22222_CAL);

		// THEN
		displayThen(TEST_NAME);
		assertSuccess(result);

		assertUserAfter(USER_GUYBRUSH_OID)
			.assertTitle(ROLE_BLOODY_NOSE_NAME)
			.assignments()
				.single()
					.assertTargetOid(ROLE_BLOODY_NOSE_OID)
					.assertSubtype(BLOODY_ASSIGNMENT_SUBTYPE)
					.activation()
						.assertValidTo(GUYBRUSH_FUNERAL_DATE_22222_CAL);
	}
	
	/**
	 * MID-5063
	 */
	@Test
	public void test326GuybrushNoFuneral() throws Exception {
		final String TEST_NAME = "test326GuybrushNoFuneral";
		displayTestTitle(TEST_NAME);

		// GIVEN
		Task task = createTask(TEST_NAME);
		OperationResult result = task.getResult();

		// WHEN
		displayWhen(TEST_NAME);
		modifyUserReplace(USER_GUYBRUSH_OID, getExtensionPath(PIRACY_FUNERAL_TIMESTAMP), task, result
				/* no value */);

		// THEN
		displayThen(TEST_NAME);
		assertSuccess(result);

		assertUserAfter(USER_GUYBRUSH_OID)
			.assertTitle(ROLE_BLOODY_NOSE_NAME)
			.assignments()
				.single()
					.assertTargetOid(ROLE_BLOODY_NOSE_OID)
					.assertSubtype(BLOODY_ASSIGNMENT_SUBTYPE)
					.activation()
						.assertNoValidTo();
	}
	
	@Test
	public void test329GuybrushNoBloodyNose() throws Exception {
		final String TEST_NAME = "test329GuybrushNoBloodyNose";
		displayTestTitle(TEST_NAME);

		// GIVEN
		Task task = createTask(TEST_NAME);
		OperationResult result = task.getResult();
		
		// WHEN
		displayWhen(TEST_NAME);
		executeChanges(
				deltaFor(UserType.class)
						.item(UserType.F_TITLE).delete(PolyString.fromOrig(ROLE_BLOODY_NOSE_NAME))
						.asObjectDelta(USER_GUYBRUSH_OID),
				null, task, result);

		// THEN
		displayThen(TEST_NAME);
		assertSuccess(result);

		assertUserAfter(USER_GUYBRUSH_OID)
			.assertNoTitle()
			.assignments()
				.assertNone();
		
	}

	private void changeManagerRaw(String id, Task task, OperationResult result) throws CommonException {
		executeChanges(
				(ObjectDelta) prismContext.deltaFor(OrgType.class)
						.item(OrgType.F_EXTENSION, MANAGER_ID_QNAME).replace(id)
						.asObjectDelta(ORG_GOVERNOR_OFFICE_OID),
				ModelExecuteOptions.createRaw(), task, result);
	}

	private void changeEmployeeIdRaw(String id, Task initTask, OperationResult initResult) throws CommonException {
		executeChanges(
				(ObjectDelta) prismContext.deltaFor(UserType.class)
						.item(UserType.F_EMPLOYEE_NUMBER).replace(id)
						.asObjectDelta(USER_ELAINE_OID),
				ModelExecuteOptions.createRaw(), initTask, initResult);
	}

}
