/**
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
package com.evolveum.midpoint.test.asserter;

import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertFalse;
import static org.testng.AssertJUnit.assertNotNull;
import static org.testng.AssertJUnit.assertNull;
import static org.testng.AssertJUnit.assertTrue;

import java.util.List;

import javax.xml.namespace.QName;

import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.PrismProperty;
import com.evolveum.midpoint.prism.util.PrismAsserts;
import com.evolveum.midpoint.schema.constants.SchemaConstants;
import com.evolveum.midpoint.test.asserter.prism.PrismObjectAsserter;
import com.evolveum.midpoint.test.asserter.prism.PrismPropertyAsserter;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ActivationStatusType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ActivationType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ObjectReferenceType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.PendingOperationType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ShadowKindType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ShadowType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.SynchronizationSituationType;
import com.evolveum.prism.xml.ns._public.types_3.PolyStringType;

/**
 * @author semancik
 *
 */
public class ShadowAsserter<RA> extends PrismObjectAsserter<ShadowType,RA> {
	
	public ShadowAsserter(PrismObject<ShadowType> shadow) {
		super(shadow);
	}
	
	public ShadowAsserter(PrismObject<ShadowType> shadow, String details) {
		super(shadow, details);
	}
	
	public ShadowAsserter(PrismObject<ShadowType> shadow, RA returnAsserter, String details) {
		super(shadow, returnAsserter, details);
	}
	
	public static ShadowAsserter<Void> forShadow(PrismObject<ShadowType> shadow) {
		return new ShadowAsserter<>(shadow);
	}
	
	public static ShadowAsserter<Void> forShadow(PrismObject<ShadowType> shadow, String details) {
		return new ShadowAsserter<>(shadow, details);
	}
	
	@Override
	public ShadowAsserter<RA> assertOid() {
		super.assertOid();
		return this;
	}
	
	@Override
	public ShadowAsserter<RA> assertOid(String expected) {
		super.assertOid(expected);
		return this;
	}
	
	@Override
	public ShadowAsserter<RA> assertName() {
		super.assertName();
		return this;
	}
	
	@Override
	public ShadowAsserter<RA> assertName(String expectedOrig) {
		super.assertName(expectedOrig);
		return this;
	}
	
	@Override
	public ShadowAsserter<RA> assertLifecycleState(String expected) {
		super.assertLifecycleState(expected);
		return this;
	}
	
	@Override
	public ShadowAsserter<RA> assertActiveLifecycleState() {
		super.assertActiveLifecycleState();
		return this;
	}
	
	public ShadowAsserter<RA> assertObjectClass() {
		assertNotNull("No objectClass in "+desc(), getObject().asObjectable().getObjectClass());
		return this;
	}
	
	public ShadowAsserter<RA> assertObjectClass(QName expected) {
		PrismAsserts.assertMatchesQName("Wrong objectClass in "+desc(), expected, getObject().asObjectable().getObjectClass());
		return this;
	}
	
	public ShadowAsserter<RA> assertKind() {
		assertNotNull("No kind in "+desc(), getObject().asObjectable().getKind());
		return this;
	}
	
	public ShadowAsserter<RA> assertKind(ShadowKindType expected) {
		assertEquals("Wrong kind in "+desc(), expected, getObject().asObjectable().getKind());
		return this;
	}
	
	public ShadowAsserter<RA> assertIntent(String expected) {
		assertEquals("Wrong intent in "+desc(), expected, getObject().asObjectable().getIntent());
		return this;
	}
	
	public ShadowAsserter<RA> assertTag(String expected) {
		assertEquals("Wrong tag in "+desc(), expected, getObject().asObjectable().getTag());
		return this;
	}
	
	public ShadowAsserter<RA> assertTagIsOid() {
		assertEquals("Wrong tag in "+desc(), getObject().getOid(), getObject().asObjectable().getTag());
		return this;
	}
	
	public ShadowAsserter<RA> assertPrimaryIdentifierValue(String expected) {
		assertEquals("Wrong primaryIdentifierValue in "+desc(), expected, getObject().asObjectable().getPrimaryIdentifierValue());
		return this;
	}
	
	public ShadowAsserter<RA> assertNoPrimaryIdentifierValue() {
		assertNull("Unexpected primaryIdentifierValue in "+desc(), getObject().asObjectable().getPrimaryIdentifierValue());
		return this;
	}
	
	public ShadowAsserter<RA> assertIteration(Integer expected) {
		assertEquals("Wrong iteration in "+desc(), expected, getObject().asObjectable().getIteration());
		return this;
	}
	
	public ShadowAsserter<RA> assertIterationToken(String expected) {
		assertEquals("Wrong iteration token in "+desc(), expected, getObject().asObjectable().getIterationToken());
		return this;
	}
	
	public ShadowAsserter<RA> assertSynchronizationSituation(SynchronizationSituationType expected) {
		assertEquals("Wrong synchronization situation in "+desc(), expected, getObject().asObjectable().getSynchronizationSituation());
		return this;
	}
	
	public ShadowAsserter<RA> assertAdministrativeStatus(ActivationStatusType expected) {
		ActivationType activation = getActivation();
		if (activation == null) {
			if (expected == null) {
				return this;
			} else {
				fail("No activation in "+desc());
			}
		}
		assertEquals("Wrong activation administrativeStatus in "+desc(), expected, activation.getAdministrativeStatus());
		return this;
	}
	
	public ShadowAsserter<RA> assertResource(String expectedResourceOid) {
		ObjectReferenceType resourceRef = getObject().asObjectable().getResourceRef();
		if (resourceRef == null) {
			fail("No resourceRef in "+desc());
		}
		assertEquals("Wrong resourceRef OID in "+desc(), expectedResourceOid, resourceRef.getOid());
		return this;
	}
	
	private ActivationType getActivation() {
		return getObject().asObjectable().getActivation();
	}
	
	public ShadowAsserter<RA> assertBasicRepoProperties() {
		assertOid();
		assertName();
		assertObjectClass();
		attributes().assertAny();
		return this;
	}

	public ShadowAsserter<RA> assertDead() {
		assertIsDead(true);
		return this;
	}
	
	public ShadowAsserter<RA> assertNotDead() {
		Boolean isDead = getObject().asObjectable().isDead();
		if (isDead != null && isDead) {
			fail("Wrong isDead in "+desc()+", expected null or false, but was true");
		}
		return this;
	}
	
	public ShadowAsserter<RA> assertIsDead(Boolean expected) {
		assertEquals("Wrong isDead in "+desc(), expected, getObject().asObjectable().isDead());
		assertNoPrimaryIdentifierValue();
		return this;
	}
	
	public ShadowAsserter<RA> assertIsExists() {
		Boolean isExists = getObject().asObjectable().isExists();
		if (isExists != null && !isExists) {
			fail("Wrong isExists in "+desc()+", expected null or true, but was false");
		}
		return this;
	}
	
	public ShadowAsserter<RA> assertIsNotExists() {
		assertIsExists(false);
		return this;
	}
	
	public ShadowAsserter<RA> assertIsExists(Boolean expected) {
		assertEquals("Wrong isExists in "+desc(), expected, getObject().asObjectable().isExists());
		return this;
	}
	
	public ShadowAsserter<RA> assertConception() {
		assertNotDead();
		assertIsNotExists();
		return this;
	}
	
	// We cannot really distinguish gestation and life now. But maybe later.
	public ShadowAsserter<RA> assertGestation() {
		assertNotDead();
		assertIsExists();
		return this;
	}
	
	public ShadowAsserter<RA> assertLife() {
		assertNotDead();
		assertIsExists();
		return this;
	}
	
	public ShadowAsserter<RA> assertTombstone() {
		assertDead();
		assertIsNotExists();
		return this;
	}
	
	// We cannot really distinguish corpse and tombstone now. But maybe later.
	public ShadowAsserter<RA> assertCorpse() {
		assertDead();
		assertIsNotExists();
		return this;
	}
		
	public PendingOperationsAsserter<RA> pendingOperations() {
		PendingOperationsAsserter<RA> asserter = new PendingOperationsAsserter<>(this, getDetails());
		copySetupTo(asserter);
		return asserter;
	}
	
	public ShadowAsserter<RA> hasUnfinishedPendingOperations() {
		pendingOperations()
			.assertUnfinishedOperation();
		return this;
	}
	
	public ShadowAttributesAsserter<RA> attributes() {
		ShadowAttributesAsserter<RA> asserter = new ShadowAttributesAsserter<>(this, getDetails());
		copySetupTo(asserter);
		return asserter;
	}
	
	public ShadowAsserter<RA> assertNoLegacyConsistency() {
		PrismAsserts.assertNoItem(getObject(), ShadowType.F_RESULT);
		PrismAsserts.assertNoItem(getObject(), ShadowType.F_ATTEMPT_NUMBER);
		PrismAsserts.assertNoItem(getObject(), ShadowType.F_FAILED_OPERATION_TYPE);
		PrismAsserts.assertNoItem(getObject(), ShadowType.F_OBJECT_CHANGE);
		return this;
	}
	
	public ShadowAsserter<RA> display() {
		super.display();
		return this;
	}
	
	public ShadowAsserter<RA> display(String message) {
		super.display(message);
		return this;
	}

	public ShadowAsserter<RA> assertOidDifferentThan(String oid) {
		super.assertOidDifferentThan(oid);
		return this;
	}

	public ShadowAsserter<RA> assertNoPassword() {
		PrismProperty<PolyStringType> passValProp = getPasswordValueProperty();
		assertNull("Unexpected password value property in "+desc()+": "+passValProp, passValProp);
		return this;
	}
	
	private PrismProperty<PolyStringType> getPasswordValueProperty() {
		return getObject().findProperty(SchemaConstants.PATH_PASSWORD_VALUE);
	}
}
