/*
 * Copyright (c) 2018-2019 Evolveum
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
package com.evolveum.midpoint.model.intest.archetypes;

import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertNotNull;
import static org.testng.AssertJUnit.assertTrue;

import java.io.File;

import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.context.ContextConfiguration;
import org.testng.annotations.Test;

import com.evolveum.midpoint.model.api.authentication.CompiledUserProfile;
import com.evolveum.midpoint.model.intest.AbstractInitializedModelIntegrationTest;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.PrismObjectDefinition;
import com.evolveum.midpoint.prism.PrismPropertyDefinition;
import com.evolveum.midpoint.prism.PrismReferenceValue;
import com.evolveum.midpoint.prism.query.ObjectFilter;
import com.evolveum.midpoint.prism.query.ObjectQuery;
import com.evolveum.midpoint.prism.util.PrismTestUtil;
import com.evolveum.midpoint.schema.SearchResultList;
import com.evolveum.midpoint.schema.constants.SchemaConstants;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.schema.util.ObjectQueryUtil;
import com.evolveum.midpoint.security.api.MidPointPrincipal;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.util.exception.CommonException;
import com.evolveum.midpoint.util.exception.CommunicationException;
import com.evolveum.midpoint.util.exception.ConfigurationException;
import com.evolveum.midpoint.util.exception.ExpressionEvaluationException;
import com.evolveum.midpoint.util.exception.ObjectNotFoundException;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.util.exception.SecurityViolationException;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ActivationStatusType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ArchetypeType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.AssignmentPolicyEnforcementType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ObjectType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.OrgType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.RichHyperlinkType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.RoleType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ShadowKindType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.UserType;

/**
 * @author Radovan Semancik
 *
 */
@ContextConfiguration(locations = {"classpath:ctx-model-intest-test-main.xml"})
@DirtiesContext(classMode = ClassMode.AFTER_CLASS)
public class TestArchetypes extends AbstractArchetypesTest {

	public static final File TEST_DIR = new File("src/test/resources/archetypes");
	
	public static final File SYSTEM_CONFIGURATION_ARCHETYPES_FILE = new File(TEST_DIR, "system-configuration-archetypes.xml");
	
	public static final String VIEW_ALL_EMPLOYEES_NAME = "all-employees";
	public static final String VIEW_ACTIVE_EMPLOYEES_IDENTIFIER = "active-employees";
	public static final String VIEW_BUSINESS_ROLES_IDENTIFIER = "business-roles-view";
	public static final String VIEW_BUSINESS_ROLES_LABEL = "Business";
	
	public static final File ARCHETYPE_EMPLOYEE_FILE = new File(TEST_DIR, "archetype-employee.xml");
	protected static final String ARCHETYPE_EMPLOYEE_OID = "7135e68c-ee53-11e8-8025-170b77da3fd6";
	private static final String ARCHETYPE_EMPLOYEE_DISPLAY_LABEL = "Employee";
	private static final String ARCHETYPE_EMPLOYEE_DISPLAY_PLURAL_LABEL = "Employees";
	
	public static final File ARCHETYPE_CONTRACTOR_FILE = new File(TEST_DIR, "archetype-contractor.xml");
	protected static final String ARCHETYPE_CONTRACTOR_OID = "3911cac2-78a6-11e9-8b5e-4b5bdb0c81d5";
	
	public static final File ARCHETYPE_TEST_FILE = new File(TEST_DIR, "archetype-test.xml");
	protected static final String ARCHETYPE_TEST_OID = "a8df34a8-f6f0-11e8-b98e-eb03652d943f";
	
	public static final File ARCHETYPE_BUSINESS_ROLE_FILE = new File(TEST_DIR, "archetype-business-role.xml");
	protected static final String ARCHETYPE_BUSINESS_ROLE_OID = "018e7340-199a-11e9-ad93-2b136d1c7ecf";
	private static final String ARCHETYPE_BUSINESS_ROLE_ICON_CSS_CLASS = "fe fe-business";
	private static final String ARCHETYPE_BUSINESS_ROLE_ICON_COLOR = "green";
	
	public static final File USER_MEATHOOK_FILE = new File(TEST_DIR, "user-meathook.xml");
	protected static final String USER_MEATHOOK_OID = "f79fc10e-78a8-11e9-92ec-cf427cb6e7a0";
	
	public static final File ROLE_EMPLOYEE_BASE_FILE = new File(TEST_DIR, "role-employee-base.xml");
	protected static final String ROLE_EMPLOYEE_BASE_OID = "e869d6c4-f6ef-11e8-b51f-df3e51bba129";
	
	public static final File ROLE_USER_ADMINISTRATOR_FILE = new File(TEST_DIR, "role-user-administrator.xml");
	protected static final String ROLE_USER_ADMINISTRATOR_OID = "6ae02e34-f8b0-11e8-9c40-87e142b606fe";
	
	public static final File ROLE_BUSINESS_CAPTAIN_FILE = new File(TEST_DIR, "role-business-captain.xml");
	protected static final String ROLE_BUSINESS_CAPTAIN_OID = "9f65f4b6-199b-11e9-a4c1-2f6b7eb1ebae";
	
	public static final File ROLE_BUSINESS_BOSUN_FILE = new File(TEST_DIR, "role-business-bosun.xml");
	protected static final String ROLE_BUSINESS_BOSUN_OID = "186b29c6-199c-11e9-9acc-3f8cc307573b";
	
	public static final File COLLECTION_ACTIVE_EMPLOYEES_FILE = new File(TEST_DIR, "collection-active-employees.xml");
	protected static final String COLLECTION_ACTIVE_EMPLOYEES_OID = "f61bcb4a-f8ae-11e8-9f5c-c3e7f27ee878";

	protected static final File USER_TEMPLATE_ARCHETYPES_GLOBAL_FILE = new File(TEST_DIR, "user-template-archetypes-global.xml");
	protected static final String USER_TEMPLATE_ARCHETYPES_GLOBAL_OID = "dab200ae-65dc-11e9-a8d3-27e5b1538f19";
	
	protected static final File USER_TEMPLATE_CONTRACTOR_FILE = new File(TEST_DIR, "user-template-contractor.xml");
	protected static final String USER_TEMPLATE_CONTRACTOR_OID = "f72193e4-78a6-11e9-a0b6-6f5ad4cfbb37";

	private static final String CONTRACTOR_EMPLOYEE_NUMBER = "CONTRACTOR";
	
	@Override
    public void initSystem(Task initTask, OperationResult initResult) throws Exception {
        super.initSystem(initTask, initResult);
        
        repoAddObjectFromFile(ROLE_EMPLOYEE_BASE_FILE, initResult);
        repoAddObjectFromFile(ROLE_USER_ADMINISTRATOR_FILE, initResult);
        repoAddObjectFromFile(ARCHETYPE_EMPLOYEE_FILE, initResult);
        repoAddObjectFromFile(ARCHETYPE_CONTRACTOR_FILE, initResult);
        repoAddObjectFromFile(COLLECTION_ACTIVE_EMPLOYEES_FILE, initResult);
        repoAddObjectFromFile(USER_TEMPLATE_ARCHETYPES_GLOBAL_FILE, initResult);
        repoAddObjectFromFile(USER_TEMPLATE_CONTRACTOR_FILE, initResult);
        
        addObject(SHADOW_GROUP_DUMMY_TESTERS_FILE, initTask, initResult);
        
        setDefaultObjectTemplate(UserType.COMPLEX_TYPE, USER_TEMPLATE_ARCHETYPES_GLOBAL_OID, initResult);
    }
	
	@Override
	protected File getSystemConfigurationFile() {
		return SYSTEM_CONFIGURATION_ARCHETYPES_FILE;
	}

	/**
	 * Test sanity of test setup. User jack, without any archetype.
	 */
	@Test
    public void test020SanityJack() throws Exception {
		final String TEST_NAME = "test020SanityJack";
        displayTestTitle(TEST_NAME);

        Task task = createTask(TEST_NAME);
        OperationResult result = task.getResult();

        PrismObject<UserType> user = assertUserBefore(USER_JACK_OID)
        	.assertFullName(USER_JACK_FULL_NAME)
        	.getObject();
        
        assertEditSchema(user);
    }
	
    @Test
    public void test050AddArchetypeTest() throws Exception {
		final String TEST_NAME = "test050AddArchetypeTest";
        displayTestTitle(TEST_NAME);

        Task task = createTask(TEST_NAME);
        OperationResult result = task.getResult();

        // WHEN
        displayWhen(TEST_NAME);
        addObject(ARCHETYPE_TEST_FILE, task, result);

        // THEN
        displayThen(TEST_NAME);
        assertSuccess(result);

        PrismObject<ArchetypeType> archetypeTest = modelService.getObject(ArchetypeType.class, ARCHETYPE_TEST_OID, null, task, result);
        display("Archetype test", archetypeTest);
    }
    
    @Test
    public void test060AddArchetypesAndRoles() throws Exception {
		final String TEST_NAME = "test060AddArchetypesAndRoles";
        displayTestTitle(TEST_NAME);

        Task task = createTask(TEST_NAME);
        OperationResult result = task.getResult();

        // WHEN
        displayWhen(TEST_NAME);
        addObject(ARCHETYPE_BUSINESS_ROLE_FILE, task, result);
        addObject(ROLE_BUSINESS_CAPTAIN_FILE, task, result);
        addObject(ROLE_BUSINESS_BOSUN_FILE, task, result);

        // THEN
        displayThen(TEST_NAME);
        assertSuccess(result);

        PrismObject<RoleType> roleBusinessCaptainAfter = assertRoleAfter(ROLE_BUSINESS_CAPTAIN_OID)
        	.assertArchetypeRef(ARCHETYPE_BUSINESS_ROLE_OID)
        	.getObject();
        
        assertAssignmentHolderSpecification(roleBusinessCaptainAfter)
        	.assignmentObjectRelations()
        		.assertItems(2)
        		.by()
        			.relations(SchemaConstants.ORG_APPROVER, SchemaConstants.ORG_OWNER)
        		.find()
        			.assertArchetypeOid(ARCHETYPE_EMPLOYEE_OID)
        			.assertObjectType(UserType.COMPLEX_TYPE)
        			.assertDescription("Only employees may be owners/approvers for business role.")
        			.end()
        		.by()
        			.relation(SchemaConstants.ORG_DEFAULT)
        		.find()
        			.assertObjectType(UserType.COMPLEX_TYPE)
        			.assertNoArchetype();
        			
    }
    
    @Test
    public void test070AssignGuybrushUserAdministrator() throws Exception {
		final String TEST_NAME = "test070AssignGuybrushUserAdministrator";
        displayTestTitle(TEST_NAME);

        Task task = createTask(TEST_NAME);
        OperationResult result = task.getResult();

        // WHEN
        displayWhen(TEST_NAME);
        assignRole(USER_GUYBRUSH_OID, ROLE_USER_ADMINISTRATOR_OID, task, result);

        // THEN
        displayThen(TEST_NAME);
        assertSuccess(result);
        
        // TODO: assert guybrush
    }
    

	@Test
    public void test100AssignJackArchetypeEmployee() throws Exception {
		final String TEST_NAME = "test100AssignJackArchetypeEmployee";
        displayTestTitle(TEST_NAME);

        Task task = createTask(TEST_NAME);
        OperationResult result = task.getResult();

        // WHEN
        displayWhen(TEST_NAME);

        assignArchetype(USER_JACK_OID, ARCHETYPE_EMPLOYEE_OID, task, result);

        // THEN
        displayThen(TEST_NAME);
        assertSuccess(result);

        PrismObject<UserType> userAfter = assertUserAfter(USER_JACK_OID)
        	.assignments()
        		.assertAssignments(1)
        		.assertArchetype(ARCHETYPE_EMPLOYEE_OID)
        		.end()
        	.assertArchetypeRef(ARCHETYPE_EMPLOYEE_OID)
        	.roleMembershipRefs()
        		.assertRoleMemberhipRefs(1)
        		.assertArchetype(ARCHETYPE_EMPLOYEE_OID)
        		.end()
        	// MID-5243
        	.assertCostCenter(costCenterEmployee())
        	.getObject();
        
        assertArchetypePolicy(userAfter)
        	.displayType()
        		.assertLabel(ARCHETYPE_EMPLOYEE_DISPLAY_LABEL)
        		.assertPluralLabel(ARCHETYPE_EMPLOYEE_DISPLAY_PLURAL_LABEL);
        
        // MID-5277
        assertEditSchema(userAfter);
    }
	
	private String costCenterEmployee() {
		return "Archetype " + ARCHETYPE_EMPLOYEE_OID +": archetype:"+ ARCHETYPE_EMPLOYEE_OID + "(Employee) isEmployee: true";
	}

	@Test
    public void test102SearchEmployeeArchetypeRef() throws Exception {
		final String TEST_NAME = "test102SearchEmployeeArchetypeRef";
        displayTestTitle(TEST_NAME);

        Task task = createTask(TEST_NAME);
        OperationResult result = task.getResult();

        ObjectQuery query = queryFor(UserType.class)
        	.item(UserType.F_ARCHETYPE_REF).ref(ARCHETYPE_EMPLOYEE_OID)
        	.build();
        
        // WHEN
        displayWhen(TEST_NAME);

        SearchResultList<PrismObject<UserType>> searchResults = modelService.searchObjects(UserType.class, query, null, task, result);

        // THEN
        displayThen(TEST_NAME);
        assertSuccess(result);
        display("Search results", searchResults);
        assertEquals("Wrong number of search results", 1, searchResults.size());
        PrismObject<UserType> foundUser = searchResults.get(0);
        assertUser(foundUser, "found user")
        	.assertName(USER_JACK_USERNAME)
        	.assertOid(USER_JACK_OID)
        	.assignments()
        		.assertAssignments(1)
        		.assertArchetype(ARCHETYPE_EMPLOYEE_OID)
        		.end()
        	.assertArchetypeRef(ARCHETYPE_EMPLOYEE_OID)
        	.roleMembershipRefs()
        		.assertRoleMemberhipRefs(1)
        		.assertArchetype(ARCHETYPE_EMPLOYEE_OID)
        		.end()
        	.getObject();
    }
	
	@Test
    public void test104GetGuybryshCompiledUserProfile() throws Exception {
		final String TEST_NAME = "test104GetGuybryshCompiledUserProfile";
        displayTestTitle(TEST_NAME);

        // GIVEN
        login(USER_GUYBRUSH_USERNAME);
        
        Task task = createTask(TEST_NAME);
        OperationResult result = task.getResult();
        
		// WHEN
		CompiledUserProfile compiledUserProfile = modelInteractionService.getCompiledUserProfile(task, result);

		// THEN
		assertSuccess(result);
		
		loginAdministrator();

		ObjectFilter allEmployeesViewFilter = assertCompiledUserProfile(compiledUserProfile)
			.assertAdditionalMenuLinks(0)
			.assertUserDashboardLinks(0)
			.assertObjectForms(2)
			.assertUserDashboardWidgets(0)
			.objectCollectionViews()
				.assertViews(3)
				.by()
					.identifier(VIEW_ACTIVE_EMPLOYEES_IDENTIFIER)
				.find()
					.assertName(VIEW_ACTIVE_EMPLOYEES_IDENTIFIER)
					.assertFilter()
					.end()
				.by()
					.identifier(VIEW_BUSINESS_ROLES_IDENTIFIER)
				.find()
					.assertName(VIEW_BUSINESS_ROLES_IDENTIFIER)
					.assertFilter()
					.displayType()
						.assertLabel(VIEW_BUSINESS_ROLES_LABEL) // Overridden in view definition
						.icon() // Inherited from archetype
							.assertCssClass(ARCHETYPE_BUSINESS_ROLE_ICON_CSS_CLASS)
							.assertColor(ARCHETYPE_BUSINESS_ROLE_ICON_COLOR)
							.end()
						.end()
					.end()
				.by()
					.identifier(VIEW_ALL_EMPLOYEES_NAME)
				.find()
					.assertName(VIEW_ALL_EMPLOYEES_NAME)
					.assertFilter()
					.getFilter();
		
		ObjectQuery viewQuery = prismContext.queryFactory().createQuery(allEmployeesViewFilter, null);
		SearchResultList<PrismObject<UserType>> searchResults = modelService.searchObjects(UserType.class, viewQuery, null, task, result);

        display("Search results", searchResults);
        assertEquals("Wrong number of search results", 1, searchResults.size());
        PrismObject<UserType> foundUser = searchResults.get(0);
        assertUser(foundUser, "found user")
        	.assertName(USER_JACK_USERNAME)
        	.assertOid(USER_JACK_OID);

	}
	
	
	
	@Test
    public void test109UnassignJackArchetypeEmployee() throws Exception {
		final String TEST_NAME = "test109UnassignJackArchetypeEmployee";
        displayTestTitle(TEST_NAME);

        loginAdministrator();
        
        Task task = createTask(TEST_NAME);
        OperationResult result = task.getResult();

        // WHEN
        displayWhen(TEST_NAME);

        unassignArchetype(USER_JACK_OID, ARCHETYPE_EMPLOYEE_OID, task, result);

        // THEN
        displayThen(TEST_NAME);
        assertSuccess(result);

        PrismObject<UserType> userAfter = assertUserAfter(USER_JACK_OID)
        	.assignments()
        		.assertAssignments(0)
        		.end()
        	.assertNoArchetypeRef()
        	.roleMembershipRefs()
        		.assertRoleMemberhipRefs(0)
        		.end()
        	.getObject();
        
        // Archetype policy derived from system config (global)
        assertArchetypePolicy(userAfter)
        	.assertNoDisplay()
        	.assertObjectTemplate(USER_TEMPLATE_ARCHETYPES_GLOBAL_OID);
    }
	
	@Test
    public void test110AssignJackRoleEmployeeBase() throws Exception {
		final String TEST_NAME = "test110AssignJackRoleEmployeeBase";
        displayTestTitle(TEST_NAME);

        Task task = createTask(TEST_NAME);
        OperationResult result = task.getResult();

        // WHEN
        displayWhen(TEST_NAME);

        assignRole(USER_JACK_OID, ROLE_EMPLOYEE_BASE_OID, task, result);

        // THEN
        displayThen(TEST_NAME);
        assertSuccess(result);

        PrismObject<UserType> userAfter = assertUserAfter(USER_JACK_OID)
        	.assignments()
        		.assertAssignments(1)
        		.assertRole(ROLE_EMPLOYEE_BASE_OID)
        		.end()
        	.assertArchetypeRef(ARCHETYPE_EMPLOYEE_OID)
        	.roleMembershipRefs()
        		.assertRoleMemberhipRefs(2)
        		.assertArchetype(ARCHETYPE_EMPLOYEE_OID)
        		.assertRole(ROLE_EMPLOYEE_BASE_OID)
        		.end()
        	.getObject();
        
        assertArchetypePolicy(userAfter)
        	.displayType()
        		.assertLabel(ARCHETYPE_EMPLOYEE_DISPLAY_LABEL)
        		.assertPluralLabel(ARCHETYPE_EMPLOYEE_DISPLAY_PLURAL_LABEL);

        assertAssignmentTargetSpecification(userAfter)
	        .assignmentObjectRelations()
	        	.assertItems(2)
	        	.by()
	        		.targetType(RoleType.COMPLEX_TYPE)
	        		.relation(SchemaConstants.ORG_DEFAULT)
	        	.find()
	        		.assertDescription("Any user can have business role (can be a member).")
	        		.assertArchetypeOid(ARCHETYPE_BUSINESS_ROLE_OID)
	        		.assertObjectType(RoleType.COMPLEX_TYPE)
	        		.end()
	        	.by()
	        		.targetType(RoleType.COMPLEX_TYPE)
	        		.relations(SchemaConstants.ORG_OWNER, SchemaConstants.ORG_APPROVER)
	        	.find()
		        	.assertDescription("Only employees may be owners/approvers for business role.")
	        		.assertArchetypeOid(ARCHETYPE_BUSINESS_ROLE_OID)
        			.assertObjectType(RoleType.COMPLEX_TYPE);
	        	
    }
	
	@Test
    public void test115UnassignJackRoleEmployeeBase() throws Exception {
		final String TEST_NAME = "test115UnassignJackRoleEmployeeBase";
        displayTestTitle(TEST_NAME);

        Task task = createTask(TEST_NAME);
        OperationResult result = task.getResult();

        // WHEN
        displayWhen(TEST_NAME);

        unassignRole(USER_JACK_OID, ROLE_EMPLOYEE_BASE_OID, task, result);

        // THEN
        displayThen(TEST_NAME);
        assertSuccess(result);

        PrismObject<UserType> userAfter = assertUserAfter(USER_JACK_OID)
	    	.assignments()
	    		.assertAssignments(0)
	    		.end()
	    	.assertNoArchetypeRef()
	    	.roleMembershipRefs()
	    		.assertRoleMemberhipRefs(0)
	    		.end()
	    	.getObject();
        
        // Archetype policy derived from system config (global)
        assertArchetypePolicy(userAfter)
        	.assertNoDisplay()
        	.assertObjectTemplate(USER_TEMPLATE_ARCHETYPES_GLOBAL_OID);
        
    	assertAssignmentTargetSpecification(userAfter)
    		.assignmentObjectRelations()
	        	.assertItems(1)
	        	.by()
	        		.targetType(RoleType.COMPLEX_TYPE)
	        		.relation(SchemaConstants.ORG_DEFAULT)
	        	.find()
	        		.assertDescription("Any user can have business role (can be a member).")
	        		.assertArchetypeOid(ARCHETYPE_BUSINESS_ROLE_OID)
	        		.assertObjectType(RoleType.COMPLEX_TYPE);
        }
	
	@Test
    public void test120AssignJackArchetypeTest() throws Exception {
		final String TEST_NAME = "test120AssignJackArchetypeTest";
        displayTestTitle(TEST_NAME);

        Task task = createTask(TEST_NAME);
        OperationResult result = task.getResult();

        // WHEN
        displayWhen(TEST_NAME);

        assignArchetype(USER_JACK_OID, ARCHETYPE_TEST_OID, task, result);

        // THEN
        displayThen(TEST_NAME);
        assertSuccess(result);

        assertUserAfter(USER_JACK_OID)
        	.assignments()
        		.assertAssignments(1)
        		.assertArchetype(ARCHETYPE_TEST_OID)
        		.end()
        	.assertArchetypeRef(ARCHETYPE_TEST_OID)
        	.roleMembershipRefs()
        		.assertRoleMemberhipRefs(1)
        		.assertArchetype(ARCHETYPE_TEST_OID)
        		.end()
        	.singleLink()
        		.target()
        			.assertResource(RESOURCE_DUMMY_OID)
        			.assertKind(ShadowKindType.ACCOUNT)
        			.assertIntent(INTENT_TEST)
        			.end()
        		.end();
    }
	
	@Test
    public void test129UnassignJackArchetypeTest() throws Exception {
		final String TEST_NAME = "test129UnassignJackArchetypeTest";
        displayTestTitle(TEST_NAME);

        Task task = createTask(TEST_NAME);
        OperationResult result = task.getResult();

        // WHEN
        displayWhen(TEST_NAME);

        unassignArchetype(USER_JACK_OID, ARCHETYPE_TEST_OID, task, result);

        // THEN
        displayThen(TEST_NAME);
        assertSuccess(result);

        assertUserAfter(USER_JACK_OID)
	    	.assignments()
	    		.assertAssignments(0)
	    		.end()
	    	.assertNoArchetypeRef()
	    	.roleMembershipRefs()
	    		.assertRoleMemberhipRefs(0)
	    		.end()
	    	.links()
	    		.assertNone();
    }
	
	/**
	 * Contractor archetype has a different object template.
	 */
	@Test
    public void test130AssignJackArchetypeContractor() throws Exception {
		final String TEST_NAME = "test130AssignJackArchetypeContractor";
        displayTestTitle(TEST_NAME);

        Task task = createTask(TEST_NAME);
        OperationResult result = task.getResult();

        // WHEN
        displayWhen(TEST_NAME);

        assignArchetype(USER_JACK_OID, ARCHETYPE_CONTRACTOR_OID, task, result);

        // THEN
        displayThen(TEST_NAME);
        assertSuccess(result);

        assertUserAfter(USER_JACK_OID)
        	.assignments()
        		.assertAssignments(1)
        		.assertArchetype(ARCHETYPE_CONTRACTOR_OID)
        		.end()
        	.assertArchetypeRef(ARCHETYPE_CONTRACTOR_OID)
        	.roleMembershipRefs()
        		.assertRoleMemberhipRefs(1)
        		.assertArchetype(ARCHETYPE_CONTRACTOR_OID)
        		.end()
        	.assertEmployeeNumber(CONTRACTOR_EMPLOYEE_NUMBER);        
    }
	
	/**
	 * Make sure everything is nice and steady and the archetype template is still applied.
	 */
	@Test
    public void test132JackContractorRecompute() throws Exception {
		final String TEST_NAME = "test132JackContractorRecompute";
        displayTestTitle(TEST_NAME);

        Task task = createTask(TEST_NAME);
        OperationResult result = task.getResult();

        // WHEN
        displayWhen(TEST_NAME);

        recomputeUser(USER_JACK_OID, task, result);

        // THEN
        displayThen(TEST_NAME);
        assertSuccess(result);

        assertUserAfter(USER_JACK_OID)
        	.assignments()
        		.assertAssignments(1)
        		.assertArchetype(ARCHETYPE_CONTRACTOR_OID)
        		.end()
        	.assertArchetypeRef(ARCHETYPE_CONTRACTOR_OID)
        	.roleMembershipRefs()
        		.assertRoleMemberhipRefs(1)
        		.assertArchetype(ARCHETYPE_CONTRACTOR_OID)
        		.end()
        	.assertEmployeeNumber(CONTRACTOR_EMPLOYEE_NUMBER);
    }
	
	/**
	 * Contractor archetype has a different object template. We unassign the archetype
	 * now. But the employeeNumber given by the template stays. There is no reason to change it.
	 */
	@Test
    public void test135UnassignJackArchetypeContractor() throws Exception {
		final String TEST_NAME = "test135UnassignJackArchetypeContractor";
        displayTestTitle(TEST_NAME);

        Task task = createTask(TEST_NAME);
        OperationResult result = task.getResult();

        // WHEN
        displayWhen(TEST_NAME);

        unassignArchetype(USER_JACK_OID, ARCHETYPE_CONTRACTOR_OID, task, result);

        // THEN
        displayThen(TEST_NAME);
        assertSuccess(result);

        assertUserAfter(USER_JACK_OID)
    	.assignments()
    		.assertAssignments(0)
    		.end()
    	.assertNoArchetypeRef()
    	.roleMembershipRefs()
    		.assertRoleMemberhipRefs(0)
    		.end()
    	.links()
    		.assertNone()
    		.end()
		.assertEmployeeNumber(CONTRACTOR_EMPLOYEE_NUMBER);
    }
	
	/**
	 * Change employeeNumber and recompute. As the contractor template should no longer
	 * be applied, the employee number should not be forced back to contractor.
	 */
	@Test
    public void test137JackEmpnoAndRecompute() throws Exception {
		final String TEST_NAME = "test137JackEmpnoAndRecompute";
        displayTestTitle(TEST_NAME);

        Task task = createTask(TEST_NAME);
        OperationResult result = task.getResult();
        
        modifyUserReplace(USER_JACK_OID, UserType.F_EMPLOYEE_NUMBER, task, result, "Number ONE");

        // WHEN
        displayWhen(TEST_NAME);

        recomputeUser(USER_JACK_OID, task, result);

        // THEN
        displayThen(TEST_NAME);
        assertSuccess(result);

        assertUserAfter(USER_JACK_OID)
    	.assignments()
    		.assertAssignments(0)
    		.end()
    	.assertNoArchetypeRef()
    	.roleMembershipRefs()
    		.assertRoleMemberhipRefs(0)
    		.end()
    	.links()
    		.assertNone()
    		.end()
		.assertEmployeeNumber("Number ONE");
    }
	
	/**
	 * Meathook is a contractor. Contractor archetype has a different object template.
	 * This template should be applied when adding the object.
	 */
	@Test
    public void test140AddMeathookContractor() throws Exception {
		final String TEST_NAME = "test140AddMeathookContractor";
        displayTestTitle(TEST_NAME);

        Task task = createTask(TEST_NAME);
        OperationResult result = task.getResult();

        // WHEN
        displayWhen(TEST_NAME);

        addObject(USER_MEATHOOK_FILE, task, result);

        // THEN
        displayThen(TEST_NAME);
        assertSuccess(result);

        assertUserAfter(USER_MEATHOOK_OID)
        	.assignments()
        		.assertAssignments(1)
        		.assertArchetype(ARCHETYPE_CONTRACTOR_OID)
        		.end()
        	.assertArchetypeRef(ARCHETYPE_CONTRACTOR_OID)
        	.roleMembershipRefs()
        		.assertRoleMemberhipRefs(1)
        		.assertArchetype(ARCHETYPE_CONTRACTOR_OID)
        		.end()
        	.assertEmployeeNumber(CONTRACTOR_EMPLOYEE_NUMBER);        
    }
	
	@Test
    public void test200AssignJackBarbossaArchetypeEmployee() throws Exception {
		final String TEST_NAME = "test200AssignJackBarbossaArchetypeEmployee";
        displayTestTitle(TEST_NAME);

        Task task = createTask(TEST_NAME);
        OperationResult result = task.getResult();

        // WHEN
        displayWhen(TEST_NAME);

        assignArchetype(USER_JACK_OID, ARCHETYPE_EMPLOYEE_OID, task, result);
        assignArchetype(USER_BARBOSSA_OID, ARCHETYPE_EMPLOYEE_OID, task, result);

        // THEN
        displayThen(TEST_NAME);
        assertSuccess(result);

        assertUserAfter(USER_JACK_OID)
        	.assertArchetypeRef(ARCHETYPE_EMPLOYEE_OID);
        
        assertUserAfter(USER_BARBOSSA_OID)
    		.assertArchetypeRef(ARCHETYPE_EMPLOYEE_OID);

    }
	
	@Test
    public void test202GetGuybryshCompiledUserProfileActiveEmployeesView() throws Exception {
		final String TEST_NAME = "test202GetGuybryshCompiledUserProfileActiveEmployeesView";
        displayTestTitle(TEST_NAME);

        // GIVEN
        login(USER_GUYBRUSH_USERNAME);
        
        Task task = createTask(TEST_NAME);
        OperationResult result = task.getResult();
        
		// WHEN
		CompiledUserProfile compiledUserProfile = modelInteractionService.getCompiledUserProfile(task, result);

		// THEN
		assertSuccess(result);
		
		loginAdministrator();

		ObjectFilter activeEmployeesViewFilter = assertCompiledUserProfile(compiledUserProfile)
			.objectCollectionViews()
				.assertViews(3)
				.by()
					.identifier(VIEW_ACTIVE_EMPLOYEES_IDENTIFIER)
				.find()
					.assertName(VIEW_ACTIVE_EMPLOYEES_IDENTIFIER)
					.assertFilter()
					.getFilter();
		
		ObjectQuery viewQuery = prismContext.queryFactory().createQuery(activeEmployeesViewFilter, null);
		SearchResultList<PrismObject<UserType>> searchResults = modelService.searchObjects(UserType.class, viewQuery, null, task, result);

        display("Search results", searchResults);
        assertEquals("Wrong number of search results", 2, searchResults.size());

	}
	
	@Test
    public void test203DisableBarbossa() throws Exception {
		final String TEST_NAME = "test203DisableBarbossa";
        displayTestTitle(TEST_NAME);

        Task task = createTask(TEST_NAME);
        OperationResult result = task.getResult();

        // WHEN
        displayWhen(TEST_NAME);

        modifyUserReplace(USER_BARBOSSA_OID, ACTIVATION_ADMINISTRATIVE_STATUS_PATH, task, result, ActivationStatusType.DISABLED);

        // THEN
        displayThen(TEST_NAME);
        assertSuccess(result);

        assertUserAfter(USER_JACK_OID)
        	.assertArchetypeRef(ARCHETYPE_EMPLOYEE_OID);
        
        assertUserAfter(USER_BARBOSSA_OID)
    		.assertArchetypeRef(ARCHETYPE_EMPLOYEE_OID);

    }

	@Test
    public void test205GetGuybryshCompiledUserProfileActiveEmployeesView() throws Exception {
		final String TEST_NAME = "test205GetGuybryshCompiledUserProfileActiveEmployeesView";
        displayTestTitle(TEST_NAME);

        // GIVEN
        login(USER_GUYBRUSH_USERNAME);
        
        Task task = createTask(TEST_NAME);
        OperationResult result = task.getResult();
        
		// WHEN
		CompiledUserProfile compiledUserProfile = modelInteractionService.getCompiledUserProfile(task, result);

		// THEN
		assertSuccess(result);
		
		loginAdministrator();

		ObjectFilter activeEmployeesViewFilter = assertCompiledUserProfile(compiledUserProfile)
			.objectCollectionViews()
				.assertViews(3)
				.by()
					.identifier(VIEW_ACTIVE_EMPLOYEES_IDENTIFIER)
				.find()
					.assertName(VIEW_ACTIVE_EMPLOYEES_IDENTIFIER)
					.assertFilter()
					.getFilter();
		
		ObjectQuery viewQuery = prismContext.queryFactory().createQuery(activeEmployeesViewFilter, null);
		SearchResultList<PrismObject<UserType>> searchResults = modelService.searchObjects(UserType.class, viewQuery, null, task, result);

        display("Search results", searchResults);
        assertEquals("Wrong number of search results", 1, searchResults.size());

	}

	// TODO: object template in archetype
	// TODO: correct application of object template for new object (not yet stored)
	
	// TODO: assertArchetypeSpec() for new object (not yet stored)
	
	// TODO: assignmentRelation (assertArchetypeSpec)

	protected void assertEditSchema(PrismObject<UserType> user) throws CommonException {
		PrismObjectDefinition<UserType> editDef = getEditObjectDefinition(user);
        
        // This has overridden lookup def in object template
 		PrismPropertyDefinition<String> preferredLanguageDef = editDef.findPropertyDefinition(UserType.F_PREFERRED_LANGUAGE);
 		assertNotNull("No definition for preferredLanguage in user", preferredLanguageDef);
 		assertEquals("Wrong preferredLanguage displayName", "Language", preferredLanguageDef.getDisplayName());
 		assertTrue("preferredLanguage not readable", preferredLanguageDef.canRead());
 		PrismReferenceValue valueEnumerationRef = preferredLanguageDef.getValueEnumerationRef();
 		assertNotNull("No valueEnumerationRef for preferredLanguage", valueEnumerationRef);
 		assertEquals("Wrong valueEnumerationRef OID for preferredLanguage", LOOKUP_LANGUAGES_OID, valueEnumerationRef.getOid());
	}
}
