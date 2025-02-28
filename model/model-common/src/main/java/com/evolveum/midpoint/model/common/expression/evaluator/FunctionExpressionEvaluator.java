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
package com.evolveum.midpoint.model.common.expression.evaluator;

import java.util.Collection;
import java.util.List;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import javax.xml.namespace.QName;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang.StringUtils;

import com.evolveum.midpoint.prism.ItemDefinition;
import com.evolveum.midpoint.prism.PrismContext;
import com.evolveum.midpoint.prism.PrismValue;
import com.evolveum.midpoint.prism.crypto.Protector;
import com.evolveum.midpoint.prism.delta.PrismValueDeltaSetTriple;
import com.evolveum.midpoint.repo.common.ObjectResolver;
import com.evolveum.midpoint.repo.common.expression.Expression;
import com.evolveum.midpoint.repo.common.expression.ExpressionEvaluationContext;
import com.evolveum.midpoint.repo.common.expression.ExpressionEvaluator;
import com.evolveum.midpoint.repo.common.expression.ExpressionFactory;
import com.evolveum.midpoint.repo.common.expression.ExpressionUtil;
import com.evolveum.midpoint.repo.common.expression.ExpressionVariables;
import com.evolveum.midpoint.repo.common.expression.Source;
import com.evolveum.midpoint.repo.common.expression.evaluator.AbstractExpressionEvaluator;
import com.evolveum.midpoint.schema.SchemaConstantsGenerated;
import com.evolveum.midpoint.schema.expression.ExpressionProfile;
import com.evolveum.midpoint.schema.expression.TypedValue;
import com.evolveum.midpoint.schema.expression.VariablesMap;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.schema.util.MiscSchemaUtil;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.util.exception.CommunicationException;
import com.evolveum.midpoint.util.exception.ConfigurationException;
import com.evolveum.midpoint.util.exception.ExpressionEvaluationException;
import com.evolveum.midpoint.util.exception.ObjectNotFoundException;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.util.exception.SecurityViolationException;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ExpressionParameterType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ExpressionReturnMultiplicityType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ExpressionType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.FunctionExpressionEvaluatorType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.FunctionLibraryType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ObjectReferenceType;

/**
 * @author katkav
 * @author semancik
 */
public class FunctionExpressionEvaluator<V extends PrismValue, D extends ItemDefinition> extends AbstractExpressionEvaluator<V, D, FunctionExpressionEvaluatorType> {
	
	private static final transient Trace LOGGER = TraceManager.getTrace(FunctionExpressionEvaluator.class); 

	private ObjectResolver objectResolver;

	FunctionExpressionEvaluator(QName elementName, FunctionExpressionEvaluatorType functionEvaluatorType, D outputDefinition,
			Protector protector, ObjectResolver objectResolver, PrismContext prismContext) {
		super(elementName, functionEvaluatorType, outputDefinition, protector, prismContext);
		this.objectResolver = objectResolver;
	}


	/*
	 * (non-Javadoc)
	 *
	 * @see
	 * com.evolveum.midpoint.common.expression.ExpressionEvaluator#evaluate(java
	 * .util.Collection, java.util.Map, boolean, java.lang.String,
	 * com.evolveum.midpoint.schema.result.OperationResult)
	 */
	@Override
	public PrismValueDeltaSetTriple<V> evaluate(ExpressionEvaluationContext context) 
			throws SchemaException, ExpressionEvaluationException, ObjectNotFoundException, CommunicationException, ConfigurationException, SecurityViolationException {
		checkEvaluatorProfile(context);

		List<ExpressionType> expressions;

		ObjectReferenceType functionLibraryRef = getExpressionEvaluatorType().getLibraryRef();
		
		if (functionLibraryRef == null) {
			throw new SchemaException("No functions library defined in "+context.getContextDescription());
		}
		
		OperationResult result = context.getResult().createMinorSubresult(FunctionExpressionEvaluator.class.getSimpleName() + ".resolveFunctionLibrary");
		try {
			Task task = context.getTask();

			FunctionLibraryType functionLibraryType = objectResolver.resolve(functionLibraryRef, FunctionLibraryType.class,
					null, "resolving value policy reference in generateExpressionEvaluator", task, result);
			expressions = functionLibraryType.getFunction();

			if (CollectionUtils.isEmpty(expressions)) {
				throw new ObjectNotFoundException(
						"No functions defined in referenced function library: " + functionLibraryType + " used in " + context
								.getContextDescription());
			}

			// TODO: this has to be determined from the library archetype
			ExpressionProfile expressionProfile = MiscSchemaUtil.getExpressionProfile();

			String functionName = getExpressionEvaluatorType().getName();

			if (StringUtils.isEmpty(functionName)) {
				throw new SchemaException(
						"Missing function name in " + shortDebugDump() + " in " + context.getContextDescription());
			}

			List<ExpressionType> filteredExpressions = expressions.stream()
					.filter(expression -> functionName.equals(expression.getName())).collect(Collectors.toList());
			if (filteredExpressions.size() == 0) {
				String possibleFunctions = "";
				for (ExpressionType expression : expressions) {
					possibleFunctions += expression.getName() + ", ";
				}
				possibleFunctions = possibleFunctions.substring(0, possibleFunctions.lastIndexOf(","));
				throw new ObjectNotFoundException("No function with name " + functionName + " found in " + shortDebugDump()
						+ ". Function defined are: " + possibleFunctions + ". In " + context.getContextDescription());
			}

			ExpressionType functionToExecute = determineFunctionToExecute(filteredExpressions);

			OperationResult functionExpressionResult = result
					.createMinorSubresult(FunctionExpressionEvaluator.class.getSimpleName() + ".makeExpression");
			ExpressionFactory factory = context.getExpressionFactory();

			// TODO: expression profile should be determined from the function library archetype
			Expression<V, D> functionExpression;
			try {
				functionExpression = factory
						.makeExpression(functionToExecute, outputDefinition, MiscSchemaUtil.getExpressionProfile(),
								"function execution", task, functionExpressionResult);
				functionExpressionResult.recordSuccess();
			} catch (SchemaException | ObjectNotFoundException e) {
				functionExpressionResult
						.recordFatalError("Cannot make expression for " + functionToExecute + ". Reason: " + e.getMessage(), e);
				throw e;
			}

			ExpressionEvaluationContext functionContext = context.shallowClone();
			ExpressionVariables functionVariables = new ExpressionVariables();

			for (ExpressionParameterType param : getExpressionEvaluatorType().getParameter()) {
				ExpressionType valueExpressionType = param.getExpression();
				OperationResult variableResult = result
						.createMinorSubresult(FunctionExpressionEvaluator.class.getSimpleName() + ".resolveVariable");
				Expression<V, D> valueExpression = null;
				try {
					variableResult.addArbitraryObjectAsParam("valueExpression", valueExpressionType);
					D variableOutputDefinition = determineVariableOutputDefinition(functionToExecute, param.getName(), context);

					valueExpression = factory
							.makeExpression(valueExpressionType, variableOutputDefinition, MiscSchemaUtil.getExpressionProfile(),
									"parameters execution", task, variableResult);
					functionExpressionResult.recordSuccess();
					PrismValueDeltaSetTriple<V> evaluatedValue = valueExpression.evaluate(context);
					V value = ExpressionUtil.getExpressionOutputValue(evaluatedValue, " evaluated value for paramter");
					functionVariables.addVariableDefinition(param.getName(), value, variableOutputDefinition);
					variableResult.recordSuccess();
				} catch (SchemaException | ExpressionEvaluationException | ObjectNotFoundException | CommunicationException
						| ConfigurationException | SecurityViolationException e) {
					variableResult
							.recordFatalError("Failed to resolve variable: " + valueExpression + ". Reason: " + e.getMessage());
					throw e;
				}
			}

			functionContext.setVariables(functionVariables);

			return functionExpression.evaluate(functionContext);
		} catch (Throwable t) {
			result.recordFatalError(t);
			throw t;
		} finally {
			result.computeStatusIfUnknown();
		}
	}

	private ExpressionType determineFunctionToExecute(List<ExpressionType> filteredExpressions) {
		
		if (filteredExpressions.size() == 1) {
			return filteredExpressions.iterator().next();
		}
		
		List<ExpressionParameterType> functionParams = getExpressionEvaluatorType().getParameter();
		
		for (ExpressionType filteredExpression : filteredExpressions) {
			List<ExpressionParameterType> filteredExpressionParameters = filteredExpression.getParameter();
			if (functionParams.size() != filteredExpressionParameters.size()) {
				continue;
			}
			if (!compareParameters(functionParams, filteredExpressionParameters)) {
				continue;
			}
			return filteredExpression;
		}
		return null;
	}
	
	private boolean compareParameters(List<ExpressionParameterType> functionParams, List<ExpressionParameterType> filteredExpressionParameters) {
		for (ExpressionParameterType  functionParam: functionParams ) {
			boolean found = false;
			for (ExpressionParameterType filteredExpressionParam : filteredExpressionParameters) {
			
				if (filteredExpressionParam.getName().equals(functionParam.getName())) {
					found = true;
					break;
				}
			}
			if (!found) {
				return false;
			}
		}
		return true;
	}
	
	private D determineVariableOutputDefinition(ExpressionType functionToExecute, String paramName, ExpressionEvaluationContext context) throws SchemaException {
		
		ExpressionParameterType functionParameter = null;
		for (ExpressionParameterType functionParam: functionToExecute.getParameter()) {
			if (functionParam.getName().equals(paramName)) {
				functionParameter = functionParam;
				break;
			}
		}
		
		if (functionParameter == null) {
			throw new SchemaException("Unexpected parameter " + paramName + " for function: " + functionToExecute);
		}
		
		QName returnType = functionParameter.getType();
		
		if (returnType == null) {
			throw new SchemaException("Cannot determine parameter output definition for " + functionParameter);
		}
		
		
			D returnTypeDef = (D) prismContext.getSchemaRegistry().findItemDefinitionByType(returnType);
			if (returnTypeDef == null) {
				returnTypeDef = (D) prismContext.definitionFactory().createPropertyDefinition(SchemaConstantsGenerated.C_VALUE, returnType);
				returnTypeDef.toMutable().setMaxOccurs(functionToExecute.getReturnMultiplicity() != null && functionToExecute.getReturnMultiplicity() == ExpressionReturnMultiplicityType.SINGLE ? 1 : -1);
			}
			
			return returnTypeDef;
		
	
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see com.evolveum.midpoint.common.expression.ExpressionEvaluator#
	 * shortDebugDump()
	 */
	@Override
	public String shortDebugDump() {
		return "function: " + getExpressionEvaluatorType().getName();
	}

}
