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

/**
 *
 */
package com.evolveum.midpoint.provisioning.impl.dummy;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;

import com.evolveum.midpoint.prism.delta.*;
import com.evolveum.midpoint.prism.path.ItemPath;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.testng.AssertJUnit;
import org.testng.annotations.Test;
import org.w3c.dom.Element;

import com.evolveum.midpoint.common.refinery.RefinedResourceSchemaImpl;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.util.PrismTestUtil;
import com.evolveum.midpoint.schema.GetOperationOptions;
import com.evolveum.midpoint.schema.SelectorOptions;
import com.evolveum.midpoint.schema.constants.SchemaConstants;
import com.evolveum.midpoint.schema.internals.InternalCounters;
import com.evolveum.midpoint.schema.internals.InternalMonitor;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.schema.util.ResourceTypeUtil;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.test.DummyResourceContoller;
import com.evolveum.midpoint.test.util.TestUtil;
import com.evolveum.midpoint.util.DOMUtil;
import com.evolveum.midpoint.util.exception.CommunicationException;
import com.evolveum.midpoint.util.exception.ConfigurationException;
import com.evolveum.midpoint.util.exception.ExpressionEvaluationException;
import com.evolveum.midpoint.util.exception.ObjectAlreadyExistsException;
import com.evolveum.midpoint.util.exception.ObjectNotFoundException;
import com.evolveum.midpoint.util.exception.PolicyViolationException;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.util.exception.SecurityViolationException;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ProjectionPolicyType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ResourceType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ShadowType;

/**
 * The test of Provisioning service on the API level. It checks proper caching of resource and schemas.
 *
 * The test is using dummy resource for speed and flexibility.
 *
 * @author Radovan Semancik
 *
 */
@ContextConfiguration(locations = "classpath:ctx-provisioning-test-main.xml")
@DirtiesContext
public class TestDummyResourceAndSchemaCaching extends AbstractDummyTest {

	private static final Trace LOGGER = TraceManager.getTrace(TestDummyResourceAndSchemaCaching.class);

	@Test
	public void test010GetResource() throws Exception {
		final String TEST_NAME = "test010GetResource";
		displayTestTitle(TEST_NAME);
		// GIVEN
		OperationResult result = new OperationResult(TestDummyResourceAndSchemaCaching.class.getName()
				+ "." + TEST_NAME);

		// Check that there is no schema before test (pre-condition)
		PrismObject<ResourceType> resourceBefore = repositoryService.getObject(ResourceType.class, RESOURCE_DUMMY_OID, null, result);
		ResourceType resourceTypeBefore = resourceBefore.asObjectable();
		Element resourceXsdSchemaElementBefore = ResourceTypeUtil.getResourceXsdSchema(resourceTypeBefore);
		AssertJUnit.assertNull("Found schema before test connection. Bad test setup?", resourceXsdSchemaElementBefore);

		assertVersion(resourceBefore, "0");
		
		// Some connector initialization and other things might happen in previous tests.
		// The monitor is static, not part of spring context, it will not be cleared
		rememberCounter(InternalCounters.RESOURCE_SCHEMA_FETCH_COUNT);
		rememberCounter(InternalCounters.RESOURCE_REPOSITORY_READ_COUNT);
		rememberCounter(InternalCounters.CONNECTOR_SCHEMA_PARSE_COUNT);
		rememberCounter(InternalCounters.CONNECTOR_CAPABILITIES_FETCH_COUNT);
		rememberCounter(InternalCounters.CONNECTOR_INSTANCE_INITIALIZATION_COUNT);
		rememberCounter(InternalCounters.CONNECTOR_INSTANCE_CONFIGURATION_COUNT);
		rememberCounter(InternalCounters.RESOURCE_SCHEMA_PARSE_COUNT);
		rememberResourceCacheStats();

		// WHEN
		displayWhen(TEST_NAME);
		PrismObject<ResourceType> resourceProvisioning = provisioningService.getObject(ResourceType.class, RESOURCE_DUMMY_OID, null, null, result);

		// THEN
		displayThen(TEST_NAME);
		display("Resource", resource);
		assertSuccess(result);

		assertHasSchema(resourceProvisioning, "provisioning resource");
		rememberSchemaMetadata(resourceProvisioning);

		// TODO not sure why are there 2 read counts. Should be 1. But this is not that important right now.
		// Some overhead on initial resource read is OK. What is important is that it does not increase during 
		// normal account operations.
		assertCounterIncrement(InternalCounters.RESOURCE_REPOSITORY_READ_COUNT, 2);
		assertCounterIncrement(InternalCounters.RESOURCE_SCHEMA_FETCH_COUNT, 1);
		assertCounterIncrement(InternalCounters.CONNECTOR_SCHEMA_PARSE_COUNT, 1);
		assertCounterIncrement(InternalCounters.CONNECTOR_CAPABILITIES_FETCH_COUNT, 1);
		assertCounterIncrement(InternalCounters.CONNECTOR_INSTANCE_INITIALIZATION_COUNT, 1);
		assertCounterIncrement(InternalCounters.CONNECTOR_INSTANCE_CONFIGURATION_COUNT, 1);
		rememberConnectorInstance(resourceProvisioning);
		assertCounterIncrement(InternalCounters.RESOURCE_SCHEMA_PARSE_COUNT, 1);

		PrismObject<ResourceType> resourceRepoAfter = repositoryService.getObject(ResourceType.class,
				RESOURCE_DUMMY_OID, null, result);
		assertHasSchema(resourceRepoAfter, "repo resource after");

		assertSchemaMetadataUnchanged(resourceRepoAfter);

		display("Resource cache", InternalMonitor.getResourceCacheStats());
		assertResourceCacheHitsIncrement(0);
		assertResourceCacheMissesIncrement(1);

		rememberResourceSchema(RefinedResourceSchemaImpl.getResourceSchema(resourceProvisioning, prismContext));
		rememberRefinedResourceSchema(RefinedResourceSchemaImpl.getRefinedSchema(resourceProvisioning));
		assertCounterIncrement(InternalCounters.RESOURCE_SCHEMA_PARSE_COUNT, 0);

		// Just refresh the resource used by other tests. This one has a complete schema.
		resourceType = resourceProvisioning.asObjectable();
		rememberResourceVersion(resourceType.getVersion());
	}

	@Test
	public void test011GetResourceAgain() throws Exception {
		final String TEST_NAME = "test011GetResourceAgain";
		displayTestTitle(TEST_NAME);
		// GIVEN
		OperationResult result = new OperationResult(TestDummyResourceAndSchemaCaching.class.getName()
				+ "." + TEST_NAME);

		// WHEN
		PrismObject<ResourceType> resourceProvisioning = provisioningService.getObject(ResourceType.class, RESOURCE_DUMMY_OID, null, null, result);

		// THEN
		display("Resource(1)", resource);
		assertSuccess(result);

		assertHasSchema(resourceProvisioning, "provisioning resource(1)");
		assertSchemaMetadataUnchanged(resourceProvisioning);

		assertCounterIncrement(InternalCounters.RESOURCE_REPOSITORY_READ_COUNT, 0);
		assertCounterIncrement(InternalCounters.RESOURCE_SCHEMA_FETCH_COUNT, 0);
		assertCounterIncrement(InternalCounters.CONNECTOR_SCHEMA_PARSE_COUNT, 0);
		assertCounterIncrement(InternalCounters.CONNECTOR_CAPABILITIES_FETCH_COUNT, 0);
		assertCounterIncrement(InternalCounters.CONNECTOR_INSTANCE_INITIALIZATION_COUNT, 0);
		assertCounterIncrement(InternalCounters.CONNECTOR_INSTANCE_CONFIGURATION_COUNT, 0);
		assertCounterIncrement(InternalCounters.RESOURCE_SCHEMA_PARSE_COUNT, 0);

		assertResourceVersionIncrement(resourceProvisioning, 0);

		display("Resource cache (1)", InternalMonitor.getResourceCacheStats());
		assertResourceCacheHitsIncrement(1);
		assertResourceCacheMissesIncrement(0);

		assertResourceSchemaUnchanged(RefinedResourceSchemaImpl.getResourceSchema(resourceProvisioning, prismContext));
		assertRefinedResourceSchemaUnchanged(RefinedResourceSchemaImpl.getRefinedSchema(resourceProvisioning));
		assertCounterIncrement(InternalCounters.RESOURCE_SCHEMA_PARSE_COUNT, 0);

		assertConnectorInstanceUnchanged(resourceProvisioning);

		// WHEN
		resourceProvisioning = provisioningService.getObject(ResourceType.class, RESOURCE_DUMMY_OID, null, null, result);

		// THEN
		display("Resource(2)", resource);
		assertSuccess(result);

		assertHasSchema(resourceProvisioning, "provisioning resource(2)");
		assertSchemaMetadataUnchanged(resourceProvisioning);

		assertCounterIncrement(InternalCounters.RESOURCE_SCHEMA_FETCH_COUNT, 0);
		assertCounterIncrement(InternalCounters.CONNECTOR_SCHEMA_PARSE_COUNT, 0);
		assertCounterIncrement(InternalCounters.CONNECTOR_CAPABILITIES_FETCH_COUNT, 0);
		assertCounterIncrement(InternalCounters.CONNECTOR_INSTANCE_INITIALIZATION_COUNT, 0);
		assertCounterIncrement(InternalCounters.CONNECTOR_INSTANCE_CONFIGURATION_COUNT, 0);

		assertResourceVersionIncrement(resourceProvisioning, 0);

		display("Resource cache (1)", InternalMonitor.getResourceCacheStats());
		assertResourceCacheHitsIncrement(1);
		assertResourceCacheMissesIncrement(0);

		assertResourceSchemaUnchanged(RefinedResourceSchemaImpl.getResourceSchema(resourceProvisioning, prismContext));
		assertRefinedResourceSchemaUnchanged(RefinedResourceSchemaImpl.getRefinedSchema(resourceProvisioning));
		assertCounterIncrement(InternalCounters.RESOURCE_SCHEMA_PARSE_COUNT, 0);

		assertConnectorInstanceUnchanged(resourceProvisioning);
	}

	/**
	 * Add new account. This is connector operation. Check that the initialized connector is
	 * reused and that it is not initialized again. Check that resource is still cached.
	 */
	@Test
	public void test012AddAccountGetResource() throws Exception {
		final String TEST_NAME = "test012AddAccountGetResource";
		displayTestTitle(TEST_NAME);
		// GIVEN
		OperationResult result = new OperationResult(TestDummyResourceAndSchemaCaching.class.getName()
				+ "." + TEST_NAME);

		// WHEN
		addAccount(ACCOUNT_WILL_FILE);

		// THEN
		display("Resource cache (1)", InternalMonitor.getResourceCacheStats());
		assertResourceCacheHitsIncrement(1);
		assertResourceCacheMissesIncrement(0);

		PrismObject<ResourceType> resourceProvisioning = provisioningService.getObject(ResourceType.class, RESOURCE_DUMMY_OID, null, null, result);
		display("Resource(2)", resource);
		assertSuccess(result);

		assertHasSchema(resourceProvisioning, "provisioning resource(2)");
		assertSchemaMetadataUnchanged(resourceProvisioning);

		assertCounterIncrement(InternalCounters.RESOURCE_REPOSITORY_READ_COUNT, 0);
		assertCounterIncrement(InternalCounters.RESOURCE_SCHEMA_FETCH_COUNT, 0);
		assertCounterIncrement(InternalCounters.CONNECTOR_SCHEMA_PARSE_COUNT, 0);
		assertCounterIncrement(InternalCounters.CONNECTOR_CAPABILITIES_FETCH_COUNT, 0);
		assertCounterIncrement(InternalCounters.CONNECTOR_INSTANCE_INITIALIZATION_COUNT, 0);
		assertCounterIncrement(InternalCounters.CONNECTOR_INSTANCE_CONFIGURATION_COUNT, 0);
		rememberConnectorInstance(resourceProvisioning);
		assertCounterIncrement(InternalCounters.RESOURCE_SCHEMA_PARSE_COUNT, 0);

		assertResourceVersionIncrement(resourceProvisioning, 0);

		display("Resource cache (2)", InternalMonitor.getResourceCacheStats());
		assertResourceCacheHitsIncrement(1);
		assertResourceCacheMissesIncrement(0);

		assertResourceSchemaUnchanged(RefinedResourceSchemaImpl.getResourceSchema(resourceProvisioning, prismContext));
		assertRefinedResourceSchemaUnchanged(RefinedResourceSchemaImpl.getRefinedSchema(resourceProvisioning));
		assertCounterIncrement(InternalCounters.RESOURCE_SCHEMA_PARSE_COUNT, 0);
	}
	
	@Test
	public void test013GetResourceNoFetch() throws Exception {
		final String TEST_NAME = "test013GetResourceNoFetch";
		displayTestTitle(TEST_NAME);
		// GIVEN
		OperationResult result = new OperationResult(TestDummyResourceAndSchemaCaching.class.getName()
				+ "." + TEST_NAME);

		Collection<SelectorOptions<GetOperationOptions>> options = GetOperationOptions.createNoFetchCollection();
		
		// WHEN
		PrismObject<ResourceType> resourceProvisioning = provisioningService.getObject(ResourceType.class, RESOURCE_DUMMY_OID, options, null, result);

		// THEN
		display("Resource(1)", resource);
		assertSuccess(result);

		assertHasSchema(resourceProvisioning, "provisioning resource(1)");
		assertSchemaMetadataUnchanged(resourceProvisioning);

		assertCounterIncrement(InternalCounters.RESOURCE_REPOSITORY_READ_COUNT, 0);
		assertCounterIncrement(InternalCounters.RESOURCE_SCHEMA_FETCH_COUNT, 0);
		assertCounterIncrement(InternalCounters.CONNECTOR_SCHEMA_PARSE_COUNT, 0);
		assertCounterIncrement(InternalCounters.CONNECTOR_CAPABILITIES_FETCH_COUNT, 0);
		assertCounterIncrement(InternalCounters.CONNECTOR_INSTANCE_INITIALIZATION_COUNT, 0);
		assertCounterIncrement(InternalCounters.CONNECTOR_INSTANCE_CONFIGURATION_COUNT, 0);
		assertCounterIncrement(InternalCounters.RESOURCE_SCHEMA_PARSE_COUNT, 0);

		assertResourceVersionIncrement(resourceProvisioning, 0);

		display("Resource cache (1)", InternalMonitor.getResourceCacheStats());
		assertResourceCacheHitsIncrement(1);
		assertResourceCacheMissesIncrement(0);

		assertResourceSchemaUnchanged(RefinedResourceSchemaImpl.getResourceSchema(resourceProvisioning, prismContext));
		assertRefinedResourceSchemaUnchanged(RefinedResourceSchemaImpl.getRefinedSchema(resourceProvisioning));
		assertCounterIncrement(InternalCounters.RESOURCE_SCHEMA_PARSE_COUNT, 0);

		assertConnectorInstanceUnchanged(resourceProvisioning);

		// WHEN
		resourceProvisioning = provisioningService.getObject(ResourceType.class, RESOURCE_DUMMY_OID, options, null, result);

		// THEN
		display("Resource(2)", resource);
		assertSuccess(result);

		assertHasSchema(resourceProvisioning, "provisioning resource(2)");
		assertSchemaMetadataUnchanged(resourceProvisioning);

		assertCounterIncrement(InternalCounters.RESOURCE_SCHEMA_FETCH_COUNT, 0);
		assertCounterIncrement(InternalCounters.CONNECTOR_SCHEMA_PARSE_COUNT, 0);
		assertCounterIncrement(InternalCounters.CONNECTOR_CAPABILITIES_FETCH_COUNT, 0);
		assertCounterIncrement(InternalCounters.CONNECTOR_INSTANCE_INITIALIZATION_COUNT, 0);
		assertCounterIncrement(InternalCounters.CONNECTOR_INSTANCE_CONFIGURATION_COUNT, 0);

		assertResourceVersionIncrement(resourceProvisioning, 0);

		display("Resource cache (1)", InternalMonitor.getResourceCacheStats());
		assertResourceCacheHitsIncrement(1);
		assertResourceCacheMissesIncrement(0);

		assertResourceSchemaUnchanged(RefinedResourceSchemaImpl.getResourceSchema(resourceProvisioning, prismContext));
		assertRefinedResourceSchemaUnchanged(RefinedResourceSchemaImpl.getRefinedSchema(resourceProvisioning));
		assertCounterIncrement(InternalCounters.RESOURCE_SCHEMA_PARSE_COUNT, 0);

		assertConnectorInstanceUnchanged(resourceProvisioning);
	}


	/**
	 * Change something that is not important. The cached resource should be refreshed, the schema re-parsed
	 * but the connector should still be cached.
	 */
	@Test
	public void test020ModifyAndGetResource() throws Exception {
		final String TEST_NAME = "test020ModifyAndGetResource";
		displayTestTitle(TEST_NAME);
		// GIVEN
		Task task = taskManager.createTaskInstance(TestDummyResourceAndSchemaCaching.class.getName()
				+ "." + TEST_NAME);
		OperationResult result = task.getResult();

		// Change something that's not that important
		ProjectionPolicyType projectionPolicyType = new ProjectionPolicyType();
		projectionPolicyType.setLegalize(true);

		ObjectDelta<ResourceType> objectDelta = prismContext.deltaFactory().object()
				.createModificationReplaceContainer(ResourceType.class, RESOURCE_DUMMY_OID,
				ResourceType.F_PROJECTION, projectionPolicyType);

		// WHEN
		provisioningService.modifyObject(ResourceType.class, RESOURCE_DUMMY_OID, objectDelta.getModifications(), null, null, task, result);

		// THEN
		assertSuccess(result);

		String versionAfter = repositoryService.getVersion(ResourceType.class, RESOURCE_DUMMY_OID, result);
		assertResourceVersionIncrement(versionAfter, 1);

		// WHEN
		PrismObject<ResourceType> resourceProvisioning = provisioningService.getObject(ResourceType.class, RESOURCE_DUMMY_OID, null, task, result);

		// THEN
		display("Resource", resource);
		assertSuccess(result);

		assertHasSchema(resourceProvisioning, "provisioning resource");
		assertSchemaMetadataUnchanged(resourceProvisioning);

		assertCounterIncrement(InternalCounters.RESOURCE_REPOSITORY_READ_COUNT, 1);
		assertCounterIncrement(InternalCounters.RESOURCE_SCHEMA_FETCH_COUNT, 0);
		assertCounterIncrement(InternalCounters.CONNECTOR_SCHEMA_PARSE_COUNT, 0);
		assertCounterIncrement(InternalCounters.CONNECTOR_CAPABILITIES_FETCH_COUNT, 0);
		assertCounterIncrement(InternalCounters.CONNECTOR_INSTANCE_INITIALIZATION_COUNT, 0);
		assertCounterIncrement(InternalCounters.CONNECTOR_INSTANCE_CONFIGURATION_COUNT, 0);
		assertCounterIncrement(InternalCounters.RESOURCE_SCHEMA_PARSE_COUNT, 1);

		assertResourceVersionIncrement(resourceProvisioning, 0);

		display("Resource cache", InternalMonitor.getResourceCacheStats());
		assertResourceCacheHitsIncrement(0);
		assertResourceCacheMissesIncrement(1);

		// There are expected to be re-parsed
		rememberResourceSchema(RefinedResourceSchemaImpl.getResourceSchema(resourceProvisioning, prismContext));
		rememberRefinedResourceSchema(RefinedResourceSchemaImpl.getRefinedSchema(resourceProvisioning));
		assertCounterIncrement(InternalCounters.RESOURCE_SCHEMA_PARSE_COUNT, 0);

		assertConnectorInstanceUnchanged(resourceProvisioning);
	}

	/**
	 * Get the account. This is connector operation. Check that the initialized connector is
	 * reused and that it is not initialized again. Check that resource is still cached.
	 */
	@Test
	public void test022GetAccountGetResource() throws Exception {
		final String TEST_NAME = "test012AddAccountGetResource";
		displayTestTitle(TEST_NAME);
		// GIVEN
		OperationResult result = new OperationResult(TestDummyResourceAndSchemaCaching.class.getName()
				+ "." + TEST_NAME);

		// WHEN
		getAccount(ACCOUNT_WILL_OID);

		// THEN
		display("Resource cache (1)", InternalMonitor.getResourceCacheStats());
		assertResourceCacheHitsIncrement(1);
		assertResourceCacheMissesIncrement(0);

		PrismObject<ResourceType> resourceProvisioning = provisioningService.getObject(ResourceType.class, RESOURCE_DUMMY_OID, null, null, result);
		display("Resource(2)", resource);
		result.computeStatus();
		TestUtil.assertSuccess(result);

		assertHasSchema(resourceProvisioning, "provisioning resource(2)");
		assertSchemaMetadataUnchanged(resourceProvisioning);

		assertCounterIncrement(InternalCounters.RESOURCE_REPOSITORY_READ_COUNT, 0);
		assertCounterIncrement(InternalCounters.RESOURCE_SCHEMA_FETCH_COUNT, 0);
		assertCounterIncrement(InternalCounters.CONNECTOR_SCHEMA_PARSE_COUNT, 0);
		assertCounterIncrement(InternalCounters.CONNECTOR_CAPABILITIES_FETCH_COUNT, 0);
		assertCounterIncrement(InternalCounters.CONNECTOR_INSTANCE_INITIALIZATION_COUNT, 0);
		assertCounterIncrement(InternalCounters.CONNECTOR_INSTANCE_CONFIGURATION_COUNT, 0);
		rememberConnectorInstance(resourceProvisioning);
		assertCounterIncrement(InternalCounters.RESOURCE_SCHEMA_PARSE_COUNT, 0);

		assertResourceVersionIncrement(resourceProvisioning, 0);

		display("Resource cache (2)", InternalMonitor.getResourceCacheStats());
		assertResourceCacheHitsIncrement(1);
		assertResourceCacheMissesIncrement(0);

		assertResourceSchemaUnchanged(RefinedResourceSchemaImpl.getResourceSchema(resourceProvisioning, prismContext));
		assertRefinedResourceSchemaUnchanged(RefinedResourceSchemaImpl.getRefinedSchema(resourceProvisioning));
		assertCounterIncrement(InternalCounters.RESOURCE_SCHEMA_PARSE_COUNT, 0);
	}

	/**
	 * Change resource directly in repo. This simulates the change done by other node. The connector cache should
	 * be refreshed.
	 *
	 * Change something that is not important. The cached resource should be refreshed, the schema re-parsed
	 * but the connector should still be cached.
	 */
	@Test
	public void test023ModifyRepoAndGetResource() throws Exception {
		final String TEST_NAME = "test023ModifyRepoAndGetResource";
		displayTestTitle(TEST_NAME);
		// GIVEN
		Task task = taskManager.createTaskInstance(TestDummyResourceAndSchemaCaching.class.getName()
				+ "." + TEST_NAME);
		OperationResult result = task.getResult();

		// Change something that's not that important
		ProjectionPolicyType projectionPolicyType = new ProjectionPolicyType();
		projectionPolicyType.setLegalize(true);

		ObjectDelta<ResourceType> objectDelta = prismContext.deltaFactory().object()
				.createModificationReplaceContainer(ResourceType.class, RESOURCE_DUMMY_OID,
				ResourceType.F_PROJECTION, projectionPolicyType);

		// WHEN
		repositoryService.modifyObject(ResourceType.class, RESOURCE_DUMMY_OID, objectDelta.getModifications(), result);

		// THEN
		assertSuccess(result);

		String versionAfter = repositoryService.getVersion(ResourceType.class, RESOURCE_DUMMY_OID, result);
		assertResourceVersionIncrement(versionAfter, 1);

		// WHEN
		PrismObject<ResourceType> resourceProvisioning = provisioningService.getObject(ResourceType.class, RESOURCE_DUMMY_OID, null, task, result);

		// THEN
		display("Resource", resource);
		assertSuccess(result);

		assertHasSchema(resourceProvisioning, "provisioning resource");
		assertSchemaMetadataUnchanged(resourceProvisioning);

		assertCounterIncrement(InternalCounters.RESOURCE_REPOSITORY_READ_COUNT, 1);
		assertCounterIncrement(InternalCounters.RESOURCE_SCHEMA_FETCH_COUNT, 0);
		assertCounterIncrement(InternalCounters.CONNECTOR_SCHEMA_PARSE_COUNT, 0);
		assertCounterIncrement(InternalCounters.CONNECTOR_CAPABILITIES_FETCH_COUNT, 0);
		assertCounterIncrement(InternalCounters.CONNECTOR_INSTANCE_INITIALIZATION_COUNT, 0);
		assertCounterIncrement(InternalCounters.CONNECTOR_INSTANCE_CONFIGURATION_COUNT, 0);
		assertCounterIncrement(InternalCounters.RESOURCE_SCHEMA_PARSE_COUNT, 1);

		assertResourceVersionIncrement(resourceProvisioning, 0);

		display("Resource cache", InternalMonitor.getResourceCacheStats());
		assertResourceCacheHitsIncrement(0);
		assertResourceCacheMissesIncrement(1);

		// There are expected to be re-parsed
		rememberResourceSchema(RefinedResourceSchemaImpl.getResourceSchema(resourceProvisioning, prismContext));
		rememberRefinedResourceSchema(RefinedResourceSchemaImpl.getRefinedSchema(resourceProvisioning));
		assertCounterIncrement(InternalCounters.RESOURCE_SCHEMA_PARSE_COUNT, 0);

		assertConnectorInstanceUnchanged(resourceProvisioning);
	}

	/**
	 * Change part of connector configuration. The cached resource should be refreshed, the schema re-parsed.
	 * The connector also needs to re-initialized.
	 */
	@Test
	public void test030ModifyConnectorConfigAndGetResource() throws Exception {
		final String TEST_NAME = "test030ModifyConnectorConfigAndGetResource";
		displayTestTitle(TEST_NAME);
		// GIVEN
		Task task = taskManager.createTaskInstance(TestDummyResourceAndSchemaCaching.class.getName()
				+ "." + TEST_NAME);
		OperationResult result = task.getResult();

		// Change part of connector configuration. We change quite a useless part. But midPoint does not know that
		// it is useless and need to re-initialize the connector
		Collection<? extends ItemDelta> modifications = new ArrayList<>(1);
		PropertyDelta<String> uselessStringDelta = createUselessStringDelta("patlama chamalalija paprtala");
		((Collection)modifications).add(uselessStringDelta);

		// WHEN
		provisioningService.modifyObject(ResourceType.class, RESOURCE_DUMMY_OID, modifications, null, null, task, result);

		// THEN
		assertSuccess(result);

		assertConnectorConfigChanged();
	}

	/**
	 * Change part of connector configuration. Change it directly in repo to simulate change
	 * from another midPoint node.
	 *
	 * The cached resource should be refreshed, the schema re-parsed.
	 * The connector also needs to re-initialized.
	 */
	@Test
	public void test031ModifyConnectorConfigRepoAndGetResource() throws Exception {
		final String TEST_NAME = "test031ModifyConnectorConfigRepoAndGetResource";
		displayTestTitle(TEST_NAME);
		// GIVEN
		Task task = taskManager.createTaskInstance(TestDummyResourceAndSchemaCaching.class.getName()
				+ "." + TEST_NAME);
		OperationResult result = task.getResult();

		// Change part of connector configuration. We change quite a useless part. But midPoint does not know that
		// it is useless and need to re-initialize the connector
		Collection<? extends ItemDelta> modifications = new ArrayList<>(1);
		PropertyDelta<String> uselessStringDelta = createUselessStringDelta("Rudolfovo Tajemstvi");
		((Collection)modifications).add(uselessStringDelta);

		// WHEN
		repositoryService.modifyObject(ResourceType.class, RESOURCE_DUMMY_OID, modifications, result);

		// THEN
		result.computeStatus();
		TestUtil.assertSuccess(result);

		assertConnectorConfigChanged();
	}

	@Test
	public void test900DeleteResource() throws Exception {
		final String TEST_NAME = "test900DeleteResource";
		displayTestTitle(TEST_NAME);
		// GIVEN
		Task task = taskManager.createTaskInstance(TestDummyResourceAndSchemaCaching.class.getName()
				+ "." + TEST_NAME);
		OperationResult result = task.getResult();

		// WHEN
		provisioningService.deleteObject(ResourceType.class, RESOURCE_DUMMY_OID, null, null, task, result);

		// THEN
		result.computeStatus();
		TestUtil.assertSuccess(result);

		try {
			repositoryService.getObject(ResourceType.class, RESOURCE_DUMMY_OID, null, result);
			AssertJUnit.fail("Resource not gone from repo");
		} catch (ObjectNotFoundException e) {
			// This is expected
		}

		try {
			provisioningService.getObject(ResourceType.class, RESOURCE_DUMMY_OID, null, task, result);
			AssertJUnit.fail("Resource not gone from provisioning");
		} catch (ObjectNotFoundException e) {
			// This is expected
		}

	}

	private PropertyDelta<String> createUselessStringDelta(String newVal) {
		PropertyDelta<String> uselessStringDelta = prismContext.deltaFactory().property().createModificationReplaceProperty(
				ItemPath.create(ResourceType.F_CONNECTOR_CONFIGURATION,
						SchemaConstants.CONNECTOR_SCHEMA_CONFIGURATION_PROPERTIES_ELEMENT_QNAME,
						DummyResourceContoller.CONNECTOR_DUMMY_USELESS_STRING_QNAME),
				prismContext.definitionFactory().createPropertyDefinition(DummyResourceContoller.CONNECTOR_DUMMY_USELESS_STRING_QNAME, DOMUtil.XSD_STRING),
				newVal);
		return uselessStringDelta;
	}

	private void assertConnectorConfigChanged() throws ObjectNotFoundException, SchemaException, CommunicationException, ConfigurationException, SecurityViolationException, ExpressionEvaluationException {
		Task task = taskManager.createTaskInstance(TestDummyResourceAndSchemaCaching.class.getName()
				+ ".assertConnectorConfigChanged");
		OperationResult result = task.getResult();

		String versionAfter = repositoryService.getVersion(ResourceType.class, RESOURCE_DUMMY_OID, result);
		assertResourceVersionIncrement(versionAfter, 1);

		// WHEN
		PrismObject<ResourceType> resourceProvisioning = provisioningService.getObject(ResourceType.class, RESOURCE_DUMMY_OID, null, task, result);

		// THEN
		display("Resource", resource);
		assertSuccess(result);

		assertHasSchema(resourceProvisioning, "provisioning resource");
		assertSchemaMetadataUnchanged(resourceProvisioning);

		assertCounterIncrement(InternalCounters.RESOURCE_REPOSITORY_READ_COUNT, 1);
		assertCounterIncrement(InternalCounters.RESOURCE_SCHEMA_FETCH_COUNT, 0);
		assertCounterIncrement(InternalCounters.CONNECTOR_SCHEMA_PARSE_COUNT, 0);
		assertCounterIncrement(InternalCounters.CONNECTOR_CAPABILITIES_FETCH_COUNT, 0);
		assertCounterIncrement(InternalCounters.CONNECTOR_INSTANCE_INITIALIZATION_COUNT, 0);
		assertCounterIncrement(InternalCounters.CONNECTOR_INSTANCE_CONFIGURATION_COUNT, 0);
		assertCounterIncrement(InternalCounters.RESOURCE_SCHEMA_PARSE_COUNT, 1);

		assertResourceVersionIncrement(resourceProvisioning, 0);

		display("Resource cache", InternalMonitor.getResourceCacheStats());
		assertResourceCacheHitsIncrement(0);
		assertResourceCacheMissesIncrement(1);

		// There are expected to be re-parsed
		rememberResourceSchema(RefinedResourceSchemaImpl.getResourceSchema(resourceProvisioning, prismContext));
		rememberRefinedResourceSchema(RefinedResourceSchemaImpl.getRefinedSchema(resourceProvisioning));
		assertCounterIncrement(InternalCounters.RESOURCE_SCHEMA_PARSE_COUNT, 0);

		// WHEN
		getAccount(ACCOUNT_WILL_OID);

		// THEN
		display("Resource cache (2)", InternalMonitor.getResourceCacheStats());
		assertResourceCacheHitsIncrement(1);
		assertResourceCacheMissesIncrement(0);

		assertCounterIncrement(InternalCounters.RESOURCE_REPOSITORY_READ_COUNT, 0);
		assertCounterIncrement(InternalCounters.RESOURCE_SCHEMA_FETCH_COUNT, 0);
		assertCounterIncrement(InternalCounters.CONNECTOR_SCHEMA_PARSE_COUNT, 0);
		assertCounterIncrement(InternalCounters.CONNECTOR_CAPABILITIES_FETCH_COUNT, 0);
		assertCounterIncrement(InternalCounters.CONNECTOR_INSTANCE_INITIALIZATION_COUNT, 0);
		assertCounterIncrement(InternalCounters.CONNECTOR_INSTANCE_CONFIGURATION_COUNT, 1);
		assertCounterIncrement(InternalCounters.RESOURCE_SCHEMA_PARSE_COUNT, 0);

		// It is only reconfigured, but the instance is the same (MID-5068)
		assertConnectorInstanceUnchanged(resourceProvisioning);
	}

	private String addAccount(File file) throws SchemaException, ObjectAlreadyExistsException, CommunicationException, ObjectNotFoundException, ConfigurationException, SecurityViolationException, IOException, ExpressionEvaluationException, PolicyViolationException {
		Task task = taskManager.createTaskInstance(TestDummyResourceAndSchemaCaching.class.getName()
				+ ".addAccount");
		OperationResult result = task.getResult();
		PrismObject<ShadowType> account = PrismTestUtil.parseObject(file);
		String oid = provisioningService.addObject(account, null, null, task, result);
		result.computeStatus();
		TestUtil.assertSuccess(result);
		return oid;
	}

	private PrismObject<ShadowType> getAccount(String oid) throws ObjectNotFoundException, CommunicationException, SchemaException, ConfigurationException, SecurityViolationException, ExpressionEvaluationException {
		OperationResult result = new OperationResult(TestDummyResourceAndSchemaCaching.class.getName()
				+ ".getAccount");
		PrismObject<ShadowType> account = provisioningService.getObject(ShadowType.class, oid, null, null, result);
		result.computeStatus();
		TestUtil.assertSuccess(result);
		return account;
	}

}
