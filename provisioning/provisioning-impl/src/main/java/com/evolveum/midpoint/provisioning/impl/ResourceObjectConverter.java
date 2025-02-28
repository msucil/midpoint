/*
 * Copyright (c) 2010-2017 Evolveum
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

package com.evolveum.midpoint.provisioning.impl;

import com.evolveum.midpoint.common.Clock;
import com.evolveum.midpoint.common.refinery.*;
import com.evolveum.midpoint.prism.*;
import com.evolveum.midpoint.prism.delta.*;
import com.evolveum.midpoint.prism.equivalence.EquivalenceStrategy;
import com.evolveum.midpoint.prism.match.MatchingRule;
import com.evolveum.midpoint.prism.match.MatchingRuleRegistry;
import com.evolveum.midpoint.prism.path.ItemPath;
import com.evolveum.midpoint.prism.query.ObjectQuery;
import com.evolveum.midpoint.prism.util.JavaTypeConverter;
import com.evolveum.midpoint.prism.util.PrismUtil;
import com.evolveum.midpoint.provisioning.api.GenericConnectorException;
import com.evolveum.midpoint.provisioning.ucf.api.*;
import com.evolveum.midpoint.provisioning.util.ProvisioningUtil;
import com.evolveum.midpoint.repo.cache.RepositoryCache;
import com.evolveum.midpoint.schema.*;
import com.evolveum.midpoint.schema.cache.CacheConfigurationManager;
import com.evolveum.midpoint.schema.constants.ObjectTypes;
import com.evolveum.midpoint.schema.constants.SchemaConstants;
import com.evolveum.midpoint.schema.internals.InternalsConfig;
import com.evolveum.midpoint.schema.processor.*;
import com.evolveum.midpoint.schema.result.*;
import com.evolveum.midpoint.schema.util.ObjectTypeUtil;
import com.evolveum.midpoint.schema.util.ResourceTypeUtil;
import com.evolveum.midpoint.schema.util.SchemaDebugUtil;
import com.evolveum.midpoint.schema.util.ShadowUtil;
import com.evolveum.midpoint.util.DebugUtil;
import com.evolveum.midpoint.util.Holder;
import com.evolveum.midpoint.util.MiscUtil;
import com.evolveum.midpoint.util.PrettyPrinter;
import com.evolveum.midpoint.util.exception.*;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;
import com.evolveum.midpoint.xml.ns._public.resource.capabilities_3.*;
import com.evolveum.prism.xml.ns._public.types_3.PolyStringType;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.Validate;
import org.apache.commons.lang3.BooleanUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.xml.datatype.XMLGregorianCalendar;
import javax.xml.namespace.QName;
import java.util.*;
import java.util.Map.Entry;

/**
 * 
 * Responsibilities:
 *     protected objects
 *     simulated activation
 *     script execution
 *     avoid duplicate values
 *     attributes returned by default/not returned by default
 *   
 * Limitations:
 *     must NOT access repository
 *     does not know about OIDs
 * 
 * @author Katarina Valalikova
 * @author Radovan Semancik
 *
 */
@Component
public class ResourceObjectConverter {
	
	private static final String DOT_CLASS = ResourceObjectConverter.class.getName() + ".";
	public static final String OPERATION_MODIFY_ENTITLEMENT = DOT_CLASS + "modifyEntitlement";
	private static final String OPERATION_ADD_RESOURCE_OBJECT = DOT_CLASS + "addResourceObject";
	private static final String OPERATION_MODIFY_RESOURCE_OBJECT = DOT_CLASS + "modifyResourceObject";
	private static final String OPERATION_DELETE_RESOURCE_OBJECT = DOT_CLASS + "deleteResourceObject";
	private static final String OPERATION_REFRESH_OPERATION_STATUS = DOT_CLASS + "refreshOperationStatus";
	
	
	@Autowired private EntitlementConverter entitlementConverter;
	@Autowired private MatchingRuleRegistry matchingRuleRegistry;	
	@Autowired private ResourceObjectReferenceResolver resourceObjectReferenceResolver;
	@Autowired private ShadowCaretaker shadowCaretaker;
	@Autowired private Clock clock;
	@Autowired private PrismContext prismContext;
	@Autowired private RelationRegistry relationRegistry;
	@Autowired private AsyncUpdateListeningRegistry listeningRegistry;
	@Autowired private CacheConfigurationManager cacheConfigurationManager;

	private static final Trace LOGGER = TraceManager.getTrace(ResourceObjectConverter.class);

	static final String FULL_SHADOW_KEY = ResourceObjectConverter.class.getName()+".fullShadow";


	public PrismObject<ShadowType> getResourceObject(ProvisioningContext ctx, 
			Collection<? extends ResourceAttribute<?>> identifiers, boolean fetchAssociations, OperationResult parentResult)
					throws ObjectNotFoundException, CommunicationException, SchemaException, ConfigurationException,
					SecurityViolationException, GenericConnectorException, ExpressionEvaluationException {
		
		LOGGER.trace("Getting resource object {}", identifiers);
		
		AttributesToReturn attributesToReturn = ProvisioningUtil.createAttributesToReturn(ctx);
		
		PrismObject<ShadowType> resourceShadow = fetchResourceObject(ctx, identifiers, 
				attributesToReturn, fetchAssociations, parentResult);			// todo consider whether it is always necessary to fetch the entitlements
		
		LOGGER.trace("Got resource object\n{}", resourceShadow.debugDumpLazily());
		
		return resourceShadow;

	}
	
	/**
	 * Tries to get the object directly if primary identifiers are present. Tries to search for the object if they are not. 
	 */
	public PrismObject<ShadowType> locateResourceObject(ProvisioningContext ctx,
			Collection<? extends ResourceAttribute<?>> identifiers, OperationResult parentResult) throws ObjectNotFoundException,
			CommunicationException, SchemaException, ConfigurationException, SecurityViolationException, GenericConnectorException, ExpressionEvaluationException {
		
		LOGGER.trace("Locating resource object {}", identifiers);
		
		ConnectorInstance connector = ctx.getConnector(ReadCapabilityType.class, parentResult);
		
		AttributesToReturn attributesToReturn = ProvisioningUtil.createAttributesToReturn(ctx);
		
		if (hasAllIdentifiers(identifiers, ctx.getObjectClassDefinition())) {
			return fetchResourceObject(ctx, identifiers, 
					attributesToReturn, true, parentResult);	// todo consider whether it is always necessary to fetch the entitlements
		} else {
			// Search
			Collection<? extends RefinedAttributeDefinition> secondaryIdentifierDefs = ctx.getObjectClassDefinition().getSecondaryIdentifiers();
			// Assume single secondary identifier for simplicity
			if (secondaryIdentifierDefs.size() > 1) {
				throw new UnsupportedOperationException("Composite secondary identifier is not supported yet");
			} else if (secondaryIdentifierDefs.isEmpty()) {
				throw new SchemaException("No secondary identifier defined, cannot search");
			}
			RefinedAttributeDefinition<String> secondaryIdentifierDef = secondaryIdentifierDefs.iterator().next();
			ResourceAttribute<?> secondaryIdentifier = null;
			for (ResourceAttribute<?> identifier: identifiers) {
				if (identifier.getElementName().equals(secondaryIdentifierDef.getName())) {
					secondaryIdentifier = identifier;
				}
			}
			if (secondaryIdentifier == null) {
				throw new SchemaException("No secondary identifier present, cannot search. Identifiers: "+identifiers);
			}
			
			final ResourceAttribute<?> finalSecondaryIdentifier = secondaryIdentifier;

            List<PrismPropertyValue<String>> secondaryIdentifierValues = (List) secondaryIdentifier.getValues();
            PrismPropertyValue<String> secondaryIdentifierValue;
            if (secondaryIdentifierValues.size() > 1) {
                throw new IllegalStateException("Secondary identifier has more than one value: " + secondaryIdentifier.getValues());
            } else if (secondaryIdentifierValues.size() == 1) {
                secondaryIdentifierValue = secondaryIdentifierValues.get(0).clone();
            } else {
                secondaryIdentifierValue = null;
            }
            ObjectQuery query = prismContext.queryFor(ShadowType.class)
					.itemWithDef(secondaryIdentifierDef, ShadowType.F_ATTRIBUTES, secondaryIdentifierDef.getName()).eq(secondaryIdentifierValue)
					.build();
			final Holder<PrismObject<ShadowType>> shadowHolder = new Holder<>();
			ShadowResultHandler handler = new ShadowResultHandler() {
				@Override
				public boolean handle(PrismObject<ShadowType> shadow) {
					if (!shadowHolder.isEmpty()) {
						throw new IllegalStateException("More than one value found for secondary identifier "+finalSecondaryIdentifier);
					}
					shadowHolder.setValue(shadow);
					return true;
				}
			};
			try {
				connector.search(ctx.getObjectClassDefinition(), query, handler, attributesToReturn, null, null, ctx, parentResult);
				if (shadowHolder.isEmpty()) {
					throw new ObjectNotFoundException("No object found for secondary identifier "+secondaryIdentifier);
				}
				PrismObject<ShadowType> shadow = shadowHolder.getValue();
				PrismObject<ShadowType> finalShadow = postProcessResourceObjectRead(ctx, shadow, true, parentResult);
				LOGGER.trace("Located resource object {}", finalShadow);
				return finalShadow;
			} catch (GenericFrameworkException e) {
				throw new GenericConnectorException(e.getMessage(), e);
			}
		}

	}

	private boolean hasAllIdentifiers(Collection<? extends ResourceAttribute<?>> attributes,
			RefinedObjectClassDefinition objectClassDefinition) {
		Collection<? extends RefinedAttributeDefinition> identifierDefs = objectClassDefinition.getPrimaryIdentifiers();
		for (RefinedAttributeDefinition identifierDef: identifierDefs) {
			boolean found = false;
			for(ResourceAttribute<?> attribute: attributes) {
				if (attribute.getElementName().equals(identifierDef.getName()) && !attribute.isEmpty()) {
					found = true;
				}
			}
			if (!found) {
				return false;
			}
		}
		return true;
	}

	

	public AsynchronousOperationReturnValue<PrismObject<ShadowType>> addResourceObject(ProvisioningContext ctx, 
			PrismObject<ShadowType> shadow, OperationProvisioningScriptsType scripts, ConnectorOperationOptions connOptions,
			boolean skipExplicitUniquenessCheck, OperationResult parentResult)
					throws ObjectNotFoundException, SchemaException, CommunicationException,
					ObjectAlreadyExistsException, ConfigurationException, SecurityViolationException, PolicyViolationException, ExpressionEvaluationException {
		
		OperationResult result = parentResult.createSubresult(OPERATION_ADD_RESOURCE_OBJECT);
		
		ResourceType resource = ctx.getResource();
		
		LOGGER.trace("Adding resource object {}", shadow);
		
		// We might be modifying the shadow (e.g. for simulated capabilities). But we do not want the changes
		// to propagate back to the calling code. Hence the clone.
		PrismObject<ShadowType> shadowClone = shadow.clone();
		ShadowType shadowType = shadowClone.asObjectable();

		Collection<ResourceAttribute<?>> resourceAttributesAfterAdd = null;

		if (ProvisioningUtil.isProtectedShadow(ctx.getObjectClassDefinition(), shadowClone, matchingRuleRegistry,
				relationRegistry)) {
			LOGGER.error("Attempt to add protected shadow " + shadowType + "; ignoring the request");
			SecurityViolationException e = new SecurityViolationException("Cannot get protected shadow " + shadowType);
			result.recordFatalError(e);
			throw e;
		}
		
		if (!skipExplicitUniquenessCheck) {
			checkForAddConflicts(ctx, shadow, result);
		}
		
		checkForCapability(ctx, CreateCapabilityType.class, result);
		
		Collection<Operation> additionalOperations = new ArrayList<>();
		addExecuteScriptOperation(additionalOperations, ProvisioningOperationTypeType.ADD, scripts, resource,
				result);
		entitlementConverter.processEntitlementsAdd(ctx, shadowClone);
		
		ConnectorInstance connector = ctx.getConnector(CreateCapabilityType.class, result);
		AsynchronousOperationReturnValue<Collection<ResourceAttribute<?>>> connectorAsyncOpRet = null;
		try {

			if (LOGGER.isDebugEnabled()) {
				LOGGER.debug("PROVISIONING ADD operation on resource {}\n ADD object:\n{}\n additional operations:\n{}",
						resource.asPrismObject(), shadowType.asPrismObject().debugDump(),
						SchemaDebugUtil.debugDump(additionalOperations,2));
			}
			transformActivationAttributesAdd(ctx, shadowType, result);
			
			connectorAsyncOpRet = connector.addObject(shadowClone, additionalOperations, ctx, result);
			resourceAttributesAfterAdd = connectorAsyncOpRet.getReturnValue();

			if (LOGGER.isDebugEnabled()) {
				LOGGER.debug("PROVISIONING ADD successful, returned attributes:\n{}",
						SchemaDebugUtil.prettyPrint(resourceAttributesAfterAdd));
			}

			// Be careful not to apply this to the cloned shadow. This needs to be propagated
			// outside this method.
			applyAfterOperationAttributes(shadow, resourceAttributesAfterAdd);
		} catch (CommunicationException ex) {
			result.recordFatalError(
					"Could not create object on the resource. Error communicating with the connector " + connector + ": " + ex.getMessage(), ex);
			throw new CommunicationException("Error communicating with the connector " + connector + ": "
					+ ex.getMessage(), ex);
		} catch (GenericFrameworkException ex) {
			result.recordFatalError("Could not create object on the resource. Generic error in connector: " + ex.getMessage(), ex);
			throw new GenericConnectorException("Generic error in connector: " + ex.getMessage(), ex);
		} catch (ObjectAlreadyExistsException ex){
			result.recordFatalError("Could not create object on the resource. Object already exists on the resource: " + ex.getMessage(), ex);
			throw new ObjectAlreadyExistsException("Object already exists on the resource: " + ex.getMessage(), ex);
		} catch (ConfigurationException | SchemaException | SecurityViolationException | PolicyViolationException | RuntimeException | Error e){
			result.recordFatalError(e);
			throw e;
		}
		
		// Execute entitlement modification on other objects (if needed)
		executeEntitlementChangesAdd(ctx, shadowClone, scripts, connOptions, result);
		
		LOGGER.trace("Added resource object {}", shadow);

		computeResultStatus(result);
		
		AsynchronousOperationReturnValue<PrismObject<ShadowType>> asyncOpRet = AsynchronousOperationReturnValue.wrap(shadow, result);
		if (connectorAsyncOpRet != null) {
			asyncOpRet.setOperationType(connectorAsyncOpRet.getOperationType());
		}
		return asyncOpRet;
	}

	/**
	 * Special case for multi-connectors (e.g. semi-manual connectors). There is a possibility that the object
	 * which we want to add is already present in the backing store. In case of manual provisioning the resource
	 * itself will not indicate "already exist" error. We have to explicitly check for that.
	 */
	private void checkForAddConflicts(ProvisioningContext ctx, PrismObject<ShadowType> shadow, OperationResult result) throws ObjectAlreadyExistsException, SchemaException, CommunicationException, ConfigurationException, SecurityViolationException, ExpressionEvaluationException, ObjectNotFoundException {
		if (LOGGER.isTraceEnabled()) {
			LOGGER.trace("Checking for add conflicts for {}", ShadowUtil.shortDumpShadow(shadow));
		}
		PrismObject<ShadowType> existingObject = null;
		ConnectorInstance readConnector = null;
		try {
			ConnectorInstance createConnector = ctx.getConnector(CreateCapabilityType.class, result);
			readConnector = ctx.getConnector(ReadCapabilityType.class, result);
			if (readConnector == createConnector) {
				// Same connector for reading and creating. We assume that the connector can check uniqueness itself. 
				// No need to check explicitly. We will gladly skip the check, as the check may be additional overhead
				// that we normally do not need or want.
				return;
			}
			ResourceObjectIdentification identification = ResourceObjectIdentification.createFromShadow(ctx.getObjectClassDefinition(), shadow.asObjectable());
		
			existingObject = readConnector.fetchObject(identification, null, ctx, result);
		} catch (ObjectNotFoundException e) {
			// This is OK
			result.muteLastSubresultError();
			return;
		} catch (CommunicationException ex) {
			result.recordFatalError(
					"Could not create object on the resource. Error communicating with the connector " + readConnector + ": " + ex.getMessage(), ex);
			throw new CommunicationException("Error communicating with the connector " + readConnector + ": "
					+ ex.getMessage(), ex);
		} catch (GenericFrameworkException ex) {
			result.recordFatalError("Could not create object on the resource. Generic error in connector: " + ex.getMessage(), ex);
			throw new GenericConnectorException("Generic error in connector: " + ex.getMessage(), ex);
		} catch (Throwable e){
			result.recordFatalError(e);
			throw e;
		}
		if (existingObject == null) {
			LOGGER.trace("No add conflicts for {}", shadow);
		} else {
			if (LOGGER.isDebugEnabled()) {
				LOGGER.debug("Detected add conflict for {}, conflicting shadow: {}", ShadowUtil.shortDumpShadow(shadow), ShadowUtil.shortDumpShadow(existingObject));
			}
			LOGGER.trace("Conflicting shadow:\n{}", existingObject.debugDumpLazily(1));
			ObjectAlreadyExistsException e = new ObjectAlreadyExistsException("Object " + ShadowUtil.shortDumpShadow(shadow) +
					" already exists in the snapshot of " + ctx.getResource() + " as " + ShadowUtil.shortDumpShadow(existingObject));
			result.recordFatalError(e);
			throw e;
		}
	}

	public AsynchronousOperationResult deleteResourceObject(ProvisioningContext ctx, PrismObject<ShadowType> shadow, 
			OperationProvisioningScriptsType scripts, ConnectorOperationOptions connOptions, OperationResult parentResult)
			throws ObjectNotFoundException, SchemaException, CommunicationException, ConfigurationException,
			SecurityViolationException, PolicyViolationException, ExpressionEvaluationException {
		
		OperationResult result = parentResult.createSubresult(OPERATION_DELETE_RESOURCE_OBJECT);
		
		LOGGER.trace("Deleting resource object {}", shadow);

		checkForCapability(ctx, DeleteCapabilityType.class, result);
		
		Collection<? extends ResourceAttribute<?>> identifiers = ShadowUtil
				.getAllIdentifiers(shadow);

		if (ProvisioningUtil.isProtectedShadow(ctx.getObjectClassDefinition(), shadow, matchingRuleRegistry, relationRegistry)) {
			LOGGER.error("Attempt to delete protected resource object " + ctx.getObjectClassDefinition() + ": "
					+ identifiers + "; ignoring the request");
			SecurityViolationException e = new SecurityViolationException("Cannot delete protected resource object "
					+ ctx.getObjectClassDefinition() + ": " + identifiers);
			result.recordFatalError(e);
			throw e;
		}
		
		//check idetifier if it is not null
		if (identifiers.isEmpty() && shadow.asObjectable().getFailedOperationType()!= null){
			throw new GenericConnectorException(
					"Unable to delete object from the resource. Probably it has not been created yet because of previous unavailability of the resource.");
		}
		
		// Execute entitlement modification on other objects (if needed)
		executeEntitlementChangesDelete(ctx, shadow, scripts, connOptions, result);

		Collection<Operation> additionalOperations = new ArrayList<>();
		addExecuteScriptOperation(additionalOperations, ProvisioningOperationTypeType.DELETE, scripts, ctx.getResource(),
				result);

		ConnectorInstance connector = ctx.getConnector(DeleteCapabilityType.class, result);
		AsynchronousOperationResult connectorAsyncOpRet = null;
		try {

			if (LOGGER.isDebugEnabled()) {
				LOGGER.debug(
						"PROVISIONING DELETE operation on {}\n DELETE object, object class {}, identified by:\n{}\n additional operations:\n{}",
						ctx.getResource(), shadow.asObjectable().getObjectClass(),
						SchemaDebugUtil.debugDump(identifiers),
						SchemaDebugUtil.debugDump(additionalOperations));
			}
			
			if (!ResourceTypeUtil.isDeleteCapabilityEnabled(ctx.getResource())){
				UnsupportedOperationException e = new UnsupportedOperationException("Resource does not support 'delete' operation");
				result.recordFatalError(e);
				throw e;
			}

			connectorAsyncOpRet = connector.deleteObject(ctx.getObjectClassDefinition(), additionalOperations, shadow, identifiers, ctx, result);

			computeResultStatus(result);
			LOGGER.debug("PROVISIONING DELETE: {}", result.getStatus());

		} catch (ObjectNotFoundException ex) {
			result.recordFatalError("Can't delete object " + shadow
					+ ". Reason: " + ex.getMessage(), ex);
			throw new ObjectNotFoundException("An error occured while deleting resource object " + shadow
					+ "whith identifiers " + identifiers + ": " + ex.getMessage(), ex);
		} catch (CommunicationException ex) {
			result.recordFatalError(
					"Error communicating with the connector " + connector + ": " + ex.getMessage(), ex);
			throw new CommunicationException("Error communicating with the connector " + connector + ": "
					+ ex.getMessage(), ex);
		} catch (ConfigurationException ex) {
			result.recordFatalError(
					"Configuration error in connector " + connector + ": " + ex.getMessage(), ex);
			throw new ConfigurationException("Configuration error in connector " + connector + ": "
					+ ex.getMessage(), ex);
		} catch (ExpressionEvaluationException ex) {
			result.recordFatalError(
					"Expression error while setting up the resource: " + ex.getMessage(), ex);
			throw new ExpressionEvaluationException("Expression error while setting up the resource: "
					+ ex.getMessage(), ex);
		} catch (GenericFrameworkException ex) {
			result.recordFatalError("Generic error in connector: " + ex.getMessage(), ex);
			throw new GenericConnectorException("Generic error in connector: " + ex.getMessage(), ex);
		} catch (SecurityViolationException | PolicyViolationException | RuntimeException | Error ex) {
			result.recordFatalError(ex);
			throw ex;
		}
		
		
		LOGGER.trace("Deleted resource object {}", shadow);
		AsynchronousOperationResult aResult = AsynchronousOperationResult.wrap(result);
		updateQuantum(ctx, connector, aResult, parentResult);
		if (connectorAsyncOpRet != null) {
			aResult.setOperationType(connectorAsyncOpRet.getOperationType());
		}
		return aResult;
	}
	
	private void updateQuantum(ProvisioningContext ctx, ConnectorInstance connectorUsedForOperation, AsynchronousOperationResult aResult, OperationResult parentResult) throws ObjectNotFoundException, SchemaException, CommunicationException, ConfigurationException, ExpressionEvaluationException {
		ConnectorInstance readConnector = ctx.getConnector(ReadCapabilityType.class, parentResult);
		if (readConnector != connectorUsedForOperation) {
			// Writing by different connector that we are going to use for reading: danger of quantum effects
			aResult.setQuantumOperation(true);
		}
	}

	public AsynchronousOperationReturnValue<Collection<PropertyDelta<PrismPropertyValue>>> modifyResourceObject(
			ProvisioningContext ctx,
			PrismObject<ShadowType> repoShadow,
			OperationProvisioningScriptsType scripts,
			ConnectorOperationOptions connOptions,
			Collection<? extends ItemDelta> itemDeltas,
			XMLGregorianCalendar now,
			OperationResult parentResult)
					throws ObjectNotFoundException, SchemaException, CommunicationException, ConfigurationException,
						SecurityViolationException, PolicyViolationException, ObjectAlreadyExistsException, ExpressionEvaluationException {
		
		OperationResult result = parentResult.createSubresult(OPERATION_MODIFY_RESOURCE_OBJECT);
		
		try {
		
			if (LOGGER.isTraceEnabled()) {
				LOGGER.trace("Modifying resource object {}, deltas:\n{}", repoShadow, DebugUtil.debugDump(itemDeltas, 1));
			}
			
			RefinedObjectClassDefinition objectClassDefinition = ctx.getObjectClassDefinition();
			Collection<Operation> operations = new ArrayList<>();
			
			Collection<? extends ResourceAttribute<?>> identifiers = ShadowUtil.getAllIdentifiers(repoShadow);
			Collection<? extends ResourceAttribute<?>> primaryIdentifiers = ShadowUtil.getPrimaryIdentifiers(repoShadow);
	
			if (ProvisioningUtil.isProtectedShadow(ctx.getObjectClassDefinition(), repoShadow, matchingRuleRegistry,
					relationRegistry)) {
				if (hasChangesOnResource(itemDeltas)) {
					LOGGER.error("Attempt to modify protected resource object " + objectClassDefinition + ": "
							+ identifiers);
					SecurityViolationException e = new SecurityViolationException("Cannot modify protected resource object "
							+ objectClassDefinition + ": " + identifiers);
					result.recordFatalError(e);
					throw e;
				} else {
					// Return immediately. This structure of the code makes sure that we do not execute any
					// resource operation for protected account even if there is a bug in the code below.
					LOGGER.trace("No resource modifications for protected resource object {}: {}; skipping",
							objectClassDefinition, identifiers);
					result.recordNotApplicableIfUnknown();
					return AsynchronousOperationReturnValue.wrap(null, result);
				}
			}
			
			boolean hasVolatilityTriggerModification = false;
			boolean hasResourceModification = false;
			for (ItemDelta modification: itemDeltas) {
				ItemPath path = modification.getPath();
				QName firstPathName = path.firstName();
				if (ProvisioningUtil.isAttributeModification(firstPathName)) {
					hasResourceModification = true;
					QName attrName = path.rest().firstNameOrFail();
					RefinedAttributeDefinition<Object> attrDef = ctx.getObjectClassDefinition().findAttributeDefinition(attrName);
					if (attrDef.isVolatilityTrigger()) {
						LOGGER.trace("Will pre-read and re-read object because volatility trigger attribute {} has changed", attrName);
						hasVolatilityTriggerModification = true;
						break;
					}
				} else if (ProvisioningUtil.isNonAttributeResourceModification(firstPathName)) {
					hasResourceModification = true;
				}
			}
			
			if (!hasResourceModification) {
				// Quit early, so we avoid potential pre-read and other processing when there is no point of doing so.
				// Also the read may fail which may invoke consistency mechanism which will complicate the situation.
				LOGGER.trace("No resource modification found for {}, skipping", identifiers);
				result.recordNotApplicableIfUnknown();
				return AsynchronousOperationReturnValue.wrap(null, result);
			}
	
	        /*
	         *  State of the shadow before execution of the deltas - e.g. with original attributes, as it may be recorded in such a way in
	         *  groups of which this account is a member of. (In case of object->subject associations.)
	         *
	         *  This is used when the resource does NOT provide referential integrity by itself. This is e.g. the case of OpenDJ with default
	         *  settings.
	         *
	         *  On the contrary, AD and OpenDJ with referential integrity plugin do provide automatic referential integrity, so this feature is
	         *  not needed.
	         *
	         *  We decide based on setting of explicitReferentialIntegrity in association definition.
	         */
	       
	
			collectAttributeAndEntitlementChanges(ctx, itemDeltas, operations, repoShadow, result);
			
			PrismObject<ShadowType> preReadShadow = null;
			Collection<PropertyModificationOperation> sideEffectOperations = null;
			AsynchronousOperationReturnValue<Collection<PropertyModificationOperation>> modifyAsyncRet = null;
			
			//check identifier if it is not null
			if ((primaryIdentifiers == null || primaryIdentifiers.isEmpty()) && repoShadow.asObjectable().getFailedOperationType() != null){
				GenericConnectorException e = new GenericConnectorException(
						"Unable to modify object in the resource. Probably it has not been created yet because of previous unavailability of the resource.");
				result.recordFatalError(e);
				throw e;
			}
			
			if (hasVolatilityTriggerModification || ResourceTypeUtil.isAvoidDuplicateValues(ctx.getResource()) || isRename(ctx, operations)) {
				// We need to filter out the deltas that add duplicate values or remove values that are not there
				LOGGER.trace("Pre-reading resource shadow");
				preReadShadow = preReadShadow(ctx, identifiers, operations, true, repoShadow, result);  // yes, we need associations here
				if (LOGGER.isTraceEnabled()) {
					LOGGER.trace("Pre-read object (straight from the resource):\n{}", preReadShadow==null?null:preReadShadow.debugDump(1));
				}
				// If there are pending changes in the shadow then we have to apply to pre-read object.
				// The pre-read object may be out of date (e.g. in case of semi-manual connectors).
				// In that case we may falsely remove some of the modifications. E.g. in case that
				// account is enabled, then disable and then enabled again. If backing store still
				// has the account as enabled, then the last enable operation would be ignored.
				// No case is created to re-enable the account. And the account stays disabled at the end.
				preReadShadow = shadowCaretaker.applyPendingOperations(ctx, repoShadow, preReadShadow, true, now);
				if (LOGGER.isTraceEnabled()) {
					LOGGER.trace("Pre-read object (applied pending operations):\n{}", preReadShadow==null?null:preReadShadow.debugDump(1));
				}
			}
			
			if (!operations.isEmpty()) {
				
				// This must go after the skip check above. Otherwise the scripts would be executed even if there is no need to.
				addExecuteScriptOperation(operations, ProvisioningOperationTypeType.MODIFY, scripts, ctx.getResource(), result);
				
				if (InternalsConfig.isSanityChecks()) {
					// MID-3964
					if (MiscUtil.hasDuplicates(operations)) {
						throw new SchemaException("Duplicated changes: "+operations);
					}
				}
				
				// Execute primary ICF operation on this shadow
				modifyAsyncRet = executeModify(ctx, (preReadShadow == null ? repoShadow.clone() : preReadShadow), identifiers, operations, connOptions, result);
				if (modifyAsyncRet != null) {
					sideEffectOperations = modifyAsyncRet.getReturnValue();
				}
				
			} else {
				// We have to check BEFORE we add script operations, otherwise the check would be pointless
				LOGGER.trace("No modifications for connector object specified. Skipping processing of subject executeModify.");
			}
	
			Collection<PropertyDelta<PrismPropertyValue>> sideEffectDeltas = convertToPropertyDelta(sideEffectOperations);
			
	        /*
	         *  State of the shadow after execution of the deltas - e.g. with new DN (if it was part of the delta), because this one should be recorded
	         *  in groups of which this account is a member of. (In case of object->subject associations.)
	         */
	        PrismObject<ShadowType> shadowAfter = preReadShadow == null ? repoShadow.clone() : preReadShadow.clone();
	        for (ItemDelta itemDelta : itemDeltas) {
	            itemDelta.applyTo(shadowAfter);
	        }
	        
	        PrismObject<ShadowType> postReadShadow = null;
	        if (hasVolatilityTriggerModification) {
	        	// There may be other changes that were not detected by the connector. Re-read the object and compare.
	        	LOGGER.trace("Post-reading resource shadow");
	        	postReadShadow = preReadShadow(ctx, identifiers, operations, true, repoShadow, result);
	        	if (LOGGER.isTraceEnabled()) {
					LOGGER.trace("Post-read object:\n{}", postReadShadow.debugDump());
				}
	        	ObjectDelta<ShadowType> resourceShadowDelta = preReadShadow.diff(postReadShadow);
	        	if (LOGGER.isTraceEnabled()) {
	        		LOGGER.trace("Determined side-effect changes by old-new diff:\n{}", resourceShadowDelta.debugDump());
	        	}
	        	for (ItemDelta modification: resourceShadowDelta.getModifications()) {
	        		if (modification.getParentPath().startsWithName(ShadowType.F_ATTRIBUTES) && !ItemDeltaCollectionsUtil
					        .hasEquivalent(itemDeltas, modification)) {
	        			ItemDeltaCollectionsUtil.merge(sideEffectDeltas, modification);
	        		}
	        	}
	        	if (LOGGER.isTraceEnabled()) {
	        		LOGGER.trace("Side-effect changes after merging with old-new diff:\n{}", DebugUtil.debugDump(sideEffectDeltas));
	        	}
	        }
	
	        Collection<? extends ItemDelta> allDeltas = new ArrayList<>();
	        ((Collection)allDeltas).addAll(itemDeltas);
	        ((Collection)allDeltas).addAll(sideEffectDeltas);
	        
	        // Execute entitlement modification on other objects (if needed)
	        shadowAfter = executeEntitlementChangesModify(ctx, 
	        		preReadShadow == null ? repoShadow : preReadShadow,
	        		postReadShadow == null ? shadowAfter : postReadShadow,
	        		scripts, connOptions, allDeltas, result);
			
	        if (!sideEffectDeltas.isEmpty()) {
				if (preReadShadow != null) {
					PrismUtil.setDeltaOldValue(preReadShadow, sideEffectDeltas);
				} else {
					PrismUtil.setDeltaOldValue(repoShadow, sideEffectDeltas);
				}
			}
	        
	        if (LOGGER.isTraceEnabled()) {
	    		LOGGER.trace("Modificaiton side-effect changes:\n{}", DebugUtil.debugDump(sideEffectDeltas));
	    	}
	        
	        LOGGER.trace("Modified resource object {}", repoShadow);
	        
	        computeResultStatus(result);
	        
			AsynchronousOperationReturnValue<Collection<PropertyDelta<PrismPropertyValue>>> aResult = AsynchronousOperationReturnValue.wrap(sideEffectDeltas, result);
			if (modifyAsyncRet != null) {
				aResult.setOperationType(modifyAsyncRet.getOperationType());
			}
			return aResult;
			
		} catch (Throwable e) {
			result.recordFatalError(e);
			throw e;
		}
	}
	
	private Collection<PropertyDelta<PrismPropertyValue>> convertToPropertyDelta(
			Collection<PropertyModificationOperation> sideEffectOperations) {
		Collection<PropertyDelta<PrismPropertyValue>> sideEffectDeltas = new ArrayList<>();
		if (sideEffectOperations != null) {
			for (PropertyModificationOperation mod : sideEffectOperations){
				sideEffectDeltas.add(mod.getPropertyDelta());
			}
		}
		
		return sideEffectDeltas;
	}

	@SuppressWarnings("rawtypes")
	private AsynchronousOperationReturnValue<Collection<PropertyModificationOperation>> executeModify(ProvisioningContext ctx, 
			PrismObject<ShadowType> currentShadow, Collection<? extends ResourceAttribute<?>> identifiers, 
			Collection<Operation> operations, ConnectorOperationOptions connOptions, OperationResult parentResult) 
			throws ObjectNotFoundException, CommunicationException, SchemaException, SecurityViolationException, PolicyViolationException, ConfigurationException, ObjectAlreadyExistsException, ExpressionEvaluationException {
		
		Collection<PropertyModificationOperation> sideEffectChanges = new HashSet<>();

		RefinedObjectClassDefinition objectClassDefinition = ctx.getObjectClassDefinition();
		if (operations.isEmpty()){
			LOGGER.trace("No modifications for resource object. Skipping modification.");
			return null;
		} else {
			LOGGER.trace("Resource object modification operations: {}", operations);
		}
		
		checkForCapability(ctx, UpdateCapabilityType.class, parentResult);
		
		if (!ShadowUtil.hasPrimaryIdentifier(identifiers, objectClassDefinition)) {
			Collection<? extends ResourceAttribute<?>> primaryIdentifiers = resourceObjectReferenceResolver.resolvePrimaryIdentifier(ctx, identifiers, "modification of resource object "+identifiers, parentResult);
			if (primaryIdentifiers == null || primaryIdentifiers.isEmpty()) {
				throw new ObjectNotFoundException("Cannot find repository shadow for identifiers "+identifiers);
			}
			identifiers = primaryIdentifiers;
		}
		
		// Invoke ICF
		ConnectorInstance connector = ctx.getConnector(UpdateCapabilityType.class, parentResult);
		AsynchronousOperationReturnValue<Collection<PropertyModificationOperation>> connectorAsyncOpRet = null;
		try {
			
			if (ResourceTypeUtil.isAvoidDuplicateValues(ctx.getResource())) {

				if (currentShadow == null) {
					LOGGER.trace("Fetching shadow for duplicate filtering");
					currentShadow = preReadShadow(ctx, identifiers, operations, false, currentShadow, parentResult);
				}
				
				if (currentShadow == null) {
					
					LOGGER.debug("We do not have pre-read shadow, skipping duplicate filtering");
					
				} else {
					
					LOGGER.trace("Filtering out duplicate values");
				
					Collection<Operation> filteredOperations = new ArrayList<>(operations.size());
					for (Operation origOperation: operations) {
						if (origOperation instanceof PropertyModificationOperation) {
							PropertyModificationOperation modificationOperation = (PropertyModificationOperation)origOperation;
							PropertyDelta<?> propertyDelta = modificationOperation.getPropertyDelta();
							PropertyDelta<?> filteredDelta = ProvisioningUtil.narrowPropertyDelta(propertyDelta, currentShadow,
									modificationOperation.getMatchingRuleQName(), matchingRuleRegistry);
							if (filteredDelta != null && !filteredDelta.isEmpty()) {
								if (propertyDelta == filteredDelta) {
									filteredOperations.add(origOperation);
								} else {
									PropertyModificationOperation newOp = new PropertyModificationOperation<>(filteredDelta);
									newOp.setMatchingRuleQName(modificationOperation.getMatchingRuleQName());
									filteredOperations.add(newOp);
								}
							} else {
								LOGGER.trace("Filtering out modification {} because it has empty delta after narrow", propertyDelta);
							}
						} else if (origOperation instanceof ExecuteProvisioningScriptOperation) {
							filteredOperations.add(origOperation);					
						}
					}
					if (filteredOperations.isEmpty()) {
						LOGGER.debug("No modifications for connector object specified (after filtering). Skipping processing.");
						parentResult.recordSuccess();
						return null;
					}
					operations = filteredOperations;
				}
			}
			
			if (LOGGER.isDebugEnabled()) {
				LOGGER.debug(
						"PROVISIONING MODIFY operation on {}\n MODIFY object, object class {}, identified by:\n{}\n changes:\n{}",
						ctx.getResource(), objectClassDefinition.getHumanReadableName(),
						SchemaDebugUtil.debugDump(identifiers, 1), SchemaDebugUtil.debugDump(operations, 1));
			}
			
			if (!ResourceTypeUtil.isUpdateCapabilityEnabled(ctx.getResource())){
				if (operations == null || operations.isEmpty()){
					LOGGER.debug("No modifications for connector object specified (after filtering). Skipping processing.");
					parentResult.recordSuccess();
					return null;
				}
				UnsupportedOperationException e = new UnsupportedOperationException("Resource does not support 'update' operation");
				parentResult.recordFatalError(e);
				throw e;
			}
			
			Collection<ResourceAttribute<?>> identifiersWorkingCopy = cloneIdentifiers(identifiers);			// because identifiers can be modified e.g. on rename operation
			List<Collection<Operation>> operationsWaves = sortOperationsIntoWaves(operations, objectClassDefinition);
			LOGGER.trace("Operation waves: {}", operationsWaves.size());
			boolean inProgress = false;
			String asynchronousOperationReference = null;
			for (Collection<Operation> operationsWave : operationsWaves) {
				Collection<RefinedAttributeDefinition> readReplaceAttributes = determineReadReplace(ctx, operationsWave, objectClassDefinition);
				LOGGER.trace("Read+Replace attributes: {}", readReplaceAttributes);
				if (!readReplaceAttributes.isEmpty()) {
					AttributesToReturn attributesToReturn = new AttributesToReturn();
					attributesToReturn.setReturnDefaultAttributes(false);
					attributesToReturn.setAttributesToReturn(readReplaceAttributes);
					// TODO eliminate this fetch if this is first wave and there are no explicitly requested attributes
					// but make sure currentShadow contains all required attributes
					LOGGER.trace("Fetching object because of READ+REPLACE mode");
					currentShadow = fetchResourceObject(ctx, 
							identifiersWorkingCopy, attributesToReturn, false, parentResult);
					operationsWave = convertToReplace(ctx, operationsWave, currentShadow);
				}
				if (!operationsWave.isEmpty()) {
					ResourceObjectIdentification identification = ResourceObjectIdentification.create(objectClassDefinition, identifiersWorkingCopy);
					connectorAsyncOpRet = connector.modifyObject(identification, currentShadow, operationsWave, connOptions, ctx, parentResult);
					Collection<PropertyModificationOperation> sideEffects = connectorAsyncOpRet.getReturnValue();
					if (sideEffects != null) {
						sideEffectChanges.addAll(sideEffects);
						// we accept that one attribute can be changed multiple times in sideEffectChanges; TODO: normalize
					}
					if (connectorAsyncOpRet.isInProgress()) {
						inProgress = true;
						asynchronousOperationReference = connectorAsyncOpRet.getOperationResult().getAsynchronousOperationReference();
					}
				}
			}

			if (LOGGER.isDebugEnabled()) {
				LOGGER.debug("PROVISIONING MODIFY successful, inProgress={}, side-effect changes {}", inProgress, DebugUtil.debugDump(sideEffectChanges));
			}
			
			if (inProgress) {
				parentResult.recordInProgress();
				parentResult.setAsynchronousOperationReference(asynchronousOperationReference);
			}

		} catch (ObjectNotFoundException ex) {
			parentResult.recordFatalError("Object to modify not found: " + ex.getMessage(), ex);
			throw new ObjectNotFoundException("Object to modify not found: " + ex.getMessage(), ex);
		} catch (CommunicationException ex) {
			parentResult.recordFatalError(
					"Error communicating with the connector " + connector + ": " + ex.getMessage(), ex);
			throw new CommunicationException("Error communicating with connector " + connector + ": "
					+ ex.getMessage(), ex);
		} catch (GenericFrameworkException ex) {
			parentResult.recordFatalError(
					"Generic error in the connector " + connector + ": " + ex.getMessage(), ex);
			throw new GenericConnectorException("Generic error in connector connector " + connector + ": "
					+ ex.getMessage(), ex);
		} catch (ObjectAlreadyExistsException ex) {
			parentResult.recordFatalError("Conflict during modify: " + ex.getMessage(), ex);
			throw new ObjectAlreadyExistsException("Conflict during modify: " + ex.getMessage(), ex);
		} catch (SchemaException | ConfigurationException | ExpressionEvaluationException | SecurityViolationException | PolicyViolationException | RuntimeException | Error ex) {
			parentResult.recordFatalError(ex.getMessage(), ex);
			throw ex;
		}
		
		AsynchronousOperationReturnValue<Collection<PropertyModificationOperation>> asyncOpRet = AsynchronousOperationReturnValue.wrap(sideEffectChanges, parentResult);
		if (connectorAsyncOpRet != null) {
			asyncOpRet.setOperationType(connectorAsyncOpRet.getOperationType());
		}
		return asyncOpRet;
	}

	@SuppressWarnings("rawtypes")
	private PrismObject<ShadowType> preReadShadow(
			ProvisioningContext ctx, 
			Collection<? extends ResourceAttribute<?>> identifiers, 
			Collection<Operation> operations,
			boolean fetchEntitlements,
			PrismObject<ShadowType> repoShadow,
			OperationResult parentResult) 
					throws ObjectNotFoundException, CommunicationException, SchemaException, SecurityViolationException, ConfigurationException, ExpressionEvaluationException {
		PrismObject<ShadowType> currentShadow;
		List<RefinedAttributeDefinition> neededExtraAttributes = new ArrayList<>();
		for (Operation operation : operations) {
            RefinedAttributeDefinition rad = getRefinedAttributeDefinitionIfApplicable(operation, ctx.getObjectClassDefinition());
            if (rad != null && (!rad.isReturnedByDefault() || rad.getFetchStrategy() == AttributeFetchStrategyType.EXPLICIT)) {
                neededExtraAttributes.add(rad);
            }
        }

		AttributesToReturn attributesToReturn = new AttributesToReturn();
		attributesToReturn.setAttributesToReturn(neededExtraAttributes);
		try {
			currentShadow = fetchResourceObject(ctx, identifiers, 
				attributesToReturn, fetchEntitlements, parentResult);
		} catch (ObjectNotFoundException e) {
			// This may happen for semi-manual connectors that are not yet up to date.
			// No big deal. We will have to work without it.
			LOGGER.warn("Cannot pre-read shadow {}, it is probably not present in the {}. Skipping pre-read.", identifiers, ctx.getResource());
			return null;
		}
		if (repoShadow != null) {
			currentShadow.setOid(repoShadow.getOid());
		}
		currentShadow.asObjectable().setName(new PolyStringType(ShadowUtil.determineShadowName(currentShadow)));
		return currentShadow;
	}

	private Collection<RefinedAttributeDefinition> determineReadReplace(ProvisioningContext ctx, Collection<Operation> operations, RefinedObjectClassDefinition objectClassDefinition) throws ObjectNotFoundException, SchemaException, CommunicationException, ConfigurationException, ExpressionEvaluationException {
		Collection<RefinedAttributeDefinition> retval = new ArrayList<>();
		for (Operation operation : operations) {
			RefinedAttributeDefinition rad = getRefinedAttributeDefinitionIfApplicable(operation, objectClassDefinition);
			if (rad != null && isReadReplaceMode(ctx, rad, objectClassDefinition) && operation instanceof PropertyModificationOperation) {		// third condition is just to be sure
				PropertyDelta propertyDelta = ((PropertyModificationOperation) operation).getPropertyDelta();
				if (propertyDelta.isAdd() || propertyDelta.isDelete()) {
					retval.add(rad);		// REPLACE operations are not needed to be converted to READ+REPLACE
				}
			}
		}
		return retval;
	}

	private boolean isReadReplaceMode(ProvisioningContext ctx, RefinedAttributeDefinition rad, RefinedObjectClassDefinition objectClassDefinition) throws ObjectNotFoundException, SchemaException, CommunicationException, ConfigurationException, ExpressionEvaluationException {
		if (rad.getReadReplaceMode() != null) {
			return rad.getReadReplaceMode();
		}
		// READ+REPLACE mode is if addRemoveAttributeCapability is NOT present. Try to determine from the capabilities. We may still need to force it.
		
		UpdateCapabilityType updateCapabilityType = objectClassDefinition.getEffectiveCapability(UpdateCapabilityType.class, ctx.getResource());
		if (updateCapabilityType == null) {
			// Strange. We are going to update, but we cannot update? Anyway, let it go, it should throw an error on a more appropriate place.
			return false;
		}
		if (BooleanUtils.isTrue(updateCapabilityType.isDelta())) {
			return false;
		}
		boolean readReplace;
		if (updateCapabilityType.isAddRemoveAttributeValues() == null) {
			// Deprecated. Legacy.
			readReplace = objectClassDefinition.getEffectiveCapability(AddRemoveAttributeValuesCapabilityType.class, ctx.getResource()) == null;
		} else {
			readReplace = !updateCapabilityType.isAddRemoveAttributeValues();
		}
		if (readReplace) {
			LOGGER.trace("Read+replace mode is forced because {} does not support addRemoveAttributeValues", ctx.getResource());
		}
		return readReplace;
	}

	private RefinedAttributeDefinition getRefinedAttributeDefinitionIfApplicable(Operation operation, RefinedObjectClassDefinition objectClassDefinition) {
		if (operation instanceof PropertyModificationOperation) {
			PropertyDelta propertyDelta = ((PropertyModificationOperation) operation).getPropertyDelta();
			if (isAttributeDelta(propertyDelta)) {
				QName attributeName = propertyDelta.getElementName();
				return objectClassDefinition.findAttributeDefinition(attributeName);
			}
		}
		return null;
	}

	/**
	 *  Converts ADD/DELETE VALUE operations into REPLACE VALUE, if needed
	 */
	private Collection<Operation> convertToReplace(ProvisioningContext ctx, Collection<Operation> operations, PrismObject<ShadowType> currentShadow) throws SchemaException, ConfigurationException, ObjectNotFoundException, CommunicationException, ExpressionEvaluationException {
		List<Operation> retval = new ArrayList<>(operations.size());
		for (Operation operation : operations) {
			if (operation instanceof PropertyModificationOperation) {
				PropertyDelta propertyDelta = ((PropertyModificationOperation) operation).getPropertyDelta();
				if (isAttributeDelta(propertyDelta)) {
					QName attributeName = propertyDelta.getElementName();
					RefinedAttributeDefinition rad = ctx.getObjectClassDefinition().findAttributeDefinition(attributeName);
					if (isReadReplaceMode(ctx, rad, ctx.getObjectClassDefinition()) && (propertyDelta.isAdd() || propertyDelta.isDelete())) {
						PropertyModificationOperation newOp = convertToReplace(propertyDelta, currentShadow, rad.getMatchingRuleQName());
						newOp.setMatchingRuleQName(((PropertyModificationOperation) operation).getMatchingRuleQName());
						retval.add(newOp);
						continue;
					}

				}
			}
			retval.add(operation);		// for yet-unprocessed operations
		}
		return retval;
	}

	private PropertyModificationOperation convertToReplace(PropertyDelta<?> propertyDelta, PrismObject<ShadowType> currentShadow, QName matchingRuleQName) throws SchemaException {
		if (propertyDelta.isReplace()) {
			// this was probably checked before
			throw new IllegalStateException("PropertyDelta is both ADD/DELETE and REPLACE");
		}
		Collection<PrismPropertyValue> currentValues = new ArrayList<>();
		if (currentShadow != null) {
			// let's extract (parent-less) current values
			PrismProperty<?> currentProperty = currentShadow.findProperty(propertyDelta.getPath());
			if (currentProperty != null) {
				for (PrismPropertyValue currentValue : currentProperty.getValues()) {
					currentValues.add(currentValue.clone());
				}
			}
		}
		final MatchingRule matchingRule;
		if (matchingRuleQName != null) {
			ItemDefinition def = propertyDelta.getDefinition();
			QName typeName;
			if (def != null) {
				typeName = def.getTypeName();
			} else {
				typeName = null;		// we'll skip testing rule fitness w.r.t type
			}
			matchingRule = matchingRuleRegistry.getMatchingRule(matchingRuleQName, typeName);
		} else {
			matchingRule = null;
		}
		Comparator comparator = (Comparator<PrismPropertyValue<?>>) (o1, o2) -> {
			//noinspection unchecked
			if (o1.equals(o2, EquivalenceStrategy.IGNORE_METADATA, matchingRule)) {
				return 0;
			} else {
				return 1;
			}
		};
		// add values that have to be added
		if (propertyDelta.isAdd()) {
			for (PrismPropertyValue valueToAdd : propertyDelta.getValuesToAdd()) {
				if (!PrismValueCollectionsUtil.containsValue(currentValues, valueToAdd, comparator)) {
					currentValues.add(valueToAdd.clone());
				} else {
					LOGGER.warn("Attempting to add a value of {} that is already present in {}: {}",
							valueToAdd, propertyDelta.getElementName(), currentValues);
				}
			}
		}
		// remove values that should not be there
		if (propertyDelta.isDelete()) {
			for (PrismPropertyValue valueToDelete : propertyDelta.getValuesToDelete()) {
				Iterator<PrismPropertyValue> iterator = currentValues.iterator();
				boolean found = false;
				while (iterator.hasNext()) {
					PrismPropertyValue pValue = iterator.next();
					LOGGER.trace("Comparing existing {} to about-to-be-deleted {}, matching rule: {}", pValue, valueToDelete, matchingRule);
					if (comparator.compare(pValue, valueToDelete) == 0) {
						LOGGER.trace("MATCH! compared existing {} to about-to-be-deleted {}", pValue, valueToDelete);
						iterator.remove();
						found = true;
					}
				}
				if (!found) {
					LOGGER.warn("Attempting to remove a value of {} that is not in {}: {}",
							valueToDelete, propertyDelta.getElementName(), currentValues);
				}
			}
		}
		PropertyDelta resultingDelta = prismContext.deltaFactory().property().create(propertyDelta.getPath(), propertyDelta.getPropertyDefinition());
		resultingDelta.setValuesToReplace(currentValues);
		return new PropertyModificationOperation(resultingDelta);
	}

	private List<Collection<Operation>> sortOperationsIntoWaves(Collection<Operation> operations, RefinedObjectClassDefinition objectClassDefinition) {
		TreeMap<Integer,Collection<Operation>> waves = new TreeMap<>();	// operations indexed by priority
		List<Operation> others = new ArrayList<>();					// operations executed at the end (either non-priority ones or non-attribute modifications)
		for (Operation operation : operations) {
			RefinedAttributeDefinition rad = getRefinedAttributeDefinitionIfApplicable(operation, objectClassDefinition);
			if (rad != null && rad.getModificationPriority() != null) {
				putIntoWaves(waves, rad.getModificationPriority(), operation);
				continue;
			}
			others.add(operation);
		}
		// computing the return value
		List<Collection<Operation>> retval = new ArrayList<>(waves.size()+1);
		Map.Entry<Integer,Collection<Operation>> entry = waves.firstEntry();
		while (entry != null) {
			retval.add(entry.getValue());
			entry = waves.higherEntry(entry.getKey());
		}
		retval.add(others);
		return retval;
	}

	private void putIntoWaves(Map<Integer, Collection<Operation>> waves, Integer key, Operation operation) {
		Collection<Operation> wave = waves.get(key);
		if (wave == null) {
			wave = new ArrayList<>();
			waves.put(key, wave);
		}
		wave.add(operation);
	}

	private Collection<ResourceAttribute<?>> cloneIdentifiers(Collection<? extends ResourceAttribute<?>> identifiers) {
		Collection<ResourceAttribute<?>> retval = new HashSet<>(identifiers.size());
		for (ResourceAttribute<?> identifier : identifiers) {
			retval.add(identifier.clone());
		}
		return retval;
	}

	private boolean isRename(ProvisioningContext ctx, Collection<Operation> modifications) throws SchemaException, ConfigurationException, ObjectNotFoundException, CommunicationException, ExpressionEvaluationException {
		for (Operation op : modifications){
			if (!(op instanceof PropertyModificationOperation)) {
				continue;
			}
			
			if (isIdentifierDelta(ctx, ((PropertyModificationOperation)op).getPropertyDelta())) {
				return true;
			}
		}
		return false;
	}

	private <T> boolean isIdentifierDelta(ProvisioningContext ctx, PropertyDelta<T> propertyDelta) throws SchemaException, ConfigurationException, ObjectNotFoundException, CommunicationException, ExpressionEvaluationException {
		return ctx.getObjectClassDefinition().isPrimaryIdentifier(propertyDelta.getElementName()) ||
				ctx.getObjectClassDefinition().isSecondaryIdentifier(propertyDelta.getElementName());
	}

	private PrismObject<ShadowType> executeEntitlementChangesAdd(ProvisioningContext ctx, PrismObject<ShadowType> shadow, 
			OperationProvisioningScriptsType scripts, ConnectorOperationOptions connOptions,
			OperationResult parentResult) throws SchemaException, ObjectNotFoundException, CommunicationException, SecurityViolationException, ConfigurationException, ObjectAlreadyExistsException, ExpressionEvaluationException {
		
		Map<ResourceObjectDiscriminator, ResourceObjectOperations> roMap = new HashMap<>();
		
		shadow = entitlementConverter.collectEntitlementsAsObjectOperationInShadowAdd(ctx, roMap, shadow, parentResult);
		
		executeEntitlements(ctx, roMap, connOptions, parentResult);
		
		return shadow;
	}
	
	private PrismObject<ShadowType> executeEntitlementChangesModify(ProvisioningContext ctx, PrismObject<ShadowType> subjectShadowBefore,
			PrismObject<ShadowType> subjectShadowAfter,
            OperationProvisioningScriptsType scripts, ConnectorOperationOptions connOptions, Collection<? extends ItemDelta> subjectDeltas, OperationResult parentResult) throws SchemaException, ObjectNotFoundException, CommunicationException, SecurityViolationException, ConfigurationException, ObjectAlreadyExistsException, ExpressionEvaluationException {
		
		Map<ResourceObjectDiscriminator, ResourceObjectOperations> roMap = new HashMap<>();
		
		if (LOGGER.isTraceEnabled()) {
			LOGGER.trace("executeEntitlementChangesModify, old shadow:\n{}", subjectShadowBefore.debugDump(1));
		}
		
		for (ItemDelta subjectDelta : subjectDeltas) {
			ItemPath subjectItemPath = subjectDelta.getPath();
			
			if (ShadowType.F_ASSOCIATION.equivalent(subjectItemPath)) {
				ContainerDelta<ShadowAssociationType> containerDelta = (ContainerDelta<ShadowAssociationType>)subjectDelta;				
				subjectShadowAfter = entitlementConverter.collectEntitlementsAsObjectOperation(ctx, roMap, containerDelta,
                        subjectShadowBefore, subjectShadowAfter, parentResult);
				
			} else {
			
				ContainerDelta<ShadowAssociationType> associationDelta = prismContext.deltaFactory().container().createDelta(ShadowType.F_ASSOCIATION, subjectShadowBefore.getDefinition());
				PrismContainer<ShadowAssociationType> associationContainer = subjectShadowBefore.findContainer(ShadowType.F_ASSOCIATION);
				if (associationContainer == null || associationContainer.isEmpty()){
					LOGGER.trace("No shadow association container in old shadow. Skipping processing entitlements change for {}.", subjectItemPath);
					continue;
				}
				if (LOGGER.isTraceEnabled()) {
					LOGGER.trace("Processing association container in old shadow for {}:\n{}", subjectItemPath, associationContainer.debugDump(1));
				}

				// Delete + re-add association values that should ensure correct functioning in case of rename
				// This has to be done only for associations that require explicit referential integrity.
				// For these that do not, it is harmful, so it must be skipped.
				for (PrismContainerValue<ShadowAssociationType> associationValue : associationContainer.getValues()) {
					QName associationName = associationValue.asContainerable().getName();
					if (associationName == null) {
						throw new IllegalStateException("No association name in " + associationValue);
					}
					RefinedAssociationDefinition associationDefinition = ctx.getObjectClassDefinition().findAssociationDefinition(associationName);
					if (associationDefinition == null) {
						throw new IllegalStateException("No association definition for " + associationValue);
					}
					if (!associationDefinition.requiresExplicitReferentialIntegrity()) {
						continue;
					}
					QName valueAttributeName = associationDefinition.getResourceObjectAssociationType().getValueAttribute();
					if (!ShadowUtil.matchesAttribute(subjectItemPath, valueAttributeName)) {
						continue;
					}
					LOGGER.trace("Processing association {} on rename", associationName);
					associationDelta.addValuesToDelete(associationValue.clone());
					associationDelta.addValuesToAdd(associationValue.clone());
				}
				if (LOGGER.isTraceEnabled()) {
					LOGGER.trace("Resulting association delta for {}:\n{}", subjectItemPath, associationDelta.debugDump(1));
				}
				if (!associationDelta.isEmpty()) {
					entitlementConverter.collectEntitlementsAsObjectOperation(ctx, roMap, associationDelta, subjectShadowBefore, subjectShadowAfter, parentResult);
				}
				
//				shadowAfter.findOrCreateContainer(ShadowType.F_ASSOCIATION).addAll((Collection) association.getClonedValues());
//				entitlementConverter.processEntitlementsAdd(resource, shadowAfter, objectClassDefinition);
			}
		}
		
		executeEntitlements(ctx, roMap, connOptions, parentResult);
		
		return subjectShadowAfter;
	}
	
	private void executeEntitlementChangesDelete(ProvisioningContext ctx, PrismObject<ShadowType> subjectShadow, 
			OperationProvisioningScriptsType scripts, ConnectorOperationOptions connOptions,
			OperationResult parentResult) throws SchemaException  {
		
		try {
			
			Map<ResourceObjectDiscriminator, ResourceObjectOperations> roMap = new HashMap<>();
				
			entitlementConverter.collectEntitlementsAsObjectOperationDelete(ctx, roMap,
					subjectShadow, parentResult);
		
			executeEntitlements(ctx, roMap, connOptions, parentResult);
			
		// TODO: now just log the errors, but not NOT re-throw the exception (except for some exceptions)
		// we want the original delete to take place, throwing an exception would spoil that
		} catch (SchemaException e) {
			throw e;
		} catch (CommunicationException | ObjectNotFoundException | SecurityViolationException | ConfigurationException | ObjectAlreadyExistsException | ExpressionEvaluationException | RuntimeException | Error e) {
			LOGGER.error(e.getMessage(), e);
		}
		
	}
	
	private void executeEntitlements(ProvisioningContext subjectCtx,
			Map<ResourceObjectDiscriminator, ResourceObjectOperations> roMap, ConnectorOperationOptions connOptions, OperationResult parentResult) throws ObjectNotFoundException, CommunicationException, SchemaException, SecurityViolationException, ConfigurationException, ObjectAlreadyExistsException {
		
		if (LOGGER.isTraceEnabled()) {
			LOGGER.trace("Excuting entitlement chanes, roMap:\n{}", DebugUtil.debugDump(roMap, 1));
		}
		
		for (Entry<ResourceObjectDiscriminator,ResourceObjectOperations> entry: roMap.entrySet()) {
			ResourceObjectDiscriminator disc = entry.getKey();
			ProvisioningContext entitlementCtx = entry.getValue().getResourceObjectContext();
			Collection<? extends ResourceAttribute<?>> primaryIdentifiers = disc.getPrimaryIdentifiers();
			ResourceObjectOperations resourceObjectOperations = entry.getValue();
			Collection<? extends ResourceAttribute<?>> allIdentifiers = resourceObjectOperations.getAllIdentifiers();
			if (allIdentifiers == null || allIdentifiers.isEmpty()) {
				allIdentifiers = primaryIdentifiers;
			}
			Collection<Operation> operations = resourceObjectOperations.getOperations();
			
			if (LOGGER.isTraceEnabled()) {
				LOGGER.trace("Excuting entitlement change identifiers={}:\n{}", allIdentifiers, DebugUtil.debugDump(operations, 1));
			}
			
			OperationResult result = parentResult.createMinorSubresult(OPERATION_MODIFY_ENTITLEMENT);
			try {
				
				executeModify(entitlementCtx, entry.getValue().getCurrentShadow(), allIdentifiers, operations, connOptions, result);
				
				result.recordSuccess();
				
			} catch (ObjectNotFoundException | CommunicationException | SchemaException | SecurityViolationException | PolicyViolationException | ConfigurationException | ObjectAlreadyExistsException | ExpressionEvaluationException e) {
				// We need to handle this specially. 
				// E.g. ObjectNotFoundException means that the entitlement object was not found,
				// not that the subject was not found. It we throw ObjectNotFoundException here it may be
				// interpreted by the consistency code to mean that the subject is missing. Which is not
				// true. And that may cause really strange reactions. In fact we do not want to throw the
				// exception at all, because the primary operation was obviously successful. So just 
				// properly record the operation in the result.
				LOGGER.error("Error while modifying entitlement {} of {}: {}", entitlementCtx, subjectCtx, e.getMessage(), e);
				result.recordFatalError(e);
			} catch (RuntimeException | Error e) {
				LOGGER.error("Error while modifying entitlement {} of {}: {}", entitlementCtx, subjectCtx, e.getMessage(), e);
				result.recordFatalError(e);
				throw e;
			}
			
		}
	}

	public SearchResultMetadata searchResourceObjects(final ProvisioningContext ctx,
			final ResultHandler<ShadowType> resultHandler, ObjectQuery query, final boolean fetchAssociations,
            final OperationResult parentResult) throws SchemaException,
			CommunicationException, ObjectNotFoundException, ConfigurationException, SecurityViolationException, ExpressionEvaluationException {
		
		LOGGER.trace("Searching resource objects, query: {}", query);
		
		RefinedObjectClassDefinition objectClassDef = ctx.getObjectClassDefinition();
		AttributesToReturn attributesToReturn = ProvisioningUtil.createAttributesToReturn(ctx);
		SearchHierarchyConstraints searchHierarchyConstraints = null;
		ResourceObjectReferenceType baseContextRef = objectClassDef.getBaseContext();
		if (baseContextRef != null) {
			PrismObject<ShadowType> baseContextShadow = resourceObjectReferenceResolver.resolve(ctx, baseContextRef, null, "base context specification in "+objectClassDef, parentResult);
			if (baseContextShadow == null) {
				throw new ObjectNotFoundException("No base context defined by "+baseContextRef+" in base context specification in "+objectClassDef);
			}
			RefinedObjectClassDefinition baseContextObjectClassDefinition = ctx.getRefinedSchema().determineCompositeObjectClassDefinition(baseContextShadow);
			ResourceObjectIdentification baseContextIdentification =  ShadowUtil.getResourceObjectIdentification(baseContextShadow, baseContextObjectClassDefinition);
			searchHierarchyConstraints = new SearchHierarchyConstraints(baseContextIdentification, null);
		}
		
		if (InternalsConfig.consistencyChecks && query != null && query.getFilter() != null) {
			query.getFilter().checkConsistence(true);
		}

		ConnectorInstance connector = ctx.getConnector(ReadCapabilityType.class, parentResult);
		
		SearchResultMetadata metadata = null;
		try {
		
			metadata = connector.search(objectClassDef, query, 
					(shadow) -> {
						// in order to utilize the cache right from the beginning...
						RepositoryCache.enter(cacheConfigurationManager);

						OperationResult objResult = parentResult.createMinorSubresult(OperationConstants.OPERATION_SEARCH_RESULT);
						
						try {
							try {
								shadow = postProcessResourceObjectRead(ctx, shadow, fetchAssociations, objResult);
							} catch (SchemaException | CommunicationException | ConfigurationException | SecurityViolationException | ObjectNotFoundException | ExpressionEvaluationException e) {
								if (objResult.isUnknown()) {
									objResult.recordFatalError(e);
								}
								throw new TunnelException(e);
							} catch (Throwable t) {
								if (objResult.isUnknown()) {
									objResult.recordFatalError(t);
								}
								throw t;
							}
							Validate.notNull(shadow, "null shadow");
							boolean doContinue;
							try {
								doContinue = resultHandler.handle(shadow, objResult);
								objResult.computeStatus();
							} catch (Throwable t) {
								if (objResult.isUnknown()) {
									objResult.recordFatalError(t);
								}
								throw t;
							}
							return doContinue;
						} finally {
							RepositoryCache.exit();
						}
					},
					attributesToReturn, objectClassDef.getPagedSearches(ctx.getResource()), searchHierarchyConstraints, 
					ctx, parentResult);
			
		} catch (GenericFrameworkException e) {
			parentResult.recordFatalError("Generic error in the connector: " + e.getMessage(), e);
			throw new SystemException("Generic error in the connector: " + e.getMessage(), e);

		} catch (CommunicationException ex) {
			parentResult.recordFatalError(
					"Error communicating with the connector " + connector + ": " + ex.getMessage(), ex);
			throw new CommunicationException("Error communicating with the connector " + connector + ": "
					+ ex.getMessage(), ex);
		} catch (SecurityViolationException ex) {
			parentResult.recordFatalError(
					"Security violation communicating with the connector " + connector + ": " + ex.getMessage(), ex);
			throw new SecurityViolationException("Security violation communicating with the connector " + connector + ": "
					+ ex.getMessage(), ex);
		} catch (TunnelException e) {
			Throwable cause = e.getCause();
			if (cause instanceof SchemaException) {
				throw (SchemaException)cause;
			} else if (cause instanceof CommunicationException) {
				throw (CommunicationException)cause;
			} else if (cause instanceof ObjectNotFoundException) {
				throw (ObjectNotFoundException)cause;
			} else if (cause instanceof ConfigurationException) {
				throw (ConfigurationException)cause;
			} else if (cause instanceof SecurityViolationException) {
				throw (SecurityViolationException)cause;
			} else if (cause instanceof ExpressionEvaluationException) {
				throw (ExpressionEvaluationException)cause;
			} else if (cause instanceof GenericFrameworkException) {
				throw new GenericConnectorException(cause.getMessage(), cause);
			} else {
				throw new SystemException(cause.getMessage(), cause);
			}
		}

		computeResultStatus(parentResult);

		LOGGER.trace("Searching resource objects done: {}", parentResult.getStatus());
		
		return metadata;
	}

	@SuppressWarnings("rawtypes")
	public PrismProperty fetchCurrentToken(ProvisioningContext ctx, OperationResult parentResult)
			throws ObjectNotFoundException, CommunicationException, SchemaException, ConfigurationException, ExpressionEvaluationException {
		Validate.notNull(parentResult, "Operation result must not be null.");

		LOGGER.trace("Fetcing current sync token for {}", ctx);
		
		PrismProperty lastToken;
		ConnectorInstance connector = ctx.getConnector(LiveSyncCapabilityType.class, parentResult);
		try {
			lastToken = connector.fetchCurrentToken(ctx.getObjectClassDefinition(), ctx, parentResult);
		} catch (GenericFrameworkException e) {
			parentResult.recordFatalError("Generic error in the connector: " + e.getMessage(), e);
			throw new CommunicationException("Generic error in the connector: " + e.getMessage(), e);

		} catch (CommunicationException ex) {
			parentResult.recordFatalError(
					"Error communicating with the connector " + connector + ": " + ex.getMessage(), ex);
			throw new CommunicationException("Error communicating with the connector " + connector + ": "
					+ ex.getMessage(), ex);
		}

		LOGGER.trace("Got last token: {}", SchemaDebugUtil.prettyPrint(lastToken));
		
		computeResultStatus(parentResult);
		
		return lastToken;
	}


	private PrismObject<ShadowType> fetchResourceObject(ProvisioningContext ctx,
			Collection<? extends ResourceAttribute<?>> identifiers, 
			AttributesToReturn attributesToReturn,
			boolean fetchAssociations,
			OperationResult parentResult) throws ObjectNotFoundException,
			CommunicationException, SchemaException, SecurityViolationException, ConfigurationException, ExpressionEvaluationException {

		PrismObject<ShadowType> resourceObject = resourceObjectReferenceResolver.fetchResourceObject(ctx, identifiers, attributesToReturn, parentResult);
		return postProcessResourceObjectRead(ctx, resourceObject, fetchAssociations, parentResult);
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private void applyAfterOperationAttributes(PrismObject<ShadowType> shadow,
			Collection<ResourceAttribute<?>> resourceAttributesAfterAdd) throws SchemaException {
		if (resourceAttributesAfterAdd == null) {
			return;
		}
		ResourceAttributeContainer attributesContainer = ShadowUtil.getAttributesContainer(shadow);
		for (ResourceAttribute attributeAfter : resourceAttributesAfterAdd) {
			ResourceAttribute attributeBefore = attributesContainer.findAttribute(attributeAfter.getElementName());
			if (attributeBefore != null) {
				attributesContainer.remove(attributeBefore);
			}
			if (!attributesContainer.contains(attributeAfter)) {
				attributesContainer.add(attributeAfter.clone());
			}
		}
	}

	private Collection<Operation> determineActivationChange(ProvisioningContext ctx, ShadowType shadow, Collection<? extends ItemDelta> objectChange,
			OperationResult result) throws SchemaException, ObjectNotFoundException, CommunicationException, ConfigurationException, ExpressionEvaluationException {
		ResourceType resource = ctx.getResource();
		Collection<Operation> operations = new ArrayList<>();
		
//		CapabilitiesType connectorCapabilities = ctx.getConnectorCapabilities(UpdateCapabilityType.class);
		ActivationCapabilityType activationCapability = ctx.getEffectiveCapability(ActivationCapabilityType.class);

		// administrativeStatus
		PropertyDelta<ActivationStatusType> enabledPropertyDelta = PropertyDeltaCollectionsUtil.findPropertyDelta(objectChange,
				SchemaConstants.PATH_ACTIVATION_ADMINISTRATIVE_STATUS);
		if (enabledPropertyDelta != null) {
			if (activationCapability == null) {
				SchemaException e = new SchemaException("Attempt to change activation administrativeStatus on "+resource+" which does not have the capability");
				result.recordFatalError(e);
				throw e;
			}
			ActivationStatusType status = enabledPropertyDelta.getPropertyNewMatchingPath().getRealValue();
			LOGGER.trace("Found activation administrativeStatus change to: {}", status);
	
			if (ctx.hasNativeCapability(ActivationCapabilityType.class)) {
				// Native activation, need to check if there is not also change to simulated activation which may be in conflict
				checkSimulatedActivationAdministrativeStatus(ctx, objectChange, status, activationCapability, shadow, result);
				operations.add(new PropertyModificationOperation(enabledPropertyDelta));
			} else {
				// Try to simulate activation capability
				PropertyModificationOperation activationAttribute = convertToSimulatedActivationAdministrativeStatusAttribute(
						ctx, enabledPropertyDelta, shadow, status, activationCapability, result);
				if (activationAttribute != null) {
					operations.add(activationAttribute);
				}
			}	
		}
		
		// validFrom
		PropertyDelta<XMLGregorianCalendar> validFromPropertyDelta = PropertyDeltaCollectionsUtil.findPropertyDelta(objectChange,
				SchemaConstants.PATH_ACTIVATION_VALID_FROM);
		if (validFromPropertyDelta != null) {
			if (CapabilityUtil.getEffectiveActivationValidFrom(activationCapability) == null) {
				SchemaException e = new SchemaException("Attempt to change activation validFrom on "+resource+" which does not have the capability");
				result.recordFatalError(e);
				throw e;
			}
			XMLGregorianCalendar xmlCal = validFromPropertyDelta.getPropertyNewMatchingPath().getRealValue();
			LOGGER.trace("Found activation validFrom change to: {}", xmlCal);
			operations.add(new PropertyModificationOperation(validFromPropertyDelta));
		}

		// validTo
		PropertyDelta<XMLGregorianCalendar> validToPropertyDelta = PropertyDeltaCollectionsUtil.findPropertyDelta(objectChange,
				SchemaConstants.PATH_ACTIVATION_VALID_TO);
		if (validToPropertyDelta != null) {
			if (CapabilityUtil.getEffectiveActivationValidTo(activationCapability) == null) {
				SchemaException e = new SchemaException("Attempt to change activation validTo on "+resource+" which does not have the capability");
				result.recordFatalError(e);
				throw e;
			}
			XMLGregorianCalendar xmlCal = validToPropertyDelta.getPropertyNewMatchingPath().getRealValue();
			LOGGER.trace("Found activation validTo change to: {}", xmlCal);
				operations.add(new PropertyModificationOperation(validToPropertyDelta));
		}
		
		PropertyDelta<LockoutStatusType> lockoutPropertyDelta = PropertyDeltaCollectionsUtil.findPropertyDelta(objectChange,
				SchemaConstants.PATH_ACTIVATION_LOCKOUT_STATUS);
		if (lockoutPropertyDelta != null) {
			if (activationCapability == null) {
				SchemaException e =  new SchemaException("Attempt to change activation lockoutStatus on "+resource+" which does not have the capability");
				result.recordFatalError(e);
				throw e;
			}
			LockoutStatusType status = lockoutPropertyDelta.getPropertyNewMatchingPath().getRealValue();
			LOGGER.trace("Found activation lockoutStatus change to: {}", status);

			if (ctx.hasNativeCapability(ActivationCapabilityType.class)) {
				// Native lockout, need to check if there is not also change to simulated activation which may be in conflict
				checkSimulatedActivationLockoutStatus(ctx, objectChange, status, activationCapability, shadow, result);
				operations.add(new PropertyModificationOperation(lockoutPropertyDelta));
			} else {
				// Try to simulate lockout capability
				PropertyModificationOperation activationAttribute = convertToSimulatedActivationLockoutStatusAttribute(
						ctx, lockoutPropertyDelta, shadow, status, activationCapability, result);
				operations.add(activationAttribute);
			}	
		}
		
		return operations;
	}
	
	private <T> void checkSimulatedActivationAdministrativeStatus(ProvisioningContext ctx, 
			Collection<? extends ItemDelta> objectChange, ActivationStatusType status, ActivationCapabilityType activationCapabilityType,
			ShadowType shadow, OperationResult result) throws SchemaException, ObjectNotFoundException, CommunicationException, ConfigurationException, ExpressionEvaluationException{
		
		ActivationStatusCapabilityType capActStatus = getActivationAdministrativeStatusFromSimulatedActivation(ctx, activationCapabilityType, shadow, result);
		ResourceAttribute<T> activationAttribute = getSimulatedActivationAdministrativeStatusAttribute(ctx, shadow, 
				capActStatus, result);
		if (activationAttribute == null) {
			return;
		}
		
		PropertyDelta<T> simulatedActivationDelta = ItemDeltaCollectionsUtil.findPropertyDelta(objectChange, activationAttribute.getPath());
		if (simulatedActivationDelta == null) {
			return;
		}
		PrismProperty<T> simulatedActivationProperty = simulatedActivationDelta.getPropertyNewMatchingPath();
		Collection<T> realValues = simulatedActivationProperty.getRealValues();
		if (realValues.isEmpty()) {
			//nothing to do, no value for simulatedActivation
			return;
		}
		
		if (realValues.size() > 1) {
			throw new SchemaException("Found more than one value for simulated activation.");
		}
		
		T simulatedActivationValue = realValues.iterator().next();
		boolean transformedValue = getTransformedValue(ctx, activationCapabilityType, shadow, simulatedActivationValue, result);
		
		if (transformedValue && status == ActivationStatusType.ENABLED) {
			//this is ok, simulated value and also value for native capability resulted to the same vale
		} else{
			throw new SchemaException("Found conflicting change for activation. Simulated activation resulted to " + transformedValue +", but native activation resulted to " + status);
		}
		
	}
	
	private void checkSimulatedActivationLockoutStatus(ProvisioningContext ctx,
			Collection<? extends ItemDelta> objectChange, LockoutStatusType status, ActivationCapabilityType activationCapability, ShadowType shadow, OperationResult result) throws SchemaException, ObjectNotFoundException, CommunicationException, ConfigurationException, ExpressionEvaluationException{
		
		ActivationLockoutStatusCapabilityType capActStatus = getActivationLockoutStatusFromSimulatedActivation(ctx, activationCapability, shadow, result);
		ResourceAttribute<?> activationAttribute = getSimulatedActivationLockoutStatusAttribute(ctx, shadow, capActStatus, result);
		if (activationAttribute == null){
			return;
		}
		
		PropertyDelta simulatedActivationDelta = ItemDeltaCollectionsUtil.findPropertyDelta(objectChange, activationAttribute.getPath());
		PrismProperty simulatedActivationProperty = simulatedActivationDelta.getPropertyNewMatchingPath();
		Collection realValues = simulatedActivationProperty.getRealValues();
		if (realValues.isEmpty()) {
			//nothing to do, no value for simulatedActivation
			return;
		}
		
		if (realValues.size() > 1) {
			throw new SchemaException("Found more than one value for simulated lockout.");
		}
		
		Object simulatedActivationValue = realValues.iterator().next();
		boolean transformedValue = getTransformedValue(ctx, activationCapability, shadow, simulatedActivationValue, result);		// TODO this is strange; evaluating lockout but looking at status! [med]
		
		if (transformedValue && status == LockoutStatusType.NORMAL) {
			//this is ok, simulated value and also value for native capability resulted to the same vale
		} else {
			throw new SchemaException("Found conflicting change for activation lockout. Simulated lockout resulted to " + transformedValue +", but native activation resulted to " + status);
		}
		
	}
	
	private boolean getTransformedValue(ProvisioningContext ctx, ActivationCapabilityType activationCapabilityType, ShadowType shadow, Object simulatedValue, OperationResult result) throws SchemaException, ObjectNotFoundException, CommunicationException, ConfigurationException, ExpressionEvaluationException{
		ActivationStatusCapabilityType capActStatus = getActivationAdministrativeStatusFromSimulatedActivation(ctx, activationCapabilityType, shadow, result);
		String simulatedAttributeStringValue = String.valueOf(simulatedValue);			// TODO MID-3374: implement correctly (convert value list to native objects before comparison)
		List<String> disableValues = capActStatus.getDisableValue();
		for (String disable : disableValues) {
			if (disable.equals(simulatedAttributeStringValue)) {
				return false; 
			}
		}
		
		List<String> enableValues = capActStatus.getEnableValue();
		for (String enable : enableValues) {
			if (enable.equals(simulatedAttributeStringValue)) {
				return true;
			}
		}
		
		throw new SchemaException("Could not map value for simulated activation: " + simulatedValue + " neither to enable nor disable values.");		
	}
	
	private void transformActivationAttributesAdd(ProvisioningContext ctx, ShadowType shadow, OperationResult result) 
					throws SchemaException, ObjectNotFoundException, CommunicationException, ConfigurationException, ExpressionEvaluationException {
		final ActivationType activation = shadow.getActivation();
		if (activation == null) {
			return;
		}
		PrismContainer attributesContainer = shadow.asPrismObject().findContainer(ShadowType.F_ATTRIBUTES);

		ActivationCapabilityType activationCapability = ctx.getEffectiveCapability(ActivationCapabilityType.class);

		if (activation.getAdministrativeStatus() != null) {
			if (!ctx.hasNativeCapability(ActivationCapabilityType.class)) {
				ActivationStatusCapabilityType capActStatus = getActivationAdministrativeStatusFromSimulatedActivation(
						ctx, activationCapability, shadow, result);
				if (capActStatus == null) {
					throw new SchemaException("Attempt to change activation/administrativeStatus on "+ctx.getResource()+" that has neither native" +
							" nor simulated activation capability");
				}
				ResourceAttribute<?> newSimulatedAttr = getSimulatedActivationAdministrativeStatusAttribute(ctx, shadow,
						capActStatus, result);
				if (newSimulatedAttr != null) {
					Class<?> simulatedAttrValueClass = getAttributeValueClass(ctx, shadow, newSimulatedAttr, capActStatus);

					Object newSimulatedAttrRealValue;
					if (activation.getAdministrativeStatus() == ActivationStatusType.ENABLED) {
						newSimulatedAttrRealValue = getEnableValue(capActStatus, simulatedAttrValueClass);
					} else {
						newSimulatedAttrRealValue = getDisableValue(capActStatus, simulatedAttrValueClass);
					}

					Item existingSimulatedAttr = attributesContainer.findItem(newSimulatedAttr.getElementName());
					if (!isBlank(newSimulatedAttrRealValue)) {
						PrismPropertyValue newSimulatedAttrValue = prismContext.itemFactory().createPropertyValue(newSimulatedAttrRealValue);
						if (existingSimulatedAttr == null) {
							newSimulatedAttr.add(newSimulatedAttrValue);
							attributesContainer.add(newSimulatedAttr);
						} else {
							existingSimulatedAttr.replace(newSimulatedAttrValue);
						}
					} else if (existingSimulatedAttr != null) {
						attributesContainer.remove(existingSimulatedAttr);
					}
					activation.setAdministrativeStatus(null);
				}
			}
		}

		// TODO enable non-string lockout values (MID-3374)
		if (activation.getLockoutStatus() != null) {
			if (!ctx.hasNativeCapability(ActivationCapabilityType.class)) {
				ActivationLockoutStatusCapabilityType capActStatus = getActivationLockoutStatusFromSimulatedActivation(
						ctx, activationCapability, shadow, result);
				if (capActStatus == null) {
					throw new SchemaException("Attempt to change activation/lockout on "+ctx.getResource()+" that has neither native" +
							" nor simulated activation capability");
				}
				ResourceAttribute<?> activationSimulateAttribute = getSimulatedActivationLockoutStatusAttribute(ctx, shadow,
						capActStatus, result);
				
				if (activationSimulateAttribute != null) {
					LockoutStatusType status = activation.getLockoutStatus();
					String activationRealValue;
					if (status == LockoutStatusType.NORMAL) {
						activationRealValue = getLockoutNormalValue(capActStatus);
					} else {
						activationRealValue = getLockoutLockedValue(capActStatus);
					}
					Item existingAttribute = attributesContainer.findItem(activationSimulateAttribute.getElementName());
					if (!StringUtils.isBlank(activationRealValue)) {
						//noinspection unchecked
						((ResourceAttribute) activationSimulateAttribute).addRealValue(activationRealValue);
						if (attributesContainer.findItem(activationSimulateAttribute.getElementName()) == null) {
							attributesContainer.add(activationSimulateAttribute);
						} else{
							attributesContainer.findItem(activationSimulateAttribute.getElementName()).replace(activationSimulateAttribute.getValue());
						}
					} else if (existingAttribute != null) {
						attributesContainer.remove(existingAttribute);
					}
					activation.setLockoutStatus(null);
				}
			}
		}
	}

	@NotNull
	private Class<?> getAttributeValueClass(ProvisioningContext ctx, ShadowType shadow, ResourceAttribute<?> attribute,
			@NotNull ActivationStatusCapabilityType capActStatus)
			throws ObjectNotFoundException, SchemaException, CommunicationException, ConfigurationException, ExpressionEvaluationException {

		ResourceAttributeDefinition attributeDefinition = attribute.getDefinition();
		Class<?> attributeValueClass = attributeDefinition != null ? attributeDefinition.getTypeClassIfKnown() : null;
		if (attributeValueClass == null) {
			LOGGER.warn("No definition for simulated administrative status attribute {} for shadow {} on {}, assuming String",
					attribute, shadow, ctx.getResource());
			attributeValueClass = String.class;
		}
		return attributeValueClass;
	}

	private boolean isBlank(Object realValue) {
		if (realValue == null) {
			return true;
		} else if (realValue instanceof String) {
			return StringUtils.isBlank((String) realValue);
		} else {
			return false;
		}
	}

	private boolean hasChangesOnResource(
			Collection<? extends ItemDelta> itemDeltas) {
		for (ItemDelta itemDelta : itemDeltas) {
			if (isAttributeDelta(itemDelta) || SchemaConstants.PATH_PASSWORD.equivalent(itemDelta.getParentPath())) {
				return true;
			} else if (SchemaConstants.PATH_ACTIVATION.equivalent(itemDelta.getParentPath())){
				return true;
			} else if (ShadowType.F_ASSOCIATION.equivalent(itemDelta.getPath())) {
				return true;				
			}
		}
		return false;
	}


	private void collectAttributeAndEntitlementChanges(ProvisioningContext ctx, 
			Collection<? extends ItemDelta> objectChange, Collection<Operation> operations, 
			PrismObject<ShadowType> shadow, OperationResult result) throws SchemaException, ObjectNotFoundException, CommunicationException, ConfigurationException, ExpressionEvaluationException {
		if (operations == null) {
			operations = new ArrayList<>();
		}
		boolean activationProcessed = false;
		RefinedObjectClassDefinition objectClassDefinition = ctx.getObjectClassDefinition();
		for (ItemDelta itemDelta : objectChange) {
			if (isAttributeDelta(itemDelta) || SchemaConstants.PATH_PASSWORD.equivalent(itemDelta.getParentPath())) {
				if (itemDelta instanceof PropertyDelta) {
					PropertyModificationOperation attributeModification = new PropertyModificationOperation(
							(PropertyDelta) itemDelta);
					RefinedAttributeDefinition<Object> attrDef = objectClassDefinition.findAttributeDefinition(itemDelta.getElementName());
					if (attrDef != null) {
						attributeModification.setMatchingRuleQName(attrDef.getMatchingRuleQName());
						if (itemDelta.getDefinition() == null) {
							itemDelta.setDefinition(attrDef);
						}
					}
					operations.add(attributeModification);
				} else if (itemDelta instanceof ContainerDelta) {
					// skip the container delta - most probably password change
					// - it is processed earlier
					continue;
				} else {
					throw new UnsupportedOperationException("Not supported delta: " + itemDelta);
				}
			} else if (SchemaConstants.PATH_ACTIVATION.equivalent(itemDelta.getParentPath())) {
				if (!activationProcessed) {
					Collection<Operation> activationOperations = determineActivationChange(ctx, shadow.asObjectable(), objectChange, result);
					if (activationOperations != null){
						operations.addAll(activationOperations);
					}
					activationProcessed = true;
				}
			} else if (ShadowType.F_ASSOCIATION.equivalent(itemDelta.getPath())) {
				if (itemDelta instanceof ContainerDelta) {
					entitlementConverter.collectEntitlementChange(ctx, (ContainerDelta<ShadowAssociationType>)itemDelta, operations);
				} else {
					throw new UnsupportedOperationException("Not supported delta: " + itemDelta);
				}
			} else if (ShadowType.F_AUXILIARY_OBJECT_CLASS.equivalent(itemDelta.getPath())) {
				if (itemDelta instanceof PropertyDelta) {
					PropertyModificationOperation attributeModification = new PropertyModificationOperation(
							(PropertyDelta) itemDelta);
					operations.add(attributeModification);
				} else {
					throw new UnsupportedOperationException("Not supported delta: " + itemDelta);
				}
			} else {
				LOGGER.trace("Skip converting item delta: {}. It's not resource object change, but it is shadow change.", itemDelta);	
			}
			
		}
	}

	private boolean isAttributeDelta(ItemDelta itemDelta) {
		return ShadowType.F_ATTRIBUTES.equivalent(itemDelta.getParentPath());
	}

	public List<Change> fetchChanges(ProvisioningContext ctx, PrismProperty<?> lastToken,
			OperationResult parentResult) throws SchemaException,
			CommunicationException, ConfigurationException, SecurityViolationException, GenericFrameworkException, ObjectNotFoundException, ExpressionEvaluationException {
		Validate.notNull(parentResult, "Operation result must not be null.");

		LOGGER.trace("START fetch changes, objectClass: {}", ctx.getObjectClassDefinition());
		AttributesToReturn attrsToReturn = null;
		if (!ctx.isWildcard()) {
			attrsToReturn = ProvisioningUtil.createAttributesToReturn(ctx);
		}
		
		ConnectorInstance connector = ctx.getConnector(LiveSyncCapabilityType.class, parentResult);
		
		// get changes from the connector
		List<Change> changes = connector.fetchChanges(ctx.getObjectClassDefinition(), lastToken, attrsToReturn, ctx, parentResult);

		Iterator<Change> iterator = changes.iterator();
		while (iterator.hasNext()) {
			Change change = iterator.next();
			LOGGER.trace("Original change:\n{}", change.debugDumpLazily());
			if (change.isTokenOnly()) {
				continue;
			}
			ProvisioningContext shadowCtx = ctx;
			AttributesToReturn shadowAttrsToReturn = attrsToReturn;
			PrismObject<ShadowType> currentShadow = change.getCurrentShadow();
			ObjectClassComplexTypeDefinition changeObjectClassDefinition = change.getObjectClassDefinition();
			if (changeObjectClassDefinition == null) {
				if (!ctx.isWildcard() || change.getObjectDelta() == null || !change.getObjectDelta().isDelete()) {
					throw new SchemaException("No object class definition in change "+change);
				}
			}
			if (ctx.isWildcard() && changeObjectClassDefinition != null) {
				shadowCtx = ctx.spawn(changeObjectClassDefinition.getTypeName());
				if (shadowCtx.isWildcard()) {
					String message = "Unknown object class "+changeObjectClassDefinition.getTypeName()+" found in synchronization delta";
					parentResult.recordFatalError(message);
					throw new SchemaException(message);
				}
				change.setObjectClassDefinition(shadowCtx.getObjectClassDefinition());
				
				shadowAttrsToReturn = ProvisioningUtil.createAttributesToReturn(shadowCtx);
			}
			
			if (change.getObjectDelta() == null || !change.getObjectDelta().isDelete()) {
				if (currentShadow == null) {
					// There is no current shadow in a change. Add it by fetching it explicitly.
					try {
						// TODO maybe we can postpone this fetch to ShadowCache.preProcessChange where it is implemented [pmed]
						LOGGER.trace("Re-fetching object {} because it is not in the change", change.getIdentifiers());
						currentShadow = fetchResourceObject(shadowCtx, 
								change.getIdentifiers(), shadowAttrsToReturn, true, parentResult);	// todo consider whether it is always necessary to fetch the entitlements
						change.setCurrentShadow(currentShadow);
					} catch (ObjectNotFoundException ex) {
						parentResult.recordHandledError(
								"Object detected in change log no longer exist on the resource. Skipping processing this object.", ex);
						LOGGER.warn("Object detected in change log no longer exist on the resource. Skipping processing this object "
								+ ex.getMessage());
						// TODO: Maybe change to DELETE instead of this?
						iterator.remove();
						continue;
					}
				} else {
					if (ctx.isWildcard()) {
						if (!MiscUtil.equals(shadowAttrsToReturn, attrsToReturn)) {
							// re-fetch the shadow if necessary (if attributesToGet does not match)
							ResourceObjectIdentification identification = ResourceObjectIdentification.create(shadowCtx.getObjectClassDefinition(), 
									change.getIdentifiers());
							identification.validatePrimaryIdenfiers();
							LOGGER.trace("Re-fetching object {} because of attrsToReturn", identification);
							currentShadow = connector.fetchObject(identification, shadowAttrsToReturn, ctx, parentResult);
						}
					}
							
					PrismObject<ShadowType> processedCurrentShadow = postProcessResourceObjectRead(shadowCtx,
							currentShadow, true, parentResult);
					change.setCurrentShadow(processedCurrentShadow);
				}
			}
			LOGGER.trace("Processed change\n:{}", change.debugDump());
		}

		computeResultStatus(parentResult);
		
		LOGGER.trace("END fetch changes ({} changes)", changes == null ? "null" : changes.size());
		return changes;
	}

	String startListeningForAsyncUpdates(@NotNull ProvisioningContext ctx,
			@NotNull ChangeListener outerListener, @NotNull OperationResult parentResult) throws SchemaException,
			CommunicationException, ConfigurationException, ObjectNotFoundException, ExpressionEvaluationException {

		LOGGER.trace("START start listening for async updates, objectClass: {}", ctx.getObjectClassDefinition());
		ConnectorInstance connector = ctx.getConnector(AsyncUpdateCapabilityType.class, parentResult);

		ChangeListener innerListener = change -> {
			try {
				LOGGER.trace("Start processing change:\n{}", change.debugDumpLazily());
				setResourceOidIfMissing(change, ctx.getResourceOid());
				ProvisioningContext shadowCtx = ctx;
				ObjectClassComplexTypeDefinition changeObjectClassDefinition = change.getObjectClassDefinition();
				if (changeObjectClassDefinition == null) {
					if (!ctx.isWildcard() || change.getObjectDelta() == null || !change.getObjectDelta().isDelete()) {
						throw new SchemaException("No object class definition in change "+change);
					}
				}
				if (ctx.isWildcard() && changeObjectClassDefinition != null) {
					shadowCtx = ctx.spawn(changeObjectClassDefinition.getTypeName());
					if (shadowCtx.isWildcard()) {
						String message = "Unknown object class " + changeObjectClassDefinition.getTypeName()
								+ " found in synchronization delta";
						throw new SchemaException(message);
					}
					change.setObjectClassDefinition(shadowCtx.getObjectClassDefinition());
				}

				if (change.getCurrentShadow() != null) {
					shadowCaretaker.applyAttributesDefinition(ctx, change.getCurrentShadow());
					PrismObject<ShadowType> processedCurrentShadow = postProcessResourceObjectRead(shadowCtx,
							change.getCurrentShadow(), true, parentResult);
					change.setCurrentShadow(processedCurrentShadow);
				} else {
					// we will fetch current shadow later
				}
				return outerListener.onChange(change);
			} catch (Throwable t) {
				throw new SystemException("Couldn't process async update: " + t.getMessage(), t);
			}
		};
		ListeningActivity listeningActivity = connector.startListeningForChanges(innerListener, parentResult);
		String handle = listeningRegistry.registerListeningActivity(listeningActivity);
		computeResultStatus(parentResult);

		LOGGER.trace("END start listening for async updates; listening handle = {}", handle);
		return handle;
	}

	private void setResourceOidIfMissing(Change change, String resourceOid) {
		setResourceOidIfMissing(change.getOldShadow(), resourceOid);
		setResourceOidIfMissing(change.getCurrentShadow(), resourceOid);
		if (change.getObjectDelta() != null) {
			setResourceOidIfMissing(change.getObjectDelta().getObjectToAdd(), resourceOid);
		}
	}

	private void setResourceOidIfMissing(PrismObject<ShadowType> object, String resourceOid) {
		if (object != null && object.asObjectable().getResourceRef() == null && resourceOid != null) {
			object.asObjectable().setResourceRef(ObjectTypeUtil.createObjectRef(resourceOid, ObjectTypes.RESOURCE));
		}
	}

	void stopListeningForAsyncUpdates(@NotNull String listeningActivityHandle, @NotNull OperationResult parentResult) {
		LOGGER.trace("START stop listening for async updates, handle: {}", listeningActivityHandle);
		listeningRegistry.removeListeningActivity(listeningActivityHandle).stop();
		computeResultStatus(parentResult);
		LOGGER.trace("END stop listening for async updates");
	}

	AsyncUpdateListeningActivityInformationType getAsyncUpdatesListeningActivityInformation(@NotNull String listeningActivityHandle, @NotNull OperationResult parentResult) {
		ListeningActivity listeningActivity = listeningRegistry.getListeningActivity(listeningActivityHandle);
		return listeningActivity != null ? listeningActivity.getInformation() : null;
	}

	/**
	 * Process simulated activation, credentials and other properties that are added to the object by midPoint. 
	 */
	private PrismObject<ShadowType> postProcessResourceObjectRead(ProvisioningContext ctx,
			PrismObject<ShadowType> resourceObject, boolean fetchAssociations,
            OperationResult parentResult) throws SchemaException, CommunicationException, ObjectNotFoundException, ConfigurationException, SecurityViolationException, ExpressionEvaluationException {
		if (resourceObject == null) {
			return null;
		}
		ShadowType resourceObjectType = resourceObject.asObjectable();
		
		ProvisioningUtil.setProtectedFlag(ctx, resourceObject, matchingRuleRegistry, relationRegistry);
		
		if (resourceObjectType.isExists() != Boolean.FALSE) {
			resourceObjectType.setExists(true);
		}
		
		completeActivation(ctx, resourceObject, parentResult);
				
		// Entitlements
        if (fetchAssociations) {
            entitlementConverter.postProcessEntitlementsRead(ctx, resourceObject, parentResult);
        }
		
		return resourceObject;
	}
	
	/**
	 * Completes activation state by determining simulated activation if necessary.
	 */
	private void completeActivation(ProvisioningContext ctx, PrismObject<ShadowType> resourceObject, OperationResult parentResult) throws ObjectNotFoundException, SchemaException, CommunicationException, ConfigurationException, ExpressionEvaluationException {
		ResourceType resourceType = ctx.getResource();
		ShadowType resourceObjectType = resourceObject.asObjectable();

		ActivationCapabilityType activationCapability = ctx.getEffectiveCapability(ActivationCapabilityType.class);

		if (resourceObjectType.getActivation() != null || CapabilityUtil.isCapabilityEnabled(activationCapability)) {
			ActivationType activationType = null;
		
			if (ctx.hasNativeCapability(ActivationCapabilityType.class)) {
				activationType = resourceObjectType.getActivation();
				
			} else if (CapabilityUtil.isCapabilityEnabled(activationCapability)) {
				activationType = convertFromSimulatedActivationAttributes(resourceType, resourceObject, activationCapability, parentResult);
				
			} else {
				// No activation capability, nothing to do
			}
			
			LOGGER.trace("Determined activation, administrativeStatus: {}, lockoutStatus: {}",
					activationType == null ? "null activationType" : activationType.getAdministrativeStatus(),
					activationType == null ? "null activationType" : activationType.getLockoutStatus());
			resourceObjectType.setActivation(activationType);
			
		} else {
			resourceObjectType.setActivation(null);
		}
		
	}
	
	private static ActivationType convertFromSimulatedActivationAttributes(ResourceType resource,
			PrismObject<ShadowType> resourceObject, ActivationCapabilityType activationCapability, OperationResult parentResult) {
		// LOGGER.trace("Start converting activation type from simulated activation attribute");
		if (activationCapability == null) {
			return null;
		}
		
		ActivationType activationType = new ActivationType();
		
		convertFromSimulatedActivationAdministrativeStatus(activationType, activationCapability, resource, resourceObject, parentResult);
		convertFromSimulatedActivationLockoutStatus(activationType, activationCapability, resource, resourceObject, parentResult);
		
		return activationType;
	}

	private static void convertFromSimulatedActivationAdministrativeStatus(ActivationType activationType, ActivationCapabilityType activationCapability,
			ResourceType resource, PrismObject<ShadowType> shadow, OperationResult parentResult) {
		
		ActivationStatusCapabilityType statusCapabilityType = CapabilityUtil.getEffectiveActivationStatus(activationCapability);
		if (statusCapabilityType == null) {
			return;
		}
		
		ResourceAttributeContainer attributesContainer = ShadowUtil.getAttributesContainer(shadow);		
		ResourceAttribute<?> simulatedStatusAttribute = null;
		if (statusCapabilityType.getAttribute() != null) {
			simulatedStatusAttribute = attributesContainer.findAttribute(statusCapabilityType.getAttribute());
		}
		
		// LOGGER.trace("activation property: {}", activationProperty.dump());
		// if (activationProperty == null) {
		// LOGGER.debug("No simulated activation attribute was defined for the account.");
		// return null;
		// }

		Collection<Object> simulatedStatusAttributeValues = null;
		if (simulatedStatusAttribute != null) {
			simulatedStatusAttributeValues = simulatedStatusAttribute.getRealValues(Object.class);
		}
		
		convertFromSimulatedActivationAdministrativeStatusInternal(activationType, statusCapabilityType, resource, simulatedStatusAttributeValues, parentResult);
		
		LOGGER.trace(
				"Detected simulated activation administrativeStatus attribute {} on {} with value {}, resolved into {}",
				SchemaDebugUtil.prettyPrint(statusCapabilityType.getAttribute()),
				ObjectTypeUtil.toShortString(resource), simulatedStatusAttributeValues,
				activationType == null ? "null" : activationType.getAdministrativeStatus());
		
		// Remove the attribute which is the source of simulated activation. If we leave it there then we
		// will have two ways to set activation.
		if (statusCapabilityType.isIgnoreAttribute() == null
				|| statusCapabilityType.isIgnoreAttribute()) {
			if (simulatedStatusAttribute != null) {
				attributesContainer.remove(simulatedStatusAttribute);
			}
		}
	}
	
	/**
	 * Moved to a separate method especially to enable good logging (see above). 
	 */
	private static void convertFromSimulatedActivationAdministrativeStatusInternal(ActivationType activationType, ActivationStatusCapabilityType statusCapabilityType,
				ResourceType resource, Collection<Object> simulatedStatusAttributeValues, OperationResult parentResult) {
		
		List<String> disableValues = statusCapabilityType.getDisableValue();
		List<String> enableValues = statusCapabilityType.getEnableValue();		

		if (MiscUtil.isNoValue(simulatedStatusAttributeValues)) {

			if (MiscUtil.hasNoValue(disableValues)) {
				activationType.setAdministrativeStatus(ActivationStatusType.DISABLED);
				return;
			}

			if (MiscUtil.hasNoValue(enableValues)) {
				activationType.setAdministrativeStatus(ActivationStatusType.ENABLED);
				return;
			}

			// No activation information.
			LOGGER.warn("The {} does not provide definition for null value of simulated activation attribute",
					ObjectTypeUtil.toShortString(resource));
			if (parentResult != null) {
				parentResult.recordPartialError("The " + ObjectTypeUtil.toShortString(resource)
						+ " has native activation capability but does not provide value for DISABLE attribute");
			}

		} else {
			if (simulatedStatusAttributeValues.size() > 1) {
				LOGGER.warn("The {} provides {} values for simulated activation status attribute, expecting just one value",
						ObjectTypeUtil.toShortString(resource), disableValues.size());
				if (parentResult != null) {
					parentResult.recordPartialError("The " + ObjectTypeUtil.toShortString(resource) + " provides "
							+ disableValues.size() + " values for simulated activation status attribute, expecting just one value");
				}
			}
			Object disableObj = simulatedStatusAttributeValues.iterator().next();

			for (String disable : disableValues) {
				if (disable.equals(String.valueOf(disableObj))) {		// TODO MID-3374: implement seriously
					activationType.setAdministrativeStatus(ActivationStatusType.DISABLED);
					return;
				}
			}

			for (String enable : enableValues) {
				if ("".equals(enable) || enable.equals(String.valueOf(disableObj))) {		// TODO MID-3374: implement seriously
					activationType.setAdministrativeStatus(ActivationStatusType.ENABLED);
					return;
				}
			}
		}

	}
	
	private static void convertFromSimulatedActivationLockoutStatus(ActivationType activationType, ActivationCapabilityType activationCapability,
			ResourceType resource, PrismObject<ShadowType> shadow, OperationResult parentResult) {
		
		ActivationLockoutStatusCapabilityType statusCapabilityType = CapabilityUtil.getEffectiveActivationLockoutStatus(activationCapability);
		if (statusCapabilityType == null) {
			return;
		}
		
		ResourceAttributeContainer attributesContainer = ShadowUtil.getAttributesContainer(shadow);		
		ResourceAttribute<?> activationProperty = null;
		if (statusCapabilityType.getAttribute() != null) {
			activationProperty = attributesContainer.findAttribute(statusCapabilityType.getAttribute());
		}
		
		// LOGGER.trace("activation property: {}", activationProperty.dump());
		// if (activationProperty == null) {
		// LOGGER.debug("No simulated activation attribute was defined for the account.");
		// return null;
		// }

		Collection<Object> activationValues = null;
		if (activationProperty != null) {
			activationValues = activationProperty.getRealValues(Object.class);
		}
		
		convertFromSimulatedActivationLockoutStatusInternal(activationType, statusCapabilityType, resource, activationValues, parentResult);
		
		LOGGER.trace(
				"Detected simulated activation lockout attribute {} on {} with value {}, resolved into {}",
				SchemaDebugUtil.prettyPrint(statusCapabilityType.getAttribute()),
				ObjectTypeUtil.toShortString(resource), activationValues,
				activationType == null ? "null" : activationType.getAdministrativeStatus());
		
		// Remove the attribute which is the source of simulated activation. If we leave it there then we
		// will have two ways to set activation.
		if (statusCapabilityType.isIgnoreAttribute() == null
				|| statusCapabilityType.isIgnoreAttribute()) {
			if (activationProperty != null) {
				attributesContainer.remove(activationProperty);
			}
		}
	}
	
	/**
	 * Moved to a separate method especially to enable good logging (see above). 
	 */
	private static void convertFromSimulatedActivationLockoutStatusInternal(ActivationType activationType, ActivationLockoutStatusCapabilityType statusCapabilityType,
				ResourceType resource, Collection<Object> activationValues, OperationResult parentResult) {
		
		List<String> lockedValues = statusCapabilityType.getLockedValue();
		List<String> normalValues = statusCapabilityType.getNormalValue();	

		if (MiscUtil.isNoValue(activationValues)) {

			if (MiscUtil.hasNoValue(lockedValues)) {
				activationType.setLockoutStatus(LockoutStatusType.LOCKED);
				return;
			}

			if (MiscUtil.hasNoValue(normalValues)) {
				activationType.setLockoutStatus(LockoutStatusType.NORMAL);
				return;
			}

			// No activation information.
			LOGGER.warn("The {} does not provide definition for null value of simulated activation lockout attribute",
					resource);
			if (parentResult != null) {
				parentResult.recordPartialError("The " + resource
						+ " has native activation capability but noes not provide value for lockout attribute");
			}

			return;

		} else {
			if (activationValues.size() > 1) {
				LOGGER.warn("The {} provides {} values for lockout attribute, expecting just one value",
						lockedValues.size(), resource);
				if (parentResult != null) {
					parentResult.recordPartialError("The " + resource + " provides "
							+ lockedValues.size() + " values for lockout attribute, expecting just one value");
				}
			}
			Object activationValue = activationValues.iterator().next();

			for (String lockedValue : lockedValues) {
				if (lockedValue.equals(String.valueOf(activationValue))) {
					activationType.setLockoutStatus(LockoutStatusType.LOCKED);
					return;
				}
			}

			for (String normalValue : normalValues) {
				if ("".equals(normalValue) || normalValue.equals(String.valueOf(activationValue))) {
					activationType.setLockoutStatus(LockoutStatusType.NORMAL);
					return;
				}
			}
		}

	}

	private ActivationStatusCapabilityType getActivationAdministrativeStatusFromSimulatedActivation(ProvisioningContext ctx,
			ActivationCapabilityType activationCapability, ShadowType shadow, OperationResult result) 
					throws ObjectNotFoundException, SchemaException, CommunicationException, ConfigurationException, ExpressionEvaluationException {
		if (activationCapability == null) {
			result.recordWarning("Resource " + ctx.getResource()
					+ " does not have native or simulated activation capability. Processing of activation for "+ shadow +" was skipped");
			shadow.setFetchResult(result.createOperationResultType());
			return null;
		}

		ActivationStatusCapabilityType capActStatus = CapabilityUtil.getEffectiveActivationStatus(activationCapability);
		if (capActStatus == null) {
			result.recordWarning("Resource " + ctx.getResource()
					+ " does not have native or simulated activation status capability. Processing of activation for "+ shadow +" was skipped");
			shadow.setFetchResult(result.createOperationResultType());
			return null;
		}
		return capActStatus;

	}
	
	private <T> ResourceAttribute<T> getSimulatedActivationAdministrativeStatusAttribute(ProvisioningContext ctx,
			ShadowType shadow, ActivationStatusCapabilityType capActStatus, OperationResult result) throws ObjectNotFoundException, SchemaException, CommunicationException, ConfigurationException, ExpressionEvaluationException {
		if (capActStatus == null){
			return null;
		}
		ResourceType resource = ctx.getResource();
		QName enableAttributeName = capActStatus.getAttribute();
		LOGGER.trace("Simulated attribute name: {}", enableAttributeName);
		if (enableAttributeName == null) {
			result.recordWarning("Resource "
							+ ObjectTypeUtil.toShortString(resource)
							+ " does not have attribute specification for simulated activation status capability. Processing of activation for "+ shadow +" was skipped");
			shadow.setFetchResult(result.createOperationResultType());
			return null;
		}

		ResourceAttributeDefinition<T> enableAttributeDefinition = ctx.getObjectClassDefinition()
				.findAttributeDefinition(enableAttributeName);
		if (enableAttributeDefinition == null) {
			result.recordWarning("Resource " + ObjectTypeUtil.toShortString(resource)
					+ "  attribute for simulated activation/enableDisable capability" + enableAttributeName
					+ " in not present in the schema for objeclass " + ctx.getObjectClassDefinition()+". Processing of activation for "+ ObjectTypeUtil.toShortString(shadow)+" was skipped");
			shadow.setFetchResult(result.createOperationResultType());
			return null;
		}

		return enableAttributeDefinition.instantiate(enableAttributeName);
	}

	private PropertyModificationOperation convertToSimulatedActivationAdministrativeStatusAttribute(ProvisioningContext ctx, 
			PropertyDelta activationDelta, ShadowType shadow, ActivationStatusType status, ActivationCapabilityType activationCapabilityType, OperationResult result)
			throws SchemaException, ObjectNotFoundException, CommunicationException, ConfigurationException, ExpressionEvaluationException {
		ResourceType resource = ctx.getResource();
		ActivationStatusCapabilityType capActStatus = getActivationAdministrativeStatusFromSimulatedActivation(ctx, activationCapabilityType, shadow, result);
		if (capActStatus == null){
			throw new SchemaException("Attempt to modify activation on "+resource+" which does not have activation capability");
		}
		
		ResourceAttribute<?> simulatedAttribute = getSimulatedActivationAdministrativeStatusAttribute(ctx, shadow, capActStatus, result);
		if (simulatedAttribute == null) {
			return null;
		}

		Class<?> simulatedAttrValueClass = getAttributeValueClass(ctx, shadow, simulatedAttribute, capActStatus);

		PropertyDelta<?> simulatedAttrDelta;
		if (status == null && activationDelta.isDelete()){
			LOGGER.trace("deleting activation property.");
			simulatedAttrDelta = prismContext.deltaFactory().property().createModificationDeleteProperty(
					ItemPath.create(ShadowType.F_ATTRIBUTES, simulatedAttribute.getElementName()), simulatedAttribute.getDefinition(), simulatedAttribute.getRealValue());
		} else if (status == ActivationStatusType.ENABLED) {
			Object enableValue = getEnableValue(capActStatus, simulatedAttrValueClass);
			simulatedAttrDelta = createActivationPropDelta(simulatedAttribute.getElementName(), simulatedAttribute.getDefinition(), enableValue);
		} else {
			Object disableValue = getDisableValue(capActStatus, simulatedAttrValueClass);
			simulatedAttrDelta = createActivationPropDelta(simulatedAttribute.getElementName(), simulatedAttribute.getDefinition(), disableValue);
		}

		PropertyModificationOperation attributeChange = new PropertyModificationOperation(simulatedAttrDelta);
		return attributeChange;
	}
	
	private PropertyModificationOperation convertToSimulatedActivationLockoutStatusAttribute(ProvisioningContext ctx,
			PropertyDelta activationDelta, ShadowType shadow, LockoutStatusType status, ActivationCapabilityType activationCapability, OperationResult result)
			throws SchemaException, ObjectNotFoundException, CommunicationException, ConfigurationException, ExpressionEvaluationException {

		ActivationLockoutStatusCapabilityType capActStatus = getActivationLockoutStatusFromSimulatedActivation(ctx, activationCapability, shadow, result);
		if (capActStatus == null){
			throw new SchemaException("Attempt to modify lockout on "+ctx.getResource()+" which does not have activation lockout capability");
		}
		
		ResourceAttribute<?> activationAttribute = getSimulatedActivationLockoutStatusAttribute(ctx, shadow, capActStatus, result);
		if (activationAttribute == null){
			return null;
		}
		
		PropertyDelta<?> lockoutAttributeDelta = null;
		
		if (status == null && activationDelta.isDelete()){
			LOGGER.trace("deleting activation property.");
			lockoutAttributeDelta = prismContext.deltaFactory().property().createModificationDeleteProperty(ItemPath.create(ShadowType.F_ATTRIBUTES, activationAttribute.getElementName()), activationAttribute.getDefinition(), activationAttribute.getRealValue());
			
		} else if (status == LockoutStatusType.NORMAL) {
			String normalValue = getLockoutNormalValue(capActStatus);
			lockoutAttributeDelta = createActivationPropDelta(activationAttribute.getElementName(), activationAttribute.getDefinition(), normalValue);
		} else {
			String lockedValue = getLockoutLockedValue(capActStatus);
			lockoutAttributeDelta = createActivationPropDelta(activationAttribute.getElementName(), activationAttribute.getDefinition(), lockedValue);
		}

		PropertyModificationOperation attributeChange = new PropertyModificationOperation(lockoutAttributeDelta);
		return attributeChange;
	}
	
	private PropertyDelta<?> createActivationPropDelta(QName attrName, ResourceAttributeDefinition attrDef, Object value) {
		if (isBlank(value)) {
			return prismContext.deltaFactory().property().createModificationReplaceProperty(ItemPath.create(ShadowType.F_ATTRIBUTES, attrName),
					attrDef);
		} else {
			return prismContext.deltaFactory().property().createModificationReplaceProperty(ItemPath.create(ShadowType.F_ATTRIBUTES, attrName),
					attrDef, value);
		}
	}

	private ActivationLockoutStatusCapabilityType getActivationLockoutStatusFromSimulatedActivation(ProvisioningContext ctx,
			ActivationCapabilityType activationCapability, ShadowType shadow, OperationResult result) 
					throws ObjectNotFoundException, SchemaException, CommunicationException, ConfigurationException, ExpressionEvaluationException{
		if (activationCapability == null) {
			result.recordWarning("Resource " + ctx.getResource()
					+ " does not have native or simulated activation capability. Processing of activation for "+ shadow +" was skipped");
			shadow.setFetchResult(result.createOperationResultType());
			return null;
		}

		ActivationLockoutStatusCapabilityType capActStatus = CapabilityUtil.getEffectiveActivationLockoutStatus(activationCapability);
		if (capActStatus == null) {
			result.recordWarning("Resource " + ctx.getResource()
					+ " does not have native or simulated activation lockout capability. Processing of activation for "+ shadow +" was skipped");
			shadow.setFetchResult(result.createOperationResultType());
			return null;
		}
		return capActStatus;

	}
	
	private ResourceAttribute<?> getSimulatedActivationLockoutStatusAttribute(ProvisioningContext ctx, 
			ShadowType shadow, ActivationLockoutStatusCapabilityType capActStatus, OperationResult result) throws ObjectNotFoundException, SchemaException, CommunicationException, ConfigurationException, ExpressionEvaluationException{
		
		QName enableAttributeName = capActStatus.getAttribute();
		LOGGER.trace("Simulated lockout attribute name: {}", enableAttributeName);
		if (enableAttributeName == null) {
			result.recordWarning("Resource "
							+ ObjectTypeUtil.toShortString(ctx.getResource())
							+ " does not have attribute specification for simulated activation lockout capability. Processing of activation for "+ shadow +" was skipped");
			shadow.setFetchResult(result.createOperationResultType());
			return null;
		}

		ResourceAttributeDefinition enableAttributeDefinition = ctx.getObjectClassDefinition()
				.findAttributeDefinition(enableAttributeName);
		if (enableAttributeDefinition == null) {
			result.recordWarning("Resource " + ObjectTypeUtil.toShortString(ctx.getResource())
					+ "  attribute for simulated activation/lockout capability" + enableAttributeName
					+ " in not present in the schema for objeclass " + ctx.getObjectClassDefinition()+". Processing of activation for "+ ObjectTypeUtil.toShortString(shadow)+" was skipped");
			shadow.setFetchResult(result.createOperationResultType());
			return null;
		}

		return enableAttributeDefinition.instantiate(enableAttributeName);

	}
	

	private <T> T getDisableValue(ActivationStatusCapabilityType capActStatus, Class<T> clazz) {
		//TODO some checks
		Object value = capActStatus.getDisableValue().iterator().next();
		return JavaTypeConverter.convert(clazz, value);
	}
	
	private <T> T getEnableValue(ActivationStatusCapabilityType capActStatus, Class<T> clazz) {
		String value = capActStatus.getEnableValue().iterator().next();
		return JavaTypeConverter.convert(clazz, value);
	}
	
	private String getLockoutNormalValue(ActivationLockoutStatusCapabilityType capActStatus) {
		String value = capActStatus.getNormalValue().iterator().next();
		return value;
	}
	
	private String getLockoutLockedValue(ActivationLockoutStatusCapabilityType capActStatus) {
		String value = capActStatus.getLockedValue().iterator().next();
		return value;
	}

	private RefinedObjectClassDefinition determineObjectClassDefinition(PrismObject<ShadowType> shadow, ResourceType resource) throws SchemaException, ConfigurationException {
		ShadowType shadowType = shadow.asObjectable();
		RefinedResourceSchema refinedSchema = RefinedResourceSchemaImpl.getRefinedSchema(resource, prismContext);
		if (refinedSchema == null) {
			throw new ConfigurationException("No schema definied for "+resource);
		}
		
		
		RefinedObjectClassDefinition objectClassDefinition = null;
		ShadowKindType kind = shadowType.getKind();
		String intent = shadowType.getIntent();
		QName objectClass = shadow.asObjectable().getObjectClass();
		if (kind != null) {
			objectClassDefinition = refinedSchema.getRefinedDefinition(kind, intent);
		} else {
			// Fallback to objectclass only
			if (objectClass == null) {
				throw new SchemaException("No kind nor objectclass definied in "+shadow);
			}
			objectClassDefinition = refinedSchema.findRefinedDefinitionByObjectClassQName(null, objectClass);
		}
		
		if (objectClassDefinition == null) {
			throw new SchemaException("Definition for "+shadow+" not found (objectClass=" + PrettyPrinter.prettyPrint(objectClass) +
					", kind="+kind+", intent='"+intent+"') in schema of " + resource);
		}		
		
		return objectClassDefinition;
	}
	
	private ObjectClassComplexTypeDefinition determineObjectClassDefinition(
			ResourceShadowDiscriminator discriminator, ResourceType resource) throws SchemaException {
		ResourceSchema schema = RefinedResourceSchemaImpl.getResourceSchema(resource, prismContext);
		// HACK FIXME
		ObjectClassComplexTypeDefinition objectClassDefinition = schema.findObjectClassDefinition(ShadowKindType.ACCOUNT, discriminator.getIntent());

		if (objectClassDefinition == null) {
			// Unknown objectclass
			throw new SchemaException("Account type " + discriminator.getIntent()
					+ " is not known in schema of " + resource);
		}
		
		return objectClassDefinition;
	}
	
	private void addExecuteScriptOperation(Collection<Operation> operations, ProvisioningOperationTypeType type,
			OperationProvisioningScriptsType scripts, ResourceType resource, OperationResult result) throws SchemaException {
		if (scripts == null) {
			// No warning needed, this is quite normal
			LOGGER.trace("Skipping creating script operation to execute. Scripts was not defined.");
			return;
		}

		for (OperationProvisioningScriptType script : scripts.getScript()) {
			for (ProvisioningOperationTypeType operationType : script.getOperation()) {
				if (type.equals(operationType)) {
					ExecuteProvisioningScriptOperation scriptOperation = ProvisioningUtil.convertToScriptOperation(
							script, "script value for " + operationType + " in " + resource, prismContext);

					scriptOperation.setScriptOrder(script.getOrder());

					LOGGER.trace("Created script operation: {}", SchemaDebugUtil.prettyPrint(scriptOperation));
					operations.add(scriptOperation);
				}
			}
		}
	}
	
	public AsynchronousOperationResult refreshOperationStatus(ProvisioningContext ctx, 
			PrismObject<ShadowType> shadow, String asyncRef, OperationResult parentResult) 
					throws ObjectNotFoundException, SchemaException, CommunicationException, ConfigurationException, ExpressionEvaluationException {
		
		OperationResult result = parentResult.createSubresult(OPERATION_REFRESH_OPERATION_STATUS);

		ResourceType resource;
		ConnectorInstance connector;
		try {
			resource = ctx.getResource();
			// TODO: not really correct. But good enough for now.
			connector = ctx.getConnector(UpdateCapabilityType.class, result);
		} catch (ObjectNotFoundException | SchemaException | CommunicationException
				| ConfigurationException | ExpressionEvaluationException | RuntimeException | Error e) {
			result.recordFatalError(e);
			throw e;
		}
		
		OperationResultStatus status = null;
		if (connector instanceof AsynchronousOperationQueryable) {
			
			LOGGER.trace("PROVISIONING REFRESH operation ref={} on {}, object: {}",
					asyncRef, resource, shadow);
			
			try {
				
				status = ((AsynchronousOperationQueryable)connector).queryOperationStatus(asyncRef, result);
				
			} catch (ObjectNotFoundException | SchemaException | ConfigurationException | CommunicationException e) {
				result.recordFatalError(e);
				throw e;
			}
			
			result.recordSuccess();
			
			LOGGER.debug("PROVISIONING REFRESH ref={} successful on {} {}, returned status: {}", asyncRef, resource, shadow, status);

		} else {
			LOGGER.trace("Ignoring refresh of shadow {}, because the connector is not async", shadow.getOid());
			result.recordNotApplicableIfUnknown();
		}

		OperationResult refreshResult = new OperationResult(OPERATION_REFRESH_OPERATION_STATUS);
		refreshResult.setStatus(status);
		AsynchronousOperationResult asyncResult = AsynchronousOperationResult.wrap(refreshResult);
		updateQuantum(ctx, connector, asyncResult, result);
		return asyncResult;
	}
	
	private void computeResultStatus(OperationResult parentResult) {
		if (parentResult.isInProgress()) {
			return;
		}
		OperationResultStatus status = OperationResultStatus.SUCCESS;
		String asyncRef = null;
		for (OperationResult subresult: parentResult.getSubresults()) {
			if (OPERATION_MODIFY_ENTITLEMENT.equals(subresult.getOperation()) && subresult.isError()) {
				status = OperationResultStatus.PARTIAL_ERROR;
			} else if (subresult.isError()) {
				status = OperationResultStatus.FATAL_ERROR;
			} else if (subresult.isInProgress()) {
				status = OperationResultStatus.IN_PROGRESS;
				asyncRef = subresult.getAsynchronousOperationReference();
			}
		}
		parentResult.setStatus(status);
		parentResult.setAsynchronousOperationReference(asyncRef);
	}
	
	private <C extends CapabilityType> void checkForCapability(ProvisioningContext ctx, Class<C> capabilityClass, OperationResult result) throws ObjectNotFoundException, SchemaException, CommunicationException, ConfigurationException, ExpressionEvaluationException {
		C capability = ctx.getEffectiveCapability(capabilityClass);
		if (capability == null) {
			UnsupportedOperationException e = new UnsupportedOperationException("Operation not supported "+ctx.getDesc()+" as "+capabilityClass.getSimpleName()+" is missing");
			if (result != null) {
				result.recordFatalError(e);
			}
			throw e;
		}
	}

	
}
