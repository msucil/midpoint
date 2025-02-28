/*
 * Copyright (c) 2016-2019 Evolveum
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.evolveum.midpoint.testing.story.ldap;


import static org.testng.AssertJUnit.assertNotNull;
import static org.testng.AssertJUnit.assertEquals;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.xml.namespace.QName;

import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.context.ContextConfiguration;
import org.testng.AssertJUnit;
import org.testng.annotations.AfterClass;
import org.testng.annotations.Test;

import com.evolveum.midpoint.util.exception.PolicyViolationException;
import com.evolveum.midpoint.prism.PrismContainer;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.delta.ItemDelta;
import com.evolveum.midpoint.prism.delta.ObjectDelta;
import com.evolveum.midpoint.prism.path.ItemName;
import com.evolveum.midpoint.prism.path.ItemPath;
import com.evolveum.midpoint.schema.constants.MidPointConstants;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.schema.statistics.ConnectorOperationalStatus;
import com.evolveum.midpoint.schema.util.MiscSchemaUtil;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.test.util.MidPointTestConstants;
import com.evolveum.midpoint.test.util.TestUtil;
import com.evolveum.midpoint.testing.story.AbstractStoryTest;
import com.evolveum.midpoint.testing.story.TestTrafo;
import com.evolveum.midpoint.util.exception.CommunicationException;
import com.evolveum.midpoint.util.exception.ConfigurationException;
import com.evolveum.midpoint.util.exception.ExpressionEvaluationException;
import com.evolveum.midpoint.util.exception.ObjectAlreadyExistsException;
import com.evolveum.midpoint.util.exception.ObjectNotFoundException;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.util.exception.SecurityViolationException;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ActivationType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.AssignmentType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.FocusType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ObjectReferenceType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ObjectType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.OrgType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ResourceType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.RoleType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ShadowKindType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ShadowType;
import com.evolveum.prism.xml.ns._public.types_3.PolyStringType;

/**
 * Testing dependencies:
 * There are two meta-roles for orgs.
 * Org Metarole contains two inducements, one for creating organizationalUnit (intent=ou) and one for creating groupOfUniqueNames (intent=group) in ldap.
 * group depends on ou (since it is created in ou)
 *
 * Org Metarole VIP is very similar it also contains two inducements, one for creating (intent=ou-vip) and one for creating groupOfUniqueNames (intent=group-vip) in ldap.
 * group-vip depends on ou-cip (since it is created in ou-vip)
 *
 * @author michael gruber
 *
 */
@ContextConfiguration(locations = {"classpath:ctx-story-test-main.xml"})
@DirtiesContext(classMode = ClassMode.AFTER_CLASS)
public abstract class AbstractLdapTest extends AbstractStoryTest {

	public static final File LDAP_TEST_DIR = new File(MidPointTestConstants.TEST_RESOURCES_DIR, "ldap");
	
	protected static final String NS_EXT_LDAP = "http://midpoint.evolveum.com/xml/ns/story/ldap/ext";
	protected static final ItemName TITLE_MAP_QNAME = new ItemName(NS_EXT_LDAP, "titleMap");
	protected static final ItemName TITLE_MAP_KEY_QNAME = new ItemName(NS_EXT_LDAP, "key");
	protected static final ItemName TITLE_MAP_VALUE_QNAME = new ItemName(NS_EXT_LDAP, "value");
	protected static final ItemPath PATH_EXTENSION_TITLE_MAP = ItemPath.create(ObjectType.F_EXTENSION, TITLE_MAP_QNAME);
	
	protected static final String LDAP_ATTRIBUTE_DESCRIPTION = "description";
	protected static final String LDAP_ATTRIBUTE_CN = "cn";

	protected static final String OBJECTCLASS_INETORGPERSON = "inetOrgPerson";

	protected abstract String getLdapResourceOid();
	
	protected void dumpLdap() throws DirectoryException {
		display("LDAP server tree", openDJController.dumpTree());
		display("LDAP server content", openDJController.dumpEntries());
	}

	//// should be in AbstractModelIntegrationTest

	protected void modifyOrgAssignment(String orgOid, String roleOid, QName refType, QName relation, Task task,
			PrismContainer<?> extension, ActivationType activationType, boolean add, OperationResult result)
			throws ObjectNotFoundException, SchemaException, ExpressionEvaluationException, CommunicationException,
			ConfigurationException, ObjectAlreadyExistsException, PolicyViolationException, SecurityViolationException {
		ObjectDelta<OrgType> orgDelta = createAssignmentOrgDelta(orgOid, roleOid, refType, relation, extension,
				activationType, add);
		Collection<ObjectDelta<? extends ObjectType>> deltas = MiscSchemaUtil.createCollection(orgDelta);
		modelService.executeChanges(deltas, null, task, result);
	}

	protected ObjectDelta<OrgType> createAssignmentOrgDelta(String orgOid, String roleOid, QName refType, QName relation,
			PrismContainer<?> extension, ActivationType activationType, boolean add) throws SchemaException {
		Collection<ItemDelta<?, ?>> modifications = new ArrayList<>();
		modifications.add((createAssignmentModification(roleOid, refType, relation, extension, activationType, add)));
		ObjectDelta<OrgType> userDelta = prismContext.deltaFactory().object().createModifyDelta(orgOid, modifications, OrgType.class
		);
		return userDelta;
	}

	protected void assignRoleToOrg(String orgOid, String roleOid, Task task, OperationResult result)
			throws ObjectNotFoundException, SchemaException, ExpressionEvaluationException, CommunicationException,
			ConfigurationException, ObjectAlreadyExistsException, PolicyViolationException, SecurityViolationException {
		assignRoleToOrg(orgOid, roleOid, (ActivationType) null, task, result);
	}

	protected void assignRoleToOrg(String orgOid, String roleOid, ActivationType activationType, Task task,
			OperationResult result)
			throws ObjectNotFoundException, SchemaException, ExpressionEvaluationException, CommunicationException,
			ConfigurationException, ObjectAlreadyExistsException, PolicyViolationException, SecurityViolationException {
		modifyOrgAssignment(orgOid, roleOid, RoleType.COMPLEX_TYPE, null, task, null, activationType, true, result);
	}

	protected void unassignRoleFromOrg(String orgOid, String roleOid, Task task, OperationResult result)
			throws ObjectNotFoundException, SchemaException, ExpressionEvaluationException, CommunicationException,
			ConfigurationException, ObjectAlreadyExistsException, PolicyViolationException, SecurityViolationException {
		unassignRoleFromOrg(orgOid, roleOid, (ActivationType) null, task, result);
	}

	protected void unassignRoleFromOrg(String orgOid, String roleOid, ActivationType activationType, Task task,
			OperationResult result)
			throws ObjectNotFoundException, SchemaException, ExpressionEvaluationException, CommunicationException,
			ConfigurationException, ObjectAlreadyExistsException, PolicyViolationException, SecurityViolationException {
		modifyOrgAssignment(orgOid, roleOid, RoleType.COMPLEX_TYPE, null, task, null, activationType, false, result);
	}

	protected <F extends FocusType> void assertOrgNotAssignedRole(PrismObject<F> focus, String roleOid) {
		assertNotAssigned(focus, roleOid, RoleType.COMPLEX_TYPE);
		// assertNotAssigned(user, roleOid, RoleType.COMPLEX_TYPE);
	}

	// TODO: maybe a replacement for MidpointAsserts.assertNotAssigned()
	// it can be used not only for user
	protected <F extends FocusType> void assertNotAssigned(PrismObject<F> focus, String targetOid, QName refType) {
		F focusType = focus.asObjectable();
		for (AssignmentType assignmentType : focusType.getAssignment()) {
			ObjectReferenceType targetRef = assignmentType.getTargetRef();
			if (targetRef != null) {
				if (refType.equals(targetRef.getType())) {
					if (targetOid.equals(targetRef.getOid())) {
						AssertJUnit.fail(focus + " does have assigned " + refType.getLocalPart() + " " + targetOid
								+ " while not expecting it");
					}
				}
			}
		}
	}
	
	protected void assertLdapConnectorInstances(int expectedConnectorInstances) throws NumberFormatException, IOException, InterruptedException, SchemaException, ObjectNotFoundException, CommunicationException, ConfigurationException, ExpressionEvaluationException {
		assertLdapConnectorInstances(expectedConnectorInstances, expectedConnectorInstances);
	}
	
	protected void assertLdapConnectorInstances(int expectedConnectorInstancesMin, int expectedConnectorInstancesMax) throws NumberFormatException, IOException, InterruptedException, SchemaException, ObjectNotFoundException, CommunicationException, ConfigurationException, ExpressionEvaluationException {
		Task task = createTask(TestLdapSyncMassive.class.getName() + ".assertLdapConnectorInstances");
		OperationResult result = task.getResult();
		List<ConnectorOperationalStatus> stats = provisioningService.getConnectorOperationalStatus(getLdapResourceOid(), task, result);
		display("Resource connector stats", stats);
		assertSuccess(result);

		assertEquals("unexpected number of stats", 1, stats.size());
		ConnectorOperationalStatus stat = stats.get(0);

		int actualConnectorInstances = stat.getPoolStatusNumIdle() + stat.getPoolStatusNumActive();
		
		if (actualConnectorInstances < expectedConnectorInstancesMin) {
			fail("Number of LDAP connector instances too low: "+actualConnectorInstances+", expected at least "+expectedConnectorInstancesMin);
		}
		if (actualConnectorInstances > expectedConnectorInstancesMax) {
			fail("Number of LDAP connector instances too high: "+actualConnectorInstances+", expected at most "+expectedConnectorInstancesMax);
		}
	}


	protected void assertLdapAccounts(int expectedNumber) throws DirectoryException {
		List<? extends Entry> entries = openDJController.search("objectclass="+OBJECTCLASS_INETORGPERSON);
		assertEquals("Wrong number of LDAP accounts ("+OBJECTCLASS_INETORGPERSON+")", expectedNumber, entries.size());
	}

	protected int getNumberOfLdapAccounts() {
		return 4;   // idm, jgibbs, hbarbossa, jbeckett
	}
}