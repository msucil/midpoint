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

package com.evolveum.midpoint.provisioning.ucf.impl.builtin.async;

import com.evolveum.midpoint.prism.Item;
import com.evolveum.midpoint.prism.ItemDefinition;
import com.evolveum.midpoint.prism.PrismContainerValue;
import com.evolveum.midpoint.prism.PrismContext;
import com.evolveum.midpoint.prism.delta.ObjectDelta;
import com.evolveum.midpoint.prism.path.ItemName;
import com.evolveum.midpoint.provisioning.ucf.api.AsyncUpdateMessageListener;
import com.evolveum.midpoint.provisioning.ucf.api.Change;
import com.evolveum.midpoint.provisioning.ucf.api.ChangeListener;
import com.evolveum.midpoint.schema.DeltaConvertor;
import com.evolveum.midpoint.schema.SchemaConstantsGenerated;
import com.evolveum.midpoint.schema.expression.VariablesMap;
import com.evolveum.midpoint.schema.processor.ObjectClassComplexTypeDefinition;
import com.evolveum.midpoint.schema.processor.ResourceAttribute;
import com.evolveum.midpoint.schema.processor.ResourceAttributeDefinition;
import com.evolveum.midpoint.schema.processor.ResourceSchema;
import com.evolveum.midpoint.security.api.SecurityContextManager;
import com.evolveum.midpoint.util.QNameUtil;
import com.evolveum.midpoint.util.exception.*;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;
import com.evolveum.prism.xml.ns._public.types_3.ChangeTypeType;
import com.evolveum.prism.xml.ns._public.types_3.ObjectDeltaType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.security.core.Authentication;

import javax.xml.namespace.QName;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

import static com.evolveum.midpoint.schema.util.ObjectTypeUtil.toPrismObject;
import static com.evolveum.midpoint.util.MiscUtil.emptyIfNull;

/**
 *  Transforms AsyncUpdateMessageType objects to Change ones (via UcfChangeType intermediary).
 *
 *  Also prepares appropriately authenticated security context. (In the future we might factor this out to a separate class.)
 */
public class TransformationalAsyncUpdateMessageListener implements AsyncUpdateMessageListener {

	private static final Trace LOGGER = TraceManager.getTrace(TransformationalAsyncUpdateMessageListener.class);

	private static final String VAR_MESSAGE = "message";

	@NotNull private final ChangeListener changeListener;
	@Nullable private final Authentication authentication;
	@NotNull private final AsyncUpdateConnectorInstance connectorInstance;

	TransformationalAsyncUpdateMessageListener(@NotNull ChangeListener changeListener,
			@Nullable Authentication authentication,
			@NotNull AsyncUpdateConnectorInstance connectorInstance) {
		this.changeListener = changeListener;
		this.authentication = authentication;
		this.connectorInstance = connectorInstance;
	}

	@Override
	public boolean onMessage(AsyncUpdateMessageType message) throws SchemaException {
		LOGGER.trace("Got {}", message);
		SecurityContextManager securityContextManager = connectorInstance.getSecurityContextManager();
		Authentication oldAuthentication = securityContextManager.getAuthentication();

		try {
			securityContextManager.setupPreAuthenticatedSecurityContext(authentication);

			VariablesMap variables = new VariablesMap();
			variables.put(VAR_MESSAGE, message, AsyncUpdateMessageType.class);
			List<UcfChangeType> changeBeans;
			try {
				ExpressionType transformExpression = connectorInstance.getTransformExpression();
				if (transformExpression != null) {
					changeBeans = connectorInstance.getUcfExpressionEvaluator().evaluate(transformExpression, variables,
							SchemaConstantsGenerated.C_UCF_CHANGE, "computing UCF change from async update");
				} else {
					changeBeans = unwrapMessage(message);
				}
			} catch (RuntimeException | SchemaException | ObjectNotFoundException | SecurityViolationException | CommunicationException |
					ConfigurationException | ExpressionEvaluationException e) {
				throw new SystemException("Couldn't evaluate message transformation expression: " + e.getMessage(), e);
			}
			boolean ok = true;
			for (UcfChangeType changeBean : changeBeans) {
				// intentionally in this order - to process changes even after failure
				// (if listener wants to fail fast, it can throw an exception)
				ok = changeListener.onChange(createChange(changeBean)) && ok;
			}
			return ok;

		} finally {
			securityContextManager.setupPreAuthenticatedSecurityContext(oldAuthentication);
		}
	}

	/**
	 * Mainly for testing purposes we provide an option to simply unwrap UcfChangeType from "any data" message.
	 */
	private List<UcfChangeType> unwrapMessage(AsyncUpdateMessageType message) throws SchemaException {
		Object data;
		if (message instanceof AnyDataAsyncUpdateMessageType) {
			data = ((AnyDataAsyncUpdateMessageType) message).getData();
		} else if (message instanceof Amqp091MessageType) {
			String text = new String(((Amqp091MessageType) message).getBody(), StandardCharsets.UTF_8);
			data = getPrismContext().parserFor(text).xml().parseRealValue();
		} else {
			throw new SchemaException(
					"Cannot apply trivial message transformation: message is not 'any data' nor AMQP one. Please "
							+ "specify transformExpression parameter");
		}
		if (data instanceof UcfChangeType) {
			return Collections.singletonList((UcfChangeType) data);
		} else {
			throw new SchemaException("Cannot apply trivial message transformation: message does not contain "
					+ "UcfChangeType object (it is " + data.getClass().getName() + " instead). Please specify transformExpression parameter");
		}
	}

	private Change createChange(UcfChangeType changeBean) throws SchemaException {
		QName objectClassName = changeBean.getObjectClass();
		if (objectClassName == null) {
			throw new SchemaException("Object class name is null in " + changeBean);
		}
		ResourceSchema resourceSchema = getResourceSchema();
		if (resourceSchema == null) {
			throw new SchemaException("No resource schema; have you executed the Test Resource operation?");
		}
		ObjectClassComplexTypeDefinition objectClassDef = resourceSchema.findObjectClassDefinition(objectClassName);
		if (objectClassDef == null) {
			throw new SchemaException("Object class " + objectClassName + " not found in " + resourceSchema);
		}
		ObjectDelta<ShadowType> delta;
		ObjectDeltaType deltaBean = changeBean.getObjectDelta();
		if (deltaBean != null) {
			setFromDefaults((ShadowType) deltaBean.getObjectToAdd(), objectClassName);
			if (deltaBean.getObjectType() == null) {
				deltaBean.setObjectType(ShadowType.COMPLEX_TYPE);
			}
			delta = DeltaConvertor.createObjectDelta(deltaBean, getPrismContext(), true);
		} else {
			delta = null;
		}
		setFromDefaults(changeBean.getObject(), objectClassName);
		Collection<ResourceAttribute<?>> identifiers = getIdentifiers(changeBean, objectClassDef);
		Change change = new Change(identifiers, toPrismObject(changeBean.getObject()), null, delta);
		change.setObjectClassDefinition(objectClassDef);
		if (change.getCurrentShadow() == null && change.getObjectDelta() == null) {
			change.setNotificationOnly(true);
		}
		return change;
	}

	private void setFromDefaults(ShadowType object, QName objectClassName) {
		if (object != null) {
			if (object.getObjectClass() == null) {
				object.setObjectClass(objectClassName);
			}
		}
	}

	private Collection<ResourceAttribute<?>> getIdentifiers(UcfChangeType changeBean, ObjectClassComplexTypeDefinition ocDef)
			throws SchemaException {
		Collection<ResourceAttribute<?>> rv = new ArrayList<>();
		PrismContainerValue<ShadowAttributesType> attributesPcv;
		boolean mayContainNonIdentifiers;
		if (changeBean.getIdentifiers() != null) {
			//noinspection unchecked
		    attributesPcv = changeBean.getIdentifiers().asPrismContainerValue();
		    mayContainNonIdentifiers = false;
		} else if (changeBean.getObject() != null) {
			//noinspection unchecked
			attributesPcv = changeBean.getObject().getAttributes().asPrismContainerValue();
			mayContainNonIdentifiers = true;
		} else if (changeBean.getObjectDelta() != null && changeBean.getObjectDelta().getChangeType() == ChangeTypeType.ADD &&
				changeBean.getObjectDelta().getObjectToAdd() instanceof ShadowType) {
			//noinspection unchecked
			attributesPcv = ((ShadowType) changeBean.getObjectDelta().getObjectToAdd()).getAttributes().asPrismContainerValue();
			mayContainNonIdentifiers = true;
		} else {
			throw new SchemaException("Change does not contain identifiers");
		}
		Set<ItemName> identifiers = ocDef.getAllIdentifiers().stream().map(ItemDefinition::getName).collect(Collectors.toSet());
		for (Item<?,?> attribute : attributesPcv.getItems()) {
			if (QNameUtil.matchAny(attribute.getElementName(), identifiers)) {
				if (attribute instanceof ResourceAttribute) {
					rv.add(((ResourceAttribute) attribute).clone());
				} else {
					ResourceAttributeDefinition<Object> definition = ocDef
							.findAttributeDefinition(attribute.getElementName());
					if (definition == null) {
						throw new SchemaException("No definition of " + attribute.getElementName() + " in " + ocDef);
					}
					ResourceAttribute<Object> resourceAttribute = definition.instantiate();
					for (Object realValue : attribute.getRealValues()) {
						resourceAttribute.addRealValue(realValue);
					}
					rv.add(resourceAttribute);
				}
			} else {
				if (!mayContainNonIdentifiers) {
					LOGGER.warn("Attribute {} is not an identifier in {} -- ignoring it", attribute, ocDef);
				}
			}
		}
		return rv;
	}

	private PrismContext getPrismContext() {
		return connectorInstance.getPrismContext();
	}

	private ResourceSchema getResourceSchema() {
		return connectorInstance.getResourceSchema();
	}
}
