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

package com.evolveum.midpoint.model.intest.async;

import com.evolveum.midpoint.model.intest.AbstractInitializedModelIntegrationTest;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.schema.ResourceShadowDiscriminator;
import com.evolveum.midpoint.schema.constants.SchemaConstants;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.test.util.MidPointTestConstants;
import com.evolveum.midpoint.test.util.TestUtil;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;
import org.apache.commons.io.IOUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.context.ContextConfiguration;
import org.testng.annotations.Test;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 *  Tests async updates using Grouper JSON messages.
 *
 *  Currently uses caching.
 */
@ContextConfiguration(locations = {"classpath:ctx-model-intest-test-main.xml"})
@DirtiesContext(classMode = ClassMode.AFTER_CLASS)
public class TestAsyncUpdateGrouperJson extends AbstractInitializedModelIntegrationTest {

	public static final File TEST_DIR = new File(MidPointTestConstants.TEST_RESOURCES_DIR, "async/grouper-amqp091");

	protected static final File RESOURCE_GROUPER_FILE = new File(TEST_DIR, "resource-grouper-amqp091.xml");
	protected static final String RESOURCE_GROUPER_ID = "Grouper";
	protected static final String RESOURCE_GROUPER_OID = "bbb9900a-b53d-4453-b60b-908725e3950e";

	public static final String BANDERSON_USERNAME = "banderson";
	public static final String JLEWIS685_USERNAME = "jlewis685";
	public static final String ALUMNI_NAME = "ref:alumni";
	public static final String STAFF_NAME = "ref:staff";

	public static final String GROUPER_USER_INTENT = "subject";
	public static final String GROUPER_GROUP_INTENT = "group";

	protected PrismObject<ResourceType> resourceGrouper;

	private static final File CHANGE_100 = new File(TEST_DIR, "change-100-banderson-add-supergroup.json");
	private static final File CHANGE_110 = new File(TEST_DIR, "change-110-alumni-add.json");
	private static final File CHANGE_110a = new File(TEST_DIR, "change-110a-staff-add.json");
	private static final File CHANGE_200 = new File(TEST_DIR, "change-200-banderson-add-alumni.json");
	private static final File CHANGE_210 = new File(TEST_DIR, "change-210-banderson-add-staff.json");
	private static final File CHANGE_220 = new File(TEST_DIR, "change-220-jlewis685-add-alumni.json");
	private static final File CHANGE_230 = new File(TEST_DIR, "change-230-jlewis685-add-supergroup.json");
	private static final File CHANGE_240 = new File(TEST_DIR, "change-240-banderson-add-staff.json");
	private static final File CHANGE_250 = new File(TEST_DIR, "change-250-banderson-delete-alumni.json");
	private static final File CHANGE_310 = new File(TEST_DIR, "change-310-staff-delete.json");

	@Override
	public void initSystem(Task initTask, OperationResult initResult) throws Exception {
		super.initSystem(initTask, initResult);

		resourceGrouper = importAndGetObjectFromFile(ResourceType.class, RESOURCE_GROUPER_FILE, RESOURCE_GROUPER_OID,
				initTask, initResult);
	}

	@Test
    public void test000Sanity() throws Exception {
		final String TEST_NAME = "test000Sanity";
        TestUtil.displayTestTitle(this, TEST_NAME);
        Task task = createTestTask(TEST_NAME);

        OperationResult testResultGrouper = modelService.testResource(RESOURCE_GROUPER_OID, task);
        TestUtil.assertSuccess(testResultGrouper);
	}

	private Task createTestTask(String TEST_NAME) {
		return taskManager.createTaskInstance(TestAsyncUpdateGrouperJson.class.getName() + "." + TEST_NAME);
	}

	/**
	 * The first MEMBERSHIP_ADD event for banderson (supergroup)
	 */
	@Test
    public void test100AddAnderson() throws Exception {
		final String TEST_NAME = "test100AddAnderson";
        TestUtil.displayTestTitle(this, TEST_NAME);
        Task task = createTestTask(TEST_NAME);
        OperationResult result = task.getResult();

        // GIVEN

		MockAsyncUpdateSource.INSTANCE.reset();
		MockAsyncUpdateSource.INSTANCE.prepareMessage(getAmqp091Message(CHANGE_100));

		// WHEN

		ResourceShadowDiscriminator coords = new ResourceShadowDiscriminator(RESOURCE_GROUPER_OID);
		String handle = provisioningService.startListeningForAsyncUpdates(coords, task, result);
		provisioningService.stopListeningForAsyncUpdates(handle, task, result);

        // THEN

        result.computeStatus();
        TestUtil.assertSuccess(result);

		assertUserAfterByUsername(BANDERSON_USERNAME)
				.displayWithProjections()
				.links()
					.single()
						.resolveTarget()
							.display()
							.assertKind(ShadowKindType.ACCOUNT)
	//						.assertIntent(GROUPER_USER_INTENT)
							.assertResource(RESOURCE_GROUPER_OID)
							.end()
						.end()
					.end()
				.assertOrganizationalUnits();
	}

	private AsyncUpdateMessageType getAmqp091Message(File file) throws IOException {
		Amqp091MessageType rv = new Amqp091MessageType();
		String json = String.join("\n", IOUtils.readLines(new FileReader(file)));
		rv.setBody(json.getBytes(StandardCharsets.UTF_8));
		return rv;
	}

	/**
	 * GROUP_ADD event for ref:alumni and ref:staff.
	 */
	@Test
    public void test110AddAlumniAndStaff() throws Exception {
		final String TEST_NAME = "test110AddAlumniAndStaff";
        TestUtil.displayTestTitle(this, TEST_NAME);
        Task task = createTestTask(TEST_NAME);
        OperationResult result = task.getResult();

        // GIVEN

		MockAsyncUpdateSource.INSTANCE.reset();
		MockAsyncUpdateSource.INSTANCE.prepareMessage(getAmqp091Message(CHANGE_110));
		MockAsyncUpdateSource.INSTANCE.prepareMessage(getAmqp091Message(CHANGE_110a));

		// WHEN

		ResourceShadowDiscriminator coords = new ResourceShadowDiscriminator(RESOURCE_GROUPER_OID);
		String handle = provisioningService.startListeningForAsyncUpdates(coords, task, result);
		provisioningService.stopListeningForAsyncUpdates(handle, task, result);

        // THEN

        result.computeStatus();
        TestUtil.assertSuccess(result);

		assertOrgByName(ALUMNI_NAME, "after")
				.displayWithProjections()
				.links()
					.single()
					.resolveTarget()
						.display()
						.assertKind(ShadowKindType.ENTITLEMENT)
//						.assertIntent(GROUPER_GROUP_INTENT)
						.assertResource(RESOURCE_GROUPER_OID);

		assertOrgByName(STAFF_NAME, "after")
				.displayWithProjections()
				.links()
					.single()
					.resolveTarget()
						.display()
						.assertKind(ShadowKindType.ENTITLEMENT)
//						.assertIntent(GROUPER_GROUP_INTENT)
						.assertResource(RESOURCE_GROUPER_OID);
	}

	/**
	 * Adding ref:alumni membership for banderson.
	 */
	@Test
	public void test200AddAlumniForAnderson() throws Exception {
		final String TEST_NAME = "test200AddAlumniForAnderson";
		TestUtil.displayTestTitle(this, TEST_NAME);
		Task task = createTestTask(TEST_NAME);
		OperationResult result = task.getResult();

		// GIVEN

		MockAsyncUpdateSource.INSTANCE.reset();
		MockAsyncUpdateSource.INSTANCE.prepareMessage(getAmqp091Message(CHANGE_200));

		// WHEN

		ResourceShadowDiscriminator coords = new ResourceShadowDiscriminator(RESOURCE_GROUPER_OID);
		String handle = provisioningService.startListeningForAsyncUpdates(coords, task, result);
		provisioningService.stopListeningForAsyncUpdates(handle, task, result);

		// THEN

		result.computeStatus();
		TestUtil.assertSuccess(result);

		assertUserAfterByUsername(BANDERSON_USERNAME)
				.displayWithProjections()
				.links()
					.single()
						.resolveTarget()
							.assertKind(ShadowKindType.ACCOUNT)
			//							.assertIntent(GROUPER_USER_INTENT)
							.assertResource(RESOURCE_GROUPER_OID)
							.display("shadow after")
							.end()
						.end()
					.end()
				.assertOrganizationalUnits(ALUMNI_NAME);
	}

	/**
	 * Adding ref:staff membership for banderson.
	 */
	@Test
	public void test210AddStaffForAnderson() throws Exception {
		final String TEST_NAME = "test210AddStaffForAnderson";
		TestUtil.displayTestTitle(this, TEST_NAME);
		Task task = createTestTask(TEST_NAME);
		OperationResult result = task.getResult();

		// GIVEN

		MockAsyncUpdateSource.INSTANCE.reset();
		MockAsyncUpdateSource.INSTANCE.prepareMessage(getAmqp091Message(CHANGE_210));

		// WHEN

		ResourceShadowDiscriminator coords = new ResourceShadowDiscriminator(RESOURCE_GROUPER_OID);
		String handle = provisioningService.startListeningForAsyncUpdates(coords, task, result);
		provisioningService.stopListeningForAsyncUpdates(handle, task, result);

		// THEN

		result.computeStatus();
		TestUtil.assertSuccess(result);

		assertUserAfterByUsername(BANDERSON_USERNAME)
				.displayWithProjections()
				.links()
					.single()
						.resolveTarget()
							.display("shadow after")
							.assertKind(ShadowKindType.ACCOUNT)
				//							.assertIntent(GROUPER_USER_INTENT)
							.assertResource(RESOURCE_GROUPER_OID)
							.end()
						.end()
					.end()
				.assertOrganizationalUnits(ALUMNI_NAME, STAFF_NAME);
	}

	/**
	 * Adding ref:alumni membership for jlewis685. But this is the first occurrence of jlewis685!
	 */
	@Test
	public void test220AddAlumniForLewis() throws Exception {
		final String TEST_NAME = "test220AddAlumniForLewis";
		TestUtil.displayTestTitle(this, TEST_NAME);
		Task task = createTestTask(TEST_NAME);
		OperationResult result = task.getResult();

		// GIVEN

		MockAsyncUpdateSource.INSTANCE.reset();
		MockAsyncUpdateSource.INSTANCE.prepareMessage(getAmqp091Message(CHANGE_220));

		// WHEN

		ResourceShadowDiscriminator coords = new ResourceShadowDiscriminator(RESOURCE_GROUPER_OID);
		String handle = provisioningService.startListeningForAsyncUpdates(coords, task, result);
		provisioningService.stopListeningForAsyncUpdates(handle, task, result);

		// THEN

		result.computeStatus();
		TestUtil.assertSuccess(result);

		assertUserAfterByUsername(JLEWIS685_USERNAME)
				.displayWithProjections()
				.links()
					.single()
						.resolveTarget()
							.display("shadow after")
							.assertKind(ShadowKindType.ACCOUNT)
				//							.assertIntent(GROUPER_USER_INTENT)
							.assertResource(RESOURCE_GROUPER_OID)
							.end()
						.end()
					.end()
				.assertOrganizationalUnits(ALUMNI_NAME);
	}

	/**
	 * Adding supergroup to jlewis (notification-only change). Should be idempotent.
	 */
	@Test
	public void test230AddLewis() throws Exception {
		final String TEST_NAME = "test230AddLewis";
		TestUtil.displayTestTitle(this, TEST_NAME);
		Task task = createTestTask(TEST_NAME);
		OperationResult result = task.getResult();

		// GIVEN

		MockAsyncUpdateSource.INSTANCE.reset();
		MockAsyncUpdateSource.INSTANCE.prepareMessage(getAmqp091Message(CHANGE_230));

		// WHEN

		ResourceShadowDiscriminator coords = new ResourceShadowDiscriminator(RESOURCE_GROUPER_OID);
		String handle = provisioningService.startListeningForAsyncUpdates(coords, task, result);
		provisioningService.stopListeningForAsyncUpdates(handle, task, result);

		// THEN

		result.computeStatus();
		TestUtil.assertSuccess(result);

		assertUserAfterByUsername(JLEWIS685_USERNAME)
				.displayWithProjections()
				.links()
					.single()
						.resolveTarget()
							.display("shadow after")
							.assertKind(ShadowKindType.ACCOUNT)
				//							.assertIntent(GROUPER_USER_INTENT)
							.assertResource(RESOURCE_GROUPER_OID)
							.end()
						.end()
					.end()
				.assertOrganizationalUnits(ALUMNI_NAME);
	}

	/**
	 * Adding ref:staff membership for banderson (again). Should be idempotent.
	 */
	@Test
	public void test240AddStaffForAnderson() throws Exception {
		final String TEST_NAME = "test240AddStaffForAnderson";
		TestUtil.displayTestTitle(this, TEST_NAME);
		Task task = createTestTask(TEST_NAME);
		OperationResult result = task.getResult();

		// GIVEN

		MockAsyncUpdateSource.INSTANCE.reset();
		MockAsyncUpdateSource.INSTANCE.prepareMessage(getAmqp091Message(CHANGE_240));

		// WHEN

		ResourceShadowDiscriminator coords = new ResourceShadowDiscriminator(RESOURCE_GROUPER_OID);
		String handle = provisioningService.startListeningForAsyncUpdates(coords, task, result);
		provisioningService.stopListeningForAsyncUpdates(handle, task, result);

		// THEN

		result.computeStatus();
		TestUtil.assertSuccess(result);

		assertUserAfterByUsername(BANDERSON_USERNAME)
				.displayWithProjections()
				.links()
					.single()
						.resolveTarget()
							.display("shadow after")
							.assertKind(ShadowKindType.ACCOUNT)
				//							.assertIntent(GROUPER_USER_INTENT)
							.assertResource(RESOURCE_GROUPER_OID)
							.end()
						.end()
					.end()
				.assertOrganizationalUnits(ALUMNI_NAME, STAFF_NAME);
	}

	/**
	 * Deleting ref:alumni membership for banderson.
	 */
	@Test
	public void test250DeleteAlumniForAnderson() throws Exception {
		final String TEST_NAME = "test250DeleteAlumniForAnderson";
		TestUtil.displayTestTitle(this, TEST_NAME);
		Task task = createTestTask(TEST_NAME);
		OperationResult result = task.getResult();

		// GIVEN

		MockAsyncUpdateSource.INSTANCE.reset();
		MockAsyncUpdateSource.INSTANCE.prepareMessage(getAmqp091Message(CHANGE_250));

		// WHEN

		ResourceShadowDiscriminator coords = new ResourceShadowDiscriminator(RESOURCE_GROUPER_OID);
		String handle = provisioningService.startListeningForAsyncUpdates(coords, task, result);
		provisioningService.stopListeningForAsyncUpdates(handle, task, result);

		// THEN

		result.computeStatus();
		TestUtil.assertSuccess(result);

		assertUserAfterByUsername(BANDERSON_USERNAME)
				.displayWithProjections()
				.links()
					.single()
						.resolveTarget()
							.display("shadow after")
							.assertKind(ShadowKindType.ACCOUNT)
				//							.assertIntent(GROUPER_USER_INTENT)
							.assertResource(RESOURCE_GROUPER_OID)
							.end()
						.end()
					.end()
				.assertOrganizationalUnits(STAFF_NAME);
	}

	/**
	 * Deleting etc:staff.
	 */
	@Test
	public void test310DeleteStaff() throws Exception {
		final String TEST_NAME = "test310DeleteStaff";
		TestUtil.displayTestTitle(this, TEST_NAME);
		Task task = createTestTask(TEST_NAME);
		OperationResult result = task.getResult();

		// GIVEN

		assertObjectByName(OrgType.class, STAFF_NAME, task, result);

		MockAsyncUpdateSource.INSTANCE.reset();
		MockAsyncUpdateSource.INSTANCE.prepareMessage(getAmqp091Message(CHANGE_310));

		// WHEN

		ResourceShadowDiscriminator coords = new ResourceShadowDiscriminator(RESOURCE_GROUPER_OID);
		String handle = provisioningService.startListeningForAsyncUpdates(coords, task, result);
		provisioningService.stopListeningForAsyncUpdates(handle, task, result);

		// THEN

		result.computeStatus();
		TestUtil.assertSuccess(result);

		assertNoObjectByName(OrgType.class, STAFF_NAME, task, result);
	}
}
