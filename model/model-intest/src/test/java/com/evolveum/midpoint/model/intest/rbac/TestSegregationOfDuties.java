/*
 * Copyright (c) 2010-2018 Evolveum
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
package com.evolveum.midpoint.model.intest.rbac;

import static org.testng.AssertJUnit.*;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

import com.evolveum.midpoint.model.api.ModelExecuteOptions;
import com.evolveum.midpoint.prism.delta.*;
import com.evolveum.midpoint.schema.constants.ObjectTypes;
import com.evolveum.midpoint.schema.util.ObjectTypeUtil;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.context.ContextConfiguration;
import org.testng.AssertJUnit;
import org.testng.annotations.Test;

import com.evolveum.midpoint.model.api.context.EvaluatedAssignment;
import com.evolveum.midpoint.model.api.context.EvaluatedAssignmentTarget;
import com.evolveum.midpoint.model.api.context.EvaluatedPolicyRule;
import com.evolveum.midpoint.model.api.context.EvaluatedPolicyRuleTrigger;
import com.evolveum.midpoint.model.api.context.ModelContext;
import com.evolveum.midpoint.model.intest.AbstractInitializedModelIntegrationTest;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.util.PrismAsserts;
import com.evolveum.midpoint.schema.constants.SchemaConstants;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.schema.util.MiscSchemaUtil;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.test.DummyResourceContoller;
import com.evolveum.midpoint.test.util.TestUtil;
import com.evolveum.midpoint.util.exception.CommunicationException;
import com.evolveum.midpoint.util.exception.ConfigurationException;
import com.evolveum.midpoint.util.exception.ExpressionEvaluationException;
import com.evolveum.midpoint.util.exception.ObjectAlreadyExistsException;
import com.evolveum.midpoint.util.exception.ObjectNotFoundException;
import com.evolveum.midpoint.util.exception.PolicyViolationException;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.util.exception.SecurityViolationException;
import com.evolveum.midpoint.xml.ns._public.common.common_3.AssignmentType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.GlobalPolicyRuleType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ObjectReferenceType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ObjectType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.PolicyActionsType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.PolicyConstraintKindType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.PolicyExceptionType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.RoleType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ShadowType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.UserType;

/**
 * @author semancik
 *
 */
@ContextConfiguration(locations = {"classpath:ctx-model-intest-test-main.xml"})
@DirtiesContext(classMode = ClassMode.AFTER_CLASS)
public class TestSegregationOfDuties extends AbstractInitializedModelIntegrationTest {

	protected static final File TEST_DIR = new File("src/test/resources/rbac/sod");

	// Gold, silver and bronze: mutual exclusion (prune), directly in the roles

	protected static final File ROLE_PRIZE_GOLD_FILE = new File(TEST_DIR, "role-prize-gold.xml");
	protected static final String ROLE_PRIZE_GOLD_OID = "bbc22f82-df21-11e6-aa6b-4b1408befd10";
	protected static final String ROLE_PRIZE_GOLD_SHIP = "Gold";
	protected static final File ROLE_PRIZE_GOLD_ENFORCED_FILE = new File(TEST_DIR, "role-prize-gold-enforced.xml");
	protected static final String ROLE_PRIZE_GOLD_ENFORCED_OID = "6bff06a9-51b7-4a19-9e77-ee0701c5bfe2";
	protected static final File ROLE_PRIZE_GOLD_BY_MAPPING_FILE = new File(TEST_DIR, "role-prize-gold-by-mapping.xml");
	protected static final String ROLE_PRIZE_GOLD_BY_MAPPING_OID = "01348e8c-1a77-4619-b375-0a3701564550";

	protected static final File ROLE_PRIZE_SILVER_FILE = new File(TEST_DIR, "role-prize-silver.xml");
	protected static final String ROLE_PRIZE_SILVER_OID = "dfb5fffe-df21-11e6-bb4f-ef02bdbc9d71";
	protected static final String ROLE_PRIZE_SILVER_SHIP = "Silver";
	protected static final File ROLE_PRIZE_SILVER_ENFORCED_FILE = new File(TEST_DIR, "role-prize-silver-enforced.xml");
	protected static final String ROLE_PRIZE_SILVER_ENFORCED_OID = "0c3b2e44-9387-4c7b-8262-a20fdea434ea";

	protected static final File ROLE_PRIZE_BRONZE_FILE = new File(TEST_DIR, "role-prize-bronze.xml");
	protected static final String ROLE_PRIZE_BRONZE_OID = "19f11686-df22-11e6-b0e9-835ed7ca08a5";
	protected static final String ROLE_PRIZE_BRONZE_SHIP = "Bronze";
	protected static final File ROLE_PRIZE_BRONZE_ENFORCED_FILE = new File(TEST_DIR, "role-prize-bronze-enforced.xml");
	protected static final String ROLE_PRIZE_BRONZE_ENFORCED_OID = "702dc3c9-9755-4880-b132-cf82d5845dd9";

	// Red, green and blue: mutual exclusion (prune) in the metarole

	protected static final File ROLE_META_COLOR_FILE = new File(TEST_DIR, "role-meta-color.xml");
	protected static final String ROLE_META_COLOR_OID = "0b759ce2-df29-11e6-a84c-9b213183a815";

	protected static final File ROLE_COLOR_RED_FILE = new File(TEST_DIR, "role-color-red.xml");
	protected static final String ROLE_COLOR_RED_OID = "eaa4ec3e-df28-11e6-9cca-336e0346d5cc";
	protected static final String ROLE_COLOR_RED_SHIP = "Red";

	protected static final File ROLE_COLOR_GREEN_FILE = new File(TEST_DIR, "role-color-green.xml");
	protected static final String ROLE_COLOR_GREEN_OID = "2fd9e8f4-df29-11e6-9605-cfcedd703b9e";
	protected static final String ROLE_COLOR_GREEN_SHIP = "Green";

	protected static final File ROLE_COLOR_BLUE_FILE = new File(TEST_DIR, "role-color-blue.xml");
	protected static final String ROLE_COLOR_BLUE_OID = "553e8df2-df29-11e6-a7ca-cb7c1f38d89f";
	protected static final String ROLE_COLOR_BLUE_SHIP = "Blue";

	protected static final File ROLE_COLOR_NONE_FILE = new File(TEST_DIR, "role-color-none.xml");
	protected static final String ROLE_COLOR_NONE_OID = "662a997e-df2b-11e6-9bb3-5f235d1a8e60";

	// Executive / controlling exclusion roles

	protected static final File ROLE_META_EXECUTIVE_FILE = new File(TEST_DIR, "role-meta-executive.xml");
	protected static final String ROLE_META_EXECUTIVE_OID = "d20aefe6-3ecf-11e7-8068-5f346db1ee00";

	protected static final File ROLE_EXECUTIVE_1_FILE = new File(TEST_DIR, "role-executive-1.xml");
	protected static final String ROLE_EXECUTIVE_1_OID = "d20aefe6-3ecf-11e7-8068-5f346db1ee01";

	protected static final File ROLE_EXECUTIVE_2_FILE = new File(TEST_DIR, "role-executive-2.xml");
	protected static final String ROLE_EXECUTIVE_2_OID = "d20aefe6-3ecf-11e7-8068-5f346db1ee02";

	protected static final File ROLE_META_CONTROLLING_FILE = new File(TEST_DIR, "role-meta-controlling.xml");
	protected static final String ROLE_META_CONTROLLING_OID = "d20aefe6-3ecf-11e7-8068-5f346db1cc00";

	protected static final File ROLE_CONTROLLING_1_FILE = new File(TEST_DIR, "role-controlling-1.xml");
	protected static final String ROLE_CONTROLLING_1_OID = "d20aefe6-3ecf-11e7-8068-5f346db1cc01";

	protected static final File ROLE_CONTROLLING_2_FILE = new File(TEST_DIR, "role-controlling-2.xml");
	protected static final String ROLE_CONTROLLING_2_OID = "d20aefe6-3ecf-11e7-8068-5f346db1cc02";

	protected static final File ROLE_CITIZEN_SK_FILE = new File(TEST_DIR, "role-citizen-sk.xml");
	protected static final String ROLE_CITIZEN_SK_OID = "88420574-5596-11e7-80e9-7f28005e6b39";

	protected static final File ROLE_CITIZEN_US_FILE = new File(TEST_DIR, "role-citizen-us.xml");
	protected static final String ROLE_CITIZEN_US_OID = "a58c5940-5596-11e7-a3a0-dba800ea7966";

	protected static final File ROLE_MINISTER_FILE = new File(TEST_DIR, "role-minister.xml");
	protected static final String ROLE_MINISTER_OID = "95565b4a-55a3-11e7-918a-3f59a532dbfc";

	protected static final File ROLE_CRIMINAL_FILE = new File(TEST_DIR, "role-criminal.xml");
	protected static final String ROLE_CRIMINAL_OID = "f6deb182-55a3-11e7-b519-27bdcd6d9490";

	protected static final File ROLE_SELF_EXCLUSION_FILE = new File(TEST_DIR, "role-self-exclusion.xml");
	protected static final String ROLE_SELF_EXCLUSION_OID = "9577bd6c-dd5d-48e5-bbb1-554bba5db9be";

	protected static final File ROLE_SELF_EXCLUSION_MANAGER_MEMBER_FILE = new File(TEST_DIR, "role-self-exclusion-manager-member.xml");
	protected static final String ROLE_SELF_EXCLUSION_MANAGER_MEMBER_OID = "aeed4751-fad6-4c4e-9ece-c793128e0c13";

	private static final File CONFIG_WITH_GLOBAL_RULES_EXCLUSION_FILE = new File(TEST_DIR, "global-policy-rules-exclusion.xml");
	private static final File CONFIG_WITH_GLOBAL_RULES_SOD_APPROVAL_FILE = new File(TEST_DIR, "global-policy-rules-sod-approval.xml");

	private static final String GLOBAL_POLICY_RULE_SOD_APPROVAL_NAME = "exclusion-global-sod-approval";

	@Override
	public void initSystem(Task initTask, OperationResult initResult) throws Exception {
		super.initSystem(initTask, initResult);

		repoAddObjectFromFile(ROLE_PRIZE_GOLD_FILE, initResult);
		repoAddObjectFromFile(ROLE_PRIZE_SILVER_FILE, initResult);
		repoAddObjectFromFile(ROLE_PRIZE_BRONZE_FILE, initResult);
		repoAddObjectFromFile(ROLE_PRIZE_GOLD_ENFORCED_FILE, initResult);
		repoAddObjectFromFile(ROLE_PRIZE_SILVER_ENFORCED_FILE, initResult);
		repoAddObjectFromFile(ROLE_PRIZE_BRONZE_ENFORCED_FILE, initResult);
		repoAddObjectFromFile(ROLE_PRIZE_GOLD_BY_MAPPING_FILE, initResult);

		repoAddObjectFromFile(ROLE_META_COLOR_FILE, initResult);
		repoAddObjectFromFile(ROLE_COLOR_RED_FILE, initResult);
		repoAddObjectFromFile(ROLE_COLOR_GREEN_FILE, initResult);
		repoAddObjectFromFile(ROLE_COLOR_BLUE_FILE, initResult);
		repoAddObjectFromFile(ROLE_COLOR_NONE_FILE, initResult);

		repoAddObjectFromFile(ROLE_META_EXECUTIVE_FILE, initResult);
		repoAddObjectFromFile(ROLE_EXECUTIVE_1_FILE, initResult);
		repoAddObjectFromFile(ROLE_EXECUTIVE_2_FILE, initResult);
		repoAddObjectFromFile(ROLE_META_CONTROLLING_FILE, initResult);
		repoAddObjectFromFile(ROLE_CONTROLLING_1_FILE, initResult);
		repoAddObjectFromFile(ROLE_CONTROLLING_2_FILE, initResult);
		repoAddObjectFromFile(ROLE_CITIZEN_SK_FILE, initResult);
		repoAddObjectFromFile(ROLE_CITIZEN_US_FILE, initResult);
		repoAddObjectFromFile(ROLE_MINISTER_FILE, initResult);
		repoAddObjectFromFile(ROLE_CRIMINAL_FILE, initResult);
		repoAddObjectFromFile(ROLE_SELF_EXCLUSION_FILE, initResult);
		repoAddObjectFromFile(ROLE_SELF_EXCLUSION_MANAGER_MEMBER_FILE, initResult);
	}

	@Test
    public void test110SimpleExclusion1() throws Exception {
		final String TEST_NAME = "test110SimpleExclusion1";
        displayTestTitle(TEST_NAME);

        Task task = createTask(TEST_NAME);
        OperationResult result = task.getResult();

        // This should go well
        assignRole(USER_JACK_OID, ROLE_PIRATE_OID, task, result);

        assertSuccess(result);

        try {
	        // This should die
	        assignRole(USER_JACK_OID, ROLE_JUDGE_OID, task, result);

	        fail("Expected policy violation after adding judge role, but it went well");
        } catch (PolicyViolationException e) {
	        System.out.println("Got expected exception: " + e.getMessage());
	        assertMessage(e, "Violation of SoD policy: Role \"Judge\" excludes role \"Pirate\", they cannot be assigned at the same time");
        	result.computeStatus();
        	assertFailure(result);
        }

        unassignRole(USER_JACK_OID, ROLE_PIRATE_OID, task, result);

        assertAssignedNoRole(USER_JACK_OID, task, result);
	}

	@Test
    public void test112SimpleExclusion1Deprecated() throws Exception {
		final String TEST_NAME = "test112SimpleExclusion1Deprecated";
        displayTestTitle(TEST_NAME);

        Task task = createTask(TEST_NAME);
        OperationResult result = task.getResult();

        // This should go well
        assignRole(USER_JACK_OID, ROLE_PIRATE_OID, task, result);

        try {
	        // This should die
	        assignRole(USER_JACK_OID, ROLE_JUDGE_DEPRECATED_OID, task, result);

	        AssertJUnit.fail("Expected policy violation after adding judge role, but it went well");
        } catch (PolicyViolationException e) {
	        System.out.println("Got expected exception: " + e.getMessage());
        }

        unassignRole(USER_JACK_OID, ROLE_PIRATE_OID, task, result);

        assertAssignedNoRole(USER_JACK_OID, task, result);
	}

	/**
	 * Same thing as before but other way around
	 */
	@Test
    public void test120SimpleExclusion2() throws Exception {
		final String TEST_NAME = "test120SimpleExclusion2";
        displayTestTitle(TEST_NAME);

        Task task = createTask(TEST_NAME);
        OperationResult result = task.getResult();

        // This should go well
        assignRole(USER_JACK_OID, ROLE_JUDGE_OID, task, result);

        try {
	        // This should die
	        assignRole(USER_JACK_OID, ROLE_PIRATE_OID, task, result);

	        AssertJUnit.fail("Expected policy violation after adding pirate role, but it went well");
        } catch (PolicyViolationException e) {
	        System.out.println("Got expected exception: " + e.getMessage());
	        assertMessage(e, "Violation of SoD policy: Role \"Judge\" excludes role \"Pirate\", they cannot be assigned at the same time");
        }

        unassignRole(USER_JACK_OID, ROLE_JUDGE_OID, task, result);
        assertAssignedNoRole(USER_JACK_OID, task, result);
	}

	/**
	 * Same thing as before but other way around
	 */
	@Test
    public void test122SimpleExclusion2Deprecated() throws Exception {
		final String TEST_NAME = "test122SimpleExclusion2Deprecated";
        displayTestTitle(TEST_NAME);

        Task task = createTask(TEST_NAME);
        OperationResult result = task.getResult();

        // This should go well
        assignRole(USER_JACK_OID, ROLE_JUDGE_DEPRECATED_OID, task, result);

        try {
	        // This should die
	        assignRole(USER_JACK_OID, ROLE_PIRATE_OID, task, result);

	        AssertJUnit.fail("Expected policy violation after adding pirate role, but it went well");
        } catch (PolicyViolationException e) {
	        System.out.println("Got expected exception: " + e.getMessage());
        }

        unassignRole(USER_JACK_OID, ROLE_JUDGE_DEPRECATED_OID, task, result);
        assertAssignedNoRole(USER_JACK_OID, task, result);
	}

	@Test
    public void test130SimpleExclusionBoth1() throws Exception {
		final String TEST_NAME = "test130SimpleExclusionBoth1";
        displayTestTitle(TEST_NAME);

        Task task = createTask(TEST_NAME);
        OperationResult result = task.getResult();

        Collection<ItemDelta<?,?>> modifications = new ArrayList<>();
		modifications.add((createAssignmentModification(ROLE_JUDGE_OID, RoleType.COMPLEX_TYPE, null, null, null, true)));
		modifications.add((createAssignmentModification(ROLE_PIRATE_OID, RoleType.COMPLEX_TYPE, null, null, null, true)));
		ObjectDelta<UserType> userDelta = prismContext.deltaFactory().object()
				.createModifyDelta(USER_JACK_OID, modifications, UserType.class);

        try {
        	modelService.executeChanges(MiscSchemaUtil.createCollection(userDelta), null, task, result);

	        AssertJUnit.fail("Expected policy violation, but it went well");
        } catch (PolicyViolationException e) {
	        System.out.println("Got expected exception: " + e.getMessage());
        }

        assertAssignedNoRole(USER_JACK_OID, task, result);
	}

	@Test
    public void test132SimpleExclusionBoth1Deprecated() throws Exception {
		final String TEST_NAME = "test132SimpleExclusionBoth1Deprecated";
        displayTestTitle(TEST_NAME);

        Task task = createTask(TEST_NAME);
        OperationResult result = task.getResult();

        Collection<ItemDelta<?,?>> modifications = new ArrayList<>();
		modifications.add((createAssignmentModification(ROLE_JUDGE_DEPRECATED_OID, RoleType.COMPLEX_TYPE, null, null, null, true)));
		modifications.add((createAssignmentModification(ROLE_PIRATE_OID, RoleType.COMPLEX_TYPE, null, null, null, true)));
		ObjectDelta<UserType> userDelta = prismContext.deltaFactory().object()
				.createModifyDelta(USER_JACK_OID, modifications, UserType.class);

        try {
        	modelService.executeChanges(MiscSchemaUtil.createCollection(userDelta), null, task, result);

	        AssertJUnit.fail("Expected policy violation, but it went well");
        } catch (PolicyViolationException e) {
	        System.out.println("Got expected exception: " + e.getMessage());
        }

        assertAssignedNoRole(USER_JACK_OID, task, result);
	}

	@Test
    public void test140SimpleExclusionBoth2() throws Exception {
		final String TEST_NAME = "test140SimpleExclusionBoth2";
        displayTestTitle(TEST_NAME);

        Task task = createTask(TEST_NAME);
        OperationResult result = task.getResult();

        Collection<ItemDelta<?,?>> modifications = new ArrayList<>();
		modifications.add((createAssignmentModification(ROLE_PIRATE_OID, RoleType.COMPLEX_TYPE, null, null, null, true)));
		modifications.add((createAssignmentModification(ROLE_JUDGE_OID, RoleType.COMPLEX_TYPE, null, null, null, true)));
		ObjectDelta<UserType> userDelta = prismContext.deltaFactory().object()
				.createModifyDelta(USER_JACK_OID, modifications, UserType.class);

        try {
        	modelService.executeChanges(MiscSchemaUtil.createCollection(userDelta), null, task, result);

	        AssertJUnit.fail("Expected policy violation, but it went well");
        } catch (PolicyViolationException e) {
	        System.out.println("Got expected exception: " + e.getMessage());
        }

        assertAssignedNoRole(USER_JACK_OID, task, result);
	}

	@Test
    public void test142SimpleExclusionBoth2Deprecated() throws Exception {
		final String TEST_NAME = "test142SimpleExclusionBoth2Deprecated";
        displayTestTitle(TEST_NAME);

        Task task = createTask(TEST_NAME);
        OperationResult result = task.getResult();

        Collection<ItemDelta<?,?>> modifications = new ArrayList<>();
		modifications.add((createAssignmentModification(ROLE_PIRATE_OID, RoleType.COMPLEX_TYPE, null, null, null, true)));
		modifications.add((createAssignmentModification(ROLE_JUDGE_DEPRECATED_OID, RoleType.COMPLEX_TYPE, null, null, null, true)));
		ObjectDelta<UserType> userDelta = prismContext.deltaFactory().object()
				.createModifyDelta(USER_JACK_OID, modifications, UserType.class);

        try {
        	modelService.executeChanges(MiscSchemaUtil.createCollection(userDelta), null, task, result);

	        AssertJUnit.fail("Expected policy violation, but it went well");
        } catch (PolicyViolationException e) {
	        System.out.println("Got expected exception: " + e.getMessage());
        }

        assertAssignedNoRole(USER_JACK_OID, task, result);
	}

	@Test
    public void test150SimpleExclusionBothBidirectional1() throws Exception {
		final String TEST_NAME = "test150SimpleExclusionBothBidirectional1";
        displayTestTitle(TEST_NAME);

        Task task = createTask(TEST_NAME);
        OperationResult result = task.getResult();

        Collection<ItemDelta<?,?>> modifications = new ArrayList<>();
		modifications.add((createAssignmentModification(ROLE_THIEF_OID, RoleType.COMPLEX_TYPE, null, null, null, true)));
		modifications.add((createAssignmentModification(ROLE_JUDGE_OID, RoleType.COMPLEX_TYPE, null, null, null, true)));
		ObjectDelta<UserType> userDelta = prismContext.deltaFactory().object()
				.createModifyDelta(USER_JACK_OID, modifications, UserType.class);

        try {
        	modelService.executeChanges(MiscSchemaUtil.createCollection(userDelta), null, task, result);

	        AssertJUnit.fail("Expected policy violation, but it went well");
        } catch (PolicyViolationException e) {
	        System.out.println("Got expected exception: " + e.getMessage());
        }

        assertAssignedNoRole(USER_JACK_OID, task, result);
	}

	@Test
    public void test160SimpleExclusionBothBidirectional2() throws Exception {
		final String TEST_NAME = "test160SimpleExclusionBothBidirectional2";
        displayTestTitle(TEST_NAME);

        Task task = createTask(TEST_NAME);
        OperationResult result = task.getResult();

        Collection<ItemDelta<?,?>> modifications = new ArrayList<>();
		modifications.add((createAssignmentModification(ROLE_JUDGE_OID, RoleType.COMPLEX_TYPE, null, null, null, true)));
		modifications.add((createAssignmentModification(ROLE_THIEF_OID, RoleType.COMPLEX_TYPE, null, null, null, true)));
		ObjectDelta<UserType> userDelta = prismContext.deltaFactory().object()
				.createModifyDelta(USER_JACK_OID, modifications, UserType.class);

        try {
        	modelService.executeChanges(MiscSchemaUtil.createCollection(userDelta), null, task, result);

	        AssertJUnit.fail("Expected policy violation, but it went well");
        } catch (PolicyViolationException e) {
	        System.out.println("Got expected exception: " + e.getMessage());
        }

        assertAssignedNoRole(USER_JACK_OID, task, result);
	}

	@Test
    public void test171SimpleExclusion1WithPolicyException() throws Exception {
		final String TEST_NAME = "test171SimpleExclusion1WithPolicyException";
        displayTestTitle(TEST_NAME);

        Task task = createTask(TEST_NAME);
        OperationResult result = task.getResult();

        assignRole(USER_JACK_OID, ROLE_JUDGE_OID, task, result);

        assignRole(USER_JACK_OID, ROLE_PIRATE_OID, null, getJudgeExceptionBlock(ROLE_PIRATE_NAME), task, result);

        PrismObject<UserType> userJackIn = getUser(USER_JACK_OID);
        assertAssignedRoles(userJackIn, ROLE_JUDGE_OID, ROLE_PIRATE_OID);

        unassignRole(USER_JACK_OID, ROLE_JUDGE_OID, task, result);

        unassignRole(USER_JACK_OID, ROLE_PIRATE_OID, null, getJudgeExceptionBlock(ROLE_PIRATE_NAME), task, result);

        assertAssignedNoRole(USER_JACK_OID, task, result);
	}

	@Test
    public void test172SimpleExclusion2WithPolicyException() throws Exception {
		final String TEST_NAME = "test172SimpleExclusion2WithPolicyException";
        displayTestTitle(TEST_NAME);

        Task task = createTask(TEST_NAME);
        OperationResult result = task.getResult();

        assignRole(USER_JACK_OID, ROLE_PIRATE_OID, null, getJudgeExceptionBlock(ROLE_PIRATE_NAME), task, result);

        assignRole(USER_JACK_OID, ROLE_JUDGE_OID, task, result);

        PrismObject<UserType> userJackIn = getUser(USER_JACK_OID);
        assertAssignedRoles(userJackIn, ROLE_JUDGE_OID, ROLE_PIRATE_OID);

        unassignRole(USER_JACK_OID, ROLE_JUDGE_OID, task, result);

        unassignRole(USER_JACK_OID, ROLE_PIRATE_OID, null, getJudgeExceptionBlock(ROLE_PIRATE_NAME), task, result);

        assertAssignedNoRole(USER_JACK_OID, task, result);
	}

	@Test
    public void test173SimpleExclusion3WithPolicyException() throws Exception {
		final String TEST_NAME = "test173SimpleExclusion3WithPolicyException";
        displayTestTitle(TEST_NAME);

        Task task = createTask(TEST_NAME);
        OperationResult result = task.getResult();

        assignRole(USER_JACK_OID, ROLE_PIRATE_OID, task, result);

        assignRole(USER_JACK_OID, ROLE_JUDGE_OID, null, getJudgeExceptionBlock(ROLE_PIRATE_NAME), task, result);

        PrismObject<UserType> userJackIn = getUser(USER_JACK_OID);
        assertAssignedRoles(userJackIn, ROLE_JUDGE_OID, ROLE_PIRATE_OID);

        unassignRole(USER_JACK_OID, ROLE_PIRATE_OID, task, result);

        unassignRole(USER_JACK_OID, ROLE_JUDGE_OID, null, getJudgeExceptionBlock(ROLE_PIRATE_NAME), task, result);

        assertAssignedNoRole(USER_JACK_OID, task, result);
	}

	@Test
    public void test174SimpleExclusion4WithPolicyException() throws Exception {
		final String TEST_NAME = "test174SimpleExclusion4WithPolicyException";
        displayTestTitle(TEST_NAME);

        Task task = createTask(TEST_NAME);
        OperationResult result = task.getResult();

        assignRole(USER_JACK_OID, ROLE_JUDGE_OID, null, getJudgeExceptionBlock(ROLE_PIRATE_NAME), task, result);

        assignRole(USER_JACK_OID, ROLE_PIRATE_OID, task, result);

        PrismObject<UserType> userJackIn = getUser(USER_JACK_OID);
        assertAssignedRoles(userJackIn, ROLE_JUDGE_OID, ROLE_PIRATE_OID);

        unassignRole(USER_JACK_OID, ROLE_PIRATE_OID, task, result);

        unassignRole(USER_JACK_OID, ROLE_JUDGE_OID, null, getJudgeExceptionBlock(ROLE_PIRATE_NAME), task, result);

        assertAssignedNoRole(USER_JACK_OID, task, result);
	}

	/**
	 * Add pirate role to judge. But include policy exception in the pirate assignment, so it
	 * should go OK. The assign thief (without exception). The exception in the pirate assignment
	 * should only apply to that assignment. The assignment of thief should fail.
	 */
	@Test
    public void test180JudgeExceptionalPirateAndThief() throws Exception {
		final String TEST_NAME = "test180JudgeExceptionalPirateAndThief";
        displayTestTitle(TEST_NAME);

        Task task = createTask(TEST_NAME);
        OperationResult result = task.getResult();

        assignRole(USER_JACK_OID, ROLE_JUDGE_OID, task, result);

        assignRole(USER_JACK_OID, ROLE_PIRATE_OID, null, getJudgeExceptionBlock(ROLE_PIRATE_NAME), task, result);

        PrismObject<UserType> userJackIn = getUser(USER_JACK_OID);
        assertAssignedRoles(userJackIn, ROLE_JUDGE_OID, ROLE_PIRATE_OID);

        try {
	        // This should die
	        assignRole(USER_JACK_OID, ROLE_THIEF_OID, task, result);

	        AssertJUnit.fail("Expected policy violation after adding thief role, but it went well");
        } catch (PolicyViolationException e) {
	        System.out.println("Got expected exception: " + e.getMessage());
        }

        // Cleanup

        unassignRole(USER_JACK_OID, ROLE_JUDGE_OID, task, result);
        unassignRole(USER_JACK_OID, ROLE_PIRATE_OID, null, getJudgeExceptionBlock(ROLE_PIRATE_NAME), task, result);

        assertAssignedNoRole(USER_JACK_OID, task, result);
	}

	private Consumer<AssignmentType> getJudgeExceptionBlock(String excludedRoleName) {
		return assignment -> {
			PolicyExceptionType policyException = new PolicyExceptionType();
			policyException.setRuleName(ROLE_JUDGE_POLICY_RULE_EXCLUSION_PREFIX + excludedRoleName);
			assignment.getPolicyException().add(policyException);
		};
	}

	@Test
	public void test190DifferentRelations() throws Exception {
		final String TEST_NAME = "test190DifferentRelations";
		displayTestTitle(TEST_NAME);

		Task task = createTask(TEST_NAME);
		OperationResult result = task.getResult();

		Collection<ItemDelta<?,?>> modifications = new ArrayList<>();
		modifications.add((createAssignmentModification(ROLE_JUDGE_OID, RoleType.COMPLEX_TYPE, SchemaConstants.ORG_APPROVER, null, null, true)));
		modifications.add((createAssignmentModification(ROLE_PIRATE_OID, RoleType.COMPLEX_TYPE, null, null, null, true)));
		ObjectDelta<UserType> userDelta = prismContext.deltaFactory().object()
				.createModifyDelta(USER_JACK_OID, modifications, UserType.class);

		try {
			modelService.executeChanges(MiscSchemaUtil.createCollection(userDelta), null, task, result);
		} finally {
			unassignRole(USER_JACK_OID, ROLE_JUDGE_OID, SchemaConstants.ORG_APPROVER, task, result);
			unassignRole(USER_JACK_OID, ROLE_PIRATE_OID, task, result);
		}

		assertAssignedNoRole(USER_JACK_OID, task, result);
	}

	@Test
	public void test191DifferentRelationsDeprecatedCase1() throws Exception {
		final String TEST_NAME = "test191DifferentRelationsCase1";
		displayTestTitle(TEST_NAME);

		Task task = createTask(TEST_NAME);
		OperationResult result = task.getResult();

		Collection<ItemDelta<?,?>> modifications = new ArrayList<>();
		modifications.add((createAssignmentModification(ROLE_JUDGE_DEPRECATED_OID, RoleType.COMPLEX_TYPE, SchemaConstants.ORG_APPROVER, null, null, true)));
		modifications.add((createAssignmentModification(ROLE_PIRATE_OID, RoleType.COMPLEX_TYPE, null, null, null, true)));
		ObjectDelta<UserType> userDelta = prismContext.deltaFactory().object()
				.createModifyDelta(USER_JACK_OID, modifications, UserType.class);

		try {
			modelService.executeChanges(MiscSchemaUtil.createCollection(userDelta), null, task, result);
		} finally {
			unassignRole(USER_JACK_OID, ROLE_JUDGE_DEPRECATED_OID, SchemaConstants.ORG_APPROVER, task, result);
			unassignRole(USER_JACK_OID, ROLE_PIRATE_OID, task, result);
		}

		assertAssignedNoRole(USER_JACK_OID, task, result);
	}

	@Test
	public void test192DifferentRelationsDeprecatedCase2() throws Exception {
		final String TEST_NAME = "test191DifferentRelationsCase1";
		displayTestTitle(TEST_NAME);

		Task task = createTask(TEST_NAME);
		OperationResult result = task.getResult();

		Collection<ItemDelta<?,?>> modifications = new ArrayList<>();
		modifications.add((createAssignmentModification(ROLE_JUDGE_DEPRECATED_OID, RoleType.COMPLEX_TYPE, null, null, null, true)));
		modifications.add((createAssignmentModification(ROLE_PIRATE_OID, RoleType.COMPLEX_TYPE, SchemaConstants.ORG_APPROVER, null, null, true)));
		ObjectDelta<UserType> userDelta = prismContext.deltaFactory().object()
				.createModifyDelta(USER_JACK_OID, modifications, UserType.class);

		try {
			modelService.executeChanges(MiscSchemaUtil.createCollection(userDelta), null, task, result);
		} finally {
			unassignRole(USER_JACK_OID, ROLE_JUDGE_DEPRECATED_OID, task, result);
			unassignRole(USER_JACK_OID, ROLE_PIRATE_OID, SchemaConstants.ORG_APPROVER, task, result);
		}

		assertAssignedNoRole(USER_JACK_OID, task, result);
	}

	@Test
	public void test193BothRelationsApprover() throws Exception {
		final String TEST_NAME = "test193BothRelationsApprover";
		displayTestTitle(TEST_NAME);

		Task task = createTask(TEST_NAME);
		OperationResult result = task.getResult();

		Collection<ItemDelta<?,?>> modifications = new ArrayList<>();
		modifications.add((createAssignmentModification(ROLE_JUDGE_OID, RoleType.COMPLEX_TYPE, SchemaConstants.ORG_APPROVER, null, null, true)));
		modifications.add((createAssignmentModification(ROLE_PIRATE_OID, RoleType.COMPLEX_TYPE, SchemaConstants.ORG_APPROVER, null, null, true)));
		ObjectDelta<UserType> userDelta = prismContext.deltaFactory().object()
				.createModifyDelta(USER_JACK_OID, modifications, UserType.class);

		try {
			modelService.executeChanges(MiscSchemaUtil.createCollection(userDelta), null, task, result);
		} finally {
			unassignRole(USER_JACK_OID, ROLE_JUDGE_OID, SchemaConstants.ORG_APPROVER, task, result);
			unassignRole(USER_JACK_OID, ROLE_PIRATE_OID, SchemaConstants.ORG_APPROVER, task, result);
		}

		assertAssignedNoRole(USER_JACK_OID, task, result);
	}

	@Test
	public void test194MemberAndManager() throws Exception {
		final String TEST_NAME = "test194MemberAndManager";
		displayTestTitle(TEST_NAME);

		Task task = createTask(TEST_NAME);
		OperationResult result = task.getResult();

		Collection<ItemDelta<?,?>> modifications = new ArrayList<>();
		modifications.add((createAssignmentModification(ROLE_JUDGE_OID, RoleType.COMPLEX_TYPE, SchemaConstants.ORG_MANAGER, null, null, true)));
		modifications.add((createAssignmentModification(ROLE_PIRATE_OID, RoleType.COMPLEX_TYPE, SchemaConstants.ORG_DEFAULT, null, null, true)));
		ObjectDelta<UserType> userDelta = prismContext.deltaFactory().object()
				.createModifyDelta(USER_JACK_OID, modifications, UserType.class);

		try {
			modelService.executeChanges(MiscSchemaUtil.createCollection(userDelta), null, task, result);

			AssertJUnit.fail("Expected policy violation, but it went well");
		} catch (PolicyViolationException e) {
			System.out.println("Got expected exception: " + e.getMessage());
		} finally {
			unassignRole(USER_JACK_OID, ROLE_JUDGE_OID, SchemaConstants.ORG_MANAGER, task, result);
			unassignRole(USER_JACK_OID, ROLE_PIRATE_OID, SchemaConstants.ORG_DEFAULT, task, result);
		}

		assertAssignedNoRole(USER_JACK_OID, task, result);
	}

	/**
	 * MID-3685
	 */
	@Test
    public void test200GuybrushAssignRoleGold() throws Exception {
		final String TEST_NAME = "test200GuybrushAssignRoleGold";
        displayTestTitle(TEST_NAME);

        // GIVEN
        Task task = createTask(TEST_NAME);
        OperationResult result = task.getResult();

        // WHEN
        TestUtil.displayWhen(TEST_NAME);
        assignRole(USER_GUYBRUSH_OID, ROLE_PRIZE_GOLD_OID, task, result);

        // THEN
        TestUtil.displayThen(TEST_NAME);
        result.computeStatus();
        TestUtil.assertSuccess(result);

        PrismObject<UserType> userAfter = getUser(USER_GUYBRUSH_OID);
        display("User after", userAfter);
        assertAssignedRole(userAfter, ROLE_PRIZE_GOLD_OID);
        assertNotAssignedRole(userAfter, ROLE_PRIZE_SILVER_OID);
        assertNotAssignedRole(userAfter, ROLE_PRIZE_BRONZE_OID);

        assertDummyAccount(null, ACCOUNT_GUYBRUSH_DUMMY_USERNAME);
        assertDummyAccountAttribute(null, ACCOUNT_GUYBRUSH_DUMMY_USERNAME,
        		DummyResourceContoller.DUMMY_ACCOUNT_ATTRIBUTE_SHIP_NAME, ROLE_PRIZE_GOLD_SHIP);
        assertDummyAccountAttribute(null, ACCOUNT_GUYBRUSH_DUMMY_USERNAME,
        		DummyResourceContoller.DUMMY_ACCOUNT_ATTRIBUTE_DRINK_NAME, RESOURCE_DUMMY_DRINK);
	}

	/**
	 * MID-3685
	 */
	@Test
    public void test202GuybrushAssignRoleSilver() throws Exception {
		final String TEST_NAME = "test202GuybrushAssignRoleSilver";
        displayTestTitle(TEST_NAME);

        // GIVEN
        Task task = createTask(TEST_NAME);
        OperationResult result = task.getResult();

        // WHEN
        TestUtil.displayWhen(TEST_NAME);
        assignRole(USER_GUYBRUSH_OID, ROLE_PRIZE_SILVER_OID, task, result);

        // THEN
        TestUtil.displayThen(TEST_NAME);
        result.computeStatus();
        TestUtil.assertSuccess(result);

        PrismObject<UserType> userAfter = getUser(USER_GUYBRUSH_OID);
        display("User after", userAfter);
        assertNotAssignedRole(userAfter, ROLE_PRIZE_GOLD_OID);
        assertAssignedRole(userAfter, ROLE_PRIZE_SILVER_OID);
        assertNotAssignedRole(userAfter, ROLE_PRIZE_BRONZE_OID);

        assertDummyAccount(null, ACCOUNT_GUYBRUSH_DUMMY_USERNAME);
        assertDummyAccountAttribute(null, ACCOUNT_GUYBRUSH_DUMMY_USERNAME,
        		DummyResourceContoller.DUMMY_ACCOUNT_ATTRIBUTE_SHIP_NAME, ROLE_PRIZE_SILVER_SHIP);
        assertDummyAccountAttribute(null, ACCOUNT_GUYBRUSH_DUMMY_USERNAME,
        		DummyResourceContoller.DUMMY_ACCOUNT_ATTRIBUTE_DRINK_NAME, RESOURCE_DUMMY_DRINK);
	}

	/**
	 * Mix in ordinary role to check for interferences.
	 * MID-3685
	 */
	@Test
    public void test204GuybrushAssignRoleSailor() throws Exception {
		final String TEST_NAME = "test204GuybrushAssignRoleSailor";
        displayTestTitle(TEST_NAME);

        // GIVEN
        Task task = createTask(TEST_NAME);
        OperationResult result = task.getResult();

        // WHEN
        TestUtil.displayWhen(TEST_NAME);
        assignRole(USER_GUYBRUSH_OID, ROLE_SAILOR_OID, task, result);

        // THEN
        TestUtil.displayThen(TEST_NAME);
        result.computeStatus();
        TestUtil.assertSuccess(result);

        PrismObject<UserType> userAfter = getUser(USER_GUYBRUSH_OID);
        display("User after", userAfter);
        assertNotAssignedRole(userAfter, ROLE_PRIZE_GOLD_OID);
        assertAssignedRole(userAfter, ROLE_PRIZE_SILVER_OID);
        assertNotAssignedRole(userAfter, ROLE_PRIZE_BRONZE_OID);
        assertAssignedRole(userAfter, ROLE_SAILOR_OID);

        assertDummyAccount(null, ACCOUNT_GUYBRUSH_DUMMY_USERNAME);
        assertDummyAccountAttribute(null, ACCOUNT_GUYBRUSH_DUMMY_USERNAME,
        		DummyResourceContoller.DUMMY_ACCOUNT_ATTRIBUTE_SHIP_NAME, ROLE_PRIZE_SILVER_SHIP);
        assertDummyAccountAttribute(null, ACCOUNT_GUYBRUSH_DUMMY_USERNAME,
        		DummyResourceContoller.DUMMY_ACCOUNT_ATTRIBUTE_DRINK_NAME, RESOURCE_DUMMY_DRINK, ROLE_SAILOR_DRINK);
	}


	/**
	 * MID-3685
	 */
	@Test
    public void test206GuybrushAssignRoleBronze() throws Exception {
		final String TEST_NAME = "test206GuybrushAssignRoleBronze";
        displayTestTitle(TEST_NAME);

        // GIVEN
        Task task = createTask(TEST_NAME);
        OperationResult result = task.getResult();

        // WHEN
        TestUtil.displayWhen(TEST_NAME);
        assignRole(USER_GUYBRUSH_OID, ROLE_PRIZE_BRONZE_OID, task, result);

        // THEN
        TestUtil.displayThen(TEST_NAME);
        result.computeStatus();
        TestUtil.assertSuccess(result);

        PrismObject<UserType> userAfter = getUser(USER_GUYBRUSH_OID);
        display("User after", userAfter);
        assertNotAssignedRole(userAfter, ROLE_PRIZE_GOLD_OID);
        assertNotAssignedRole(userAfter, ROLE_PRIZE_SILVER_OID);
        assertAssignedRole(userAfter, ROLE_PRIZE_BRONZE_OID);
        assertAssignedRole(userAfter, ROLE_SAILOR_OID);

        assertDummyAccount(null, ACCOUNT_GUYBRUSH_DUMMY_USERNAME);
        assertDummyAccountAttribute(null, ACCOUNT_GUYBRUSH_DUMMY_USERNAME,
        		DummyResourceContoller.DUMMY_ACCOUNT_ATTRIBUTE_SHIP_NAME, ROLE_PRIZE_BRONZE_SHIP);
        assertDummyAccountAttribute(null, ACCOUNT_GUYBRUSH_DUMMY_USERNAME,
        		DummyResourceContoller.DUMMY_ACCOUNT_ATTRIBUTE_DRINK_NAME, RESOURCE_DUMMY_DRINK, ROLE_SAILOR_DRINK);
	}

	/**
	 * MID-3685
	 */
	@Test
    public void test208GuybrushUnassignRoleBronze() throws Exception {
		final String TEST_NAME = "test208GuybrushUnassignRoleSilver";
        displayTestTitle(TEST_NAME);

        // GIVEN
        Task task = createTask(TEST_NAME);
        OperationResult result = task.getResult();

        // WHEN
        TestUtil.displayWhen(TEST_NAME);
        unassignRole(USER_GUYBRUSH_OID, ROLE_PRIZE_BRONZE_OID, task, result);

        // THEN
        TestUtil.displayThen(TEST_NAME);
        result.computeStatus();
        TestUtil.assertSuccess(result);

        PrismObject<UserType> userAfter = getUser(USER_GUYBRUSH_OID);
        display("User after", userAfter);
        assertNotAssignedRole(userAfter, ROLE_PRIZE_GOLD_OID);
        assertNotAssignedRole(userAfter, ROLE_PRIZE_SILVER_OID);
        assertNotAssignedRole(userAfter, ROLE_PRIZE_BRONZE_OID);
        assertAssignedRole(userAfter, ROLE_SAILOR_OID);

        assertDummyAccount(null, ACCOUNT_GUYBRUSH_DUMMY_USERNAME);
        assertDummyAccountAttribute(null, ACCOUNT_GUYBRUSH_DUMMY_USERNAME,
        		DummyResourceContoller.DUMMY_ACCOUNT_ATTRIBUTE_DRINK_NAME, RESOURCE_DUMMY_DRINK, ROLE_SAILOR_DRINK);
	}

	/**
	 * MID-3685
	 */
	@Test
    public void test209GuybrushUnassignRoleSailor() throws Exception {
		final String TEST_NAME = "test209GuybrushUnassignRoleSailor";
        displayTestTitle(TEST_NAME);

        // GIVEN
        Task task = createTask(TEST_NAME);
        OperationResult result = task.getResult();

        // WHEN
        TestUtil.displayWhen(TEST_NAME);
        unassignRole(USER_GUYBRUSH_OID, ROLE_SAILOR_OID, task, result);

        // THEN
        TestUtil.displayThen(TEST_NAME);
        result.computeStatus();
        TestUtil.assertSuccess(result);

        PrismObject<UserType> userAfter = getUser(USER_GUYBRUSH_OID);
        display("User after", userAfter);
        assertAssignedNoRole(userAfter);

        assertNoDummyAccount(ACCOUNT_GUYBRUSH_DUMMY_USERNAME);
	}

	/**
	 * There's an inherent conflict on resource attribute preventing the two roles to be assigned at once.
	 * Original enforcement hook reacted too late so the "Attempt to replace 2 values to a single-valued item" comes first.
	 *
	 * MID-4797
	 */
	@Test
	public void test209aGuybrushAssignRoleGoldAndSilverEnforced() throws Exception {
		final String TEST_NAME = "test209aGuybrushAssignRoleGoldAndSilverEnforced";
		displayTestTitle(TEST_NAME);

		// GIVEN
		Task task = createTask(TEST_NAME);
		OperationResult result = task.getResult();

		// WHEN
		TestUtil.displayWhen(TEST_NAME);
		ObjectDelta<UserType> delta = prismContext.deltaFor(UserType.class)
				.item(UserType.F_ASSIGNMENT).add(
						ObjectTypeUtil.createAssignmentTo(ROLE_PRIZE_GOLD_ENFORCED_OID, ObjectTypes.ROLE, prismContext),
						ObjectTypeUtil.createAssignmentTo(ROLE_PRIZE_SILVER_ENFORCED_OID, ObjectTypes.ROLE, prismContext))
				.asObjectDeltaCast(USER_GUYBRUSH_OID);

		try {
			executeChanges(delta, null, task, result);
			// THEN
			fail("unexpected success");
		} catch (PolicyViolationException e) {
			System.out.println("Got expected exception: " + e.getMessage());
			// order is not strictly defined; if this would lead to false failures, revisit the following assert
			assertMessage(e, "Violation of SoD policy: Role \"Prize: Gold (enforced)\" excludes role \"Prize: Silver (enforced)\", they cannot be assigned at the same time; Violation of SoD policy: Role \"Prize: Silver (enforced)\" excludes role \"Prize: Gold (enforced)\", they cannot be assigned at the same time");
			result.computeStatus();
			assertFailure(result);
		}
	}

	/**
	 * MID-4766
	 */
	@Test
	public void test209bGuybrushAssignRoleGoldAndSilver() throws Exception {
		final String TEST_NAME = "test209bGuybrushAssignRoleGoldAndSilver";
		displayTestTitle(TEST_NAME);

		// GIVEN
		Task task = createTask(TEST_NAME);
		OperationResult result = task.getResult();

		// WHEN
		TestUtil.displayWhen(TEST_NAME);
		ObjectDelta<UserType> delta = prismContext.deltaFor(UserType.class)
				.item(UserType.F_ASSIGNMENT).add(
						ObjectTypeUtil.createAssignmentTo(ROLE_PRIZE_GOLD_OID, ObjectTypes.ROLE, prismContext),
						ObjectTypeUtil.createAssignmentTo(ROLE_PRIZE_SILVER_OID, ObjectTypes.ROLE, prismContext))
				.asObjectDeltaCast(USER_GUYBRUSH_OID);

		try {
			executeChanges(delta, null, task, result);
			// THEN
			fail("unexpected success");
		} catch (PolicyViolationException e) {
			System.out.println("Got expected exception: " + e.getMessage());
			// fragile, depends on the evaluation internals ... if it would cause problems please adapt the following assert
			assertMessage(e, "Mutually-pruned roles cannot be assigned at the same time: role \"Prize: Silver\" and role \"Prize: Gold\"; Mutually-pruned roles cannot be assigned at the same time: role \"Prize: Gold\" and role \"Prize: Silver\"");
			result.computeStatus();
			assertFailure(result);
		}
	}

	/**
	 * MID-3685
	 */
	@Test
    public void test210GuybrushAssignRoleRed() throws Exception {
		final String TEST_NAME = "test210GuybrushAssignRoleRed";
        displayTestTitle(TEST_NAME);

        // GIVEN
        Task task = createTask(TEST_NAME);
        OperationResult result = task.getResult();

        // WHEN
        displayWhen(TEST_NAME);
        assignRole(USER_GUYBRUSH_OID, ROLE_COLOR_RED_OID, task, result);

        // THEN
        displayThen(TEST_NAME);
        assertSuccess(result);

        PrismObject<UserType> userAfter = getUser(USER_GUYBRUSH_OID);
        display("User after", userAfter);
        assertAssignedRole(userAfter, ROLE_COLOR_RED_OID);
        assertNotAssignedRole(userAfter, ROLE_COLOR_GREEN_OID);
        assertNotAssignedRole(userAfter, ROLE_COLOR_BLUE_OID);
        assertNotAssignedRole(userAfter, ROLE_COLOR_NONE_OID);

        assertDummyAccount(null, ACCOUNT_GUYBRUSH_DUMMY_USERNAME);
        assertDummyAccountAttribute(null, ACCOUNT_GUYBRUSH_DUMMY_USERNAME,
        		DummyResourceContoller.DUMMY_ACCOUNT_ATTRIBUTE_SHIP_NAME, ROLE_COLOR_RED_SHIP);
	}

	/**
	 * MID-3685
	 */
	@Test
    public void test212GuybrushAssignRoleGreen() throws Exception {
		final String TEST_NAME = "test212GuybrushAssignRoleGreen";
        displayTestTitle(TEST_NAME);

        // GIVEN
        Task task = createTask(TEST_NAME);
        OperationResult result = task.getResult();

        // WHEN
        TestUtil.displayWhen(TEST_NAME);
        assignRole(USER_GUYBRUSH_OID, ROLE_COLOR_GREEN_OID, task, result);

        // THEN
        TestUtil.displayThen(TEST_NAME);
        result.computeStatus();
        TestUtil.assertSuccess(result);

        PrismObject<UserType> userAfter = getUser(USER_GUYBRUSH_OID);
        display("User after", userAfter);
        assertNotAssignedRole(userAfter, ROLE_COLOR_RED_OID);
        assertAssignedRole(userAfter, ROLE_COLOR_GREEN_OID);
        assertNotAssignedRole(userAfter, ROLE_COLOR_BLUE_OID);
        assertNotAssignedRole(userAfter, ROLE_COLOR_NONE_OID);

        assertDummyAccount(null, ACCOUNT_GUYBRUSH_DUMMY_USERNAME);
        assertDummyAccountAttribute(null, ACCOUNT_GUYBRUSH_DUMMY_USERNAME,
        		DummyResourceContoller.DUMMY_ACCOUNT_ATTRIBUTE_SHIP_NAME, ROLE_COLOR_GREEN_SHIP);
	}

	/**
	 * MID-3685
	 */
	@Test
    public void test214GuybrushAssignRoleColorNone() throws Exception {
		final String TEST_NAME = "test214GuybrushAssignRoleColorNone";
        displayTestTitle(TEST_NAME);

        // GIVEN
        Task task = createTask(TEST_NAME);
        OperationResult result = task.getResult();

        // WHEN
        TestUtil.displayWhen(TEST_NAME);
        assignRole(USER_GUYBRUSH_OID, ROLE_COLOR_NONE_OID, task, result);

        // THEN
        TestUtil.displayThen(TEST_NAME);
        result.computeStatus();
        TestUtil.assertSuccess(result);

        PrismObject<UserType> userAfter = getUser(USER_GUYBRUSH_OID);
        display("User after", userAfter);
        assertNotAssignedRole(userAfter, ROLE_COLOR_RED_OID);
        assertNotAssignedRole(userAfter, ROLE_COLOR_GREEN_OID);
        assertNotAssignedRole(userAfter, ROLE_COLOR_BLUE_OID);
        assertAssignedRole(userAfter, ROLE_COLOR_NONE_OID);

        assertNoDummyAccount(ACCOUNT_GUYBRUSH_DUMMY_USERNAME);
	}

	/**
	 * MID-3685
	 */
	@Test
    public void test216GuybrushAssignRoleBlue() throws Exception {
		final String TEST_NAME = "test216GuybrushAssignRoleBlue";
        displayTestTitle(TEST_NAME);

        // GIVEN
        Task task = createTask(TEST_NAME);
        OperationResult result = task.getResult();

        // WHEN
        TestUtil.displayWhen(TEST_NAME);
        assignRole(USER_GUYBRUSH_OID, ROLE_COLOR_BLUE_OID, task, result);

        // THEN
        TestUtil.displayThen(TEST_NAME);
        result.computeStatus();
        TestUtil.assertSuccess(result);

        PrismObject<UserType> userAfter = getUser(USER_GUYBRUSH_OID);
        display("User after", userAfter);
        assertNotAssignedRole(userAfter, ROLE_COLOR_RED_OID);
        assertNotAssignedRole(userAfter, ROLE_COLOR_GREEN_OID);
        assertAssignedRole(userAfter, ROLE_COLOR_BLUE_OID);
        assertNotAssignedRole(userAfter, ROLE_COLOR_NONE_OID);

        assertDummyAccount(null, ACCOUNT_GUYBRUSH_DUMMY_USERNAME);
        assertDummyAccountAttribute(null, ACCOUNT_GUYBRUSH_DUMMY_USERNAME,
        		DummyResourceContoller.DUMMY_ACCOUNT_ATTRIBUTE_SHIP_NAME, ROLE_COLOR_BLUE_SHIP);
	}

	@Test
    public void test219GuybrushUnassignRoleBlue() throws Exception {
		final String TEST_NAME = "test219GuybrushUnassignRoleBlue";
        displayTestTitle(TEST_NAME);

        // GIVEN
        Task task = createTask(TEST_NAME);
        OperationResult result = task.getResult();

        // WHEN
        displayWhen(TEST_NAME);
        unassignRole(USER_GUYBRUSH_OID, ROLE_COLOR_BLUE_OID, task, result);

        // THEN
        displayThen(TEST_NAME);
        assertSuccess(result);

        PrismObject<UserType> userAfter = getUser(USER_GUYBRUSH_OID);
        display("User after", userAfter);
        assertNotAssignedRole(userAfter, ROLE_COLOR_RED_OID);
        assertNotAssignedRole(userAfter, ROLE_COLOR_GREEN_OID);
        assertNotAssignedRole(userAfter, ROLE_COLOR_BLUE_OID);
        assertNotAssignedRole(userAfter, ROLE_COLOR_NONE_OID);
        assertAssignments(userAfter, 0);

        assertNoDummyAccount(null, ACCOUNT_GUYBRUSH_DUMMY_USERNAME);
	}
	
	@Test
    public void test220GuybrushAssignRoleBlue() throws Exception {
		final String TEST_NAME = "test220GuybrushAssignRoleBlue";
        displayTestTitle(TEST_NAME);

        // GIVEN
        Task task = createTask(TEST_NAME);
        OperationResult result = task.getResult();

        // WHEN
        displayWhen(TEST_NAME);
        assignRole(USER_GUYBRUSH_OID, ROLE_COLOR_BLUE_OID, task, result);

        // THEN
        displayThen(TEST_NAME);
        assertSuccess(result);

        PrismObject<UserType> userAfter = getUser(USER_GUYBRUSH_OID);
        display("User after", userAfter);
        assertAssignedRole(userAfter, ROLE_COLOR_BLUE_OID);
        assertNotAssignedRole(userAfter, ROLE_COLOR_GREEN_OID);
        assertNotAssignedRole(userAfter, ROLE_COLOR_RED_OID);
        assertNotAssignedRole(userAfter, ROLE_COLOR_NONE_OID);
        assertLinks(userAfter, 1);

        assertDummyAccount(null, ACCOUNT_GUYBRUSH_DUMMY_USERNAME);
        assertDummyAccountAttribute(null, ACCOUNT_GUYBRUSH_DUMMY_USERNAME,
        		DummyResourceContoller.DUMMY_ACCOUNT_ATTRIBUTE_SHIP_NAME, ROLE_COLOR_BLUE_SHIP);
	}
	
	/**
	 * Testing that we have correct test setup.
	 */
	@Test
    public void test221GuybrushDestroyAndRecompute() throws Exception {
		final String TEST_NAME = "test221GuybrushDestroyAndRecompute";
        displayTestTitle(TEST_NAME);

        // GIVEN
        Task task = createTask(TEST_NAME);
        OperationResult result = task.getResult();
        
        PrismObject<UserType> userBefore = getUser(USER_GUYBRUSH_OID);
        String shadowBeforeOid = getSingleLinkOid(userBefore);
        removeLinks(userBefore);
        deleteObjectRepo(ShadowType.class, shadowBeforeOid);
        userBefore = getUser(USER_GUYBRUSH_OID);
        display("User before", userBefore);
        
        // WHEN
        displayWhen(TEST_NAME);
        recomputeUser(USER_GUYBRUSH_OID, task, result);

        // THEN
        displayThen(TEST_NAME);
        assertSuccess(result, 2);

        PrismObject<UserType> userAfter = getUser(USER_GUYBRUSH_OID);
        display("User after", userAfter);
        assertAssignedRole(userAfter, ROLE_COLOR_BLUE_OID);
        assertNotAssignedRole(userAfter, ROLE_COLOR_GREEN_OID);
        assertNotAssignedRole(userAfter, ROLE_COLOR_RED_OID);
        assertNotAssignedRole(userAfter, ROLE_COLOR_NONE_OID);

        assertDummyAccount(null, ACCOUNT_GUYBRUSH_DUMMY_USERNAME);
        assertDummyAccountAttribute(null, ACCOUNT_GUYBRUSH_DUMMY_USERNAME,
        		DummyResourceContoller.DUMMY_ACCOUNT_ATTRIBUTE_SHIP_NAME, ROLE_COLOR_BLUE_SHIP);
	}
	
	/**
	 * Exclusive roles with pruning. Account not assigned, but it exists
	 * on resource. Error handled by consistency.
	 * MID-4463
	 */
	@Test
    public void test222GuybrushDestroyAndAssignRoleRed() throws Exception {
		final String TEST_NAME = "test222GuybrushDestroyAndAssignRoleRed";
        displayTestTitle(TEST_NAME);

        // GIVEN
        Task task = createTask(TEST_NAME);
        OperationResult result = task.getResult();
        
        PrismObject<UserType> userBefore = getUser(USER_GUYBRUSH_OID);
        String shadowBeforeOid = getSingleLinkOid(userBefore);
        removeLinks(userBefore);
        deleteObjectRepo(ShadowType.class, shadowBeforeOid);
        userBefore = getUser(USER_GUYBRUSH_OID);
        display("User before", userBefore);
        
        // WHEN
        displayWhen(TEST_NAME);
        assignRole(USER_GUYBRUSH_OID, ROLE_COLOR_RED_OID, task, result);

        // THEN
        displayThen(TEST_NAME);
        assertSuccess(result, 2);

        PrismObject<UserType> userAfter = getUser(USER_GUYBRUSH_OID);
        display("User after", userAfter);
        assertAssignedRole(userAfter, ROLE_COLOR_RED_OID);
        assertNotAssignedRole(userAfter, ROLE_COLOR_GREEN_OID);
        assertNotAssignedRole(userAfter, ROLE_COLOR_BLUE_OID);
        assertNotAssignedRole(userAfter, ROLE_COLOR_NONE_OID);
        
        String shadowAfterOid = getSingleLinkOid(userAfter);

        assertDummyAccount(null, ACCOUNT_GUYBRUSH_DUMMY_USERNAME);
        assertDummyAccountAttribute(null, ACCOUNT_GUYBRUSH_DUMMY_USERNAME,
        		DummyResourceContoller.DUMMY_ACCOUNT_ATTRIBUTE_SHIP_NAME, ROLE_COLOR_RED_SHIP);
	}
	
	@Test
    public void test229GuybrushUnassignRoleRed() throws Exception {
		final String TEST_NAME = "test229GuybrushUnassignRoleRed";
        displayTestTitle(TEST_NAME);

        // GIVEN
        Task task = createTask(TEST_NAME);
        OperationResult result = task.getResult();
        
        // WHEN
        displayWhen(TEST_NAME);
        unassignRole(USER_GUYBRUSH_OID, ROLE_COLOR_RED_OID, task, result);

        // THEN
        displayThen(TEST_NAME);
        assertSuccess(result);

        PrismObject<UserType> userAfter = getUser(USER_GUYBRUSH_OID);
        display("User after", userAfter);
        assertNotAssignedRole(userAfter, ROLE_COLOR_RED_OID);
        assertNotAssignedRole(userAfter, ROLE_COLOR_GREEN_OID);
        assertNotAssignedRole(userAfter, ROLE_COLOR_BLUE_OID);
        assertNotAssignedRole(userAfter, ROLE_COLOR_NONE_OID);
        assertLinks(userAfter, 0);

        assertNoDummyAccount(null, ACCOUNT_GUYBRUSH_DUMMY_USERNAME);
	}

	/**
	 * MID-4766
	 */
	@Test
	public void test230GuybrushAssignRoleRedAndBlueAndGreen() throws Exception {
		final String TEST_NAME = "test230GuybrushAssignRoleRedAndBlueAndGreen";
		displayTestTitle(TEST_NAME);

		// GIVEN
		Task task = createTask(TEST_NAME);
		OperationResult result = task.getResult();

		// WHEN
		TestUtil.displayWhen(TEST_NAME);
		ObjectDelta<UserType> delta = prismContext.deltaFor(UserType.class)
				.item(UserType.F_ASSIGNMENT).add(
						ObjectTypeUtil.createAssignmentTo(ROLE_COLOR_RED_OID, ObjectTypes.ROLE, prismContext),
						ObjectTypeUtil.createAssignmentTo(ROLE_COLOR_BLUE_OID, ObjectTypes.ROLE, prismContext),
						ObjectTypeUtil.createAssignmentTo(ROLE_COLOR_GREEN_OID, ObjectTypes.ROLE, prismContext))
				.asObjectDeltaCast(USER_GUYBRUSH_OID);

		try {
			executeChanges(delta, null, task, result);
			// THEN
			fail("unexpected success");
		} catch (PolicyViolationException e) {
			System.out.println("Got expected exception: " + e.getMessage());
			// fragile, depends on the evaluation internals ... if it would cause problems please adapt the following assert
			assertMessage(e, "Mutually-pruned roles cannot be assigned at the same time: role \"Color: Green\" and role \"Color: Red\"; Mutually-pruned roles cannot be assigned at the same time: role \"Color: Green\" and role \"Color: Blue\"; Mutually-pruned roles cannot be assigned at the same time: role \"Color: Red\" and role \"Color: Green\"; Mutually-pruned roles cannot be assigned at the same time: role \"Color: Red\" and role \"Color: Blue\"; Mutually-pruned roles cannot be assigned at the same time: role \"Color: Blue\" and role \"Color: Red\"; Mutually-pruned roles cannot be assigned at the same time: role \"Color: Blue\" and role \"Color: Green\"");
			result.computeStatus();
			assertFailure(result);
		}
	}

	/**
	 * MID-3694
	 */
	@Test
    public void test240GuybrushAssignRoleExecutiveOne() throws Exception {
		final String TEST_NAME = "test240GuybrushAssignRoleExecutiveOne";
        displayTestTitle(TEST_NAME);

        // GIVEN
        Task task = createTask(TEST_NAME);
        OperationResult result = task.getResult();

        // WHEN
        displayWhen(TEST_NAME);
        assignRole(USER_GUYBRUSH_OID, ROLE_EXECUTIVE_1_OID, task, result);

        // THEN
        displayThen(TEST_NAME);
        assertSuccess(result);

        PrismObject<UserType> userAfter = getUser(USER_GUYBRUSH_OID);
        display("User after", userAfter);
        assertAssignedRole(userAfter, ROLE_EXECUTIVE_1_OID);
	}

	/**
	 * MID-3694
	 */
	@Test
    public void test242GuybrushAssignRoleControllingOne() throws Exception {
		final String TEST_NAME = "test242GuybrushAssignRoleControllingOne";
        displayTestTitle(TEST_NAME);

        // GIVEN
        Task task = createTask(TEST_NAME);
        OperationResult result = task.getResult();

        // WHEN
        PrismObject<UserType> userAfter = assignRolePolicyFailure(TEST_NAME, USER_GUYBRUSH_OID, ROLE_CONTROLLING_1_OID, task, result);

    	// THEN
        assertAssignedRole(userAfter, ROLE_EXECUTIVE_1_OID);
	}

	/**
	 * MID-3694
	 */
	@Test
    public void test244GuybrushAssignRoleExecutiveTwo() throws Exception {
		final String TEST_NAME = "test244GuybrushAssignRoleExecutiveTwo";
        displayTestTitle(TEST_NAME);

        // GIVEN
        Task task = createTask(TEST_NAME);
        OperationResult result = task.getResult();

        // WHEN
        displayWhen(TEST_NAME);
        assignRole(USER_GUYBRUSH_OID, ROLE_EXECUTIVE_2_OID, task, result);

        // THEN
        displayThen(TEST_NAME);
        assertSuccess(result);

        PrismObject<UserType> userAfter = getUser(USER_GUYBRUSH_OID);
        display("User after", userAfter);
        assertAssignedRole(userAfter, ROLE_EXECUTIVE_1_OID);
        assertAssignedRole(userAfter, ROLE_EXECUTIVE_2_OID);
	}

	/**
	 * MID-3694
	 */
	@Test
    public void test245GuybrushAssignRoleControllingTwo() throws Exception {
		final String TEST_NAME = "test245GuybrushAssignRoleControllingTwo";
        displayTestTitle(TEST_NAME);

        // GIVEN
        Task task = createTask(TEST_NAME);
        OperationResult result = task.getResult();

        // WHEN
        PrismObject<UserType> userAfter = assignRolePolicyFailure(TEST_NAME, USER_GUYBRUSH_OID, ROLE_CONTROLLING_2_OID, task, result);

    	// THEN
        assertAssignedRole(userAfter, ROLE_EXECUTIVE_1_OID);
        assertAssignedRole(userAfter, ROLE_EXECUTIVE_2_OID);
        assertNotAssignedRole(userAfter, ROLE_CONTROLLING_1_OID);
	}

	/**
	 * MID-3694
	 */
	@Test
    public void test246GuybrushUnassignRoleExecutiveOne() throws Exception {
		final String TEST_NAME = "test246GuybrushUnassignRoleExecutiveOne";
        displayTestTitle(TEST_NAME);

        // GIVEN
        Task task = createTask(TEST_NAME);
        OperationResult result = task.getResult();

        // WHEN
        displayWhen(TEST_NAME);
        unassignRole(USER_GUYBRUSH_OID, ROLE_EXECUTIVE_1_OID, task, result);

        // THEN
        displayThen(TEST_NAME);
        assertSuccess(result);

        PrismObject<UserType> userAfter = getUser(USER_GUYBRUSH_OID);
        display("User after", userAfter);
        assertAssignedRole(userAfter, ROLE_EXECUTIVE_2_OID);
	}

	/**
	 * MID-3694
	 */
	@Test
    public void test247GuybrushAssignRoleControllingOne() throws Exception {
		final String TEST_NAME = "test247GuybrushAssignRoleControllingOne";
        displayTestTitle(TEST_NAME);

        // GIVEN
        Task task = createTask(TEST_NAME);
        OperationResult result = task.getResult();

        // WHEN
        PrismObject<UserType> userAfter = assignRolePolicyFailure(TEST_NAME, USER_GUYBRUSH_OID, ROLE_CONTROLLING_1_OID, task, result);

    	// THEN
        assertNotAssignedRole(userAfter, ROLE_EXECUTIVE_1_OID);
        assertAssignedRole(userAfter, ROLE_EXECUTIVE_2_OID);
        assertNotAssignedRole(userAfter, ROLE_CONTROLLING_2_OID);
	}

	/**
	 * MID-3694
	 */
	@Test
    public void test249GuybrushUnassignRoleExecutiveTwo() throws Exception {
		final String TEST_NAME = "test249GuybrushUnassignRoleExecutiveTwo";
        displayTestTitle(TEST_NAME);

        // GIVEN
        Task task = createTask(TEST_NAME);
        OperationResult result = task.getResult();

        // WHEN
        displayWhen(TEST_NAME);
        unassignRole(USER_GUYBRUSH_OID, ROLE_EXECUTIVE_2_OID, task, result);

        // THEN
        displayThen(TEST_NAME);
        assertSuccess(result);

        PrismObject<UserType> userAfter = getUser(USER_GUYBRUSH_OID);
        display("User after", userAfter);
        assertNotAssignedRole(userAfter, ROLE_EXECUTIVE_1_OID);
        assertNotAssignedRole(userAfter, ROLE_EXECUTIVE_2_OID);
        assertNotAssignedRole(userAfter, ROLE_CONTROLLING_1_OID);
        assertNotAssignedRole(userAfter, ROLE_CONTROLLING_2_OID);
	}

	/**
	 * MID-3694
	 */
	@Test
    public void test250GuybrushAssignRoleControllingOne() throws Exception {
		final String TEST_NAME = "test250GuybrushAssignRoleControllingOne";
        displayTestTitle(TEST_NAME);

        // GIVEN
        Task task = createTask(TEST_NAME);
        OperationResult result = task.getResult();

        // WHEN
        displayWhen(TEST_NAME);
        assignRole(USER_GUYBRUSH_OID, ROLE_CONTROLLING_1_OID, task, result);

        // THEN
        displayThen(TEST_NAME);
        assertSuccess(result);

        PrismObject<UserType> userAfter = getUser(USER_GUYBRUSH_OID);
        display("User after", userAfter);
        assertAssignedRole(userAfter, ROLE_CONTROLLING_1_OID);
	}

	/**
	 * MID-3694
	 */
	@Test
    public void test252GuybrushAssignRoleExecutiveOne() throws Exception {
		final String TEST_NAME = "test252GuybrushAssignRoleExecutiveOne";
        displayTestTitle(TEST_NAME);

        // GIVEN
        Task task = createTask(TEST_NAME);
        OperationResult result = task.getResult();

        // WHEN
        PrismObject<UserType> userAfter = assignRolePolicyFailure(TEST_NAME, USER_GUYBRUSH_OID, ROLE_EXECUTIVE_1_OID, task, result);

    	// THEN
        assertAssignedRole(userAfter, ROLE_CONTROLLING_1_OID);
	}

	/**
	 * MID-3694
	 */
	@Test
    public void test259GuybrushUnassignRoleControllingOne() throws Exception {
		final String TEST_NAME = "test259GuybrushUnassignRoleControllingOne";
        displayTestTitle(TEST_NAME);

        // GIVEN
        Task task = createTask(TEST_NAME);
        OperationResult result = task.getResult();

        // WHEN
        displayWhen(TEST_NAME);
        unassignRole(USER_GUYBRUSH_OID, ROLE_CONTROLLING_1_OID, task, result);

        // THEN
        displayThen(TEST_NAME);
        assertSuccess(result);

        PrismObject<UserType> userAfter = getUser(USER_GUYBRUSH_OID);
        display("User after", userAfter);
        assertNotAssignedRole(userAfter, ROLE_EXECUTIVE_1_OID);
        assertNotAssignedRole(userAfter, ROLE_EXECUTIVE_2_OID);
        assertNotAssignedRole(userAfter, ROLE_CONTROLLING_1_OID);
        assertNotAssignedRole(userAfter, ROLE_CONTROLLING_2_OID);
	}

	@Test
    public void test800ApplyGlobalPolicyRulesExclusion() throws Exception {
		final String TEST_NAME = "test800ApplyGlobalPolicyRulesExclusion";
        displayTestTitle(TEST_NAME);

        // GIVEN
        Task task = createTask(TEST_NAME);
        OperationResult result = task.getResult();

        // WHEN
        displayWhen(TEST_NAME);
        transplantGlobalPolicyRulesAdd(CONFIG_WITH_GLOBAL_RULES_EXCLUSION_FILE, task, result);

        // THEN
        displayThen(TEST_NAME);
        assertSuccess(result);

        List<GlobalPolicyRuleType> globalPolicyRules = getSystemConfiguration().getGlobalPolicyRule();
        display("Global policy rules", globalPolicyRules);
        assertEquals("Wrong number of global policy rules", NUMBER_OF_GLOBAL_POLICY_RULES + 1, globalPolicyRules.size());
	}

	@Test
    public void test810GuybrushAssignRoleCitizenSk() throws Exception {
		final String TEST_NAME = "test810GuybrushAssignRoleCitizenSk";
        displayTestTitle(TEST_NAME);

        // GIVEN
        Task task = createTask(TEST_NAME);
        OperationResult result = task.getResult();

        // WHEN
        displayWhen(TEST_NAME);
        assignRole(USER_GUYBRUSH_OID, ROLE_CITIZEN_SK_OID, task, result);

        // THEN
        displayThen(TEST_NAME);
        assertSuccess(result);

        PrismObject<UserType> userAfter = getUser(USER_GUYBRUSH_OID);
        display("User after", userAfter);
        assertAssignedRole(userAfter, ROLE_CITIZEN_SK_OID);
	}

	@Test
    public void test812GuybrushAssignRoleCitizenUs() throws Exception {
		final String TEST_NAME = "test812GuybrushAssignRoleCitizenUs";
        displayTestTitle(TEST_NAME);

        // GIVEN
        Task task = createTask(TEST_NAME);
        OperationResult result = task.getResult();

        // WHEN
        PrismObject<UserType> userAfter = assignRolePolicyFailure(TEST_NAME, USER_GUYBRUSH_OID, ROLE_CITIZEN_US_OID, task, result);

    	// THEN
        assertAssignedRole(userAfter, ROLE_CITIZEN_SK_OID);
	}

	/**
	 * Assign non-citizen role. This should go smoothly.
	 */
	@Test
    public void test814GuybrushAssignRoleEmpty() throws Exception {
		final String TEST_NAME = "test814GuybrushAssignRoleEmpty";
        displayTestTitle(TEST_NAME);

        // GIVEN
        Task task = createTask(TEST_NAME);
        OperationResult result = task.getResult();

        // WHEN
        displayWhen(TEST_NAME);
        assignRole(USER_GUYBRUSH_OID, ROLE_EMPTY_OID, task, result);

        // THEN
        displayThen(TEST_NAME);
        assertSuccess(result);

        PrismObject<UserType> userAfter = getUser(USER_GUYBRUSH_OID);
        display("User after", userAfter);
        assertAssignedRole(userAfter, ROLE_CITIZEN_SK_OID);
        assertAssignedRole(userAfter, ROLE_EMPTY_OID);
	}

	@Test
    public void test818GuybrushUnassignRoleCitizenSk() throws Exception {
		final String TEST_NAME = "test818GuybrushUnassignRoleCitizenSk";
        displayTestTitle(TEST_NAME);

        // GIVEN
        Task task = createTask(TEST_NAME);
        OperationResult result = task.getResult();

        // WHEN
        displayWhen(TEST_NAME);
        unassignRole(USER_GUYBRUSH_OID, ROLE_CITIZEN_SK_OID, task, result);

        // THEN
        displayThen(TEST_NAME);
        assertSuccess(result);

        PrismObject<UserType> userAfter = getUser(USER_GUYBRUSH_OID);
        display("User after", userAfter);
        assertNotAssignedRole(userAfter, ROLE_CITIZEN_SK_OID);
        assertNotAssignedRole(userAfter, ROLE_CITIZEN_US_OID);
        assertAssignedRole(userAfter, ROLE_EMPTY_OID);
        assertAssignments(userAfter, 1);
	}

	@Test
    public void test819GuybrushUnassignRoleEmpty() throws Exception {
		final String TEST_NAME = "test818GuybrushUnassignRoleCitizenSk";
        displayTestTitle(TEST_NAME);

        // GIVEN
        Task task = createTask(TEST_NAME);
        OperationResult result = task.getResult();

        // WHEN
        displayWhen(TEST_NAME);
        unassignRole(USER_GUYBRUSH_OID, ROLE_EMPTY_OID, task, result);

        // THEN
        displayThen(TEST_NAME);
        assertSuccess(result);

        PrismObject<UserType> userAfter = getUser(USER_GUYBRUSH_OID);
        display("User after", userAfter);
        assertAssignments(userAfter, 0);
	}

	/**
	 * Minister and Criminal are mutually exclusive. But there is not enforcement for
	 * this (yet).
	 */
	@Test
    public void test820GuybrushAssignRoleCriminal() throws Exception {
		final String TEST_NAME = "test820GuybrushAssignRoleCriminal";
        displayTestTitle(TEST_NAME);

        // GIVEN
        Task task = createTask(TEST_NAME);
        OperationResult result = task.getResult();

        // WHEN
        displayWhen(TEST_NAME);
        assignRole(USER_GUYBRUSH_OID, ROLE_CRIMINAL_OID, task, result);

        // THEN
        displayThen(TEST_NAME);
        assertSuccess(result);

        PrismObject<UserType> userAfter = getUser(USER_GUYBRUSH_OID);
        display("User after", userAfter);
        assertAssignedRole(userAfter, ROLE_CRIMINAL_OID);
	}

	/**
	 * Minister and Criminal are mutually exclusive. But there is not enforcement for
	 * this (yet). So the assignment should go smoothly. Policy situation should be indicated.
	 */
	@Test
    public void test822GuybrushAssignRoleMinister() throws Exception {
		final String TEST_NAME = "test822GuybrushAssignRoleMinister";
        displayTestTitle(TEST_NAME);

        // GIVEN
        Task task = createTask(TEST_NAME);
        OperationResult result = task.getResult();

        // WHEN
        displayWhen(TEST_NAME);
        assignRole(USER_GUYBRUSH_OID, ROLE_MINISTER_OID, task, result);

        // THEN
        displayThen(TEST_NAME);
        assertSuccess(result);

        PrismObject<UserType> userAfter = getUser(USER_GUYBRUSH_OID);
        display("User after", userAfter);
        assertAssignedRole(userAfter, ROLE_CRIMINAL_OID);
        assertAssignedRole(userAfter, ROLE_MINISTER_OID);

        //assertPolicySituation(userAfter, ROLE_MINISTER_OID, SchemaConstants.MODEL_POLICY_SITUATION_EXCLUSION_VIOLATION);
	}

	@Test
    public void test826GuybrushUnassignRoleCriminal() throws Exception {
		final String TEST_NAME = "test826GuybrushUnassignRoleCriminal";
        displayTestTitle(TEST_NAME);

        // GIVEN
        Task task = createTask(TEST_NAME);
        OperationResult result = task.getResult();

        // WHEN
        displayWhen(TEST_NAME);
        unassignRole(USER_GUYBRUSH_OID, ROLE_CRIMINAL_OID, task, result);

        // THEN
        displayThen(TEST_NAME);
        assertSuccess(result);

        PrismObject<UserType> userAfter = getUser(USER_GUYBRUSH_OID);
        display("User after", userAfter);
        assertNotAssignedRole(userAfter, ROLE_CRIMINAL_OID);
        assertAssignedRole(userAfter, ROLE_MINISTER_OID);
	}

	@Test
    public void test829GuybrushUnassignRoleMinister() throws Exception {
		final String TEST_NAME = "test829GuybrushUnassignRoleMinister";
        displayTestTitle(TEST_NAME);

        // GIVEN
        Task task = createTask(TEST_NAME);
        OperationResult result = task.getResult();

        // WHEN
        displayWhen(TEST_NAME);
        unassignRole(USER_GUYBRUSH_OID, ROLE_MINISTER_OID, task, result);

        // THEN
        displayThen(TEST_NAME);
        assertSuccess(result);

        PrismObject<UserType> userAfter = getUser(USER_GUYBRUSH_OID);
        display("User after", userAfter);
        assertNotAssignedRole(userAfter, ROLE_CRIMINAL_OID);
        assertNotAssignedRole(userAfter, ROLE_MINISTER_OID);
	}

	@Test
    public void test900ApplyGlobalPolicyRulesSoDApproval() throws Exception {
		final String TEST_NAME = "test900ApplyGlobalPolicyRulesSoDApproval";
        displayTestTitle(TEST_NAME);

        // GIVEN
        Task task = createTask(TEST_NAME);
        OperationResult result = task.getResult();

        // WHEN
        displayWhen(TEST_NAME);
        transplantGlobalPolicyRulesAdd(CONFIG_WITH_GLOBAL_RULES_SOD_APPROVAL_FILE, task, result);

        // THEN
        displayThen(TEST_NAME);
        assertSuccess(result);

        List<GlobalPolicyRuleType> globalPolicyRules = getSystemConfiguration().getGlobalPolicyRule();
        assertEquals("Wrong number of global policy rules", NUMBER_OF_GLOBAL_POLICY_RULES + 2, globalPolicyRules.size());
	}


	/**
	 * Minister and Criminal are mutually exclusive. There is not enforcement for
	 * this in the roles. But now there is a global policy rule that drives this through
	 * an approval. This should NOT be triggered yet, so this assignment should go smoothly.
	 */
	@Test
    public void test920GuybrushAssignRoleCriminal() throws Exception {
		final String TEST_NAME = "test920GuybrushAssignRoleCriminal";
        displayTestTitle(TEST_NAME);

        // GIVEN
        Task task = createTask(TEST_NAME);
        OperationResult result = task.getResult();

        // WHEN
        displayWhen(TEST_NAME);
        assignRole(USER_GUYBRUSH_OID, ROLE_CRIMINAL_OID, task, result);

        // THEN
        displayThen(TEST_NAME);
        assertSuccess(result);

        PrismObject<UserType> userAfter = getUser(USER_GUYBRUSH_OID);
        display("User after", userAfter);
        assertAssignedRole(userAfter, ROLE_CRIMINAL_OID);
        assertAssignments(userAfter, 1);
	}

	/**
	 * Minister and Criminal are mutually exclusive. There is not enforcement for
	 * this in the roles. But now there is a global policy rule that drives this through
	 * an approval. This should NOT be triggered yet. We do not want to deal with the
	 * complexities of approval in this test. So we are only interested in whether the
	 * preview works correctly - that it indicates that the rule was triggered.
	 */
	@Test
    public void test922GuybrushPreviewAssignRoleMinister() throws Exception {
		final String TEST_NAME = "test922GuybrushPreviewAssignRoleMinister";
        displayTestTitle(TEST_NAME);

        // GIVEN
        Task task = createTask(TEST_NAME);
        OperationResult result = task.getResult();

        ObjectDelta<UserType> delta = createAssignmentAssignmentHolderDelta(UserType.class, USER_GUYBRUSH_OID,
        		ROLE_MINISTER_OID, RoleType.COMPLEX_TYPE, null, null, null, true);

        // WHEN
        displayWhen(TEST_NAME);
        ModelContext<ObjectType> modelContext = modelInteractionService.previewChanges(MiscSchemaUtil.createCollection(delta), null, task, result);

        // THEN
        displayThen(TEST_NAME);
        assertSuccess(result);

        display("Preview context", modelContext);

        DeltaSetTriple<? extends EvaluatedAssignment<?>> evaluatedAssignmentTriple = modelContext.getEvaluatedAssignmentTriple();

        Collection<? extends EvaluatedAssignment> evaluatedAssignmentsZero = evaluatedAssignmentTriple.getZeroSet();
        assertEquals("Wrong number of evaluated assignments (zero)", 1, evaluatedAssignmentsZero.size());

        PrismAsserts.assertTripleNoMinus(evaluatedAssignmentTriple);

        Collection<? extends EvaluatedAssignment> evaluatedAssignmentsPlus = evaluatedAssignmentTriple.getPlusSet();
        assertEquals("Wrong number of evaluated assignments (plus)", 1, evaluatedAssignmentsPlus.size());
        EvaluatedAssignment<UserType> evaluatedAssignment = evaluatedAssignmentsPlus.iterator().next();
        DeltaSetTriple<? extends EvaluatedAssignmentTarget> rolesTriple = evaluatedAssignment.getRoles();
        PrismAsserts.assertTripleNoPlus(rolesTriple);
        PrismAsserts.assertTripleNoMinus(rolesTriple);
        Collection<? extends EvaluatedAssignmentTarget> evaluatedRoles = rolesTriple.getZeroSet();
        assertEquals("Wrong number of evaluated role", 1, evaluatedRoles.size());
        assertEvaluatedRole(evaluatedRoles, ROLE_MINISTER_OID);
        Collection<EvaluatedPolicyRule> allTargetsPolicyRules = evaluatedAssignment.getAllTargetsPolicyRules();
        display("Evaluated policy rules", allTargetsPolicyRules);
        assertEquals("Wrong number of evaluated policy rules", 2, allTargetsPolicyRules.size());
        EvaluatedPolicyRule evaluatedSodPolicyRule = getEvaluatedPolicyRule(allTargetsPolicyRules, GLOBAL_POLICY_RULE_SOD_APPROVAL_NAME);
        EvaluatedPolicyRuleTrigger<?> sodTrigger = getSinglePolicyRuleTrigger(evaluatedSodPolicyRule, evaluatedSodPolicyRule.getTriggers());
        display("Own trigger", sodTrigger);
        assertEvaluatedPolicyRuleTriggers(evaluatedSodPolicyRule, evaluatedSodPolicyRule.getAllTriggers(), 2);
        EvaluatedPolicyRuleTrigger situationTrigger = getEvaluatedPolicyRuleTrigger(evaluatedSodPolicyRule, evaluatedSodPolicyRule.getAllTriggers(), PolicyConstraintKindType.SITUATION);
        display("Situation trigger", situationTrigger);
        PolicyActionsType sodActions = evaluatedSodPolicyRule.getActions();
        display("Actions", sodActions);
        assertPolicyActionApproval(evaluatedSodPolicyRule);
	}

	private void assertPolicyActionApproval(EvaluatedPolicyRule evaluatedPolicyRule) {
		PolicyActionsType actions = evaluatedPolicyRule.getActions();
		assertNotNull("No policy actions in "+evaluatedPolicyRule, actions);
		assertFalse("No approval action in "+evaluatedPolicyRule, actions.getApproval().isEmpty());
	}

	private void assertEvaluatedPolicyRuleTriggers(EvaluatedPolicyRule evaluatedPolicyRule,
			Collection<EvaluatedPolicyRuleTrigger<?>> triggers, int expectedNumberOfTriggers) {
		assertEquals("Wrong number of triggers in evaluated policy rule "+evaluatedPolicyRule.getName(), expectedNumberOfTriggers, triggers.size());
	}

	private EvaluatedPolicyRuleTrigger<?> getSinglePolicyRuleTrigger(EvaluatedPolicyRule evaluatedPolicyRule, Collection<EvaluatedPolicyRuleTrigger<?>> triggers) {
		assertEvaluatedPolicyRuleTriggers(evaluatedPolicyRule, triggers, 1);
		return triggers.iterator().next();
	}

	private EvaluatedPolicyRuleTrigger getEvaluatedPolicyRuleTrigger(EvaluatedPolicyRule evaluatedPolicyRule,
			Collection<EvaluatedPolicyRuleTrigger<?>> triggers, PolicyConstraintKindType expectedConstraintType) {
		return triggers.stream().filter(trigger -> expectedConstraintType.equals(trigger.getConstraintKind())).findFirst().get();
	}


	private EvaluatedPolicyRule getEvaluatedPolicyRule(Collection<EvaluatedPolicyRule> evaluatedPolicyRules, String ruleName) {
		return evaluatedPolicyRules.stream().filter(rule -> ruleName.equals(rule.getName())).findFirst().get();
	}

	@Test
    public void test929GuybrushUnassignRoleCriminal() throws Exception {
		final String TEST_NAME = "test929GuybrushUnassignRoleCriminal";
        displayTestTitle(TEST_NAME);

        // GIVEN
        Task task = createTask(TEST_NAME);
        OperationResult result = task.getResult();

        // WHEN
        displayWhen(TEST_NAME);
        unassignRole(USER_GUYBRUSH_OID, ROLE_CRIMINAL_OID, task, result);

        // THEN
        displayThen(TEST_NAME);
        assertSuccess(result);

        PrismObject<UserType> userAfter = getUser(USER_GUYBRUSH_OID);
        display("User after", userAfter);
        assertNotAssignedRole(userAfter, ROLE_CRIMINAL_OID);
        assertNotAssignedRole(userAfter, ROLE_MINISTER_OID);
	}

	/**
	 * This does not work because of current optimizations regarding non-default relations:
	 * "2018-02-19 17:02:15,977 [main] DEBUG (c.e.m.model.impl.lens.AssignmentEvaluator): Skipping processing of assignment target 9577bd6c-dd5d-48e5-bbb1-554bba5db9be because
	 *     relation {http://midpoint.evolveum.com/xml/ns/public/common/org-3}approver is configured for recompute skip (mode=ZERO)"
	 *
	 * i.e. it works only when evaluateAllAssignmentRelationsOnRecompute option is set
	 */
	@Test(enabled = false)
	public void test950JackSelfExclusion() throws Exception {
		final String TEST_NAME = "test950JackSelfExclusion";
		displayTestTitle(TEST_NAME);

		Task task = createTask(TEST_NAME);
		OperationResult result = task.getResult();

		// This should go well
		assignRole(USER_JACK_OID, ROLE_SELF_EXCLUSION_OID, SchemaConstants.ORG_APPROVER, task, result);

		assertSuccess(result);

		try {
			// This should die
			ModelExecuteOptions options = new ModelExecuteOptions();
//			options.setEvaluateAllAssignmentRelationsOnRecompute(true);
			assignRole(USER_JACK_OID, ROLE_SELF_EXCLUSION_OID, SchemaConstants.ORG_OWNER, options, task, result);

			fail("Expected policy violation after adding second self-exclusion role, but it went well");
		} catch (PolicyViolationException e) {
			System.out.println("Got expected exception: " + e.getMessage());
			//assertMessage(e, "Violation of SoD policy: Role \"Judge\" excludes role \"Pirate\", they cannot be assigned at the same time");
			result.computeStatus();
			assertFailure(result);
		}

		unassignRole(USER_JACK_OID, ROLE_SELF_EXCLUSION_OID, SchemaConstants.ORG_APPROVER, task, result);

		assertAssignedNoRole(USER_JACK_OID, task, result);
	}

	@Test
	public void test952JackSelfExclusionManagerMember() throws Exception {
		final String TEST_NAME = "test952JackSelfExclusionManagerMember";
		displayTestTitle(TEST_NAME);

		Task task = createTask(TEST_NAME);
		OperationResult result = task.getResult();

		// This should go well
		assignRole(USER_JACK_OID, ROLE_SELF_EXCLUSION_MANAGER_MEMBER_OID, SchemaConstants.ORG_DEFAULT, task, result);

		assertSuccess(result);

		try {
			// This should die
			assignRole(USER_JACK_OID, ROLE_SELF_EXCLUSION_MANAGER_MEMBER_OID, SchemaConstants.ORG_MANAGER, task, result);

			fail("Expected policy violation after adding second self-exclusion role, but it went well");
		} catch (PolicyViolationException e) {
			System.out.println("Got expected exception: " + e.getMessage());
			//assertMessage(e, "Violation of SoD policy: Role \"Judge\" excludes role \"Pirate\", they cannot be assigned at the same time");
			result.computeStatus();
			assertFailure(result);
		}

		unassignRole(USER_JACK_OID, ROLE_SELF_EXCLUSION_MANAGER_MEMBER_OID, SchemaConstants.ORG_DEFAULT, task, result);

		assertAssignedNoRole(USER_JACK_OID, task, result);
	}

	// MID-5207
	@Test
	public void test960JimGoldByMapping() throws Exception {
		final String TEST_NAME = "test960JimGoldByMapping";
		displayTestTitle(TEST_NAME);

		Task task = createTask(TEST_NAME);
		OperationResult result = task.getResult();

		// GIVEN

		UserType jim = new UserType(prismContext)
				.name("jim")
				.beginAssignment()
					.targetRef(ROLE_PRIZE_SILVER_OID, RoleType.COMPLEX_TYPE)
				.end();
		addObject(jim.asPrismObject());

		// WHEN
		TestUtil.displayWhen(TEST_NAME);

		assignRole(jim.getOid(), ROLE_PRIZE_GOLD_BY_MAPPING_OID, task, result);

		// THEN
		TestUtil.displayThen(TEST_NAME);
		result.computeStatus();
		TestUtil.assertSuccess(result);

		PrismObject<UserType> userAfter = getUser(jim.getOid());
		display("User after", userAfter);
		assertAssignedRole(userAfter, ROLE_PRIZE_GOLD_BY_MAPPING_OID);
		assertAssignedRole(userAfter, ROLE_PRIZE_GOLD_OID);
		assertNotAssignedRole(userAfter, ROLE_PRIZE_SILVER_OID);
		assertNotAssignedRole(userAfter, ROLE_PRIZE_BRONZE_OID);
	}

	private PrismObject<UserType> assignRolePolicyFailure(String TEST_NAME, String userOid, String roleOid, Task task, OperationResult result) throws ObjectNotFoundException, SchemaException, ExpressionEvaluationException, CommunicationException, ConfigurationException, ObjectAlreadyExistsException, SecurityViolationException {
		try {
	        // WHEN

	        displayWhen(TEST_NAME);
	        assignRole(userOid, roleOid, task, result);

	        assertNotReached();

        } catch (PolicyViolationException e) {
			System.out.println("Got expected exception: " + e.getMessage());

        	// THEN
        	displayThen(TEST_NAME);
        	assertFailure(result);
        }

        PrismObject<UserType> userAfter = getUser(USER_GUYBRUSH_OID);
        display("User after", userAfter);
        assertNotAssignedRole(userAfter, roleOid);
        return userAfter;
	}

	private void assertPolicySituation(PrismObject<UserType> user, String targetOid, String... expectedPolicySituation) {
		for (AssignmentType assignment: user.asObjectable().getAssignment()) {
			ObjectReferenceType targetRef = assignment.getTargetRef();
			if (targetRef != null && targetOid.equals(targetRef.getOid())) {
				PrismAsserts.assertSets("Wrong policy situation for target "+targetOid+" in "+user, assignment.getPolicySituation(), expectedPolicySituation);
			}
		}
	}


}
