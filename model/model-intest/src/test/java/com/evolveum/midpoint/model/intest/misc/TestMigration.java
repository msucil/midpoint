/*
 * Copyright (c) 2019 Evolveum
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
package com.evolveum.midpoint.model.intest.misc;

import static org.testng.AssertJUnit.assertEquals;

import java.io.File;

import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.context.ContextConfiguration;
import org.testng.annotations.Test;

import com.evolveum.midpoint.model.api.authentication.CompiledUserProfile;
import com.evolveum.midpoint.model.intest.AbstractInitializedModelIntegrationTest;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.query.ObjectFilter;
import com.evolveum.midpoint.prism.query.ObjectQuery;
import com.evolveum.midpoint.prism.util.PrismTestUtil;
import com.evolveum.midpoint.schema.SearchResultList;
import com.evolveum.midpoint.schema.constants.SchemaConstants;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.schema.util.ObjectQueryUtil;
import com.evolveum.midpoint.security.api.MidPointPrincipal;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ActivationStatusType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ArchetypeType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.AssignmentPolicyEnforcementType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ObjectType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.OrgType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.RichHyperlinkType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.RoleType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ShadowKindType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ShadowType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.UserType;

/**
 * Tests for various migration and temporary situations.
 * 
 * @author Radovan Semancik
 */
@ContextConfiguration(locations = {"classpath:ctx-model-intest-test-main.xml"})
@DirtiesContext(classMode = ClassMode.AFTER_CLASS)
public class TestMigration extends AbstractInitializedModelIntegrationTest {

	public static final File TEST_DIR = new File("src/test/resources/misc");
	
	// Shadow without primaryIdentifierValue, e.g. shadow how it would look like in midPoint 3.9.
	public static final File SHADOW_ACCOUNT_DUMMY_LOST1_FILE = new File(TEST_DIR, "shadow-dummy-lost1.xml");
	public static final String SHADOW_ACCOUNT_DUMMY_LOST1_OID = "69ec62da-65a3-11e9-9aa7-8b3feffc5f05";
	public static final String ACCOUNT_DUMMY_LOST1_NAME = "lost1";
	public static final String ACCOUNT_DUMMY_LOST1_FULL_NAME = "Lost One";
	
	protected static final File TASK_SHADOW_REFRESH_FILE = new File(TEST_DIR, "task-shadow-refresh.xml");
	protected static final String TASK_SHADOW_REFRESH_OID = "eb8f5be6-2b51-11e7-848c-2fd84a283b03";
	
	protected static final File TASK_SHADOW_REFRESH_EXPLICIT_DUMMY_FILE = new File(TEST_DIR, "task-shadow-refresh-explicit-dummy.xml");
	protected static final String TASK_SHADOW_REFRESH_EXPLICIT_DUMMY_OID = "220865f2-65a5-11e9-a835-9b2de4ec0be6";

	
	@Override
    public void initSystem(Task initTask, OperationResult initResult) throws Exception {
        super.initSystem(initTask, initResult);
        
        getDummyResourceController().addAccount(ACCOUNT_DUMMY_LOST1_NAME, ACCOUNT_DUMMY_LOST1_FULL_NAME);
        importObjectFromFile(SHADOW_ACCOUNT_DUMMY_LOST1_FILE, initTask, initResult);
    }
	
	/**
	 * Make sure the initialization haven't fixed the missing primaryIdentifierValue already.
	 */
    @Test
    public void test050SanityLost1() throws Exception {
		final String TEST_NAME = "test050SanityLost1";
        displayTestTitle(TEST_NAME);

        // WHEN
        displayWhen(TEST_NAME);
        PrismObject<ShadowType> shadowLost1Repo = getShadowRepo(SHADOW_ACCOUNT_DUMMY_LOST1_OID);

        // THEN
        displayThen(TEST_NAME);

        assertShadow(shadowLost1Repo, "Repo shadow")
        	.assertPrimaryIdentifierValue(null);
    }
    
    /**
     * Import ordinary refresh task. This should not touch the lost1 shadow yet,
     * as it does not have any pending operations.
     */
    @Test
    public void test100RefreshTaskDefault() throws Exception {
		final String TEST_NAME = "test100RefreshTaskDefault";
        displayTestTitle(TEST_NAME);

        addObject(TASK_SHADOW_REFRESH_FILE);

        // WHEN
        displayWhen(TEST_NAME);
        waitForTaskStart(TASK_SHADOW_REFRESH_OID, false);
        waitForTaskFinish(TASK_SHADOW_REFRESH_OID, false);

        // THEN
        displayThen(TEST_NAME);

        PrismObject<ShadowType> shadowLost1Repo = getShadowRepo(SHADOW_ACCOUNT_DUMMY_LOST1_OID);
        assertShadow(shadowLost1Repo, "Repo shadow")
        	.assertPrimaryIdentifierValue(null);
    }
    
    /**
     * Import refresh task with an explicit filter.
     * This should process lost1 shadow.
     * MID-5293
     */
    @Test
    public void test110RefreshTaskExplicitDummy() throws Exception {
		final String TEST_NAME = "test110RefreshTaskExplicitDummy";
        displayTestTitle(TEST_NAME);

        addObject(TASK_SHADOW_REFRESH_EXPLICIT_DUMMY_FILE);

        // WHEN
        displayWhen(TEST_NAME);
        waitForTaskStart(TASK_SHADOW_REFRESH_EXPLICIT_DUMMY_OID, false);
        waitForTaskFinish(TASK_SHADOW_REFRESH_EXPLICIT_DUMMY_OID, false);

        // THEN
        displayThen(TEST_NAME);

        PrismObject<ShadowType> shadowLost1Repo = getShadowRepo(SHADOW_ACCOUNT_DUMMY_LOST1_OID);
        assertShadow(shadowLost1Repo, "Repo shadow")
        	.assertPrimaryIdentifierValue(ACCOUNT_DUMMY_LOST1_NAME);
    }
    
}
