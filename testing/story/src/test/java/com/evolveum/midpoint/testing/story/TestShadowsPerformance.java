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

package com.evolveum.midpoint.testing.story;

import com.evolveum.icf.dummy.resource.DummyAccount;
import com.evolveum.midpoint.schema.internals.InternalsConfig;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.test.DummyAuditService;
import com.evolveum.midpoint.test.DummyResourceContoller;
import com.evolveum.midpoint.test.util.MidPointTestConstants;
import com.evolveum.midpoint.util.aspect.MidpointInterceptor;
import com.evolveum.midpoint.xml.ns._public.common.common_3.OperationStatsType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ShadowType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.context.ContextConfiguration;
import org.testng.annotations.Test;

import java.io.File;
import java.util.*;

import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertNotNull;

/**
 * Tests creation and deletion of shadows.
 */
@ContextConfiguration(locations = {"classpath:ctx-story-test-main.xml"})
@DirtiesContext(classMode = ClassMode.AFTER_CLASS)
public class TestShadowsPerformance extends AbstractStoryTest {

	public static final File TEST_DIR = new File(MidPointTestConstants.TEST_RESOURCES_DIR, "shadows");

	private static final File SYSTEM_CONFIGURATION_FILE = new File(TEST_DIR, "system-configuration.xml");

	protected static final File RESOURCE_DUMMY_FILE = new File(TEST_DIR, "resource-dummy.xml");
	protected static final String RESOURCE_DUMMY_OID = "10000000-0000-0000-0000-000000000004";

	private static final File TASK_IMPORT_FILE = new File(TEST_DIR, "task-import.xml");
	private static final String TASK_IMPORT_OID = "40d5bba3-20fd-4578-86ac-a9bdce1d3776";

	private static final File TASK_RECONCILIATION_FILE = new File(TEST_DIR, "task-reconciliation.xml");
	private static final String TASK_RECONCILIATION_OID = "b6af0823-7d2c-4752-a04d-80bcfa7f0f2a";

	private static final File TASK_BULK_DELETE_FILE = new File(TEST_DIR, "task-bulk-delete.xml");
	private static final String TASK_BULK_DELETE_OID = "5658878f-7d80-4530-afd6-69561d9762fd";

	protected static final int NUMBER_OF_GENERATED_USERS = 20;
	protected static final String GENERATED_USER_NAME_FORMAT = "user%06d";
	protected static final String GENERATED_USER_FULL_NAME_FORMAT = "Random J. U%06d";

	private static final int SYNC_TASK_WAIT_TIMEOUT = 600000;

	private static final String SUMMARY_LINE_FORMAT = "%50s: %5d ms (%4d ms/user)\n";
	private static final String REPO_LINE_FORMAT = "%6d (%8.1f/%s)\n";

	private Map<String,Long> durations = new LinkedHashMap<>();

	protected DummyResourceContoller dummyResourceCtl;

	@Override
	protected File getSystemConfigurationFile() {
		return SYSTEM_CONFIGURATION_FILE;
	}

	@Override
	protected void importSystemTasks(OperationResult initResult) {
		// we don't want these
	}

	@Override
	protected boolean isAvoidLoggingChange() {
		return false;           // we want logging from our system config
	}

	@Override
	public void initSystem(Task initTask, OperationResult initResult) throws Exception {
		super.initSystem(initTask, initResult);

		DummyAuditService.getInstance().setEnabled(false);
		InternalsConfig.turnOffAllChecks();

		// Resources

		dummyResourceCtl = initDummyResource(null, RESOURCE_DUMMY_FILE, RESOURCE_DUMMY_OID,
				initTask, initResult);

	}

	@Test
	public void test010Sanity() throws Exception {
		final String TEST_NAME = "test010Sanity";
		displayTestTitle(TEST_NAME);

		Task task = createTask(TEST_NAME);

		// WHEN
		displayWhen(TEST_NAME);
		OperationResult result = modelService.testResource(RESOURCE_DUMMY_OID, task);

		// THEN
		displayThen(TEST_NAME);
		result.computeStatusIfUnknown();
		assertSuccess(result);
	}

	@Test
	public void test100ImportAccounts() throws Exception {
		final String TEST_NAME = "test100ImportAccounts";
		displayTestTitle(TEST_NAME);

		Task task = createTask(TEST_NAME);
		OperationResult result = task.getResult();

		for (int i = 0; i < NUMBER_OF_GENERATED_USERS; i++) {
			dummyResourceCtl.addAccount(
					String.format(GENERATED_USER_NAME_FORMAT, i),
					String.format(GENERATED_USER_FULL_NAME_FORMAT, i));
		}

		// WHEN
		displayWhen(TEST_NAME);
		addTask(TASK_IMPORT_FILE);

		// THEN
		displayThen(TEST_NAME);
		Task taskAfter = waitForTaskFinish(TASK_IMPORT_OID, true, SYNC_TASK_WAIT_TIMEOUT);

		display("task after", prismContext.xmlSerializer().serialize(taskAfter.getTaskPrismObject()));

		OperationStatsType statistics = getTaskTreeOperationStatistics(TASK_IMPORT_OID);
		displayOperationStatistics("Task operation statistics for " + TEST_NAME, statistics);
		assertNotNull(statistics);

		int shadows = repositoryService.countObjects(ShadowType.class, null, null, result);
		assertEquals("Wrong # of shadows after import", NUMBER_OF_GENERATED_USERS, shadows);
	}

	@Test(enabled = false)
	public void test200DeleteAccountsAndReconcile() throws Exception {
		final String TEST_NAME = "test200DeleteAccountsAndReconcile";
		displayTestTitle(TEST_NAME);

		Task task = createTask(TEST_NAME);
		OperationResult result = task.getResult();

		for (DummyAccount account : new ArrayList<>(dummyResourceCtl.getDummyResource().listAccounts())) {
			dummyResourceCtl.deleteAccount(account.getName());
		}
		assertEquals("Wrong # of remaining accounts", 0, dummyResourceCtl.getDummyResource().listAccounts().size());

		// WHEN
		displayWhen(TEST_NAME);
		addTask(TASK_RECONCILIATION_FILE);

		// THEN
		displayThen(TEST_NAME);
		Task taskAfter = waitForTaskFinish(TASK_RECONCILIATION_OID, true, 0L, SYNC_TASK_WAIT_TIMEOUT, false, 100);

		display("task after", prismContext.xmlSerializer().serialize(taskAfter.getTaskPrismObject()));

		OperationStatsType statistics = getTaskTreeOperationStatistics(TASK_RECONCILIATION_OID);
		displayOperationStatistics("Task operation statistics for " + TEST_NAME, statistics);
		assertNotNull(statistics);

		int shadows = repositoryService.countObjects(ShadowType.class, null, null, result);
		assertEquals("Wrong # of shadows after import", 0, shadows);
	}

	@Test
	public void test210DeleteShadows() throws Exception {
		final String TEST_NAME = "test210DeleteShadows";
		displayTestTitle(TEST_NAME);

		Task task = createTask(TEST_NAME);
		OperationResult result = task.getResult();

		for (DummyAccount account : new ArrayList<>(dummyResourceCtl.getDummyResource().listAccounts())) {
			dummyResourceCtl.deleteAccount(account.getName());
		}
		assertEquals("Wrong # of remaining accounts", 0, dummyResourceCtl.getDummyResource().listAccounts().size());

		// WHEN
		displayWhen(TEST_NAME);
		addTask(TASK_BULK_DELETE_FILE);

		// THEN
		displayThen(TEST_NAME);
		Task taskAfter = waitForTaskFinish(TASK_BULK_DELETE_OID, true, 0L, SYNC_TASK_WAIT_TIMEOUT, false, 100);

		display("task after", prismContext.xmlSerializer().serialize(taskAfter.getTaskPrismObject()));

		OperationStatsType statistics = getTaskTreeOperationStatistics(TASK_BULK_DELETE_OID);
		displayOperationStatistics("Task operation statistics for " + TEST_NAME, statistics);
		assertNotNull(statistics);

		int shadows = repositoryService.countObjects(ShadowType.class, null, null, result);
		assertEquals("Wrong # of shadows after import", 0, shadows);
	}

	@Test
	public void test900Summarize() throws Exception {
		final String TEST_NAME = "test900Summarize";
		displayTestTitle(TEST_NAME);

		Task task = createTask(TEST_NAME);
		OperationResult result = task.getResult();

		StringBuilder sb = new StringBuilder();
		for (Map.Entry<String, Long> entry : durations.entrySet()) {
			sb.append(summary(entry.getKey(), entry.getValue()));
		}
		display("Summary ("+NUMBER_OF_GENERATED_USERS+" users)", sb.toString());

		// THEN
		displayThen(TEST_NAME);

		// TODO: more thresholds

	}

	private long recordDuration(String label, long duration) {
		durations.put(label, duration);
		return duration;
	}

	private Object summary(String label, long duration) {
		return String.format(SUMMARY_LINE_FORMAT, label, duration, duration/NUMBER_OF_GENERATED_USERS);
	}

}