/**
 * Copyright (c) 2015-2019 Evolveum
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
package com.evolveum.midpoint.testing.conntest.ad;

import java.io.File;

import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.context.ContextConfiguration;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ActivationStatusType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ShadowType;

/**
 * @author semancik
 *
 */
@ContextConfiguration(locations = {"classpath:ctx-conntest-test-main.xml"})
@Listeners({ com.evolveum.midpoint.tools.testng.AlphabeticalMethodInterceptor.class })
@DirtiesContext(classMode = ClassMode.AFTER_CLASS)
public class TestAdLdapChimeraRunAs extends AbstractAdLdapMultidomainRunAsTest {

	@Override
	protected File getResourceFile() {
		return new File(getBaseDir(), "resource-chimera-runas.xml");
	}

	protected String getResourceOid() {
		return "eced6d24-73e3-11e5-8457-93eff15a6b85";
	}

	@Override
	protected String getLdapServerHost() {
		return "chimera.ad.evolveum.com";
	}

	@Override
	protected int getLdapServerPort() {
		return 636;
	}
	
	@Override
	protected File getReconciliationTaskFile() {
		return new File(getBaseDir(), "task-reconcile-chimera-users.xml");
	}
	
	@Override
	protected String getReconciliationTaskOid() {
		return "6e2689dc-88fa-11e9-a382-0baf927677fd";
	}

	@Override
	protected void assertAccountDisabled(PrismObject<ShadowType> shadow) {
		assertAdministrativeStatus(shadow, ActivationStatusType.DISABLED);
	}

	@Override
	protected void assertAccountEnabled(PrismObject<ShadowType> shadow) {
		assertAdministrativeStatus(shadow, ActivationStatusType.ENABLED);
	}
}
