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
package com.evolveum.midpoint.provisioning.impl.dummy;

import static org.testng.AssertJUnit.assertNotNull;
import static org.testng.AssertJUnit.assertNull;
import static org.testng.AssertJUnit.assertTrue;

import java.io.File;

import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.testng.AssertJUnit;
import org.testng.annotations.Test;

import com.evolveum.icf.dummy.resource.DummyAccount;
import com.evolveum.midpoint.prism.PrismObjectDefinition;
import com.evolveum.midpoint.prism.delta.ObjectDelta;
import com.evolveum.midpoint.prism.delta.PropertyDelta;
import com.evolveum.midpoint.prism.xml.XmlTypeConverter;
import com.evolveum.midpoint.schema.constants.SchemaConstants;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.test.util.TestUtil;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ActivationStatusType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.LockoutStatusType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.OperationProvisioningScriptsType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ShadowType;
import com.evolveum.midpoint.xml.ns._public.resource.capabilities_3.AddRemoveAttributeValuesCapabilityType;
import com.evolveum.midpoint.xml.ns._public.resource.capabilities_3.CountObjectsSimulateType;
import com.evolveum.midpoint.xml.ns._public.resource.capabilities_3.RunAsCapabilityType;

/**
 * Almost the same as TestDummy but quite limited:
 * - no activation support
 * - no paging
 * - no count simulation using sequential search
 * - no runAs
 * - legacy AddRemoveAttributeValuesCapabilityType
 * Let's test that we are able to do all the operations without NPEs and other side effects.
 *
 * @author Radovan Semancik
 *
 */
@ContextConfiguration(locations = "classpath:ctx-provisioning-test-main.xml")
@DirtiesContext
public class TestDummyLimited extends TestDummy {

	public static final File TEST_DIR = new File(TEST_DIR_DUMMY, "dummy-limited");
	public static final File RESOURCE_DUMMY_FILE = new File(TEST_DIR, "resource-dummy.xml");

	protected static final File ACCOUNT_WILL_FILE = new File(TEST_DIR, "account-will.xml");

	@Override
	protected boolean supportsActivation() {
		return false;
	}

	@Override
	protected File getResourceDummyFile() {
		return RESOURCE_DUMMY_FILE;
	}

	protected File getAccountWillFile() {
		return ACCOUNT_WILL_FILE;
	}

	@Override
	public void initSystem(Task initTask, OperationResult initResult) throws Exception {
		super.initSystem(initTask, initResult);

		syncServiceMock.setSupportActivation(false);
	}
	
	@Override
	protected CountObjectsSimulateType getCountSimulationMode() {
		return CountObjectsSimulateType.SEQUENTIAL_SEARCH;
	}
	
	@Override
	protected void assertRunAsCapability(RunAsCapabilityType capRunAs) {
		assertNull("Unexpected native runAs capability", capRunAs);
	}

	// No runAs capability, modifier is always the default one.
	// No matter what kind of runAs was requested.
	@Override
	protected String getLastModifierName(String expected) {
		return null;
	}
	
	@Test
	@Override
	public void test150DisableAccount() throws Exception {
		final String TEST_NAME = "test150DisableAccount";
		TestUtil.displayTestTitle(TEST_NAME);
		// GIVEN

		Task task = taskManager.createTaskInstance(TestDummy.class.getName() + "." + TEST_NAME);
		OperationResult result = task.getResult();
		syncServiceMock.reset();

		ObjectDelta<ShadowType> delta = prismContext.deltaFactory().object().createModificationReplaceProperty(ShadowType.class,
				ACCOUNT_WILL_OID, SchemaConstants.PATH_ACTIVATION_ADMINISTRATIVE_STATUS,
				ActivationStatusType.DISABLED);
		display("ObjectDelta", delta);
		delta.checkConsistence();

		try {
			// WHEN
			provisioningService.modifyObject(ShadowType.class, delta.getOid(),
				delta.getModifications(), new OperationProvisioningScriptsType(), null, task, result);

			AssertJUnit.fail("Unexpected success");
		} catch (SchemaException e) {
			// This is expected

		}

		// THEN
		result.computeStatus();
		display("modifyObject result", result);
		TestUtil.assertFailure(result);

		delta.checkConsistence();
		// check if activation was unchanged
		DummyAccount dummyAccount = dummyResource.getAccountByUsername(ACCOUNT_WILL_USERNAME);
		assertTrue("Dummy account "+ACCOUNT_WILL_USERNAME+" is disabled, expected enabled", dummyAccount.isEnabled());

		syncServiceMock.assertNotifyFailureOnly();

		assertSteadyResource();
	}

	@Override
	public void test151SearchDisabledAccounts() throws Exception {
		// N/A
	}

	@Override
	public void test152ActivationStatusUndefinedAccount() throws Exception {
		final String TEST_NAME = "test152ActivationStatusUndefinedAccount";
		TestUtil.displayTestTitle(TEST_NAME);
		// GIVEN

		Task task = taskManager.createTaskInstance(TestDummy.class.getName() + "." + TEST_NAME);
		OperationResult result = task.getResult();
		syncServiceMock.reset();

		ObjectDelta<ShadowType> delta = prismContext.deltaFactory().object().createModificationDeleteProperty(ShadowType.class,
				ACCOUNT_WILL_OID, SchemaConstants.PATH_ACTIVATION_ADMINISTRATIVE_STATUS,
				ActivationStatusType.DISABLED);
		display("ObjectDelta", delta);
		delta.checkConsistence();


		try {
			// WHEN
			provisioningService.modifyObject(ShadowType.class, delta.getOid(),
				delta.getModifications(), new OperationProvisioningScriptsType(), null, task, result);

			AssertJUnit.fail("Unexpected success");
		} catch (SchemaException e) {
			// This is expected

		}

		// THEN
		result.computeStatus();
		display("modifyObject result", result);
		TestUtil.assertFailure(result);

		delta.checkConsistence();
		// check if activation was unchanged
		DummyAccount dummyAccount = dummyResource.getAccountByUsername(ACCOUNT_WILL_USERNAME);
		assertTrue("Dummy account "+ACCOUNT_WILL_USERNAME+" is disabled, expected enabled", dummyAccount.isEnabled());

		syncServiceMock.assertNotifyFailureOnly();

		assertSteadyResource();
	}

	@Test
	@Override
	public void test154EnableAccount() throws Exception {
		final String TEST_NAME = "test154EnableAccount";
		TestUtil.displayTestTitle(TEST_NAME);
		// GIVEN

		Task task = taskManager.createTaskInstance(TestDummy.class.getName() + "." + TEST_NAME);
		OperationResult result = task.getResult();
		syncServiceMock.reset();

		ObjectDelta<ShadowType> delta = prismContext.deltaFactory().object().createModificationReplaceProperty(ShadowType.class,
				ACCOUNT_WILL_OID, SchemaConstants.PATH_ACTIVATION_ADMINISTRATIVE_STATUS,
				ActivationStatusType.ENABLED);
		display("ObjectDelta", delta);
		delta.checkConsistence();


		try {
			// WHEN
			provisioningService.modifyObject(ShadowType.class, delta.getOid(),
				delta.getModifications(), new OperationProvisioningScriptsType(), null, task, result);

			AssertJUnit.fail("Unexpected success");
		} catch (SchemaException e) {
			// This is expected

		}

		// THEN
		result.computeStatus();
		display("modifyObject result", result);
		TestUtil.assertFailure(result);

		delta.checkConsistence();
		// check if activation was unchanged
		DummyAccount dummyAccount = dummyResource.getAccountByUsername(ACCOUNT_WILL_USERNAME);
		assertTrue("Dummy account "+ACCOUNT_WILL_USERNAME+" is disabled, expected enabled", dummyAccount.isEnabled());

		syncServiceMock.assertNotifyFailureOnly();

		assertSteadyResource();
	}

	@Override
	public void test155SearchDisabledAccounts() throws Exception {
		// N/A
	}

	@Test
	@Override
	public void test156SetValidFrom() throws Exception {
		final String TEST_NAME = "test156SetValidFrom";
		TestUtil.displayTestTitle(TEST_NAME);
		// GIVEN

		Task task = taskManager.createTaskInstance(TestDummy.class.getName() + "." + TEST_NAME);
		OperationResult result = task.getResult();
		syncServiceMock.reset();

		long millis = VALID_FROM_MILLIS;

		ObjectDelta<ShadowType> delta = prismContext.deltaFactory().object().createModificationReplaceProperty(ShadowType.class,
				ACCOUNT_WILL_OID, SchemaConstants.PATH_ACTIVATION_VALID_FROM,
				XmlTypeConverter.createXMLGregorianCalendar(VALID_FROM_MILLIS));
		delta.checkConsistence();

		try {
			// WHEN
			provisioningService.modifyObject(ShadowType.class, delta.getOid(),
				delta.getModifications(), new OperationProvisioningScriptsType(), null, task, result);

			AssertJUnit.fail("Unexpected success");
		} catch (SchemaException e) {
			// This is expected

		}

		// THEN
		result.computeStatus();
		display("modifyObject result", result);
		TestUtil.assertFailure(result);

		delta.checkConsistence();
		// check if activation was not changed
		DummyAccount dummyAccount = dummyResource.getAccountByUsername(ACCOUNT_WILL_USERNAME);
		assertTrue("Dummy account "+ACCOUNT_WILL_USERNAME+" is disabled, expected enabled", dummyAccount.isEnabled());
		assertNull("Unexpected account validFrom in account "+ACCOUNT_WILL_USERNAME+": "+dummyAccount.getValidFrom(), dummyAccount.getValidFrom());
		assertNull("Unexpected account validTo in account "+ACCOUNT_WILL_USERNAME+": "+dummyAccount.getValidTo(), dummyAccount.getValidTo());

		syncServiceMock.assertNotifyFailureOnly();

		assertSteadyResource();
	}

	@Test
	@Override
	public void test157SetValidTo() throws Exception {
		final String TEST_NAME = "test157SetValidTo";
		TestUtil.displayTestTitle(TEST_NAME);
		// GIVEN

		Task task = taskManager.createTaskInstance(TestDummy.class.getName() + "." + TEST_NAME);
		OperationResult result = task.getResult();
		syncServiceMock.reset();

		long millis = VALID_TO_MILLIS;

		ObjectDelta<ShadowType> delta = prismContext.deltaFactory().object().createModificationReplaceProperty(ShadowType.class,
				ACCOUNT_WILL_OID, SchemaConstants.PATH_ACTIVATION_VALID_TO,
				XmlTypeConverter.createXMLGregorianCalendar(VALID_TO_MILLIS));
		delta.checkConsistence();

		try {
			// WHEN
			provisioningService.modifyObject(ShadowType.class, delta.getOid(),
				delta.getModifications(), new OperationProvisioningScriptsType(), null, task, result);

			AssertJUnit.fail("Unexpected success");
		} catch (SchemaException e) {
			// This is expected

		}

		// THEN
		result.computeStatus();
		display("modifyObject result", result);
		TestUtil.assertFailure(result);

		delta.checkConsistence();
		// check if activation was changed
		DummyAccount dummyAccount = dummyResource.getAccountByUsername(ACCOUNT_WILL_USERNAME);
		assertTrue("Dummy account "+ACCOUNT_WILL_USERNAME+" is disabled, expected enabled", dummyAccount.isEnabled());
		assertNull("Unexpected account validFrom in account "+ACCOUNT_WILL_USERNAME+": "+dummyAccount.getValidFrom(), dummyAccount.getValidFrom());
		assertNull("Unexpected account validTo in account "+ACCOUNT_WILL_USERNAME+": "+dummyAccount.getValidTo(), dummyAccount.getValidTo());

		syncServiceMock.assertNotifyFailureOnly();

		assertSteadyResource();
	}

	@Override
	public void test158DeleteValidToValidFrom() throws Exception {
		final String TEST_NAME = "test158DeleteValidToValidFrom";
		TestUtil.displayTestTitle(TEST_NAME);
		// GIVEN

		Task task = taskManager.createTaskInstance(TestDummy.class.getName() + "." + TEST_NAME);
		OperationResult result = task.getResult();
		syncServiceMock.reset();


		ObjectDelta<ShadowType> delta = prismContext.deltaFactory().object().createModificationDeleteProperty(ShadowType.class,
				ACCOUNT_WILL_OID, SchemaConstants.PATH_ACTIVATION_VALID_TO,
				XmlTypeConverter.createXMLGregorianCalendar(VALID_TO_MILLIS));
		PrismObjectDefinition def = prismContext.getSchemaRegistry().findObjectDefinitionByCompileTimeClass(ShadowType.class);
		PropertyDelta validFromDelta = prismContext.deltaFactory().property().createModificationDeleteProperty(SchemaConstants.PATH_ACTIVATION_VALID_FROM,
				def.findPropertyDefinition(SchemaConstants.PATH_ACTIVATION_VALID_FROM),
				XmlTypeConverter.createXMLGregorianCalendar(VALID_FROM_MILLIS));
		delta.addModification(validFromDelta);
		delta.checkConsistence();


		try {
			// WHEN
			provisioningService.modifyObject(ShadowType.class, delta.getOid(),
				delta.getModifications(), new OperationProvisioningScriptsType(), null, task, result);

			AssertJUnit.fail("Unexpected success");
		} catch (SchemaException e) {
			// This is expected

		}

		// THEN
		result.computeStatus();
		display("modifyObject result", result);
		TestUtil.assertFailure(result);

		delta.checkConsistence();
		// check if activation was changed
		DummyAccount dummyAccount = dummyResource.getAccountByUsername(ACCOUNT_WILL_USERNAME);
		assertTrue("Dummy account "+ACCOUNT_WILL_USERNAME+" is disabled, expected enabled", dummyAccount.isEnabled());
		assertNull("Unexpected account validFrom in account "+ACCOUNT_WILL_USERNAME+": "+dummyAccount.getValidFrom(), dummyAccount.getValidFrom());
		assertNull("Unexpected account validTo in account "+ACCOUNT_WILL_USERNAME+": "+dummyAccount.getValidTo(), dummyAccount.getValidTo());

		syncServiceMock.assertNotifyFailureOnly();

		assertSteadyResource();

	}

	@Test
	@Override
	public void test159GetLockedoutAccount() throws Exception {
		// Not relevant
	}

	@Override
	public void test160SearchLockedAccounts() throws Exception {
		// N/A
	}

	@Test
	@Override
	public void test162UnlockAccount() throws Exception {
		final String TEST_NAME = "test162UnlockAccount";
		TestUtil.displayTestTitle(TEST_NAME);
		// GIVEN

		Task task = taskManager.createTaskInstance(TestDummy.class.getName() + "." + TEST_NAME);
		OperationResult result = task.getResult();

		syncServiceMock.reset();

		ObjectDelta<ShadowType> delta = prismContext.deltaFactory().object().createModificationReplaceProperty(ShadowType.class,
				ACCOUNT_WILL_OID, SchemaConstants.PATH_ACTIVATION_LOCKOUT_STATUS,
				LockoutStatusType.NORMAL);
		display("ObjectDelta", delta);
		delta.checkConsistence();

		try {
			// WHEN
			provisioningService.modifyObject(ShadowType.class, delta.getOid(),
				delta.getModifications(), new OperationProvisioningScriptsType(), null, task, result);

			AssertJUnit.fail("Unexpected success");
		} catch (SchemaException e) {
			// This is expected
		}


		// THEN
		TestUtil.displayThen(TEST_NAME);
		result.computeStatus();
		display("modifyObject result", result);
		TestUtil.assertFailure(result);

		delta.checkConsistence();

		syncServiceMock.assertNotifyFailureOnly();

		assertSteadyResource();
	}
	
	// No paging means no support for server-side sorting
	// Note: ordering may change here if dummy resource impl is changed
	@Override
	protected String[] getSortedUsernames18x() {
		// daemon, Will, morgan, carla, meathook 
		return new String[] { "daemon", transformNameFromResource("Will"), transformNameFromResource("morgan"), "carla", "meathook" };
	}
	
	// No paging
	@Override
	protected Integer getTest18xApproxNumberOfSearchResults() {
		return null;
	}
	
	@Test
	@Override
	public void test181SearchNullPagingOffset0Size3Desc() throws Exception {
		// Nothing to do. No sorting support. So desc sorting won't work at all.
	}
	
	@Test
	@Override
	public void test183SearchNullPagingOffset2Size3Desc() throws Exception {
		// Nothing to do. No sorting support. So desc sorting won't work at all.
	}

}
