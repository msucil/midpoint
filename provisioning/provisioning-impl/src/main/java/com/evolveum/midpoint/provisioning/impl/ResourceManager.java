/*
 * Copyright (c) 2010-2018 Evolveum
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.annotation.Resource;
import javax.xml.namespace.QName;

import com.evolveum.midpoint.common.refinery.RefinedObjectClassDefinition;
import com.evolveum.midpoint.common.refinery.RefinedResourceSchemaImpl;
import com.evolveum.midpoint.prism.*;
import com.evolveum.midpoint.prism.delta.*;
import com.evolveum.midpoint.prism.path.ItemPath;
import com.evolveum.midpoint.prism.schema.MutablePrismSchema;
import com.evolveum.midpoint.schema.processor.*;
import com.evolveum.prism.xml.ns._public.types_3.SchemaDefinitionType;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.Validate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.evolveum.midpoint.prism.query.ObjectQuery;
import com.evolveum.midpoint.prism.schema.PrismSchema;
import com.evolveum.midpoint.provisioning.api.GenericConnectorException;
import com.evolveum.midpoint.provisioning.api.ProvisioningOperationOptions;
import com.evolveum.midpoint.provisioning.ucf.api.ConnectorInstance;
import com.evolveum.midpoint.provisioning.ucf.api.ExecuteProvisioningScriptOperation;
import com.evolveum.midpoint.provisioning.ucf.api.GenericFrameworkException;
import com.evolveum.midpoint.provisioning.util.ProvisioningUtil;
import com.evolveum.midpoint.repo.api.RepositoryService;
import com.evolveum.midpoint.repo.common.expression.Expression;
import com.evolveum.midpoint.repo.common.expression.ExpressionEvaluationContext;
import com.evolveum.midpoint.repo.common.expression.ExpressionFactory;
import com.evolveum.midpoint.repo.common.expression.ExpressionVariables;
import com.evolveum.midpoint.schema.CapabilityUtil;
import com.evolveum.midpoint.schema.GetOperationOptions;
import com.evolveum.midpoint.schema.SelectorOptions;
import com.evolveum.midpoint.schema.constants.ConnectorTestOperation;
import com.evolveum.midpoint.schema.constants.SchemaConstants;
import com.evolveum.midpoint.schema.internals.InternalCounters;
import com.evolveum.midpoint.schema.internals.InternalMonitor;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.schema.statistics.ConnectorOperationalStatus;
import com.evolveum.midpoint.schema.util.ConnectorTypeUtil;
import com.evolveum.midpoint.schema.util.MiscSchemaUtil;
import com.evolveum.midpoint.schema.util.ObjectTypeUtil;
import com.evolveum.midpoint.schema.util.ResourceTypeUtil;
import com.evolveum.midpoint.task.api.StateReporter;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.util.DOMUtil;
import com.evolveum.midpoint.util.DebugUtil;
import com.evolveum.midpoint.util.exception.CommunicationException;
import com.evolveum.midpoint.util.exception.ConfigurationException;
import com.evolveum.midpoint.util.exception.ExpressionEvaluationException;
import com.evolveum.midpoint.util.exception.ObjectAlreadyExistsException;
import com.evolveum.midpoint.util.exception.ObjectNotFoundException;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.util.exception.SecurityViolationException;
import com.evolveum.midpoint.util.exception.SystemException;
import com.evolveum.midpoint.util.exception.TunnelException;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.xml.ns._public.common.common_3.AvailabilityStatusType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.CachingMetadataType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.CapabilitiesType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.CapabilityCollectionType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ConnectorConfigurationType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ConnectorInstanceSpecificationType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ConnectorType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ExpressionType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.OperationalStateType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ProvisioningScriptType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ResourceType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.SchemaGenerationConstraintsType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.XmlSchemaType;
import com.evolveum.midpoint.xml.ns._public.resource.capabilities_3.ActivationCapabilityType;
import com.evolveum.midpoint.xml.ns._public.resource.capabilities_3.CapabilityType;
import com.evolveum.midpoint.xml.ns._public.resource.capabilities_3.SchemaCapabilityType;
import com.evolveum.midpoint.xml.ns._public.resource.capabilities_3.ScriptCapabilityType;

@Component
public class ResourceManager {

	@Autowired(required = true)
	@Qualifier("cacheRepositoryService")
	private RepositoryService repositoryService;
	
	@Autowired(required = true)
	private ResourceCache resourceCache;
	
	@Autowired(required = true)
	private ConnectorManager connectorManager;
	
	@Autowired(required = true)
	private PrismContext prismContext;
	
	@Autowired(required = true)
	private ExpressionFactory expressionFactory;

	private static final Trace LOGGER = TraceManager.getTrace(ResourceManager.class);
	
	private static final String OPERATION_COMPLETE_RESOURCE = ResourceManager.class.getName() + ".completeResource";
	
	public PrismObject<ResourceType> getResource(PrismObject<ResourceType> repositoryObject, GetOperationOptions options, Task task, OperationResult parentResult) throws ObjectNotFoundException, SchemaException, CommunicationException, ConfigurationException, ExpressionEvaluationException{
		InternalMonitor.getResourceCacheStats().recordRequest();
		
		PrismObject<ResourceType> cachedResource = resourceCache.get(repositoryObject, options);
		if (cachedResource != null) {
			InternalMonitor.getResourceCacheStats().recordHit();
			return cachedResource;
		}
		
		LOGGER.debug("Storing fetched resource {}, version {} to cache (previously cached version {})",
				repositoryObject.getOid(), repositoryObject.getVersion(), resourceCache.getVersion(repositoryObject.getOid()));
		
		return loadAndCacheResource(repositoryObject, options, task, parentResult);
	}
	
	public PrismObject<ResourceType> getResource(String oid, GetOperationOptions options, Task task, OperationResult parentResult) throws ObjectNotFoundException, SchemaException, CommunicationException, ConfigurationException, ExpressionEvaluationException{
		InternalMonitor.getResourceCacheStats().recordRequest();
		
		String version = repositoryService.getVersion(ResourceType.class, oid, parentResult);
		PrismObject<ResourceType> cachedResource = resourceCache.get(oid, version, options);
		if (cachedResource != null) {
			InternalMonitor.getResourceCacheStats().recordHit();
			if (LOGGER.isTraceEnabled()){
				LOGGER.trace("Returning resource from cache:\n{}", cachedResource.debugDump());
			}
			return cachedResource;
		}
		
		if (LOGGER.isDebugEnabled()) {
			LOGGER.debug("Fetching resource {}, version {}, storing to cache (previously cached version {})", 
					oid, version, resourceCache.getVersion(oid));
		}
		
		Collection<SelectorOptions<GetOperationOptions>> repoOptions = null;
		if (GetOperationOptions.isReadOnly(options)) {
			repoOptions = SelectorOptions.createCollection(GetOperationOptions.createReadOnly());
		}
		PrismObject<ResourceType> repositoryObject = readResourceFromRepository(oid, repoOptions, parentResult);
		
		return loadAndCacheResource(repositoryObject, options, task, parentResult);
	}
	
	private PrismObject<ResourceType> readResourceFromRepository(String oid, Collection<SelectorOptions<GetOperationOptions>> repoOptions, OperationResult parentResult) throws ObjectNotFoundException, SchemaException {
		InternalMonitor.recordCount(InternalCounters.RESOURCE_REPOSITORY_READ_COUNT);
		return repositoryService.getObject(ResourceType.class, oid, repoOptions, parentResult);
	}
	
	private PrismObject<ResourceType> loadAndCacheResource(PrismObject<ResourceType> repositoryObject, 
			GetOperationOptions options, Task task, OperationResult parentResult) throws ObjectNotFoundException, SchemaException, CommunicationException, ConfigurationException, ExpressionEvaluationException {
		
		PrismObject<ResourceType> completedResource = completeResource(repositoryObject, null, false, null, options, task, parentResult);
		
		if (!isComplete(completedResource)) {
			// No not cache non-complete resources (e.g. those retrieved with noFetch)
			LOGGER.trace("Not putting resource {} ({}) into cache because it's not complete", repositoryObject.getName(), repositoryObject.getOid());
			return completedResource; 
		}

		if (LOGGER.isTraceEnabled()) {
			LOGGER.trace("Putting resource in cache:\n{}", completedResource.debugDump());
			Element xsdSchemaElement = ResourceTypeUtil.getResourceXsdSchema(completedResource);
			if (xsdSchemaElement == null) {
				LOGGER.trace("Schema: null");
			} else {
				LOGGER.trace("Schema:\n{}",
						DOMUtil.serializeDOMToString(ResourceTypeUtil.getResourceXsdSchema(completedResource)));
			}
		}
		OperationResult completeResourceResult = parentResult.findSubresult(OPERATION_COMPLETE_RESOURCE);
		if (completeResourceResult.isSuccess()) {
			// Cache only resources that are completely OK
			resourceCache.put(completedResource);
		} else {
			LOGGER.trace("Not putting {} into cache because the completeResource operation status is {}",
					ObjectTypeUtil.toShortString(repositoryObject), completeResourceResult.getStatus());
		}
		
		InternalMonitor.getResourceCacheStats().recordMiss();
		return completedResource;
	}
	
	public void deleteResource(String oid, ProvisioningOperationOptions options, Task task, OperationResult parentResult) throws ObjectNotFoundException {
		resourceCache.remove(oid);
		repositoryService.deleteObject(ResourceType.class, oid, parentResult);
	}
		
	/**
	 * Make sure that the resource is complete.
	 * 
	 * It will check if the resource has a sufficiently fresh schema, etc.
	 * 
	 * Returned resource may be the same or may be a different instance, but it
	 * is guaranteed that it will be "fresher" and will correspond to the
	 * repository state (assuming that the provided resource also corresponded
	 * to the repository state).
	 * 
	 * The connector schema that was fetched before can be supplied to this
	 * method. This is just an optimization. It comes handy e.g. in test
	 * connection case.
	 * 
	 * Note: This is not really the best place for this method. Need to figure
	 * out correct place later.
	 * 
	 * @param repoResource
	 *            Resource to check
	 * @param resourceSchema
	 *            schema that was freshly pre-fetched (or null)
	 * @param parentResult
	 * 
	 * @return completed resource
	 */
	private PrismObject<ResourceType> completeResource(PrismObject<ResourceType> repoResource, ResourceSchema resourceSchema,
			boolean fetchedSchema, Map<String,Collection<Object>> capabilityMap, GetOperationOptions options, Task task, OperationResult parentResult) throws ObjectNotFoundException, SchemaException,
			CommunicationException, ConfigurationException, ExpressionEvaluationException {

		// do not add as a subresult..it will be added later, if the completing
		// of resource will be successfull.if not, it will be only set as a
		// fetch result in the resource..
		OperationResult result = parentResult.createMinorSubresult(OPERATION_COMPLETE_RESOURCE);
		
		try {
			
			applyConnectorSchemaToResource(repoResource, task, result);
						
		} catch (SchemaException e) {
			String message = "Schema error while applying connector schema to connectorConfiguration section of "+repoResource+": "+e.getMessage();
			result.recordPartialError(message, e);
			LOGGER.warn(message, e);
			return repoResource;
		} catch (ObjectNotFoundException e) {
			String message = "Object not found error while processing connector configuration of "+repoResource+": "+e.getMessage();
			result.recordPartialError(message, e);
			LOGGER.warn(message, e);
			return repoResource;
		} catch (RuntimeException e) {
			String message = "Unexpected error while processing connector configuration of "+repoResource+": "+e.getMessage();
			result.recordPartialError(message, e);
			LOGGER.warn(message, e);
			return repoResource;
		}

		PrismObject<ResourceType> newResource;
		
		if (isComplete(repoResource)) {
			// The resource is complete.
			newResource = repoResource;
			
		} else {
			// The resource is NOT complete. Try to fetch schema and capabilities
			
			if (GetOperationOptions.isNoFetch(options)) {
				// We need to fetch schema, but the noFetch option is specified. Therefore return whatever we have.
				result.recordSuccessIfUnknown();
				return repoResource;
			}
	
			try {

				repoResource = repoResource.cloneIfImmutable();
				completeSchemaAndCapabilities(repoResource, resourceSchema, fetchedSchema, capabilityMap, task, result);
				
			} catch (Exception ex) {
				// Catch the exceptions. There are not critical. We need to catch them all because the connector may
				// throw even undocumented runtime exceptions.
				// Even non-complete resource may still be usable. The fetchResult indicates that there was an error
				result.recordPartialError("Cannot complete resource schema and capabilities: "+ex.getMessage(), ex);
				return repoResource;
			}
			
			
			try {
				// Now we need to re-read the resource from the repository and re-aply the schemas. This ensures that we will
				// cache the correct version and that we avoid race conditions, etc.
				
				newResource = readResourceFromRepository(repoResource.getOid(), null, result);
				applyConnectorSchemaToResource(newResource, task, result);
				
			} catch (SchemaException e) {
				result.recordFatalError(e);
				throw e;
			} catch (ObjectNotFoundException e) {
				result.recordFatalError(e);
				throw e;
			} catch (RuntimeException e) {
				result.recordFatalError(e);
				throw e;
			}
			
		}
		
		try {
			// make sure it has parsed resource and refined schema. We are going to cache
			// it, so we want to cache it with the parsed schemas
			RefinedResourceSchemaImpl.getResourceSchema(newResource, prismContext);
			RefinedResourceSchemaImpl.getRefinedSchema(newResource);
			
		} catch (SchemaException e) {
			String message = "Schema error while processing schemaHandling section of "+newResource+": "+e.getMessage();
			result.recordPartialError(message, e);
			LOGGER.warn(message, e);
			return newResource;
		} catch (RuntimeException e) {
			String message = "Unexpected error while processing schemaHandling section of "+newResource+": "+e.getMessage();
			result.recordPartialError(message, e);
			LOGGER.warn(message, e);
			return newResource;
		}
		
		result.recordSuccessIfUnknown();

		return newResource;
	}

	private boolean isComplete(PrismObject<ResourceType> resource) {
		ResourceType resourceType = resource.asObjectable();
		Element xsdSchema = ResourceTypeUtil.getResourceXsdSchema(resource);
		if (xsdSchema == null) {
			return false;
		}
		CapabilitiesType capabilitiesType = resourceType.getCapabilities();
		if (capabilitiesType == null) {
			return false;
		}
		CachingMetadataType capCachingMetadata = capabilitiesType.getCachingMetadata();
		if (capCachingMetadata == null) {
			return false;
		}
		return true;
	}


	private void completeSchemaAndCapabilities(PrismObject<ResourceType> resource, ResourceSchema resourceSchema, boolean fetchedSchema,
			Map<String,Collection<Object>> capabilityMap, Task task, OperationResult result) 
					throws SchemaException, CommunicationException, ObjectNotFoundException, GenericFrameworkException, ConfigurationException {

		Collection<ItemDelta<?,?>> modifications = new ArrayList<>();

		// Capabilities
		// we need to process capabilities first. Schema is one of the connector capabilities.
		// We need to determine this capability to select the right connector for schema retrieval.
		completeCapabilities(resource, capabilityMap != null, capabilityMap, modifications, result);		
		
		if (resourceSchema == null) { 
			// Try to get existing schema from resource. We do not want to override this if it exists
			// (but we still want to refresh the capabilities, that happens below)
			resourceSchema = RefinedResourceSchemaImpl.getResourceSchema(resource, prismContext);
		}
			
		if (resourceSchema == null || resourceSchema.isEmpty()) {
		
			LOGGER.trace("Fetching resource schema for {}", resource);
			
			resourceSchema = fetchResourceSchema(resource, capabilityMap, task, result);

			if (resourceSchema == null) {
				LOGGER.warn("No resource schema fetched from {}", resource);
			} else if (resourceSchema.isEmpty()) {
				LOGGER.warn("Empty resource schema fetched from {}", resource);
			} else {
				LOGGER.debug("Fetched resource schema for {}: {} definitions", resource, resourceSchema.getDefinitions().size());
				fetchedSchema = true;
			}
		}
		
		if (fetchedSchema) {
			adjustSchemaForSimulatedCapabilities(resource, resourceSchema);
			ContainerDelta<XmlSchemaType> schemaContainerDelta = createSchemaUpdateDelta(resource, resourceSchema);
			modifications.add(schemaContainerDelta);
			
			// We have successfully fetched the resource schema. Therefore the resource must be up.
			modifications.add(createResourceAvailabilityStatusDelta(resource, AvailabilityStatusType.UP));
			
		} else {
			if (resourceSchema != null) {
				CachingMetadataType schemaCachingMetadata = resource.asObjectable().getSchema().getCachingMetadata();
				if (schemaCachingMetadata == null) {
					schemaCachingMetadata = MiscSchemaUtil.generateCachingMetadata();
					modifications.add(
							prismContext.deltaFactory().property().createModificationReplaceProperty(
								ItemPath.create(ResourceType.F_SCHEMA, CapabilitiesType.F_CACHING_METADATA),
								resource.getDefinition(),
								schemaCachingMetadata)
						);
				}
			}
		}

		if (!modifications.isEmpty()) {
	        try {
	        	if (LOGGER.isTraceEnabled()) {
	        		LOGGER.trace("Completing {}:\n{}", resource, DebugUtil.debugDump(modifications, 1));
	        	}
				repositoryService.modifyObject(ResourceType.class, resource.getOid(), modifications, result);
				InternalMonitor.recordCount(InternalCounters.RESOURCE_REPOSITORY_MODIFY_COUNT);
	        } catch (ObjectAlreadyExistsException ex) {
	        	// This should not happen
	            throw new SystemException(ex);
	        }
		}

	}
	
	private void completeCapabilities(PrismObject<ResourceType> resource, boolean forceRefresh, Map<String,Collection<Object>> capabilityMap, Collection<ItemDelta<?, ?>> modifications,
			OperationResult result) throws SchemaException, ObjectNotFoundException, CommunicationException, ConfigurationException {
		ResourceType resourceType = resource.asObjectable();
		ConnectorSpec defaultConnectorSpec = getDefaultConnectorSpec(resource);
		CapabilitiesType resourceCapType = resourceType.getCapabilities();
		if (resourceCapType == null) {
			resourceCapType = new CapabilitiesType();
			resourceType.setCapabilities(resourceCapType);
		}
		completeConnectorCapabilities(defaultConnectorSpec, resourceCapType, ResourceType.F_CAPABILITIES, forceRefresh,
				capabilityMap==null?null:capabilityMap.get(null),
				modifications, result);
		
		for (ConnectorInstanceSpecificationType additionalConnectorType: resource.asObjectable().getAdditionalConnector()) {
			ConnectorSpec connectorSpec = getConnectorSpec(resource, additionalConnectorType);
			CapabilitiesType connectorCapType = additionalConnectorType.getCapabilities();
			if (connectorCapType == null) {
				connectorCapType = new CapabilitiesType();
				additionalConnectorType.setCapabilities(connectorCapType);
			}
			ItemPath itemPath = additionalConnectorType.asPrismContainerValue().getPath().append(ConnectorInstanceSpecificationType.F_CAPABILITIES);
			completeConnectorCapabilities(connectorSpec, connectorCapType, itemPath, forceRefresh, 
					capabilityMap==null?null:capabilityMap.get(additionalConnectorType.getName()),
					modifications, result);
		}
	}
		
	private void completeConnectorCapabilities(ConnectorSpec connectorSpec, CapabilitiesType capType, ItemPath itemPath, boolean forceRefresh,
			Collection<Object> retrievedCapabilities, Collection<ItemDelta<?, ?>> modifications, OperationResult result) 
					throws ObjectNotFoundException, SchemaException, CommunicationException, ConfigurationException {
		
		if (capType.getNative() != null && !capType.getNative().getAny().isEmpty()) {
			if (!forceRefresh) {
				CachingMetadataType cachingMetadata = capType.getCachingMetadata();
				if (cachingMetadata == null) {
					cachingMetadata = MiscSchemaUtil.generateCachingMetadata();
					modifications.add(
							prismContext.deltaFactory().property().createModificationReplaceProperty(
								ItemPath.create(ResourceType.F_CAPABILITIES, CapabilitiesType.F_CACHING_METADATA),
								connectorSpec.getResource().getDefinition(),
								cachingMetadata)
						);
				}
				return;
			}
		}
		
		if (retrievedCapabilities == null) {
			try {
	
				InternalMonitor.recordCount(InternalCounters.CONNECTOR_CAPABILITIES_FETCH_COUNT);
				
				ConnectorInstance connector = connectorManager.getConfiguredConnectorInstance(connectorSpec, false, result);
				retrievedCapabilities = connector.fetchCapabilities(result);
	
			} catch (GenericFrameworkException e) {
				throw new GenericConnectorException("Generic error in connector " + connectorSpec + ": "
						+ e.getMessage(), e);
			}
		}
				
		CapabilityCollectionType nativeCapType = new CapabilityCollectionType();
		capType.setNative(nativeCapType);
		nativeCapType.getAny().addAll(retrievedCapabilities);

		CachingMetadataType cachingMetadata = MiscSchemaUtil.generateCachingMetadata();
		capType.setCachingMetadata(cachingMetadata);
		
		ObjectDelta<ResourceType> capabilitiesReplaceDelta = prismContext.deltaFactory().object()
				.createModificationReplaceContainer(ResourceType.class, connectorSpec.getResource().getOid(),
				itemPath, capType.asPrismContainerValue().clone());
		
		modifications.addAll(capabilitiesReplaceDelta.getModifications());
	}

	private ContainerDelta<XmlSchemaType> createSchemaUpdateDelta(PrismObject<ResourceType> resource, ResourceSchema resourceSchema) throws SchemaException {
		Document xsdDoc = null;
		try {
			xsdDoc = resourceSchema.serializeToXsd();

			if (LOGGER.isTraceEnabled()) {
				LOGGER.trace("Serialized XSD resource schema for {}:\n{}",
						resource, DOMUtil.serializeDOMToString(xsdDoc));
			}

		} catch (SchemaException e) {
			throw new SchemaException("Error processing resource schema for "
					+ resource + ": " + e.getMessage(), e);
		}

		Element xsdElement = DOMUtil.getFirstChildElement(xsdDoc);
		if (xsdElement == null) {
			throw new SchemaException("No schema was generated for " + resource);
		}
		CachingMetadataType cachingMetadata = MiscSchemaUtil.generateCachingMetadata();

		ContainerDelta<XmlSchemaType> schemaContainerDelta = prismContext.deltaFactory().container().createDelta(
				ResourceType.F_SCHEMA, ResourceType.class);
		PrismContainerValue<XmlSchemaType> cval = prismContext.itemFactory().createContainerValue();
		schemaContainerDelta.setValueToReplace(cval);
		PrismProperty<CachingMetadataType> cachingMetadataProperty = cval
				.createProperty(XmlSchemaType.F_CACHING_METADATA);
		cachingMetadataProperty.setRealValue(cachingMetadata);
		List<QName> objectClasses = ResourceTypeUtil.getSchemaGenerationConstraints(resource);
		if (objectClasses != null) {
			PrismProperty<SchemaGenerationConstraintsType> generationConstraints = cval
					.createProperty(XmlSchemaType.F_GENERATION_CONSTRAINTS);
			SchemaGenerationConstraintsType constraints = new SchemaGenerationConstraintsType();
			constraints.getGenerateObjectClass().addAll(objectClasses);
			generationConstraints.setRealValue(constraints);
		}
		PrismProperty<SchemaDefinitionType> definitionProperty = cval.createProperty(XmlSchemaType.F_DEFINITION);
		ObjectTypeUtil.setXsdSchemaDefinition(definitionProperty, xsdElement);
		
		return schemaContainerDelta;
	}

	/**
	 * Apply proper definition (connector schema) to the resource.
	 */
	private void applyConnectorSchemaToResource(PrismObject<ResourceType> resource, Task task, OperationResult result)
			throws SchemaException, ObjectNotFoundException, ExpressionEvaluationException {
		try {
			resource.modifyUnfrozen(() -> {
				try {
					PrismObjectDefinition<ResourceType> newResourceDefinition = resource.getDefinition().clone();
					for (ConnectorSpec connectorSpec : getAllConnectorSpecs(resource)) {
						applyConnectorSchemaToResource(connectorSpec, newResourceDefinition, resource, task, result);
					}
					resource.setDefinition(newResourceDefinition);
				} catch (Exception e) {
					throw new TunnelException(e);
				}
			});
		} catch (TunnelException te) {
			Throwable originalException = te.getCause();
			if (originalException instanceof SchemaException) {
				throw (SchemaException) originalException;
			} else if (originalException instanceof ObjectNotFoundException) {
				throw (ObjectNotFoundException) originalException;
			} else if (originalException instanceof ExpressionEvaluationException) {
				throw (ExpressionEvaluationException) originalException;
			} else if (originalException instanceof RuntimeException) {
				throw (RuntimeException) originalException;
			} else {
				throw new IllegalStateException("Unexpected exception: "+te+": "+te.getMessage(), te);
			}
		}
	}
	
	/**
	 * Apply proper definition (connector schema) to the resource.  
	 */
	private void applyConnectorSchemaToResource(ConnectorSpec connectorSpec, PrismObjectDefinition<ResourceType> resourceDefinition, 
			PrismObject<ResourceType> resource, Task task, OperationResult result)
			throws SchemaException, ObjectNotFoundException, ExpressionEvaluationException, CommunicationException, ConfigurationException, SecurityViolationException {

			ConnectorType connectorType = connectorManager.getConnectorTypeReadOnly(connectorSpec, result);
			PrismSchema connectorSchema = connectorManager.getConnectorSchema(connectorType);
			if (connectorSchema == null) {
				throw new SchemaException("No connector schema in " + connectorType);
			}
			PrismContainerDefinition<ConnectorConfigurationType> configurationContainerDefinition = ConnectorTypeUtil
					.findConfigurationContainerDefinition(connectorType, connectorSchema);
			if (configurationContainerDefinition == null) {
				throw new SchemaException("No configuration container definition in schema of " + connectorType);
			}

			configurationContainerDefinition = configurationContainerDefinition.clone();
			PrismContainer<ConnectorConfigurationType> configurationContainer = connectorSpec.getConnectorConfiguration();
			// We want element name, minOccurs/maxOccurs and similar definition to be taken from the original, not the schema
			// the element is global in the connector schema. therefore it does not have correct maxOccurs
			if (configurationContainer != null) {
				configurationContainerDefinition.adoptElementDefinitionFrom(configurationContainer.getDefinition());
				configurationContainer.applyDefinition(configurationContainerDefinition, true);
				
				try {
					configurationContainer.accept(visitable -> {
						if ((visitable instanceof PrismProperty<?>)) {
							try {
								evaluateExpression((PrismProperty<?>)visitable, resource, task, result);
							} catch (SchemaException | ObjectNotFoundException | ExpressionEvaluationException | CommunicationException | ConfigurationException | SecurityViolationException e) {
								throw new TunnelException(e);
							}
						}
					});
				} catch (TunnelException te) {
					Throwable e = te.getCause();
					if (e instanceof SchemaException) {
						throw (SchemaException)e;
					} else if (e instanceof ObjectNotFoundException) {
						throw (ObjectNotFoundException)e;
					} else if (e instanceof ExpressionEvaluationException) {
						throw (ExpressionEvaluationException)e;
					} else if (e instanceof CommunicationException) {
						throw (CommunicationException)e;
					} else if (e instanceof ConfigurationException) {
						throw (ConfigurationException)e;
					} else if (e instanceof SecurityViolationException) {
						throw (SecurityViolationException)e;
					} else if (e instanceof RuntimeException) {
						throw (RuntimeException)e;
					} else if (e instanceof Error) {
						throw (Error)e;
					} else {
						throw new SystemException(e);
					}
				}
				
			} else {
				configurationContainerDefinition.adoptElementDefinitionFrom(
						resourceDefinition.findContainerDefinition(ResourceType.F_CONNECTOR_CONFIGURATION));
			}

			if (connectorSpec.getConnectorName() == null) {
				// Default connector, for compatibility
				// It does not make sense to update this for any other connectors.
				// We cannot have one definition for addiitionalConnector[1]/connectorConfiguraiton and
				// different definition for addiitionalConnector[2]/connectorConfiguraiton in the object definition.
				// The way to go is to set up definitions on the container level.
				resourceDefinition.replaceDefinition(ResourceType.F_CONNECTOR_CONFIGURATION, configurationContainerDefinition);
			}
			
	}

	private <T> void evaluateExpression(PrismProperty<T> configurationProperty, PrismObject<ResourceType> resource, Task task, OperationResult result) throws SchemaException, ObjectNotFoundException, ExpressionEvaluationException, CommunicationException, ConfigurationException, SecurityViolationException {
		PrismPropertyDefinition<T> propDef = configurationProperty.getDefinition();
		String shortDesc = "connector configuration property "+configurationProperty+" in "+resource;
		List<PrismPropertyValue<T>> extraValues = new ArrayList<>();
		for (PrismPropertyValue<T> configurationPropertyValue: configurationProperty.getValues()) {
			ExpressionWrapper expressionWrapper = configurationPropertyValue.getExpression();
			if (expressionWrapper == null) {
				return;
			}
			Object expressionObject = expressionWrapper.getExpression();
			if (!(expressionObject instanceof ExpressionType)) {
				throw new IllegalStateException("Expected that expression in "+configurationPropertyValue+" will be ExpressionType, but it was "+expressionObject);
			}
			ExpressionType expressionType = (ExpressionType) expressionWrapper.getExpression();

			Expression<PrismPropertyValue<T>, PrismPropertyDefinition<T>> expression = expressionFactory.makeExpression(expressionType, propDef, MiscSchemaUtil.getExpressionProfile(), shortDesc, task, result);
			ExpressionVariables variables = new ExpressionVariables();
			
			// TODO: populate variables
			
			ExpressionEvaluationContext expressionContext = new ExpressionEvaluationContext(null, variables, shortDesc, task, result);
			PrismValueDeltaSetTriple<PrismPropertyValue<T>> expressionOutputTriple = expression.evaluate(expressionContext);
			Collection<PrismPropertyValue<T>> expressionOutputValues = expressionOutputTriple.getNonNegativeValues();
			if (expressionOutputValues != null && !expressionOutputValues.isEmpty()) {
				Iterator<PrismPropertyValue<T>> iterator = expressionOutputValues.iterator();
				PrismPropertyValue<T> firstValue = iterator.next();
				configurationPropertyValue.setValue(firstValue.getValue());
				while (iterator.hasNext()) {
					extraValues.add(iterator.next());
				}
			}
		}
		for (PrismPropertyValue<T> extraValue: extraValues) {
			configurationProperty.add(extraValue);
		}
	}

	private ResourceSchema fetchResourceSchema(PrismObject<ResourceType> resource, Map<String,Collection<Object>> capabilityMap, Task task, OperationResult parentResult) 
			throws CommunicationException, GenericFrameworkException, ConfigurationException, ObjectNotFoundException, SchemaException {
		ConnectorSpec connectorSpec = selectConnectorSpec(resource, capabilityMap, SchemaCapabilityType.class);
		if (connectorSpec == null) {
			LOGGER.debug("No connector has schema capability, cannot fetch resource schema");
			return null;
		}
		InternalMonitor.recordCount(InternalCounters.RESOURCE_SCHEMA_FETCH_COUNT);
		List<QName> generateObjectClasses = ResourceTypeUtil.getSchemaGenerationConstraints(resource);
		ConnectorInstance connectorInstance = connectorManager.getConfiguredConnectorInstance(connectorSpec, false, parentResult);
		
		LOGGER.debug("Trying to get schema from {}, objectClasses to generate: {}", connectorSpec, generateObjectClasses);
		ResourceSchema resourceSchema = connectorInstance.fetchResourceSchema(parentResult);
		
		if (ResourceTypeUtil.isValidateSchema(resource.asObjectable())) {
			ResourceTypeUtil.validateSchema(resourceSchema, resource);
		}
		return resourceSchema;
		
	}
	
	public void testConnection(PrismObject<ResourceType> resource, Task task, OperationResult parentResult) {

		List<ConnectorSpec> allConnectorSpecs;
		try {
			allConnectorSpecs = getAllConnectorSpecs(resource);
		} catch (SchemaException e) {
			modifyResourceAvailabilityStatus(resource, AvailabilityStatusType.BROKEN, parentResult);
			parentResult.recordFatalError("Configuration error: " + e.getMessage(), e);
			return;
		}
		
		Map<String,Collection<Object>> capabilityMap = new HashMap<>();
		for (ConnectorSpec connectorSpec: allConnectorSpecs) {
			
			OperationResult connectorTestResult = parentResult
					.createSubresult(ConnectorTestOperation.CONNECTOR_TEST.getOperation());
			connectorTestResult.addParam(OperationResult.PARAM_NAME, connectorSpec.getConnectorName());
			connectorTestResult.addParam(OperationResult.PARAM_OID, connectorSpec.getConnectorOid());
			
			testConnectionConnector(connectorSpec, capabilityMap, task, connectorTestResult);
			
			connectorTestResult.computeStatus();
			
			if (!connectorTestResult.isAcceptable()) {
				//nothing more to do.. if it failed while testing connection, status is set. 
				// we do not need to continue and waste the time.
				return;
			}
		}

		// === test SCHEMA ===

		OperationResult schemaResult = parentResult.createSubresult(ConnectorTestOperation.RESOURCE_SCHEMA
				.getOperation());

		ResourceSchema schema = null;
		try {

			schema = fetchResourceSchema(resource, capabilityMap, task, schemaResult);
			
		} catch (CommunicationException e) {
			modifyResourceAvailabilityStatus(resource, AvailabilityStatusType.BROKEN, parentResult);
			schemaResult.recordFatalError("Communication error: " + e.getMessage(), e);
			return;
		} catch (GenericFrameworkException e) {
			modifyResourceAvailabilityStatus(resource, AvailabilityStatusType.BROKEN, parentResult);
			schemaResult.recordFatalError("Generic error: " + e.getMessage(), e);
			return;
		} catch (ConfigurationException e) {
			modifyResourceAvailabilityStatus(resource, AvailabilityStatusType.BROKEN, parentResult);
			schemaResult.recordFatalError("Configuration error: " + e.getMessage(), e);
			return;
		} catch (ObjectNotFoundException e) {
			modifyResourceAvailabilityStatus(resource, AvailabilityStatusType.BROKEN, parentResult);
			schemaResult.recordFatalError("Configuration error: " + e.getMessage(), e);
			return;
		} catch (SchemaException e) {
			modifyResourceAvailabilityStatus(resource, AvailabilityStatusType.BROKEN, parentResult);
			schemaResult.recordFatalError("Configuration error: " + e.getMessage(), e);
			return;
		}

		if (schema == null || schema.isEmpty()) {
			// Resource does not support schema
			// If there is a static schema in resource definition this may still be OK
			try {
				schema = RefinedResourceSchemaImpl.getResourceSchema(resource, prismContext);
			} catch (SchemaException e) {
				modifyResourceAvailabilityStatus(resource, AvailabilityStatusType.BROKEN, parentResult);
				schemaResult.recordFatalError(e);
				return;
			}
			
			if (schema == null || schema.isEmpty()) {
				modifyResourceAvailabilityStatus(resource, AvailabilityStatusType.BROKEN, parentResult);
				schemaResult.recordFatalError("Connector does not support schema and no static schema available");
				return;
			}
		}

		// Invoke completeResource(). This will store the fetched schema to the
		// ResourceType
		// if there is no <schema> definition already. Therefore the
		// testResource() can be used to
		// generate the resource schema - until we have full schema caching
		// capability.
		try {
			resource = completeResource(resource, schema, true, capabilityMap, null, task, schemaResult);
		} catch (ObjectNotFoundException e) {
			modifyResourceAvailabilityStatus(resource, AvailabilityStatusType.BROKEN, parentResult);
			schemaResult.recordFatalError(
					"Object not found (unexpected error, probably a bug): " + e.getMessage(), e);
			return;
		} catch (SchemaException e) {
			modifyResourceAvailabilityStatus(resource, AvailabilityStatusType.BROKEN, parentResult);
			schemaResult.recordFatalError(
					"Schema processing error (probably connector bug): " + e.getMessage(), e);
			return;
		} catch (CommunicationException e) {
			modifyResourceAvailabilityStatus(resource, AvailabilityStatusType.BROKEN, parentResult);
			schemaResult.recordFatalError("Communication error: " + e.getMessage(), e);
			return;
		} catch (ConfigurationException e) {
			modifyResourceAvailabilityStatus(resource, AvailabilityStatusType.BROKEN, parentResult);
			schemaResult.recordFatalError("Configuration error: " + e.getMessage(), e);
			return;
		} catch (ExpressionEvaluationException e) {
			modifyResourceAvailabilityStatus(resource, AvailabilityStatusType.BROKEN, parentResult);
			schemaResult.recordFatalError("Expression error: " + e.getMessage(), e);
			return;
		}

		schemaResult.recordSuccess();

		try {
			updateResourceSchema(allConnectorSpecs, parentResult, resource);
		} catch (SchemaException | ObjectNotFoundException | CommunicationException | ConfigurationException | RuntimeException e) {
			modifyResourceAvailabilityStatus(resource, AvailabilityStatusType.BROKEN, parentResult);
			parentResult.recordFatalError("Couldn't update resource schema: " + e.getMessage(), e);
			return;
		}

		// TODO: connector sanity (e.g. refined schema, at least one account type, identifiers
		// in schema, etc.)

	}

	private void updateResourceSchema(List<ConnectorSpec> allConnectorSpecs, OperationResult parentResult,
			PrismObject<ResourceType> resource)
			throws SchemaException, ObjectNotFoundException, CommunicationException, ConfigurationException {
		ResourceSchema resourceSchema = RefinedResourceSchemaImpl.getResourceSchema(resource, prismContext);
		if (resourceSchema != null) {
			for (ConnectorSpec connectorSpec : allConnectorSpecs) {
				ConnectorInstance instance = connectorManager.getConfiguredConnectorInstance(connectorSpec, false, parentResult);
				instance.updateSchema(resourceSchema);
			}
		}
	}

	public void testConnectionConnector(ConnectorSpec connectorSpec, Map<String,Collection<Object>> capabilityMap, Task task, OperationResult parentResult) {

		// === test INITIALIZATION ===

		OperationResult initResult = parentResult
				.createSubresult(ConnectorTestOperation.CONNECTOR_INITIALIZATION.getOperation());
		
		LOGGER.debug("Testing connection using {}", connectorSpec);
		
		ConfiguredConnectorInstanceEntry connectorInstanceCacheEntry;
		try {
			// Make sure we are getting non-configured instance.
			connectorInstanceCacheEntry = connectorManager.getConnectorInstanceCacheEntry(connectorSpec, initResult);
			initResult.recordSuccess();
		} catch (ObjectNotFoundException e) {
			// The connector was not found. The resource definition is either
			// wrong or the connector is not installed.
			modifyResourceAvailabilityStatus(connectorSpec.getResource(), AvailabilityStatusType.BROKEN, parentResult);
			initResult.recordFatalError("The connector was not found: "+e.getMessage(), e);
			return;
		} catch (SchemaException e) {
			modifyResourceAvailabilityStatus(connectorSpec.getResource(), AvailabilityStatusType.BROKEN, parentResult);
			initResult.recordFatalError("Schema error while dealing with the connector definition: "+e.getMessage(), e);
			return;
		} catch (RuntimeException | Error e) {
			modifyResourceAvailabilityStatus(connectorSpec.getResource(), AvailabilityStatusType.BROKEN, parentResult);
			initResult.recordFatalError("Unexpected runtime error: "+e.getMessage(), e);
			return;
		} catch (CommunicationException e) {
			modifyResourceAvailabilityStatus(connectorSpec.getResource(), AvailabilityStatusType.BROKEN, parentResult);
			initResult.recordFatalError("Communication error: "+e.getMessage(), e);
			return;
		} catch (ConfigurationException e) {
			modifyResourceAvailabilityStatus(connectorSpec.getResource(), AvailabilityStatusType.BROKEN, parentResult);
			initResult.recordFatalError("Configuration error: "+e.getMessage(), e);
			return;
		}
		
		ConnectorInstance connector = connectorInstanceCacheEntry.getConnectorInstance();
		

		// === test CONFIGURATION ===

		OperationResult configResult = parentResult
				.createSubresult(ConnectorTestOperation.CONNECTOR_CONFIGURATION.getOperation());

		try {
			PrismObject<ResourceType> resource = connectorSpec.getResource();
			PrismObjectDefinition<ResourceType> newResourceDefinition = resource.getDefinition().clone();
			applyConnectorSchemaToResource(connectorSpec, newResourceDefinition, resource, task, configResult);
			PrismContainerValue<ConnectorConfigurationType> connectorConfiguration = connectorSpec.getConnectorConfiguration().getValue();
			
			InternalMonitor.recordCount(InternalCounters.CONNECTOR_INSTANCE_CONFIGURATION_COUNT);
			
			connector.configure(connectorConfiguration, ResourceTypeUtil.getSchemaGenerationConstraints(resource), configResult);
		
			// We need to explicitly initialize the instance, e.g. in case that the schema and capabilities
			// cannot be detected by the connector and therefore are provided in the resource
			//
			// NOTE: the capabilities and schema that are used here are NOT necessarily those that are detected by the resource.
			//       The detected schema will come later. The schema here is the one that is stored in the resource
			//       definition (ResourceType). This may be schema that was detected previously. But it may also be a schema
			//       that was manually defined. This is needed to be passed to the connector in case that the connector
			//       cannot detect the schema and needs schema/capabilities definition to establish a connection.
			//       Most connectors will just ignore the schema and capabilities that are provided here.
			//       But some connectors may need it (e.g. CSV connector working with CSV file without a header).
			//
			ResourceSchema previousResourceSchema = RefinedResourceSchemaImpl.getResourceSchema(connectorSpec.getResource(), prismContext);
			Collection<Object> previousCapabilities = ResourceTypeUtil.getNativeCapabilitiesCollection(connectorSpec.getResource().asObjectable());
			connector.initialize(previousResourceSchema, previousCapabilities, 
					ResourceTypeUtil.isCaseIgnoreAttributeNames(connectorSpec.getResource().asObjectable()), configResult);
			
			configResult.recordSuccess();
		} catch (CommunicationException e) {
			modifyResourceAvailabilityStatus(connectorSpec.getResource(), AvailabilityStatusType.BROKEN, parentResult);
			configResult.recordFatalError("Communication error", e);
			return;
		} catch (GenericFrameworkException e) {
			modifyResourceAvailabilityStatus(connectorSpec.getResource(), AvailabilityStatusType.BROKEN, parentResult);
			configResult.recordFatalError("Generic error", e);
			return;
		} catch (SchemaException e) {
			modifyResourceAvailabilityStatus(connectorSpec.getResource(), AvailabilityStatusType.BROKEN, parentResult);
			configResult.recordFatalError("Schema error", e);
			return;
		} catch (ConfigurationException e) {
			modifyResourceAvailabilityStatus(connectorSpec.getResource(), AvailabilityStatusType.BROKEN, parentResult);
			configResult.recordFatalError("Configuration error", e);
			return;
		} catch (ObjectNotFoundException e) {
			modifyResourceAvailabilityStatus(connectorSpec.getResource(), AvailabilityStatusType.BROKEN, parentResult);
			configResult.recordFatalError("Object not found", e);
			return;
		} catch (ExpressionEvaluationException e) {
			modifyResourceAvailabilityStatus(connectorSpec.getResource(), AvailabilityStatusType.BROKEN, parentResult);
			configResult.recordFatalError("Expression error", e);
			return;
		} catch (SecurityViolationException e) {
			modifyResourceAvailabilityStatus(connectorSpec.getResource(), AvailabilityStatusType.BROKEN, parentResult);
			configResult.recordFatalError("Security violation", e);
			return;
		} catch (RuntimeException | Error e) {
			modifyResourceAvailabilityStatus(connectorSpec.getResource(), AvailabilityStatusType.BROKEN, parentResult);
			configResult.recordFatalError("Unexpected runtime error", e);
			return;
		}
		
		// === test CONNECTION ===

		// delegate the main part of the test to the connector
		connector.test(parentResult);

		parentResult.computeStatus();
		if (!parentResult.isAcceptable()) {
			modifyResourceAvailabilityStatus(connectorSpec.getResource(), AvailabilityStatusType.DOWN, parentResult);
			// No point in going on. Following tests will fail anyway, they will
			// just produce misleading
			// messages.
			return;
		} else {
			modifyResourceAvailabilityStatus(connectorSpec.getResource(), AvailabilityStatusType.UP, parentResult);
		}
		
		// === test CAPABILITIES ===
		
		OperationResult capabilitiesResult = parentResult
				.createSubresult(ConnectorTestOperation.CONNECTOR_CAPABILITIES.getOperation());
		try {
			InternalMonitor.recordCount(InternalCounters.CONNECTOR_CAPABILITIES_FETCH_COUNT);
			Collection<Object> retrievedCapabilities = connector.fetchCapabilities(capabilitiesResult);
			
			capabilityMap.put(connectorSpec.getConnectorName(), retrievedCapabilities);
			capabilitiesResult.recordSuccess();
		} catch (CommunicationException e) {
			modifyResourceAvailabilityStatus(connectorSpec.getResource(), AvailabilityStatusType.BROKEN, parentResult);
			capabilitiesResult.recordFatalError("Communication error", e);
			return;
		} catch (GenericFrameworkException e) {
			modifyResourceAvailabilityStatus(connectorSpec.getResource(), AvailabilityStatusType.BROKEN, parentResult);
			capabilitiesResult.recordFatalError("Generic error", e);
			return;
		} catch (ConfigurationException e) {
			modifyResourceAvailabilityStatus(connectorSpec.getResource(), AvailabilityStatusType.BROKEN, parentResult);
			capabilitiesResult.recordFatalError("Configuration error", e);
			return;
		} catch (SchemaException e) {
			modifyResourceAvailabilityStatus(connectorSpec.getResource(), AvailabilityStatusType.BROKEN, parentResult);
			capabilitiesResult.recordFatalError("Schema error", e);
			return;
		} catch (RuntimeException | Error e) {
			modifyResourceAvailabilityStatus(connectorSpec.getResource(), AvailabilityStatusType.BROKEN, parentResult);
			capabilitiesResult.recordFatalError("Unexpected runtime error", e);
			return;
		}
		
		// Connector instance is fully configured at this point.
		// But the connector cache entry may not be set up properly and it is not yet placed into the cache.
		// Therefore make sure the caching bit is completed.
		// Place the connector to cache even if it was configured at the beginning. The connector is reconfigured now.
		connectorManager.cacheConfiguredConnector(connectorInstanceCacheEntry, connectorSpec);
	}
	
	public void modifyResourceAvailabilityStatus(PrismObject<ResourceType> resource, AvailabilityStatusType newStatus, OperationResult result){
			ResourceType resourceType = resource.asObjectable();
			
			synchronized (resource) {
				AvailabilityStatusType currentStatus = ResourceTypeUtil.getLastAvailabilityStatus(resourceType);
				if (!newStatus.equals(currentStatus)) {
					LOGGER.debug("Changing availability status of {}: {} -> {}", resource, currentStatus, newStatus);
					List<PropertyDelta<?>> modifications = new ArrayList<>();
					PropertyDelta<?> statusDelta = createResourceAvailabilityStatusDelta(resource, newStatus);
					modifications.add(statusDelta);
					
					try {
						repositoryService.modifyObject(ResourceType.class, resourceType.getOid(), modifications, result);
						InternalMonitor.recordCount(InternalCounters.RESOURCE_REPOSITORY_MODIFY_COUNT);
					} catch(SchemaException | ObjectAlreadyExistsException | ObjectNotFoundException ex) {
						throw new SystemException(ex);
					}
				}
				resource.modifyUnfrozen(() -> {
					if (resourceType.getOperationalState() == null) {
						OperationalStateType operationalState = new OperationalStateType();
						operationalState.setLastAvailabilityStatus(newStatus);
						resourceType.setOperationalState(operationalState);
					} else {
						resourceType.getOperationalState().setLastAvailabilityStatus(newStatus);
					}
				});
			}
		}
	
	private PropertyDelta<AvailabilityStatusType> createResourceAvailabilityStatusDelta(PrismObject<ResourceType> resource, AvailabilityStatusType status) {
		return prismContext.deltaFactory().property().createModificationReplaceProperty(SchemaConstants.PATH_OPERATIONAL_STATE_LAST_AVAILABILITY_STATUS, resource.getDefinition(), status);
	}

	/**
	 * Adjust scheme with respect to capabilities. E.g. disable attributes that
	 * are used for special purpose (such as account activation simulation).
	 *
	 * TODO treat also objectclass-specific capabilities here
	 */
	private void adjustSchemaForSimulatedCapabilities(PrismObject<ResourceType> resource, ResourceSchema resourceSchema) {
		ResourceType resourceType = resource.asObjectable();
		if (resourceType.getCapabilities() == null || resourceType.getCapabilities().getConfigured() == null) {
			return;
		}
		ActivationCapabilityType activationCapability = CapabilityUtil.getCapability(resourceType
				.getCapabilities().getConfigured().getAny(), ActivationCapabilityType.class);
		if (CapabilityUtil.getEffectiveActivationStatus(activationCapability) != null) {
			QName attributeName = activationCapability.getStatus().getAttribute();
			Boolean ignore = activationCapability.getStatus().isIgnoreAttribute();
			if (attributeName != null) {
				// The attribute used for enable/disable simulation should be ignored in the schema
				// otherwise strange things may happen, such as changing the same attribute both from
				// activation/enable and from the attribute using its native name.
				for (ObjectClassComplexTypeDefinition objectClassDefinition : resourceSchema
						.getDefinitions(ObjectClassComplexTypeDefinition.class)) {
					ResourceAttributeDefinition attributeDefinition = objectClassDefinition
							.findAttributeDefinition(attributeName);
					if (attributeDefinition != null) {
						if (ignore == null || ignore.booleanValue()) {
							((MutableItemDefinition) attributeDefinition).setProcessing(ItemProcessing.IGNORE);
						}
					} else {
						// simulated activation attribute points to something that is not in the schema
						// technically, this is an error. But it looks to be quite common in connectors.
						// The enable/disable is using operational attributes that are not exposed in the
						// schema, but they work if passed to the connector.
						// Therefore we don't want to break anything. We could log an warning here, but the
						// warning would be quite frequent. Maybe a better place to warn user would be import
						// of the object.
						LOGGER.debug("Simulated activation attribute "
								+ attributeName
								+ " for objectclass "
								+ objectClassDefinition.getTypeName()
								+ " in "
								+ resource
								+ " does not exist in the resource schema. This may work well, but it is not clean. Connector exposing such schema should be fixed.");
					}
				}
			}
		}
	}

	private void checkSchema(PrismSchema schema) throws SchemaException {
		// This is resource schema, it should contain only
		// ResourceObjectDefinitions
		for (Definition def : schema.getDefinitions()) {
			if (def instanceof ComplexTypeDefinition) {
				// This is OK
			} else if (def instanceof ResourceAttributeContainerDefinition) {
				checkResourceObjectDefinition((ResourceAttributeContainerDefinition) def);
			} else {
				throw new SchemaException("Unexpected definition in resource schema: " + def);
			}
		}
	}

	private void checkResourceObjectDefinition(ResourceAttributeContainerDefinition rod)
			throws SchemaException {
		for (ItemDefinition def : rod.getDefinitions()) {
			if (!(def instanceof ResourceAttributeDefinition)) {
				throw new SchemaException("Unexpected definition in resource schema object " + rod + ": "
						+ def);
			}
		}
	}

	public void applyDefinition(ObjectDelta<ResourceType> delta, ResourceType resourceWhenNoOid, GetOperationOptions options, Task task, OperationResult objectResult) throws SchemaException, ObjectNotFoundException, CommunicationException, ConfigurationException, ExpressionEvaluationException {
		
		if (delta.isAdd()) {
			PrismObject<ResourceType> resource = delta.getObjectToAdd();
			applyConnectorSchemaToResource(resource, task, objectResult);
			return;
			
		} else if (delta.isModify()) {
			// Go on
		} else {
			return;
		}

		if (delta.hasCompleteDefinition()){
			//nothing to do, all modifications has definitions..just aplly this deltas..
			return;
		}
		
	
        PrismObject<ResourceType> resource;
		String resourceOid = delta.getOid();
        if (resourceOid == null) {
            Validate.notNull(resourceWhenNoOid, "Resource oid not specified in the object delta, and resource is not specified as well. Could not apply definition.");
            resource = resourceWhenNoOid.asPrismObject();
        } else {
		    resource = getResource(resourceOid, options, task, objectResult);
        }

        ResourceType resourceType = resource.asObjectable();
//		ResourceType resourceType = completeResource(resource.asObjectable(), null, objectResult);
		//TODO TODO TODO FIXME FIXME FIXME copied from ObjectImprted..union this two cases
		PrismContainer<ConnectorConfigurationType> configurationContainer = ResourceTypeUtil.getConfigurationContainer(resourceType);
        if (configurationContainer == null || configurationContainer.isEmpty()) {
            // Nothing to check
            objectResult.recordWarning("The resource has no configuration");
            return;
        }

        // Check the resource configuration. The schema is in connector, so fetch the connector first
        String connectorOid = resourceType.getConnectorRef().getOid();
        if (StringUtils.isBlank(connectorOid)) {
            objectResult.recordFatalError("The connector reference (connectorRef) is null or empty");
            return;
        }
        
        //ItemDelta.findItemDelta(delta.getModifications(), ResourceType.F_SCHEMA, ContainerDelta.class) == null || 
       
        ReferenceDelta connectorRefDelta = ItemDeltaCollectionsUtil.findReferenceModification(delta.getModifications(), ResourceType.F_CONNECTOR_REF);
        if (connectorRefDelta != null){
        	Item<PrismReferenceValue,PrismReferenceDefinition> connectorRefNew = connectorRefDelta.getItemNewMatchingPath(null);
        	if (connectorRefNew.getValues().size() == 1){
        		PrismReferenceValue connectorRefValue = connectorRefNew.getValues().iterator().next();
        		if (connectorRefValue.getOid() != null && !connectorOid.equals(connectorRefValue.getOid())){
        			connectorOid = connectorRefValue.getOid();
        		}
        	}
        }

        PrismObject<ConnectorType> connector = null;
        ConnectorType connectorType = null;
        try {
            connector = repositoryService.getObject(ConnectorType.class, connectorOid, null, objectResult);
            connectorType = connector.asObjectable();
        } catch (ObjectNotFoundException e) {
            // No connector, no fun. We can't check the schema. But this is referential integrity problem.
            // Mark the error ... there is nothing more to do
            objectResult.recordFatalError("Connector (OID:" + connectorOid + ") referenced from the resource is not in the repository", e);
            return;
        } catch (SchemaException e) {
            // Probably a malformed connector. To be kind of robust, lets allow the import.
            // Mark the error ... there is nothing more to do
            objectResult.recordPartialError("Connector (OID:" + connectorOid + ") referenced from the resource has schema problems: " + e.getMessage(), e);
            LOGGER.error("Connector (OID:{}) referenced from the imported resource \"{}\" has schema problems: {}-{}",
				new Object[]{connectorOid, resourceType.getName(), e.getMessage(), e});
            return;
        }
        
        Element connectorSchemaElement = ConnectorTypeUtil.getConnectorXsdSchema(connector);
        MutablePrismSchema connectorSchema = null;
        if (connectorSchemaElement == null) {
        	// No schema to validate with
        	return;
        }
		try {
			connectorSchema = prismContext.schemaFactory().createPrismSchema();
			connectorSchema.parseThis(connectorSchemaElement, true, "schema for " + connector, prismContext);
		} catch (SchemaException e) {
			objectResult.recordFatalError("Error parsing connector schema for " + connector + ": "+e.getMessage(), e);
			return;
		}
        QName configContainerQName = new QName(connectorType.getNamespace(), ResourceType.F_CONNECTOR_CONFIGURATION.getLocalPart());
		PrismContainerDefinition<ConnectorConfigurationType> configContainerDef =
				connectorSchema.findContainerDefinitionByElementName(configContainerQName);
		if (configContainerDef == null) {
			objectResult.recordFatalError("Definition of configuration container " + configContainerQName + " not found in the schema of of " + connector);
            return;
		}
        
        try {
			configurationContainer.applyDefinition(configContainerDef);
		} catch (SchemaException e) {
			objectResult.recordFatalError("Configuration error in " + resource + ": "+e.getMessage(), e);
            return;
		}

		PrismContainer configContainer = resourceType.asPrismObject().findContainer(ResourceType.F_CONNECTOR_CONFIGURATION);
		configContainer.applyDefinition(configContainerDef);
 
        for (ItemDelta<?,?> itemDelta : delta.getModifications()){
        	applyItemDefinition(itemDelta, configContainerDef, objectResult);
        }
	}
	
	private <V extends PrismValue, D extends ItemDefinition> void applyItemDefinition(ItemDelta<V,D> itemDelta, 
			PrismContainerDefinition<ConnectorConfigurationType> configContainerDef, OperationResult objectResult) throws SchemaException {
		if (itemDelta.getParentPath() == null){
    		LOGGER.trace("No parent path defined for item delta {}", itemDelta);
    		return;
    	}
    	
    	QName first = itemDelta.getParentPath().firstToNameOrNull();
    	if (first == null) {
    		return;
    	}
    	
    	if (itemDelta.getDefinition() == null && (ResourceType.F_CONNECTOR_CONFIGURATION.equals(first) || ResourceType.F_SCHEMA.equals(first))){
    		ItemPath path = itemDelta.getPath().rest();
    		D itemDef = configContainerDef.findItemDefinition(path);
    		if (itemDef == null){
    			LOGGER.warn("No definition found for item {}. Check your namespaces?", path);
    			objectResult.recordWarning("No definition found for item delta: " + itemDelta +". Check your namespaces?" );
//    			throw new SchemaException("No definition found for item " + path+ ". Check your namespaces?" );
    			return;
    		}
			itemDelta.applyDefinition(itemDef);
    		
    	}
	}
	
	public void applyDefinition(PrismObject<ResourceType> resource, Task task, OperationResult parentResult) throws ObjectNotFoundException, SchemaException, CommunicationException, ConfigurationException, ExpressionEvaluationException {
		applyConnectorSchemaToResource(resource, task, parentResult);
	}

	public void applyDefinition(ObjectQuery query, OperationResult result) {
		// TODO: not implemented yet
	}

	public Object executeScript(String resourceOid, ProvisioningScriptType script, Task task, OperationResult result) throws ObjectNotFoundException, SchemaException, CommunicationException, ConfigurationException, SecurityViolationException, ExpressionEvaluationException {
		PrismObject<ResourceType> resource = getResource(resourceOid, null, task, result);
		ConnectorSpec connectorSpec = selectConnectorSpec(resource, ScriptCapabilityType.class);
		if (connectorSpec == null) {
			throw new UnsupportedOperationException("No connector supports script capability");
		}
		ConnectorInstance connectorInstance = connectorManager.getConfiguredConnectorInstance(connectorSpec, false, result);
		ExecuteProvisioningScriptOperation scriptOperation = ProvisioningUtil.convertToScriptOperation(script, "script on "+resource, prismContext);
		try {
			StateReporter reporter = new StateReporter(resourceOid, task);
			return connectorInstance.executeScript(scriptOperation, reporter, result);
		} catch (GenericFrameworkException e) {
			// Not expected. Transform to system exception
			result.recordFatalError("Generic provisioning framework error", e);
			throw new SystemException("Generic provisioning framework error: " + e.getMessage(), e);
		}
	}
	
	public List<ConnectorOperationalStatus> getConnectorOperationalStatus(PrismObject<ResourceType> resource, OperationResult result) throws ObjectNotFoundException, SchemaException, CommunicationException, ConfigurationException {
		List<ConnectorOperationalStatus> statuses = new ArrayList<>();
		for (ConnectorSpec connectorSpec: getAllConnectorSpecs(resource)) {
			ConnectorInstance connectorInstance = connectorManager.getConfiguredConnectorInstance(connectorSpec, false, result);
			ConnectorOperationalStatus operationalStatus = connectorInstance.getOperationalStatus();
			if (operationalStatus != null) {
				operationalStatus.setConnectorName(connectorSpec.getConnectorName());
				statuses.add(operationalStatus);
			}
		}
		return statuses;
	}
	
	private List<ConnectorSpec> getAllConnectorSpecs(PrismObject<ResourceType> resource) throws SchemaException {
		List<ConnectorSpec> connectorSpecs = new ArrayList<>();
		connectorSpecs.add(getDefaultConnectorSpec(resource));
		for (ConnectorInstanceSpecificationType additionalConnectorType: resource.asObjectable().getAdditionalConnector()) {
			connectorSpecs.add(getConnectorSpec(resource, additionalConnectorType));
		}
		return connectorSpecs;
	}
	
	// Should be used only internally (private). But it is public, because it is accessed from the tests.
	public <T extends CapabilityType> ConnectorInstance getConfiguredConnectorInstance(PrismObject<ResourceType> resource,
			Class<T> operationCapabilityClass, boolean forceFresh, OperationResult parentResult) throws SchemaException, ObjectNotFoundException, CommunicationException, ConfigurationException {
		ConnectorSpec connectorSpec = selectConnectorSpec(resource, operationCapabilityClass);
		if (connectorSpec == null) {
			return null;
		}
		return connectorManager.getConfiguredConnectorInstance(connectorSpec, forceFresh, parentResult);
	}
	
	// Used by the tests. Does not change anything.
	public <T extends CapabilityType> ConnectorInstance getConfiguredConnectorInstanceFromCache(PrismObject<ResourceType> resource,
			Class<T> operationCapabilityClass, boolean forceFresh, OperationResult parentResult) throws SchemaException, ObjectNotFoundException, CommunicationException, ConfigurationException {
		ConnectorSpec connectorSpec = selectConnectorSpec(resource, operationCapabilityClass);
		if (connectorSpec == null) {
			return null;
		}
		return connectorManager.getConfiguredConnectorInstanceFromCache(connectorSpec, forceFresh, parentResult);
	}
	
	public <T extends CapabilityType> CapabilitiesType getConnectorCapabilities(PrismObject<ResourceType> resource,
																				RefinedObjectClassDefinition objectClassDefinition, Class<T> operationCapabilityClass) {
		CapabilitiesType connectorCapabilities = null;
		for (ConnectorInstanceSpecificationType additionalConnectorType: resource.asObjectable().getAdditionalConnector()) {
			if (supportsCapability(additionalConnectorType, operationCapabilityClass)) {
				connectorCapabilities = additionalConnectorType.getCapabilities();
			}
		}

		if (connectorCapabilities == null) {
			connectorCapabilities = resource.asObjectable().getCapabilities();
		}

		return applyObjectClassCapabilities(connectorCapabilities, objectClassDefinition);

	}

	private CapabilitiesType applyObjectClassCapabilities(CapabilitiesType connectorCapabilities, RefinedObjectClassDefinition objectClassDefinition) {
		if (connectorCapabilities == null) {
			return null;
		}

		CapabilitiesType finalCapabilities = new CapabilitiesType();
		finalCapabilities.setNative(connectorCapabilities.getNative());

		CapabilitiesType objectClassCapabilities = objectClassDefinition.getCapabilities();

		if (objectClassCapabilities == null) {
			finalCapabilities.setConfigured(connectorCapabilities.getConfigured());
			return finalCapabilities;
		}

		CapabilityCollectionType configured = objectClassCapabilities.getConfigured();
		if (!hasConfiguredCapabilities(connectorCapabilities)) {
			finalCapabilities.setConfigured(configured);
			return finalCapabilities;
		}

		for (Object capability : connectorCapabilities.getConfigured().getAny()) {

			if (!CapabilityUtil.containsCapabilityWithSameElementName(configured.getAny(), capability)) {
				configured.getAny().add(capability);
			}
		}

		finalCapabilities.setConfigured(configured);
		return finalCapabilities;
	}

	private boolean hasConfiguredCapabilities(CapabilitiesType supportedCapabilities) {
		if (supportedCapabilities.getConfigured() == null) {
			return false;
		}

		if (supportedCapabilities.getConfigured().getAny().isEmpty()) {
			return false;

		}
		return true;
	}
	
	private <T extends CapabilityType> ConnectorSpec selectConnectorSpec(PrismObject<ResourceType> resource, Map<String,Collection<Object>> capabilityMap, Class<T> capabilityClass) throws SchemaException {
		if (capabilityMap == null) {
			return selectConnectorSpec(resource, capabilityClass);
		}
		for (ConnectorInstanceSpecificationType additionalConnectorType: resource.asObjectable().getAdditionalConnector()) {
			if (supportsCapability(additionalConnectorType, capabilityMap.get(additionalConnectorType.getName()), capabilityClass)) {
				return getConnectorSpec(resource, additionalConnectorType);
			}
		}
		return getDefaultConnectorSpec(resource);
	}
	
	private <T extends CapabilityType> ConnectorSpec selectConnectorSpec(PrismObject<ResourceType> resource, Class<T> operationCapabilityClass) throws SchemaException {
		for (ConnectorInstanceSpecificationType additionalConnectorType: resource.asObjectable().getAdditionalConnector()) {
			if (supportsCapability(additionalConnectorType, operationCapabilityClass)) {
				return getConnectorSpec(resource, additionalConnectorType);
			}
		}
		return getDefaultConnectorSpec(resource);
	}
	
	private <T extends CapabilityType> boolean supportsCapability(ConnectorInstanceSpecificationType additionalConnectorType, Class<T> capabilityClass) {
		T cap = CapabilityUtil.getEffectiveCapability(additionalConnectorType.getCapabilities(), capabilityClass);
		if (cap == null) {
			return false;
		}
		return CapabilityUtil.isCapabilityEnabled(cap);
	}
	
	private <T extends CapabilityType> boolean supportsCapability(ConnectorInstanceSpecificationType additionalConnectorType, Collection<Object> nativeCapabilities, Class<T> capabilityClass) {
		CapabilitiesType specifiedCapabilitiesType = additionalConnectorType.getCapabilities();
		if (specifiedCapabilitiesType != null) {
			CapabilityCollectionType configuredCapCollectionType = specifiedCapabilitiesType.getConfigured();
			if (configuredCapCollectionType != null) {
				T configuredCap = CapabilityUtil.getCapability(configuredCapCollectionType.getAny(), capabilityClass);
				if (configuredCap != null && !CapabilityUtil.isCapabilityEnabled(configuredCap)) {
					return false;
				}
			}
			
		}
		T cap = CapabilityUtil.getCapability(nativeCapabilities, capabilityClass);
		if (cap == null) {
			return false;
		}
		return CapabilityUtil.isCapabilityEnabled(cap);
	}

	private ConnectorSpec getDefaultConnectorSpec(PrismObject<ResourceType> resource) {
		PrismContainer<ConnectorConfigurationType> connectorConfiguration = resource.findContainer(ResourceType.F_CONNECTOR_CONFIGURATION);
		return new ConnectorSpec(resource, null, ResourceTypeUtil.getConnectorOid(resource), connectorConfiguration);
	}

	private ConnectorSpec getConnectorSpec(PrismObject<ResourceType> resource, ConnectorInstanceSpecificationType additionalConnectorType) throws SchemaException {
		if (additionalConnectorType.getConnectorRef() == null) {
			throw new SchemaException("No connector reference in additional connector in "+resource);
		}
		String connectorOid = additionalConnectorType.getConnectorRef().getOid();
		if (StringUtils.isBlank(connectorOid)) {
			throw new SchemaException("No connector OID in additional connector in "+resource);
		}
		PrismContainer<ConnectorConfigurationType> connectorConfiguration = additionalConnectorType.asPrismContainerValue().findContainer(ConnectorInstanceSpecificationType.F_CONNECTOR_CONFIGURATION);
		String connectorName = additionalConnectorType.getName();
		if (StringUtils.isBlank(connectorName)) {
			throw new SchemaException("No connector name in additional connector in "+resource);
		}
		return new ConnectorSpec(resource, connectorName, connectorOid, connectorConfiguration);
	}

	public PrismContext getPrismContext() {
		return prismContext;
	}
}
