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

package com.evolveum.midpoint.model.impl.sync;

import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertNotNull;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.context.ContextConfiguration;
import org.testng.AssertJUnit;
import org.testng.annotations.Test;

import com.evolveum.midpoint.model.impl.AbstractInternalModelIntegrationTest;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.delta.ItemDelta;
import com.evolveum.midpoint.prism.delta.PropertyDelta;
import com.evolveum.midpoint.prism.polystring.PolyString;
import com.evolveum.midpoint.prism.util.PrismTestUtil;
import com.evolveum.midpoint.repo.api.RepositoryService;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.test.IntegrationTestTools;
import com.evolveum.midpoint.test.util.TestUtil;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ConditionalSearchFilterType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ObjectSynchronizationType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ResourceType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ShadowType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.SystemConfigurationType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.UserType;
import com.evolveum.prism.xml.ns._public.types_3.PolyStringType;

@ContextConfiguration(locations = {"classpath:ctx-model-test-main.xml"})
@DirtiesContext(classMode = ClassMode.AFTER_CLASS)
public class TestCorrelationConfiramtionEvaluator extends AbstractInternalModelIntegrationTest {

	private static final String TEST_DIR = "src/test/resources/sync";
	private static final String CORRELATION_OR_FILTER = TEST_DIR + "/correlation-or-filter.xml";
	private static final String CORRELATION_CASE_INSENSITIVE = TEST_DIR + "/correlation-case-insensitive.xml";
	private static final String CORRELATION_CASE_INSENSITIVE_EMPL_NUMBER = TEST_DIR + "/correlation-case-insensitive_empl_number.xml";
	private static final String CORRELATION_FIRST_FILTER = TEST_DIR + "/correlation-first-filter.xml";
	private static final String CORRELATION_SECOND_FILTER = TEST_DIR + "/correlation-second-filter.xml";
	private static final String CORRELATION_WITH_CONDITION = TEST_DIR + "/correlation-with-condition.xml";
	private static final String CORRELATION_WITH_CONDITION_EMPL_NUMBER = TEST_DIR + "/correlation-with-condition-emplNumber.xml";

	@Autowired(required=true)
	private RepositoryService repositoryService;

	@Autowired(required = true)
	private SynchronizationExpressionsEvaluator evaluator;

	@Test
	public void test001CorrelationOrFilter() throws Exception{
		String TEST_NAME = "test001CorrelationOrFilter";
		displayTestTitle(TEST_NAME);

		Task task = taskManager.createTaskInstance(TEST_NAME);
		OperationResult result = task.getResult();

		PrismObject<UserType> userType = repositoryService.getObject(UserType.class, USER_JACK_OID, null, result);
		//assert jack
		assertNotNull(userType);

		ShadowType shadow = parseObjectType(ACCOUNT_SHADOW_JACK_DUMMY_FILE, ShadowType.class);

		ConditionalSearchFilterType filter = PrismTestUtil.parseAtomicValue(new File(CORRELATION_OR_FILTER), ConditionalSearchFilterType.COMPLEX_TYPE);
		List<ConditionalSearchFilterType> filters = new ArrayList<>();
		filters.add(filter);

		ResourceType resourceType = parseObjectType(RESOURCE_DUMMY_FILE, ResourceType.class);
		IntegrationTestTools.display("Queries", filters);

		// WHEN
		List<PrismObject<UserType>> matchedUsers = evaluator.findFocusesByCorrelationRule(UserType.class,
				shadow, filters, resourceType, getSystemConfiguration(), task, result);

		// THEN
		assertNotNull("Correlation evaluator returned null collection of matched users.", matchedUsers);
		assertEquals("Found more than one user.", 1, matchedUsers.size());

		PrismObject<UserType> jack = matchedUsers.get(0);
		assertUser(jack, "c0c010c0-d34d-b33f-f00d-111111111111", "jack", "Jack Sparrow", "Jack", "Sparrow");

	}

	@Test
	public void test002CorrelationMoreThanOne() throws Exception{
		String TEST_NAME = "test002CorrelationMoreThanOne";
		displayTestTitle(TEST_NAME);

		Task task = taskManager.createTaskInstance(TEST_NAME);
		OperationResult result = task.getResult();

		PrismObject<UserType> userType = repositoryService.getObject(UserType.class, USER_JACK_OID, null, result);
		//assert jack
		assertNotNull(userType);

		ShadowType shadow = parseObjectType(ACCOUNT_SHADOW_JACK_DUMMY_FILE, ShadowType.class);

		List<ConditionalSearchFilterType> filters = new ArrayList<>();
        ConditionalSearchFilterType filter = PrismTestUtil.parseAtomicValue(new File(CORRELATION_FIRST_FILTER), ConditionalSearchFilterType.COMPLEX_TYPE);
		filters.add(filter);

		filter = PrismTestUtil.parseAtomicValue(new File(CORRELATION_SECOND_FILTER), ConditionalSearchFilterType.COMPLEX_TYPE);
		filters.add(filter);

		ResourceType resourceType = parseObjectType(RESOURCE_DUMMY_FILE, ResourceType.class);
		List<PrismObject<UserType>> matchedUsers = evaluator.findFocusesByCorrelationRule(UserType.class,
				shadow, filters, resourceType, getSystemConfiguration(), task, result);

		assertNotNull("Correlation evaluator returned null collection of matched users.", matchedUsers);
		assertEquals("Found more than one user.", 1, matchedUsers.size());

		PrismObject<UserType> jack = matchedUsers.get(0);
		assertUser(jack, "c0c010c0-d34d-b33f-f00d-111111111111", "jack", "Jack Sparrow", "Jack", "Sparrow");

	}

	@Test
	public void test003CorrelationWithCondition() throws Exception{
		String TEST_NAME = "test003CorrelationWithCondition";
		displayTestTitle(TEST_NAME);

		Task task = taskManager.createTaskInstance(TEST_NAME);
		OperationResult result = task.getResult();

//		importObjectFromFile(USER_JACK_FILENAME);

		PrismObject<UserType> userType = repositoryService.getObject(UserType.class, USER_JACK_OID, null, result);
		//assert jack
		assertNotNull(userType);

		ShadowType shadow = parseObjectType(ACCOUNT_SHADOW_JACK_DUMMY_FILE, ShadowType.class);

		List<ConditionalSearchFilterType> queries = new ArrayList<>();
        ConditionalSearchFilterType query = PrismTestUtil.parseAtomicValue(new File(CORRELATION_WITH_CONDITION), ConditionalSearchFilterType.COMPLEX_TYPE);
		queries.add(query);

		query = PrismTestUtil.parseAtomicValue(new File(CORRELATION_WITH_CONDITION_EMPL_NUMBER), ConditionalSearchFilterType.COMPLEX_TYPE);
		queries.add(query);

		ResourceType resourceType = parseObjectType(RESOURCE_DUMMY_FILE, ResourceType.class);
		List<PrismObject<UserType>> matchedUsers = evaluator.findFocusesByCorrelationRule(UserType.class,
				shadow, queries, resourceType, getSystemConfiguration(), task, result);

		assertNotNull("Correlation evaluator returned null collection of matched users.", matchedUsers);
		assertEquals("Found more than one user.", 1, matchedUsers.size());

		PrismObject<UserType> jack = matchedUsers.get(0);
		assertUser(jack, "c0c010c0-d34d-b33f-f00d-111111111111", "jack", "Jack Sparrow", "Jack", "Sparrow");

	}

	@Test
	public void test004CorrelationMatchCaseInsensitive() throws Exception{
		String TEST_NAME = "test004CorrelationMatchCaseInsensitive";
		displayTestTitle(TEST_NAME);

		Task task = taskManager.createTaskInstance(TEST_NAME);
		OperationResult result = task.getResult();
		
		PrismObject<UserType> userType = repositoryService.getObject(UserType.class, USER_JACK_OID, null, result);
		//assert jack
		assertNotNull(userType);
		userType.asObjectable().setName(new PolyStringType("JACK"));

		SynchronizationContext<UserType> syncCtx = createSynchronizationContext(ACCOUNT_SHADOW_JACK_DUMMY_FILE, CORRELATION_CASE_INSENSITIVE, RESOURCE_DUMMY_FILE, task, result);

		try{
			boolean matchedUsers = evaluator.matchFocusByCorrelationRule(syncCtx, userType);
	
			System.out.println("matched users " + matchedUsers);
	
			AssertJUnit.assertTrue(matchedUsers);
		} catch (Exception ex){
			LOGGER.error("exception occured: {}", ex.getMessage(), ex);
			throw ex;
		}

	}

	@Test
	public void test005CorrelationMatchCaseInsensitive() throws Exception{
		String TEST_NAME = "test005CorrelationMatchCaseInsensitive";
		displayTestTitle(TEST_NAME);

		Task task = taskManager.createTaskInstance(TEST_NAME);
		OperationResult result = task.getResult();

		PrismObject<UserType> userType = repositoryService.getObject(UserType.class, USER_JACK_OID, null, result);
		//assert jack
		assertNotNull(userType);
		userType.asObjectable().setEmployeeNumber("JaCk");
		
		SynchronizationContext<UserType> syncCtx = createSynchronizationContext(ACCOUNT_SHADOW_JACK_DUMMY_FILE, CORRELATION_CASE_INSENSITIVE_EMPL_NUMBER, RESOURCE_DUMMY_FILE, task, result);
		
		try{
			boolean matchedUsers = evaluator.matchFocusByCorrelationRule(syncCtx, userType);

			System.out.println("matched users " + matchedUsers);

			AssertJUnit.assertTrue(matchedUsers);
		} catch (Exception ex){
			LOGGER.error("exception occured: {}", ex.getMessage(), ex);
			throw ex;
		}

	}
	
	private SynchronizationContext<UserType> createSynchronizationContext(File account, String correlationFilter, File resource, Task task, OperationResult result) throws SchemaException, IOException {
		
		ShadowType shadow = parseObjectType(account, ShadowType.class);

        ConditionalSearchFilterType query = PrismTestUtil.parseAtomicValue(new File(correlationFilter), ConditionalSearchFilterType.COMPLEX_TYPE);

        ResourceType resourceType = parseObjectType(resource, ResourceType.class);
		resourceType.getSynchronization().getObjectSynchronization().get(0).getCorrelation().clear();
		resourceType.getSynchronization().getObjectSynchronization().get(0).getCorrelation().add(query);
		
		ObjectSynchronizationType objectSynchronizationType = resourceType.getSynchronization().getObjectSynchronization().get(0);
		PrismObject<SystemConfigurationType> systemConfiguration = systemObjectCache.getSystemConfiguration(result);
		assertNotNull("Unexpected null system configuration", systemConfiguration);
		
		SynchronizationContext<UserType> syncCtx = new SynchronizationContext<>(shadow.asPrismObject(), shadow.asPrismObject(), resourceType.asPrismObject(), null, prismContext, task, result);
		syncCtx.setSystemConfiguration(systemConfiguration);
		syncCtx.setObjectSynchronization(objectSynchronizationType);
		syncCtx.setFocusClass(UserType.class);
		return syncCtx;
	}


	@Test
	public void test006CorrelationFindCaseInsensitive() throws Exception{
		String TEST_NAME = "test006CorrelationFindCaseInsensitive";
		displayTestTitle(TEST_NAME);

		Task task = taskManager.createTaskInstance(TEST_NAME);
		OperationResult result = task.getResult();


		PrismObject<UserType> userType = repositoryService.getObject(UserType.class, USER_JACK_OID, null, result);
		//assert jack
		assertNotNull(userType);

		ShadowType shadow = parseObjectType(ACCOUNT_SHADOW_JACK_DUMMY_FILE, ShadowType.class);

        ConditionalSearchFilterType query = PrismTestUtil.parseAtomicValue(new File(CORRELATION_CASE_INSENSITIVE), ConditionalSearchFilterType.COMPLEX_TYPE);
		List<ConditionalSearchFilterType> queries = new ArrayList<>();
		queries.add(query);

		ResourceType resourceType = parseObjectType(RESOURCE_DUMMY_FILE, ResourceType.class);
		userType.asObjectable().setName(new PolyStringType("JACK"));
		Collection<? extends ItemDelta> modifications = prismContext.deltaFactory().property()
				.createModificationReplacePropertyCollection(UserType.F_NAME, userType.getDefinition(), new PolyString("JACK", "jack"));
		repositoryService.modifyObject(UserType.class, USER_JACK_OID, modifications, result);

		List<PrismObject<UserType>> matchedUsers = evaluator.findFocusesByCorrelationRule(UserType.class,
				shadow, queries, resourceType, getSystemConfiguration(), task, result);

		System.out.println("matched users " + matchedUsers);

		assertNotNull("Correlation evaluator returned null collection of matched users.", matchedUsers);
		assertEquals("Found more than one user.", 1, matchedUsers.size());

		PrismObject<UserType> jack = matchedUsers.get(0);
		assertUser(jack, "c0c010c0-d34d-b33f-f00d-111111111111", "JACK", "Jack Sparrow", "Jack", "Sparrow");

	}
}
