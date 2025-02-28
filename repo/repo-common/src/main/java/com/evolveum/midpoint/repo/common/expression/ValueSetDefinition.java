/**
 * Copyright (c) 2017-2019 Evolveum
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
package com.evolveum.midpoint.repo.common.expression;

import org.apache.commons.lang3.Validate;

import com.evolveum.midpoint.prism.ItemDefinition;
import com.evolveum.midpoint.prism.PrismPropertyDefinition;
import com.evolveum.midpoint.prism.PrismPropertyValue;
import com.evolveum.midpoint.prism.PrismValue;
import com.evolveum.midpoint.prism.delta.PrismValueDeltaSetTriple;
import com.evolveum.midpoint.schema.constants.ExpressionConstants;
import com.evolveum.midpoint.schema.expression.ExpressionProfile;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.util.exception.CommunicationException;
import com.evolveum.midpoint.util.exception.ConfigurationException;
import com.evolveum.midpoint.util.exception.ExpressionEvaluationException;
import com.evolveum.midpoint.util.exception.ObjectNotFoundException;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.util.exception.SecurityViolationException;
import com.evolveum.midpoint.util.exception.TunnelException;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ExpressionType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ValueSetDefinitionPredefinedType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ValueSetDefinitionType;

/**
 * @author semancik
 *
 */
public class ValueSetDefinition<IV extends PrismValue, D extends ItemDefinition> {

	private final ValueSetDefinitionType setDefinitionType;
	private final D itemDefinition;
	private final ExpressionProfile expressionProfile;
	private final String shortDesc;
	private final Task task;
	private final OperationResult result;
	private ValueSetDefinitionPredefinedType pre;
	private final String additionalVariableName;
	private ExpressionVariables additionalVariables;
	private Expression<PrismPropertyValue<Boolean>,PrismPropertyDefinition<Boolean>> condition;

	public ValueSetDefinition(ValueSetDefinitionType setDefinitionType, D itemDefinition, ExpressionProfile expressionProfile, String additionalVariableName, String shortDesc, Task task, OperationResult result) {
		super();
		this.setDefinitionType = setDefinitionType;
		Validate.notNull(itemDefinition, "No item definition for value set in %s", shortDesc);
		this.itemDefinition = itemDefinition;
		this.expressionProfile = expressionProfile;
		this.additionalVariableName = additionalVariableName;
		this.shortDesc = shortDesc;
		this.task = task;
		this.result = result;
	}

	public void init(ExpressionFactory expressionFactory) throws SchemaException, ObjectNotFoundException, SecurityViolationException {
		pre = setDefinitionType.getPredefined();
		ExpressionType conditionType = setDefinitionType.getCondition();
		if (conditionType != null) {
			condition = ExpressionUtil.createCondition(conditionType, expressionProfile, expressionFactory, shortDesc, task, result);
		}
	}

	public void setAdditionalVariables(ExpressionVariables additionalVariables) {
		this.additionalVariables = additionalVariables;
	}

	public boolean contains(IV pval) throws SchemaException, ExpressionEvaluationException, ObjectNotFoundException, CommunicationException, ConfigurationException, SecurityViolationException {
		if (pre != null) {
			switch (pre) {
				case NONE:
					return false;
				case ALL:
					return true;
				default:
					throw new IllegalStateException("Unknown pre value: "+pre);
			}
		} else {
			return evalCondition(pval);
		}
	}

	/**
	 * Same as contains, but wraps exceptions in TunnelException.
	 */
	public boolean containsTunnel(IV pval) {
		try {
			return contains(pval);
		} catch (SchemaException | ExpressionEvaluationException | ObjectNotFoundException | CommunicationException | ConfigurationException | SecurityViolationException e) {
			throw new TunnelException(e);
		}
	}

	private <IV extends PrismValue> boolean evalCondition(IV pval) throws SchemaException, ExpressionEvaluationException, ObjectNotFoundException, CommunicationException, ConfigurationException, SecurityViolationException {
		ExpressionVariables variables = new ExpressionVariables();
		Object value = pval.getRealValue();
		variables.addVariableDefinition(ExpressionConstants.VAR_INPUT, value, itemDefinition);
		if (additionalVariableName != null) {
			variables.addVariableDefinition(additionalVariableName, value, itemDefinition);
		}
		if (additionalVariables != null) {
			variables.addVariableDefinitions(additionalVariables, variables.keySet());
		}
		ExpressionEvaluationContext context = new ExpressionEvaluationContext(null, variables, shortDesc, task, result);
		PrismValueDeltaSetTriple<PrismPropertyValue<Boolean>> outputTriple = condition.evaluate(context);
		if (outputTriple == null) {
			return false;
		}
		return ExpressionUtil.computeConditionResult(outputTriple.getNonNegativeValues());
	}

}
