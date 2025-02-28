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

package com.evolveum.midpoint.prism.impl.marshaller;

import com.evolveum.midpoint.prism.*;
import com.evolveum.midpoint.prism.impl.PrismContextImpl;
import com.evolveum.midpoint.prism.impl.PrismPropertyValueImpl;
import com.evolveum.midpoint.prism.impl.SerializerTarget;
import com.evolveum.midpoint.prism.xnode.RootXNode;
import com.evolveum.midpoint.prism.impl.xnode.RootXNodeImpl;
import com.evolveum.midpoint.prism.impl.xnode.XNodeImpl;
import com.evolveum.midpoint.util.exception.SchemaException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.xml.bind.JAXBElement;
import javax.xml.namespace.QName;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author mederly
 */
public class PrismSerializerImpl<T> implements PrismSerializer<T> {

	@NotNull private final PrismContextImpl prismContext;
	@NotNull private final SerializerTarget<T> target;
	private final QName itemName;
	private final ItemDefinition itemDefinition;
	private final SerializationContext context;
	private final Collection<? extends QName> itemsToSkip;

	//region Setting up =============================================================================================
	public PrismSerializerImpl(@NotNull SerializerTarget<T> target, QName itemName, ItemDefinition itemDefinition,
			SerializationContext context, @NotNull PrismContextImpl prismContext,
			Collection<? extends QName> itemsToSkip) {
		this.target = target;
		this.itemName = itemName;
		this.itemDefinition = itemDefinition;
		this.context = context;
		this.prismContext = prismContext;
		this.itemsToSkip = itemsToSkip;
	}

	@NotNull
	@Override
	public PrismSerializerImpl<T> context(SerializationContext context) {
		return new PrismSerializerImpl<>(this.target, itemName, itemDefinition, context, prismContext, itemsToSkip);
	}

	@NotNull
	@Override
	public PrismSerializerImpl<T> root(QName elementName) {
		return new PrismSerializerImpl<>(this.target, elementName, itemDefinition, this.context, prismContext, itemsToSkip);
	}

	@NotNull
	@Override
	public PrismSerializer<T> definition(ItemDefinition itemDefinition) {
		return new PrismSerializerImpl<>(this.target, itemName, itemDefinition, this.context, prismContext, itemsToSkip);
	}

	@NotNull
	@Override
	public PrismSerializerImpl<T> options(SerializationOptions options) {
		SerializationContext context;
		if (this.context != null) {
			context = this.context.clone();
			context.setOptions(options);
		} else {
			context = new SerializationContext(options);
		}
		return new PrismSerializerImpl<>(target, itemName, itemDefinition, context, prismContext, itemsToSkip);
	}

	@NotNull
	@Override
	public PrismSerializer<T> itemsToSkip(Collection<? extends QName> itemsToSkip) {
		return new PrismSerializerImpl<>(target, itemName, itemDefinition, context, prismContext, itemsToSkip);
	}

	//endregion

	//region Serialization =============================================================================================

	@NotNull
	@Override
	public T serialize(@NotNull Item<?, ?> item) throws SchemaException {
		RootXNodeImpl xroot = getMarshaller().marshalItemAsRoot(item, itemName, itemDefinition, context, itemsToSkip);
		checkPostconditions(xroot);			// TODO find better way
		return target.write(xroot, context);
	}

	@NotNull
	public T serialize(@NotNull Item<?, ?> item, QName itemName) throws SchemaException {
		return root(itemName).serialize(item);
	}

	@NotNull
	@Override
	public T serialize(@NotNull PrismValue value) throws SchemaException {
		QName nameToUse;
		if (itemName != null) {
			nameToUse = itemName;
		} else if (itemDefinition != null) {
			nameToUse = itemDefinition.getName();
		} else if (value.getParent() != null) {
			nameToUse = value.getParent().getElementName();
		} else {
			nameToUse = null;
		}
//		else {
//			// TODO derive from the value type itself? Not worth the effort.
//			throw new IllegalArgumentException("Item name nor definition is not known for " + value);
//		}
		RootXNodeImpl xroot = getMarshaller().marshalPrismValueAsRoot(value, nameToUse, itemDefinition, context, itemsToSkip);
		checkPostconditions(xroot);				// TODO find better way
		return target.write(xroot, context);
	}

	@NotNull
	@Override
	public T serialize(@NotNull PrismValue value, QName itemName) throws SchemaException {
		return root(itemName).serialize(value);
	}

	@NotNull
	@Override
	public T serialize(@NotNull RootXNode xnode) throws SchemaException {
		return target.write((RootXNodeImpl) xnode, context);
	}

	@NotNull
	@Override
	public T serializeObjects(@NotNull List<PrismObject<?>> objects, @Nullable QName aggregateElementName) throws SchemaException {
		List<RootXNodeImpl> roots = new ArrayList<>();
		for (PrismObject<?> object : objects) {
			// itemName and itemDefinition might be set only if they apply to all the objects
			RootXNodeImpl xroot = getMarshaller().marshalItemAsRoot(object, itemName, itemDefinition, context, itemsToSkip);
			checkPostconditions(xroot);			// TODO find better way
			roots.add(xroot);
		}
		return target.write(roots, aggregateElementName, context);
	}

	@Override
	public T serializeRealValue(Object realValue) throws SchemaException {
		PrismValue prismValue;
		if (realValue instanceof Objectable) {
			return serialize(((Objectable) realValue).asPrismObject(), itemName);		// to preserve OID and name
		} else if (realValue instanceof Containerable) {
			prismValue = ((Containerable) realValue).asPrismContainerValue();
		} else {
			prismValue = new PrismPropertyValueImpl<>(realValue);
		}
		return serialize(prismValue, itemName);
	}

	@Override
	public T serializeRealValue(Object realValue, QName itemName) throws SchemaException {
		return root(itemName).serializeRealValue(realValue);
	}

	@Override
	public T serialize(JAXBElement<?> value) throws SchemaException {
		return serializeRealValue(value.getValue(), value.getName());		// TODO declared type?
	}

	@Override
	public T serializeAnyData(Object value) throws SchemaException {
		RootXNodeImpl xnode = getMarshaller().marshalAnyData(value, itemName, itemDefinition, context, itemsToSkip);
		checkPostconditions(xnode);				// TODO find better way
		return target.write(xnode, context);
	}

	@Override
	public T serializeAnyData(Object value, QName itemName) throws SchemaException {
		return root(itemName).serializeAnyData(value);
	}

	@NotNull
	private PrismMarshaller getMarshaller() {
		return target.prismContext.getPrismMarshaller();
	}

	private void checkPostconditions(RootXNodeImpl root) {
		if (itemName != null && !(root.getRootElementName().equals(itemName))) {
			throw new IllegalStateException("Postcondition fail: marshaled root name (" + root.getRootElementName() +
				" is different from preset one (" + itemName + ")");
		}
		if (PrismContextImpl.isExtraValidation()) {
			checkTypeResolvable(root);
		}
	}

	private void checkTypeResolvable(RootXNodeImpl root) {
		root.accept(n -> {
			QName type;
			if (n instanceof XNodeImpl && (type = ((XNodeImpl) n).getTypeQName()) != null && ((XNodeImpl) n).isExplicitTypeDeclaration()) {
				if (prismContext.getSchemaRegistry().determineClassForType(type) == null) {
					// it could be sufficient to find a TD
					if (prismContext.getSchemaRegistry().findTypeDefinitionByType(type) == null) {
						throw new IllegalStateException(
								"Postcondition fail: type " + type + " is not resolvable in:\n" + root.debugDump());
					}
				}
			}
		});
	}
	//endregion

}
