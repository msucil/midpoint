/**
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
package com.evolveum.midpoint.model.impl.controller;

import static com.evolveum.midpoint.model.api.util.DashboardUtils.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.evolveum.midpoint.audit.api.AuditService;
import com.evolveum.midpoint.common.Clock;
import com.evolveum.midpoint.model.api.CollectionStats;
import com.evolveum.midpoint.model.api.ModelInteractionService;
import com.evolveum.midpoint.model.api.ModelService;
import com.evolveum.midpoint.model.api.authentication.CompiledObjectCollectionView;
import com.evolveum.midpoint.model.api.context.EvaluatedPolicyRule;
import com.evolveum.midpoint.model.api.interaction.DashboardService;
import com.evolveum.midpoint.model.api.interaction.DashboardWidget;
import com.evolveum.midpoint.model.impl.ModelObjectResolver;
import com.evolveum.midpoint.prism.PrismContext;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.PrismPropertyValue;
import com.evolveum.midpoint.prism.query.ObjectQuery;
import com.evolveum.midpoint.repo.common.expression.ExpressionFactory;
import com.evolveum.midpoint.repo.common.expression.ExpressionUtil;
import com.evolveum.midpoint.repo.common.expression.ExpressionVariables;
import com.evolveum.midpoint.schema.constants.ExpressionConstants;
import com.evolveum.midpoint.schema.expression.VariablesMap;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.task.api.TaskManager;
import com.evolveum.midpoint.util.exception.CommunicationException;
import com.evolveum.midpoint.util.exception.ConfigurationException;
import com.evolveum.midpoint.util.exception.ExpressionEvaluationException;
import com.evolveum.midpoint.util.exception.ObjectNotFoundException;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.util.exception.SecurityViolationException;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.xml.ns._public.common.common_3.AuditSearchType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.DashboardWidgetDataFieldTypeType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.DashboardWidgetPresentationType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.DashboardWidgetSourceTypeType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.DashboardWidgetType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.DashboardWidgetVariationType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.DisplayType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ExpressionType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.IconType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.IntegerStatType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ObjectCollectionType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ObjectReferenceType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ObjectType;
import com.evolveum.prism.xml.ns._public.query_3.SearchFilterType;

/**
 * @author skublik
 */
@Component("dashboardService")
public class DashboardServiceImpl implements DashboardService {
	
	private static final Trace LOGGER = TraceManager.getTrace(DashboardServiceImpl.class);

	private static final String VAR_PROPORTIONAL = "proportional";
	private static final String VAR_POLICY_SITUATIONS = "policySituations";
	
	@Autowired private TaskManager taskManager;
    @Autowired private AuditService auditService;
    @Autowired private PrismContext prismContext;
    @Autowired private Clock clock;
    @Autowired private ModelInteractionService modelInteractionService;
    @Autowired private ModelService modelService;
    @Autowired private ExpressionFactory expressionFactory;
    @Autowired private ModelObjectResolver objectResolver;

    @Override
	public DashboardWidget createWidgetData(DashboardWidgetType widget, Task task, OperationResult result) throws SchemaException, CommunicationException, ConfigurationException, SecurityViolationException, ExpressionEvaluationException, ObjectNotFoundException {
		
		Validate.notNull(widget, "Widget is null");
		
		DashboardWidget data = new DashboardWidget();
		getNumberMessage(widget, data, task, result);
		data.setWidget(widget);
		if(data.getDisplay() == null) {
			data.setDisplay(widget.getDisplay());
		}
		LOGGER.debug("Widget Data: {}", data);
		return data;
	}
	
	private DisplayType combinateDisplay(DisplayType display, DisplayType variationDisplay) {
		DisplayType combinatedDisplay = new DisplayType();
		if (variationDisplay == null) {
			return display;
		}
		if(display == null) {
			return variationDisplay;
		}
		if(StringUtils.isBlank(variationDisplay.getColor())) {
			combinatedDisplay.setColor(display.getColor());
		} else {
			combinatedDisplay.setColor(variationDisplay.getColor());
		}
		if(StringUtils.isBlank(variationDisplay.getCssClass())) {
			combinatedDisplay.setCssClass(display.getCssClass());
		} else {
			combinatedDisplay.setCssClass(variationDisplay.getCssClass());
		}
		if(StringUtils.isBlank(variationDisplay.getCssStyle())) {
			combinatedDisplay.setCssStyle(display.getCssStyle());
		} else {
			combinatedDisplay.setCssStyle(variationDisplay.getCssStyle());
		}
		if(variationDisplay.getHelp() == null) {
			combinatedDisplay.setHelp(display.getHelp());
		} else {
			combinatedDisplay.setHelp(variationDisplay.getHelp());
		}
		if(variationDisplay.getLabel() == null) {
			combinatedDisplay.setLabel(display.getLabel());
		} else {
			combinatedDisplay.setLabel(variationDisplay.getLabel());
		}
		if(variationDisplay.getPluralLabel() == null) {
			combinatedDisplay.setPluralLabel(display.getPluralLabel());
		} else {
			combinatedDisplay.setPluralLabel(variationDisplay.getPluralLabel());
		}
		if(variationDisplay.getTooltip() == null) {
			combinatedDisplay.setTooltip(display.getTooltip());
		} else {
			combinatedDisplay.setTooltip(variationDisplay.getTooltip());
		}
		if(variationDisplay.getIcon() == null) {
			combinatedDisplay.setIcon(display.getIcon());
		} else if(display.getIcon() != null){
			IconType icon = new IconType();
			if(StringUtils.isBlank(variationDisplay.getIcon().getCssClass())) {
				icon.setCssClass(display.getIcon().getCssClass());
			} else {
				icon.setCssClass(variationDisplay.getIcon().getCssClass());
			}
			if(StringUtils.isBlank(variationDisplay.getIcon().getColor())) {
				icon.setColor(display.getIcon().getColor());
			} else {
				icon.setColor(variationDisplay.getIcon().getColor());
			}
			if(StringUtils.isBlank(variationDisplay.getIcon().getImageUrl())) {
				icon.setImageUrl(display.getIcon().getImageUrl());
			} else {
				icon.setImageUrl(variationDisplay.getIcon().getImageUrl());
			}
			combinatedDisplay.setIcon(icon);
		}
		
		return combinatedDisplay;
	}

	public DashboardWidgetSourceTypeType getSourceType(DashboardWidgetType widget) {
		if(isSourceTypeOfDataNull(widget)) {
			return null;
		}
		return widget.getData().getSourceType();
	}
	
	private String getNumberMessage(DashboardWidgetType widget, DashboardWidget data, Task task, OperationResult result) throws SchemaException, CommunicationException, ConfigurationException, SecurityViolationException, ExpressionEvaluationException, ObjectNotFoundException {
		DashboardWidgetSourceTypeType sourceType = getSourceType(widget);
		DashboardWidgetPresentationType presentation = widget.getPresentation();
		switch (sourceType) {
		case OBJECT_COLLECTION:
			if(!isDataFieldsOfPresentationNullOrEmpty(presentation)) {
				return generateNumberMessageForCollection(widget, data, task, result);
			}
			break;
		case AUDIT_SEARCH:
			if(!isDataFieldsOfPresentationNullOrEmpty(presentation)) {
				return generateNumberMessageForAuditSearch(widget, data, task, result);
			}
			break;
		case OBJECT:
			if(!isDataFieldsOfPresentationNullOrEmpty(presentation)) {
				return generateNumberMessageForObject(widget, data, task, result);
			}
			break;
		}
		return null;
	}

	private String generateNumberMessageForObject(DashboardWidgetType widget, DashboardWidget data, Task task, OperationResult result) throws ObjectNotFoundException, SchemaException, CommunicationException, ConfigurationException, SecurityViolationException, ExpressionEvaluationException {
		ObjectType object = getObjectFromObjectRef(widget, task, result);
		if(object == null) {
			return null;
		}
		return generateNumberMessage(widget, createVariables(object.asPrismObject(), null, null), data);
	}

	private String generateNumberMessageForAuditSearch(DashboardWidgetType widget, DashboardWidget data, Task task, OperationResult result) throws ObjectNotFoundException, SchemaException, CommunicationException, ConfigurationException, SecurityViolationException, ExpressionEvaluationException {
		ObjectCollectionType collection = getObjectCollectionType(widget, task, result);
		if(collection == null) {
			return null;
		}
		AuditSearchType auditSearch = collection.getAuditSearch();
		if(auditSearch == null) {
			LOGGER.error("AuditSearch of ObjectCollection is not found in widget " +
					widget.getIdentifier());
			return null;
		}
		if(auditSearch.getRecordQuery() == null) {
			LOGGER.error("RecordQuery of auditSearch is not defined in widget " + 
					widget.getIdentifier());
			return null;
		}
		
		Map<String, Object> parameters = new HashMap<String, Object>();
		String query = getQueryForCount(createQuery(collection,
				parameters, false, clock));
		LOGGER.debug("Parameters for select: " + parameters);
		int value = (int) auditService.countObjects(
				query, parameters);
		Integer domainValue = null;
		if(auditSearch.getDomainQuery() == null) {
			LOGGER.error("DomainQuery of auditSearch is not defined");
		} else {
			parameters = new HashMap<String, Object>();
			query = getQueryForCount(createQuery(collection,
					parameters, true, clock));
			LOGGER.debug("Parameters for select: " + parameters);
			domainValue = (int) auditService.countObjects(
					query, parameters);
		}
		LOGGER.debug("Value: {}, Domain value: {}", value, domainValue);
		IntegerStatType statType = generateIntegerStat(value, domainValue);
		return generateNumberMessage(widget, createVariables(null, statType, null), data);
	}
	
	private String getQueryForCount(String query) {
		query = "select count (*) " + query;
		query = query.split("order")[0];
		LOGGER.debug("Query for select: " + query);
		return query;
	}
		
	private String generateNumberMessageForCollection(DashboardWidgetType widget, DashboardWidget data, Task task, OperationResult result) 
			throws SchemaException, CommunicationException, ConfigurationException, SecurityViolationException, ExpressionEvaluationException, ObjectNotFoundException {
		ObjectCollectionType valueCollection = getObjectCollectionType(widget, task, result);
		if(valueCollection != null && valueCollection.getType() != null && 
				valueCollection.getType().getLocalPart() != null) {
			
			CompiledObjectCollectionView compiledCollection = modelInteractionService.compileObjectCollectionView(
					valueCollection.asPrismObject(), null, task, task.getResult());
			CollectionStats collStats = modelInteractionService.determineCollectionStats(compiledCollection, task, result);
			
			int value = collStats.getObjectCount();//getObjectCount(valueCollection, true, task, result);
			Integer domainValue = collStats.getDomainCount();
			IntegerStatType statType = generateIntegerStat(value, domainValue);
			
			Collection<EvaluatedPolicyRule> evalPolicyRules = modelInteractionService.evaluateCollectionPolicyRules(
					valueCollection.asPrismObject(), compiledCollection, null, task, task.getResult());
			Collection<String> policySituations = new ArrayList<String>();
			for(EvaluatedPolicyRule evalPolicyRule : evalPolicyRules) {
				if(!evalPolicyRule.getAllTriggers().isEmpty()) {
					policySituations.add(evalPolicyRule.getPolicySituation());
				}
			}
			return generateNumberMessage(widget, createVariables(null, statType, policySituations), data);
			
		}  else {
			LOGGER.error("CollectionType from collectionRef is null in widget " + widget.getIdentifier());
		}
		return null;
	}
	
	private static ExpressionVariables createVariables(PrismObject<? extends ObjectType> object,
			IntegerStatType statType, Collection<String> policySituations) {
		ExpressionVariables variables = new ExpressionVariables();
		if(statType != null || policySituations != null) {
			VariablesMap variablesMap = new VariablesMap();
			if(statType != null ) {
				variablesMap.put(ExpressionConstants.VAR_INPUT, statType, statType.getClass());
				variablesMap.put(VAR_PROPORTIONAL, statType, statType.getClass());
			}
			if(policySituations != null) {
				variablesMap.put(VAR_POLICY_SITUATIONS, policySituations, EvaluatedPolicyRule.class);
			}
			variables.addVariableDefinitions(variablesMap );
		}
		if(object != null) {
			variables.addVariableDefinition(ExpressionConstants.VAR_OBJECT, object, object.getDefinition());
		}
		
		return variables;
	}
	
	private static IntegerStatType generateIntegerStat(Integer value, Integer domainValue){
		IntegerStatType statType = new IntegerStatType();
		statType.setValue(value);
		statType.setDomain(domainValue);
		return statType;
	}
	
	private String generateNumberMessage(DashboardWidgetType widget, ExpressionVariables variables, DashboardWidget data) {
		Map<DashboardWidgetDataFieldTypeType, String> numberMessagesParts = new HashMap<DashboardWidgetDataFieldTypeType, String>(); 
		widget.getPresentation().getDataField().forEach(dataField -> {
			switch(dataField.getFieldType()) {
			
			case VALUE:
				Task task = taskManager.createTaskInstance("Search domain collection");
				try {
				String valueMessage = getStringExpressionMessage(variables, 
						dataField.getExpression(), "Get value message", task, task.getResult());
				if(valueMessage != null) {
					numberMessagesParts.put(DashboardWidgetDataFieldTypeType.VALUE, valueMessage);
				}
				} catch (Exception e) {
					LOGGER.error(e.getMessage(), e);
				}
				break;
				
			case UNIT:
				task = taskManager.createTaskInstance("Get unit");
				String unit = getStringExpressionMessage(new ExpressionVariables(), dataField.getExpression(), "Unit",
						task, task.getResult());
				numberMessagesParts.put(DashboardWidgetDataFieldTypeType.UNIT, unit);
				break;
			}
		});
		if(!numberMessagesParts.containsKey(DashboardWidgetDataFieldTypeType.VALUE)) {
			LOGGER.error("Value message is not generate from widget " + widget.getIdentifier());
			return null;
		}
		StringBuilder sb = new StringBuilder();
		sb.append(numberMessagesParts.get(DashboardWidgetDataFieldTypeType.VALUE));
		if(numberMessagesParts.containsKey(DashboardWidgetDataFieldTypeType.UNIT)) {
			sb.append(" ").append(numberMessagesParts.get(DashboardWidgetDataFieldTypeType.UNIT));
		}
		
		try {
			evaluateVariation(widget, variables, data);
		} catch (Exception e) {
			LOGGER.error(e.getMessage(), e);
		}
		data.setNumberMessage(sb.toString());
		return sb.toString();
	}

	private void evaluateVariation(DashboardWidgetType widget, ExpressionVariables variables, DashboardWidget data) {
		
		if(widget.getPresentation() != null) {
			if(widget.getPresentation().getVariation() != null) {
				for(DashboardWidgetVariationType variation : widget.getPresentation().getVariation()) {
					Task task = taskManager.createTaskInstance("Evaluate variation");
					PrismPropertyValue<Boolean> usingVariation;
					try {
						usingVariation = ExpressionUtil.evaluateCondition(variables, variation.getCondition(), null,
								expressionFactory,
								"Variation", task, task.getResult());
				
						if(usingVariation != null && usingVariation.getRealValue() != null
								&& usingVariation.getRealValue().equals(Boolean.TRUE)) {
							data.setDisplay(combinateDisplay(widget.getDisplay(), variation.getDisplay()));
						} else {
							data.setDisplay(widget.getDisplay());
						}
					} catch (Exception e) {
						LOGGER.error("Couldn't evaluate condition " + variation.toString(), e);
					}
				}
			}  else {
				LOGGER.error("Variation of presentation is not found in widget " + widget.getIdentifier());
			}
		}  else {
			LOGGER.error("Presentation is not found in widget " + widget.getIdentifier());
		}
	}

	@Override
	public List<PrismObject<ObjectType>> searchObjectFromCollection(ObjectCollectionType collection, boolean usingFilter, Task task, OperationResult result) throws SchemaException, ObjectNotFoundException, SecurityViolationException, CommunicationException, ConfigurationException, ExpressionEvaluationException {
		Class<ObjectType> type = (Class<ObjectType>) prismContext.getSchemaRegistry()
				.getCompileTimeClassForObjectType(collection.getType());
		SearchFilterType searchFilter = collection.getFilter();
		// TODO evaluate filter expressions here (call CollectionProcessor.evaluateExpressionsInFilter)
		ObjectQuery query = prismContext.queryFactory().createQuery();
		if (searchFilter != null && usingFilter) {
			try {
				query.setFilter(prismContext.getQueryConverter().parseFilter(searchFilter, type));
			} catch (Exception e) {
				LOGGER.error("Filter couldn't parse in collection " + collection.toString(), e);
			}
		}
		List<PrismObject<ObjectType>> values;
		values = modelService.searchObjects(type, query, null, task, task.getResult());
		return values;
	}
	
	@Override
	public ObjectCollectionType getObjectCollectionType(DashboardWidgetType widget, Task task, OperationResult result) throws ObjectNotFoundException, SchemaException, CommunicationException, ConfigurationException, SecurityViolationException, ExpressionEvaluationException {
		if (isCollectionRefOfCollectionNull(widget)) {
			return null;
		}
		ObjectReferenceType ref = widget.getData().getCollection().getCollectionRef();
		ObjectCollectionType collection = objectResolver.resolve(ref, ObjectCollectionType.class, null, "resolving collection from "+widget, task, result);
		return collection;
	}
	
	private ObjectType getObjectFromObjectRef(DashboardWidgetType widget, Task task, OperationResult result) throws ObjectNotFoundException, SchemaException, CommunicationException, ConfigurationException, SecurityViolationException, ExpressionEvaluationException {
		if(isDataNull(widget)) {
			return null;
		}
		ObjectReferenceType ref = widget.getData().getObjectRef();
		if(ref == null) {
			LOGGER.error("ObjectRef of data is not found in widget " + widget.getIdentifier());
			return null;
		}
		ObjectType object = objectResolver.resolve(ref, ObjectType.class, null, "resolving data object reference in "+widget, task, result);
		if(object == null) {
			LOGGER.error("Object from ObjectRef " + ref + " is null in widget " + widget.getIdentifier());
		}
		return object;
	}
	
	private String getStringExpressionMessage(ExpressionVariables variables,
    		ExpressionType expression, String shortDes, Task task, OperationResult result) {
    	if (expression != null) {
        	Collection<String> contentTypeList = null;
        	try {
        		contentTypeList = ExpressionUtil.evaluateStringExpression(variables, prismContext,
	        			expression, null, expressionFactory, shortDes, task, result);
        	} catch (SchemaException | ExpressionEvaluationException | ObjectNotFoundException | CommunicationException
        			| ConfigurationException | SecurityViolationException e) {
        		LOGGER.error("Couldn't evaluate Expression " + expression.toString(), e);
        	}
            if (contentTypeList == null || contentTypeList.isEmpty()) {
            	LOGGER.error("Expression " + expression + " returned nothing");
            	return null;
            }
            if (contentTypeList.size() > 1) {
                LOGGER.error("Expression returned more than 1 item. First item is used.");
            }
            return contentTypeList.iterator().next();
        } else {
            return null;
        }
    }
		
}
