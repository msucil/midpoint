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
package com.evolveum.midpoint.model.impl.lens;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.xml.namespace.QName;

import com.evolveum.midpoint.repo.common.expression.ExpressionUtil;
import com.evolveum.midpoint.repo.common.expression.ExpressionVariables;
import com.evolveum.midpoint.common.refinery.RefinedAssociationDefinition;
import com.evolveum.midpoint.common.refinery.RefinedAttributeDefinition;
import com.evolveum.midpoint.common.refinery.RefinedObjectClassDefinition;
import com.evolveum.midpoint.common.refinery.RefinedResourceSchema;
import com.evolveum.midpoint.common.refinery.RefinedResourceSchemaImpl;
import com.evolveum.midpoint.model.common.mapping.MappingImpl;
import com.evolveum.midpoint.model.common.mapping.MappingFactory;
import com.evolveum.midpoint.model.impl.lens.projector.MappingEvaluator;
import com.evolveum.midpoint.model.impl.util.ModelImplUtils;
import com.evolveum.midpoint.prism.ItemDefinition;
import com.evolveum.midpoint.prism.OriginType;
import com.evolveum.midpoint.prism.PrismContainerDefinition;
import com.evolveum.midpoint.prism.PrismContainerValue;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.PrismObjectDefinition;
import com.evolveum.midpoint.prism.PrismPropertyDefinition;
import com.evolveum.midpoint.prism.PrismPropertyValue;
import com.evolveum.midpoint.prism.PrismValue;
import com.evolveum.midpoint.prism.delta.PrismValueDeltaSetTriple;
import com.evolveum.midpoint.prism.query.ObjectFilter;
import com.evolveum.midpoint.prism.util.ItemPathTypeUtil;
import com.evolveum.midpoint.schema.ResourceShadowDiscriminator;
import com.evolveum.midpoint.schema.ResultHandler;
import com.evolveum.midpoint.schema.constants.ExpressionConstants;
import com.evolveum.midpoint.schema.expression.ExpressionProfile;
import com.evolveum.midpoint.schema.processor.ResourceAttributeDefinition;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.schema.util.MiscSchemaUtil;
import com.evolveum.midpoint.schema.util.ObjectTypeUtil;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.util.DebugUtil;
import com.evolveum.midpoint.util.PrettyPrinter;
import com.evolveum.midpoint.util.exception.CommunicationException;
import com.evolveum.midpoint.util.exception.ConfigurationException;
import com.evolveum.midpoint.util.exception.ExpressionEvaluationException;
import com.evolveum.midpoint.util.exception.ObjectNotFoundException;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.util.exception.SecurityViolationException;
import com.evolveum.midpoint.util.exception.SystemException;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.xml.ns._public.common.common_3.AssignmentHolderType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ConstructionStrengthType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ConstructionType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.FocusType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.LayerType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.MappingType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ObjectType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ResourceAttributeDefinitionType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ResourceObjectAssociationType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ResourceType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ShadowAssociationType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ShadowKindType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ShadowType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.SystemConfigurationType;

/**
 * Live class that contains "construction" - a definition how to construct a
 * resource object. It in fact reflects the definition of ConstructionType but
 * it also contains "live" objects and can evaluate the construction. It also
 * contains intermediary and side results of the evaluation.
 *
 * @author Radovan Semancik
 *
 *         This class is Serializable but it is not in fact serializable. It
 *         implements Serializable interface only to be storable in the
 *         PrismPropertyValue.
 */
public class Construction<AH extends AssignmentHolderType> extends AbstractConstruction<AH,ConstructionType> {

	private static final Trace LOGGER = TraceManager.getTrace(Construction.class);

	private static final String OP_EVALUATE = Construction.class.getName() + ".evaluate";

	private ObjectType orderOneObject;
	private ResourceType resource;
	private ExpressionProfile expressionProfile;
	private MappingFactory mappingFactory;
	private MappingEvaluator mappingEvaluator;
	private Collection<MappingImpl<? extends PrismPropertyValue<?>, ? extends PrismPropertyDefinition<?>>> attributeMappings;
	private Collection<MappingImpl<PrismContainerValue<ShadowAssociationType>, PrismContainerDefinition<ShadowAssociationType>>> associationMappings;
	private RefinedObjectClassDefinition refinedObjectClassDefinition;
	private List<RefinedObjectClassDefinition> auxiliaryObjectClassDefinitions;
	private AssignmentPathVariables assignmentPathVariables = null;
	private PrismContainerDefinition<ShadowAssociationType> associationContainerDefinition;
	private PrismObject<SystemConfigurationType> systemConfiguration; // only to provide $configuration variable (MID-2372)
	private LensProjectionContext projectionContext;

	public Construction(ConstructionType constructionType, ObjectType source) {
		super(constructionType, source);
		this.attributeMappings = null;
		// TODO: this is wrong. It should be set up during the evaluation process.
		this.expressionProfile = MiscSchemaUtil.getExpressionProfile();
	}

	public ObjectType getOrderOneObject() {
		return orderOneObject;
	}

	public void setOrderOneObject(ObjectType orderOneObject) {
		this.orderOneObject = orderOneObject;
	}

	public MappingFactory getMappingFactory() {
		return mappingFactory;
	}

	public void setMappingFactory(MappingFactory mappingFactory) {
		this.mappingFactory = mappingFactory;
	}

	public MappingEvaluator getMappingEvaluator() {
		return mappingEvaluator;
	}

	public void setMappingEvaluator(MappingEvaluator mappingEvaluator) {
		this.mappingEvaluator = mappingEvaluator;
	}

	public PrismObject<SystemConfigurationType> getSystemConfiguration() {
		return systemConfiguration;
	}

	public void setSystemConfiguration(PrismObject<SystemConfigurationType> systemConfiguration) {
		this.systemConfiguration = systemConfiguration;
	}

	public RefinedObjectClassDefinition getRefinedObjectClassDefinition() {
		return refinedObjectClassDefinition;
	}

	public void setRefinedObjectClassDefinition(RefinedObjectClassDefinition refinedObjectClassDefinition) {
		this.refinedObjectClassDefinition = refinedObjectClassDefinition;
	}

	public List<RefinedObjectClassDefinition> getAuxiliaryObjectClassDefinitions() {
		return auxiliaryObjectClassDefinitions;
	}

	public void addAuxiliaryObjectClassDefinition(
			RefinedObjectClassDefinition auxiliaryObjectClassDefinition) {
		if (auxiliaryObjectClassDefinitions == null) {
			auxiliaryObjectClassDefinitions = new ArrayList<>();
		}
		auxiliaryObjectClassDefinitions.add(auxiliaryObjectClassDefinition);
	}

	public ShadowKindType getKind() {
		if (refinedObjectClassDefinition == null) {
			throw new IllegalStateException("Kind can only be fetched from evaluated Construction");
		}
		return refinedObjectClassDefinition.getKind();
	}

	public String getIntent() {
		if (refinedObjectClassDefinition == null) {
			throw new IllegalStateException("Intent can only be fetched from evaluated Construction");
		}
		return refinedObjectClassDefinition.getIntent();
	}

	public Collection<MappingImpl<? extends PrismPropertyValue<?>, ? extends PrismPropertyDefinition<?>>> getAttributeMappings() {
		if (attributeMappings == null) {
			attributeMappings = new ArrayList<>();
		}
		return attributeMappings;
	}

	public MappingImpl<? extends PrismPropertyValue<?>, ? extends PrismPropertyDefinition<?>> getAttributeMapping(
			QName attrName) {
		for (MappingImpl<? extends PrismPropertyValue<?>, ? extends PrismPropertyDefinition<?>> myVc : getAttributeMappings()) {
			if (myVc.getItemName().equals(attrName)) {
				return myVc;
			}
		}
		return null;
	}

	public void addAttributeMapping(
			MappingImpl<? extends PrismPropertyValue<?>, ? extends PrismPropertyDefinition<?>> mapping) {
		getAttributeMappings().add(mapping);
	}

	public boolean containsAttributeMapping(QName attributeName) {
		for (MappingImpl<?, ?> mapping : getAttributeMappings()) {
			if (attributeName.equals(mapping.getItemName())) {
				return true;
			}
		}
		return false;
	}

	public Collection<MappingImpl<PrismContainerValue<ShadowAssociationType>, PrismContainerDefinition<ShadowAssociationType>>> getAssociationMappings() {
		if (associationMappings == null) {
			associationMappings = new ArrayList<>();
		}
		return associationMappings;
	}

	public void addAssociationMapping(
			MappingImpl<PrismContainerValue<ShadowAssociationType>, PrismContainerDefinition<ShadowAssociationType>> mapping) {
		getAssociationMappings().add(mapping);
	}

	public boolean containsAssociationMapping(QName assocName) {
		for (MappingImpl<?, ?> mapping : getAssociationMappings()) {
			if (assocName.equals(mapping.getItemName())) {
				return true;
			}
		}
		return false;
	}

	private ResourceType resolveTarget(String sourceDescription, Task task, OperationResult result)
			throws SchemaException, ObjectNotFoundException, ExpressionEvaluationException,
			CommunicationException, ConfigurationException, SecurityViolationException {
		// SearchFilterType filter = targetRef.getFilter();
		ExpressionVariables variables = ModelImplUtils
				.getDefaultExpressionVariables(getFocusOdo().getNewObject().asObjectable(), null, null, null, mappingEvaluator.getPrismContext());
		if (assignmentPathVariables == null) {
			assignmentPathVariables = LensUtil.computeAssignmentPathVariables(getAssignmentPath());
		}
		ModelImplUtils.addAssignmentPathVariables(assignmentPathVariables, variables, getPrismContext());
		LOGGER.info("Expression variables for filter evaluation: {}", variables);

		ObjectFilter origFilter = getPrismContext().getQueryConverter().parseFilter(getConstructionType().getResourceRef().getFilter(),
				ResourceType.class);
		LOGGER.info("Orig filter {}", origFilter);
		ObjectFilter evaluatedFilter = ExpressionUtil.evaluateFilterExpressions(origFilter, variables,
				expressionProfile, getMappingFactory().getExpressionFactory(), getPrismContext(),
				" evaluating resource filter expression ", task, result);
		LOGGER.info("evaluatedFilter filter {}", evaluatedFilter);

		if (evaluatedFilter == null) {
			throw new SchemaException(
					"The OID is null and filter could not be evaluated in assignment targetRef in " + getSource());
		}

		final Collection<PrismObject<ResourceType>> results = new ArrayList<>();
		ResultHandler<ResourceType> handler = (object, parentResult) -> {
			LOGGER.info("Found object {}", object);
			return results.add(object);
		};
		getObjectResolver().searchIterative(ResourceType.class, getPrismContext().queryFactory().createQuery(evaluatedFilter),
				null, handler, task, result);

		if (org.apache.commons.collections.CollectionUtils.isEmpty(results)) {
			throw new IllegalArgumentException("Got no target from repository, filter:" + evaluatedFilter
					+ ", class:" + ResourceType.class + " in " + sourceDescription);
		}

		if (results.size() > 1) {
			throw new IllegalArgumentException("Got more than one target from repository, filter:"
					+ evaluatedFilter + ", class:" + ResourceType.class + " in " + sourceDescription);
		}

		PrismObject<ResourceType> target = results.iterator().next();

		// assignmentType.getTargetRef().setOid(target.getOid());
		return target.asObjectable();
	}

	public ResourceType getResource(Task task, OperationResult result)
			throws ObjectNotFoundException, SchemaException {
		if (resource == null) {
			if (getConstructionType().getResource() != null) {
				resource = getConstructionType().getResource();
			} else if (getConstructionType().getResourceRef() != null) {
				try {

					if (getConstructionType().getResourceRef().getOid() == null) {
						resource = resolveTarget(" resolving resource ", task, result);
					} else {
						resource = LensUtil.getResourceReadOnly(getLensContext(),
								getConstructionType().getResourceRef().getOid(), getObjectResolver(), task, result);
					}
				} catch (ObjectNotFoundException e) {
					throw new ObjectNotFoundException(
							"Resource reference seems to be invalid in account construction in " + getSource()
									+ ": " + e.getMessage(),
							e);
				} catch (SecurityViolationException | CommunicationException | ConfigurationException e) {
					throw new SystemException("Couldn't fetch the resource in account construction in "
							+ getSource() + ": " + e.getMessage(), e);
				} catch (ExpressionEvaluationException e) {
					throw new SystemException(
							"Couldn't evaluate filter expression for the resource in account construction in "
									+ getSource() + ": " + e.getMessage(),
							e);
				}
			}
			if (resource == null) {
				throw new SchemaException("No resource set in account construction in " + getSource()
						+ ", resource : " + getConstructionType().getResource() + ", resourceRef: "
						+ getConstructionType().getResourceRef());
			}
			getConstructionType().getResourceRef().setOid(resource.getOid());
		}
		return resource;
	}

	public void evaluate(Task task, OperationResult parentResult)
			throws SchemaException, ExpressionEvaluationException, ObjectNotFoundException, SecurityViolationException, ConfigurationException, CommunicationException {
		// Subresult is needed here. If something fails here, this needs to be recorded as a subresult of
		// AssignmentProcessor.processAssignments. Otherwise partial error won't be propagated properly.
		OperationResult result = parentResult.createMinorSubresult(OP_EVALUATE);
		try {
			assignmentPathVariables = LensUtil.computeAssignmentPathVariables(getAssignmentPath());
			evaluateKindIntentObjectClass(task, result);
			evaluateAttributes(task, result);
			evaluateAssociations(task, result);
			result.recordSuccess();
		} catch (Throwable e) {
			result.recordFatalError(e);
			throw e;
		}
	}

	private void evaluateKindIntentObjectClass(Task task, OperationResult result)
			throws SchemaException, ObjectNotFoundException {
		String resourceOid = null;
		if (getConstructionType().getResourceRef() != null) {
			resourceOid = getConstructionType().getResourceRef().getOid();
		}
		if (getConstructionType().getResource() != null) {
			resourceOid = getConstructionType().getResource().getOid();
		}
		ResourceType resource = getResource(task, result);
		if (resourceOid != null && !resource.getOid().equals(resourceOid)) {
			throw new IllegalStateException(
					"The specified resource and the resource in construction does not match");
		}

		RefinedResourceSchema refinedSchema = RefinedResourceSchemaImpl.getRefinedSchema(resource,
				LayerType.MODEL, getPrismContext());
		if (refinedSchema == null) {
			// Refined schema may be null in some error-related border cases
			throw new SchemaException("No (refined) schema for " + resource);
		}

		ShadowKindType kind = getConstructionType().getKind();
		if (kind == null) {
			kind = ShadowKindType.ACCOUNT;
		}
		refinedObjectClassDefinition = refinedSchema.getRefinedDefinition(kind, getConstructionType().getIntent());

		if (refinedObjectClassDefinition == null) {
			if (getConstructionType().getIntent() != null) {
				throw new SchemaException(
						"No " + kind + " type '" + getConstructionType().getIntent() + "' found in "
								+ getResource(task, result) + " as specified in construction in " + getSource());
			} else {
				throw new SchemaException("No default " + kind + " type found in " + resource
						+ " as specified in construction in " + getSource());
			}
		}

		auxiliaryObjectClassDefinitions = new ArrayList<>(getConstructionType().getAuxiliaryObjectClass().size());
		for (QName auxiliaryObjectClassName : getConstructionType().getAuxiliaryObjectClass()) {
			RefinedObjectClassDefinition auxOcDef = refinedSchema
					.getRefinedDefinition(auxiliaryObjectClassName);
			if (auxOcDef == null) {
				throw new SchemaException(
						"No auxiliary object class " + auxiliaryObjectClassName + " found in "
								+ getResource(task, result) + " as specified in construction in " + getSource());
			}
			auxiliaryObjectClassDefinitions.add(auxOcDef);
		}
		
		ResourceShadowDiscriminator rat = new ResourceShadowDiscriminator(resourceOid, kind, getConstructionType().getIntent(), null, false);
		projectionContext = getLensContext().findProjectionContext(rat);
		// projection context may not exist yet (existence might not be yet decided)
	}

	private void evaluateAttributes(Task task, OperationResult result)
			throws ExpressionEvaluationException, ObjectNotFoundException, SchemaException, SecurityViolationException, ConfigurationException, CommunicationException {
		attributeMappings = new ArrayList<>();
		// LOGGER.trace("Assignments used for account construction for {} ({}):
		// {}", new Object[]{this.resource,
		// assignments.size(), assignments});
		for (ResourceAttributeDefinitionType attribudeDefinitionType : getConstructionType().getAttribute()) {
			QName attrName = ItemPathTypeUtil.asSingleNameOrFailNullSafe(attribudeDefinitionType.getRef());
			if (attrName == null) {
				throw new SchemaException(
						"No attribute name (ref) in attribute definition in account construction in "
								+ getSource());
			}
			if (!attribudeDefinitionType.getInbound().isEmpty()) {
				throw new SchemaException("Cannot process inbound section in definition of attribute "
						+ attrName + " in account construction in " + getSource());
			}
			MappingType outboundMappingType = attribudeDefinitionType.getOutbound();
			if (outboundMappingType == null) {
				throw new SchemaException("No outbound section in definition of attribute " + attrName
						+ " in account construction in " + getSource());
			}
			MappingImpl<? extends PrismPropertyValue<?>, ? extends PrismPropertyDefinition<?>> attributeMapping = evaluateAttribute(
					attribudeDefinitionType, task, result);
			if (attributeMapping != null) {
				attributeMappings.add(attributeMapping);
			}
		}
	}

	private <T> MappingImpl<PrismPropertyValue<T>, ResourceAttributeDefinition<T>> evaluateAttribute(
			ResourceAttributeDefinitionType attribudeDefinitionType, Task task, OperationResult result)
					throws ExpressionEvaluationException, ObjectNotFoundException, SchemaException, SecurityViolationException, ConfigurationException, CommunicationException {
		QName attrName = ItemPathTypeUtil.asSingleNameOrFailNullSafe(attribudeDefinitionType.getRef());
		if (attrName == null) {
			throw new SchemaException("Missing 'ref' in attribute construction in account construction in "
					+ getSource());
		}
		if (!attribudeDefinitionType.getInbound().isEmpty()) {
			throw new SchemaException("Cannot process inbound section in definition of attribute " + attrName
					+ " in account construction in " + getSource());
		}
		MappingType outboundMappingType = attribudeDefinitionType.getOutbound();
		if (outboundMappingType == null) {
			throw new SchemaException("No outbound section in definition of attribute " + attrName
					+ " in account construction in " + getSource());
		}
		ResourceAttributeDefinition<T> outputDefinition = findAttributeDefinition(attrName);
		if (outputDefinition == null) {
			throw new SchemaException("Attribute " + attrName + " not found in schema for account type "
					+ getIntent() + ", " + getResource(task, result)
					+ " as definied in " + getSource(), attrName);
		}
		MappingImpl.Builder<PrismPropertyValue<T>, ResourceAttributeDefinition<T>> builder = mappingFactory.createMappingBuilder(
				outboundMappingType,
				"for attribute " + PrettyPrinter.prettyPrint(attrName) + " in " + getSource());

		MappingImpl<PrismPropertyValue<T>, ResourceAttributeDefinition<T>> evaluatedMapping;
		
		try {
		
			evaluatedMapping = evaluateMapping(builder, attrName, outputDefinition, null, task, result);
			
		} catch (SchemaException e) {
			throw new SchemaException(getAttributeEvaluationErrorMesssage(attrName, e), e);
		} catch (ExpressionEvaluationException e) {
			// No need to specially handle this here. It was already handled in the expression-processing
			// code and it has proper description.
			throw e;
		} catch (ObjectNotFoundException e) {
			throw new ObjectNotFoundException(getAttributeEvaluationErrorMesssage(attrName, e), e);
		} catch (SecurityViolationException e) {
			throw new SecurityViolationException(getAttributeEvaluationErrorMesssage(attrName, e), e);
		} catch (ConfigurationException e) {
			throw new ConfigurationException(getAttributeEvaluationErrorMesssage(attrName, e), e);
		} catch (CommunicationException e) {
			throw new CommunicationException(getAttributeEvaluationErrorMesssage(attrName, e), e);
		}

		LOGGER.trace("Evaluated mapping for attribute " + attrName + ": " + evaluatedMapping);
		return evaluatedMapping;
	}

	private String getAttributeEvaluationErrorMesssage(QName attrName, Exception e) {
		return "Error evaluating mapping for attribute "+PrettyPrinter.prettyPrint(attrName)+" in "+getHumanReadableConstructionDescription()+": "+e.getMessage();
	}

	private String getHumanReadableConstructionDescription() {
		return "construction for ("+resource+"/"+getKind()+"/"+getIntent()+") in "+getSource();
	}

	public <T> RefinedAttributeDefinition<T> findAttributeDefinition(QName attributeName) {
		if (refinedObjectClassDefinition == null) {
			throw new IllegalStateException(
					"Construction " + this + " was not evaluated:\n" + this.debugDump());
		}
		RefinedAttributeDefinition<T> attrDef = refinedObjectClassDefinition
				.findAttributeDefinition(attributeName);
		if (attrDef != null) {
			return attrDef;
		}
		for (RefinedObjectClassDefinition auxiliaryObjectClassDefinition : auxiliaryObjectClassDefinitions) {
			attrDef = auxiliaryObjectClassDefinition.findAttributeDefinition(attributeName);
			if (attrDef != null) {
				return attrDef;
			}
		}
		return null;
	}

	public boolean hasValueForAttribute(QName attributeName) {
		for (MappingImpl<? extends PrismPropertyValue<?>, ? extends PrismPropertyDefinition<?>> attributeConstruction : attributeMappings) {
			if (attributeName.equals(attributeConstruction.getItemName())) {
				PrismValueDeltaSetTriple<? extends PrismPropertyValue<?>> outputTriple = attributeConstruction
						.getOutputTriple();
				if (outputTriple != null && !outputTriple.isEmpty()) {
					return true;
				}
			}
		}
		return false;
	}

	private void evaluateAssociations(Task task, OperationResult result)
			throws ExpressionEvaluationException, ObjectNotFoundException, SchemaException, SecurityViolationException, ConfigurationException, CommunicationException {
		associationMappings = new ArrayList<>();
		for (ResourceObjectAssociationType associationDefinitionType : getConstructionType().getAssociation()) {
			QName assocName = ItemPathTypeUtil.asSingleNameOrFailNullSafe(associationDefinitionType.getRef());
			if (assocName == null) {
				throw new SchemaException(
						"No association name (ref) in association definition in construction in " + getSource());
			}
			MappingType outboundMappingType = associationDefinitionType.getOutbound();
			if (outboundMappingType == null) {
				throw new SchemaException("No outbound section in definition of association " + assocName
						+ " in construction in " + getSource());
			}
			MappingImpl<PrismContainerValue<ShadowAssociationType>, PrismContainerDefinition<ShadowAssociationType>> assocMapping =
					evaluateAssociation(associationDefinitionType, task, result);
			if (assocMapping != null) {
				associationMappings.add(assocMapping);
			}
		}
	}

	private MappingImpl<PrismContainerValue<ShadowAssociationType>, PrismContainerDefinition<ShadowAssociationType>> evaluateAssociation(
			ResourceObjectAssociationType associationDefinitionType, Task task, OperationResult result)
					throws ExpressionEvaluationException, ObjectNotFoundException, SchemaException, SecurityViolationException, ConfigurationException, CommunicationException {
		QName assocName = ItemPathTypeUtil.asSingleNameOrFailNullSafe(associationDefinitionType.getRef());
		if (assocName == null) {
			throw new SchemaException("Missing 'ref' in association in construction in " + getSource());
		}
		
		RefinedAssociationDefinition rAssocDef = refinedObjectClassDefinition.findAssociationDefinition(assocName);
		if (rAssocDef == null) {
			throw new SchemaException("No association " + assocName + " in object class "
					+ refinedObjectClassDefinition.getHumanReadableName() + " in construction in " + getSource());
		}
		// Make sure that assocName is complete with the namespace and all.
		assocName = rAssocDef.getName();
		
		MappingType outboundMappingType = associationDefinitionType.getOutbound();
		if (outboundMappingType == null) {
			throw new SchemaException("No outbound section in definition of association " + assocName
					+ " in construction in " + getSource());
		}
		PrismContainerDefinition<ShadowAssociationType> outputDefinition = getAssociationContainerDefinition();

		MappingImpl.Builder<PrismContainerValue<ShadowAssociationType>, PrismContainerDefinition<ShadowAssociationType>> mappingBuilder =
				mappingFactory.<PrismContainerValue<ShadowAssociationType>, PrismContainerDefinition<ShadowAssociationType>>createMappingBuilder()
						.mappingType(outboundMappingType)
						.contextDescription("for association " + PrettyPrinter.prettyPrint(assocName) + " in " + getSource())
						.originType(OriginType.ASSIGNMENTS)
						.originObject(getSource());

		MappingImpl<PrismContainerValue<ShadowAssociationType>, PrismContainerDefinition<ShadowAssociationType>> evaluatedMapping = evaluateMapping(
				mappingBuilder, assocName, outputDefinition, rAssocDef.getAssociationTarget(), task, result);

		LOGGER.trace("Evaluated mapping for association " + assocName + ": " + evaluatedMapping);
		return evaluatedMapping;
	}

	private <V extends PrismValue, D extends ItemDefinition> MappingImpl<V, D> evaluateMapping(
			MappingImpl.Builder<V, D> builder, QName mappingQName, D outputDefinition,
			RefinedObjectClassDefinition assocTargetObjectClassDefinition, Task task, OperationResult result)
					throws ExpressionEvaluationException, ObjectNotFoundException, SchemaException, SecurityViolationException, ConfigurationException, CommunicationException {

		if (!builder.isApplicableToChannel(getChannel())) {
			return null;
		}

		builder = builder.mappingQName(mappingQName)
				.sourceContext(getFocusOdo())
				.defaultTargetDefinition(outputDefinition)
				.originType(getOriginType())
				.originObject(getSource())
				.refinedObjectClassDefinition(refinedObjectClassDefinition)
				.rootNode(getFocusOdo())
				.addVariableDefinition(ExpressionConstants.VAR_USER, getFocusOdo())
				.addVariableDefinition(ExpressionConstants.VAR_FOCUS, getFocusOdo())
				.addVariableDefinition(ExpressionConstants.VAR_SOURCE, getSource(), ObjectType.class)
				.addVariableDefinition(ExpressionConstants.VAR_CONTAINING_OBJECT, getSource(), ObjectType.class)
				.addVariableDefinition(ExpressionConstants.VAR_ORDER_ONE_OBJECT, orderOneObject, ObjectType.class);

		if (assocTargetObjectClassDefinition != null) {
			builder = builder.addVariableDefinition(ExpressionConstants.VAR_ASSOCIATION_TARGET_OBJECT_CLASS_DEFINITION,
					assocTargetObjectClassDefinition, RefinedObjectClassDefinition.class);
		}
		builder = builder.addVariableDefinition(ExpressionConstants.VAR_RESOURCE, resource, ResourceType.class);
		builder = LensUtil.addAssignmentPathVariables(builder, assignmentPathVariables, getPrismContext());
		if (getSystemConfiguration() != null) {
			builder = builder.addVariableDefinition(ExpressionConstants.VAR_CONFIGURATION, getSystemConfiguration(), SystemConfigurationType.class);
		}
		// TODO: other variables ?

		// Set condition masks. There are used as a brakes to avoid evaluating
		// to nonsense values in case user is not present
		// (e.g. in old values in ADD situations and new values in DELETE
		// situations).
		if (getFocusOdo().getOldObject() == null) {
			builder = builder.conditionMaskOld(false);
		}
		if (getFocusOdo().getNewObject() == null) {
			builder = builder.conditionMaskNew(false);
		}

		MappingImpl<V, D> mapping = builder.build();
		mappingEvaluator.evaluateMapping(mapping, getLensContext(), projectionContext, task, result);

		return mapping;
	}

	private PrismContainerDefinition<ShadowAssociationType> getAssociationContainerDefinition() {
		if (associationContainerDefinition == null) {
			PrismObjectDefinition<ShadowType> shadowDefinition = getPrismContext().getSchemaRegistry()
					.findObjectDefinitionByCompileTimeClass(ShadowType.class);
			associationContainerDefinition = shadowDefinition
					.findContainerDefinition(ShadowType.F_ASSOCIATION);
		}
		return associationContainerDefinition;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result
				+ ((assignmentPathVariables == null) ? 0 : assignmentPathVariables.hashCode());
		result = prime * result
				+ ((associationContainerDefinition == null) ? 0 : associationContainerDefinition.hashCode());
		result = prime * result + ((associationMappings == null) ? 0 : associationMappings.hashCode());
		result = prime * result + ((attributeMappings == null) ? 0 : attributeMappings.hashCode());
		result = prime * result + ((auxiliaryObjectClassDefinitions == null) ? 0
				: auxiliaryObjectClassDefinitions.hashCode());
		result = prime * result + ((mappingEvaluator == null) ? 0 : mappingEvaluator.hashCode());
		result = prime * result + ((mappingFactory == null) ? 0 : mappingFactory.hashCode());
		result = prime * result + ((orderOneObject == null) ? 0 : orderOneObject.hashCode());
		result = prime * result
				+ ((refinedObjectClassDefinition == null) ? 0 : refinedObjectClassDefinition.hashCode());
		result = prime * result + ((resource == null) ? 0 : resource.hashCode());
		result = prime * result + ((systemConfiguration == null) ? 0 : systemConfiguration.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (!super.equals(obj)) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		Construction other = (Construction) obj;
		if (assignmentPathVariables == null) {
			if (other.assignmentPathVariables != null) {
				return false;
			}
		} else if (!assignmentPathVariables.equals(other.assignmentPathVariables)) {
			return false;
		}
		if (associationContainerDefinition == null) {
			if (other.associationContainerDefinition != null) {
				return false;
			}
		} else if (!associationContainerDefinition.equals(other.associationContainerDefinition)) {
			return false;
		}
		if (associationMappings == null) {
			if (other.associationMappings != null) {
				return false;
			}
		} else if (!associationMappings.equals(other.associationMappings)) {
			return false;
		}
		if (attributeMappings == null) {
			if (other.attributeMappings != null) {
				return false;
			}
		} else if (!attributeMappings.equals(other.attributeMappings)) {
			return false;
		}
		if (auxiliaryObjectClassDefinitions == null) {
			if (other.auxiliaryObjectClassDefinitions != null) {
				return false;
			}
		} else if (!auxiliaryObjectClassDefinitions.equals(other.auxiliaryObjectClassDefinitions)) {
			return false;
		}
		if (mappingEvaluator == null) {
			if (other.mappingEvaluator != null) {
				return false;
			}
		} else if (!mappingEvaluator.equals(other.mappingEvaluator)) {
			return false;
		}
		if (mappingFactory == null) {
			if (other.mappingFactory != null) {
				return false;
			}
		} else if (!mappingFactory.equals(other.mappingFactory)) {
			return false;
		}
		if (orderOneObject == null) {
			if (other.orderOneObject != null) {
				return false;
			}
		} else if (!orderOneObject.equals(other.orderOneObject)) {
			return false;
		}
		if (refinedObjectClassDefinition == null) {
			if (other.refinedObjectClassDefinition != null) {
				return false;
			}
		} else if (!refinedObjectClassDefinition.equals(other.refinedObjectClassDefinition)) {
			return false;
		}
		if (resource == null) {
			if (other.resource != null) {
				return false;
			}
		} else if (!resource.equals(other.resource)) {
			return false;
		}
		if (systemConfiguration == null) {
			if (other.systemConfiguration != null) {
				return false;
			}
		} else if (!systemConfiguration.equals(other.systemConfiguration)) {
			return false;
		}
		return true;
	}

	@Override
	public String debugDump(int indent) {
		StringBuilder sb = new StringBuilder();
		DebugUtil.debugDumpLabel(sb, "Construction", indent);
		if (refinedObjectClassDefinition == null) {
			sb.append(" (no object class definition)");
			if (getConstructionType() != null && getConstructionType().getResourceRef() != null) { // should
																							// be
																							// always
																							// the
																							// case
				sb.append("\n");
				DebugUtil.debugDumpLabel(sb, "resourceRef / kind / intent", indent + 1);
				sb.append(" ");
				sb.append(ObjectTypeUtil.toShortString(getConstructionType().getResourceRef()));
				sb.append(" / ");
				sb.append(getConstructionType().getKind());
				sb.append(" / ");
				sb.append(getConstructionType().getIntent());
			}
		} else {
			sb.append(refinedObjectClassDefinition.getShadowDiscriminator());
		}
		if (getConstructionType() != null && getConstructionType().getStrength() == ConstructionStrengthType.WEAK) {
			sb.append(" weak");
		}
		sb.append("\n");
		DebugUtil.debugDumpWithLabelLn(sb, "isValid", isValid(), indent + 1);
		DebugUtil.debugDumpWithLabelLn(sb, "wasValid", getWasValid(), indent + 1);
		DebugUtil.debugDumpWithLabelToStringLn(sb, "relativityMode", getRelativityMode(), indent + 1);
		DebugUtil.debugDumpLabel(sb, "auxiliary object classes", indent + 1);
		if (auxiliaryObjectClassDefinitions == null) {
			sb.append(" (null)");
		} else if (auxiliaryObjectClassDefinitions.isEmpty()) {
			sb.append(" (empty)");
		} else {
			for (RefinedObjectClassDefinition auxiliaryObjectClassDefinition : auxiliaryObjectClassDefinitions) {
				sb.append("\n");
				DebugUtil.indentDebugDump(sb, indent + 2);
				sb.append(auxiliaryObjectClassDefinition.getTypeName());
			}
		}
		if (getConstructionType() != null && getConstructionType().getDescription() != null) {
			sb.append("\n");
			DebugUtil.debugDumpLabel(sb, "description", indent + 1);
			sb.append(" ").append(getConstructionType().getDescription());
		}
		if (attributeMappings != null && !attributeMappings.isEmpty()) {
			sb.append("\n");
			DebugUtil.debugDumpLabel(sb, "attribute mappings", indent + 1);
			for (MappingImpl mapping : attributeMappings) {
				sb.append("\n");
				sb.append(mapping.debugDump(indent + 2));
			}
		}
		if (associationMappings != null && !associationMappings.isEmpty()) {
			sb.append("\n");
			DebugUtil.debugDumpLabel(sb, "association mappings", indent + 1);
			for (MappingImpl mapping : associationMappings) {
				sb.append("\n");
				sb.append(mapping.debugDump(indent + 2));
			}
		}
		if (getAssignmentPath() != null) {
			sb.append("\n");
			sb.append(getAssignmentPath().debugDump(indent + 1));
		}
		return sb.toString();
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder("Construction(");
		if (refinedObjectClassDefinition == null) {
			sb.append(getConstructionType());
		} else {
			sb.append(refinedObjectClassDefinition.getShadowDiscriminator());
		}
		sb.append(" in ").append(getSource());
//		if (getRelativityMode() != null) {
//			sb.append(", ").append(getRelativityMode());
//		}
		if (isValid()) {
			if (!getWasValid()) {
				sb.append(", invalid->valid");
			}
		} else {
			if (getWasValid()) {
				sb.append(", valid->invalid");
			} else {
				sb.append(", invalid");
			}
		}
		sb.append(")");
		return sb.toString();
	}

}
