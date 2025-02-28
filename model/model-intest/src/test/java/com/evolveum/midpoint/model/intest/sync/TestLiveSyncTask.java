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
package com.evolveum.midpoint.model.intest.sync;

import static org.testng.AssertJUnit.assertNotNull;
import static org.testng.AssertJUnit.assertNull;

import java.io.FileNotFoundException;
import java.util.concurrent.TimeUnit;

import com.evolveum.midpoint.schema.constants.SchemaConstants;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.context.ContextConfiguration;
import org.testng.annotations.Test;

import com.evolveum.icf.dummy.resource.DummyAccount;
import com.evolveum.icf.dummy.resource.DummySyncStyle;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.test.DummyResourceContoller;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ResourceType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.UserType;

/**
 * @author semancik
 *
 */
@ContextConfiguration(locations = {"classpath:ctx-model-intest-test-main.xml"})
@DirtiesContext(classMode = ClassMode.AFTER_CLASS)
public class TestLiveSyncTask extends AbstractSynchronizationStoryTest {

	@Override
	public void initSystem(Task initTask, OperationResult initResult) throws Exception {
		super.initSystem(initTask, initResult);

		getDummyResource(RESOURCE_DUMMY_GREEN_NAME).setSyncStyle(DummySyncStyle.SMART);
		getDummyResource().setSyncStyle(DummySyncStyle.DUMB);
		getDummyResource(RESOURCE_DUMMY_BLUE_NAME).setSyncStyle(DummySyncStyle.SMART);
	}

	@Override
	protected String getExpectedChannel() {
		return SchemaConstants.CHANGE_CHANNEL_LIVE_SYNC_URI;
	}

	@Override
	protected void importSyncTask(PrismObject<ResourceType> resource) throws FileNotFoundException {
		if (resource == getDummyResourceObject(RESOURCE_DUMMY_GREEN_NAME)) {
			importObjectFromFile(TASK_LIVE_SYNC_DUMMY_GREEN_FILENAME);
		} else if (resource == getDummyResourceObject(RESOURCE_DUMMY_BLUE_NAME)) {
			importObjectFromFile(TASK_LIVE_SYNC_DUMMY_BLUE_FILENAME);
		} else if (resource == getDummyResourceObject()) {
			importObjectFromFile(TASK_LIVE_SYNC_DUMMY_FILENAME);
		} else {
			throw new IllegalArgumentException("Unknown resource "+resource);
		}
	}

	@Override
	protected String getSyncTaskOid(PrismObject<ResourceType> resource) {
		if (resource == getDummyResourceObject(RESOURCE_DUMMY_GREEN_NAME)) {
			return TASK_LIVE_SYNC_DUMMY_GREEN_OID;
		} else if (resource == getDummyResourceObject(RESOURCE_DUMMY_BLUE_NAME)) {
			return TASK_LIVE_SYNC_DUMMY_BLUE_OID;
		} else if (resource == getDummyResourceObject()) {
			return TASK_LIVE_SYNC_DUMMY_OID;
		} else {
			throw new IllegalArgumentException("Unknown resource "+resource);
		}
	}
	
	@Test
    public void test999DeletingNotUpdatedShadowDummyGreen() throws Exception {
		final String TEST_NAME = "test800DeletingNotUpdatedShadowDummyGreen";
        displayTestTitle(TEST_NAME);
        String ACCOUNT_JACK_DUMMY_USERNAME = "jack";
        String ACCOUNT_CAROL_DUMMY_USERNAME = "carol";


        // GIVEN
        Task task = createTask(AbstractSynchronizationStoryTest.class.getName() + "." + TEST_NAME);
        OperationResult result = task.getResult();
        rememberTimeBeforeSync();
        prepareNotifications();
        
        DummyAccount accountCarol = new DummyAccount(ACCOUNT_CAROL_DUMMY_USERNAME);
        accountCarol.setEnabled(true);
        accountCarol.addAttributeValues(DummyResourceContoller.DUMMY_ACCOUNT_ATTRIBUTE_FULLNAME_NAME, "Carol Seepgood");
        accountCarol.addAttributeValues(DummyResourceContoller.DUMMY_ACCOUNT_ATTRIBUTE_LOCATION_NAME, "Melee Island");

		DummyAccount accountJack = new DummyAccount(ACCOUNT_JACK_DUMMY_USERNAME);
		accountJack.setEnabled(true);
		accountJack.addAttributeValues(DummyResourceContoller.DUMMY_ACCOUNT_ATTRIBUTE_FULLNAME_NAME, "Jack Sevenseas");
		accountJack.addAttributeValues(DummyResourceContoller.DUMMY_ACCOUNT_ATTRIBUTE_LOCATION_NAME, "The Seven Seas");
		
		/// WHEN
        displayWhen(TEST_NAME);

        getDummyResource(RESOURCE_DUMMY_GREEN_NAME).addAccount(accountJack);
        waitForSyncTaskNextRunAssertSuccess(getDummyResourceObject(RESOURCE_DUMMY_GREEN_NAME));
        importObjectFromFile(TASK_DELETE_NOT_UPDATED_SHADOWS);
        TimeUnit.SECONDS.sleep(6);
        getDummyResource(RESOURCE_DUMMY_GREEN_NAME).addAccount(accountCarol);
        waitForSyncTaskNextRunAssertSuccess(getDummyResourceObject(RESOURCE_DUMMY_GREEN_NAME));
        suspendTask(getSyncTaskOid(getDummyResourceObject(RESOURCE_DUMMY_GREEN_NAME)));
        rerunTask(TASK_DELETE_NOT_UPDATED_SHADOWS_OID);

        // THEN
        displayThen(TEST_NAME);

        PrismObject<UserType> userCarol = findUserByUsername(ACCOUNT_CAROL_DUMMY_USERNAME);
        display("User carol", userCarol);
        assertNotNull("No carol user", userCarol);
        
        PrismObject<UserType> userJack = findUserByUsername(ACCOUNT_JACK_DUMMY_USERNAME);
        display("User jack", userJack);
        assertNull("User jack is not null", userJack);

        // notifications
		displayAllNotifications();
        notificationManager.setDisabled(true);
	}

}
