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

package com.evolveum.midpoint.model.api.context;

import com.evolveum.midpoint.prism.PrismContext;
import com.evolveum.midpoint.util.LocalizableMessage;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;
import org.jetbrains.annotations.NotNull;

/**
 * @author mederly
 */
public class EvaluatedCollectionStatsTrigger extends EvaluatedPolicyRuleTrigger<CollectionStatsPolicyConstraintType> {

	public EvaluatedCollectionStatsTrigger(@NotNull PolicyConstraintKindType kind, @NotNull CollectionStatsPolicyConstraintType constraint,
			LocalizableMessage message, LocalizableMessage shortMessage) {
		super(kind, constraint, message, shortMessage, false);
	}

	@Override
	public EvaluatedCollectionStatsTriggerType toEvaluatedPolicyRuleTriggerType(PolicyRuleExternalizationOptions options, PrismContext prismContext) {
		EvaluatedCollectionStatsTriggerType rv = new EvaluatedCollectionStatsTriggerType();
		fillCommonContent(rv);
		return rv;
	}
}
