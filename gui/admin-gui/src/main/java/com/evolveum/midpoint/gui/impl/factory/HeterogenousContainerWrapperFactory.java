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
package com.evolveum.midpoint.gui.impl.factory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.annotation.PostConstruct;
import javax.xml.namespace.QName;

import org.slf4j.spi.LocationAwareLogger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.evolveum.midpoint.gui.api.prism.ItemStatus;
import com.evolveum.midpoint.gui.api.prism.ItemWrapper;
import com.evolveum.midpoint.gui.api.prism.PrismContainerWrapper;
import com.evolveum.midpoint.gui.api.registry.GuiComponentRegistry;
import com.evolveum.midpoint.gui.impl.prism.PrismContainerPanel;
import com.evolveum.midpoint.gui.impl.prism.PrismContainerValueWrapper;
import com.evolveum.midpoint.gui.impl.prism.PrismContainerValueWrapperImpl;
import com.evolveum.midpoint.gui.impl.prism.PrismContainerWrapperImpl;
import com.evolveum.midpoint.gui.impl.prism.PrismPropertyPanel;
import com.evolveum.midpoint.gui.impl.prism.PrismValueWrapper;
import com.evolveum.midpoint.prism.Containerable;
import com.evolveum.midpoint.prism.Item;
import com.evolveum.midpoint.prism.ItemDefinition;
import com.evolveum.midpoint.prism.PrismContainer;
import com.evolveum.midpoint.prism.PrismContainerDefinition;
import com.evolveum.midpoint.prism.PrismContainerValue;
import com.evolveum.midpoint.prism.path.ItemName;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.web.component.prism.ValueStatus;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ExclusionPolicyConstraintType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.HasAssignmentPolicyConstraintType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.LocalizableMessageTemplateType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.PolicyConstraintEnforcementType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.PolicyConstraintPresentationType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.PolicyConstraintsType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.StatePolicyConstraintType;

/**
 * @author katka
 *
 */
@Component
public class HeterogenousContainerWrapperFactory<C extends Containerable> implements PrismContainerWrapperFactory<C> {

	private static final transient Trace LOGGER = TraceManager.getTrace(HeterogenousContainerWrapperFactory.class); 
	
	@Autowired private GuiComponentRegistry registry; 
	
	@Override
	public PrismContainerWrapper<C> createWrapper(PrismContainerValueWrapper<?> parent,
			ItemDefinition<?> def, WrapperContext context) throws SchemaException {
		ItemName name = def.getName();
		
		PrismContainer<C> childItem = parent.getNewValue().findContainer(name);
		ItemStatus status = ItemStatus.NOT_CHANGED;
		if (childItem == null) {
			childItem = (PrismContainer<C>) def.instantiate();
			status = ItemStatus.ADDED;
		}
		
		PrismContainerWrapper<C> itemWrapper = new PrismContainerWrapperImpl<C>(parent, childItem, status);
		registry.registerWrapperPanel(childItem.getDefinition().getTypeName(), PrismContainerPanel.class);
		
		List<PrismContainerValueWrapper<C>> valueWrappers  = createValuesWrapper(itemWrapper, childItem, context);
		LOGGER.trace("valueWrappers {}", itemWrapper.getValues());
		itemWrapper.getValues().addAll((Collection) valueWrappers);
		
		return itemWrapper;
	}

	@Override
	public PrismContainerValueWrapper<C> createValueWrapper(PrismContainerWrapper<C> parent,
			PrismContainerValue<C> value, ValueStatus status, WrapperContext context)
			throws SchemaException {
		PrismContainerValueWrapper<C> containerValueWrapper = new PrismContainerValueWrapperImpl<C>(parent, value, status);
		containerValueWrapper.setHeterogenous(true);
		
		List<ItemWrapper<?,?,?,?>> wrappers = new ArrayList<>();
		
		for (ItemDefinition<?> def : value.getDefinition().getDefinitions()) {
			
			Item<?,?> childItem = value.findItem(def.getName());
			
			if (childItem == null && def instanceof PrismContainerDefinition) {
				LOGGER.trace("Skipping craeting wrapper for {}, only property and refernce wrappers are created for heterogenous containers.");
				continue;
			}
			
			ItemWrapperFactory<?,?,?> factory = registry.findWrapperFactory(def);
			
			ItemWrapper<?, ?, ?, ?> wrapper = factory.createWrapper(containerValueWrapper, def, context);
			if (wrapper != null) {
				wrappers.add(wrapper);
			}
		}
		
		containerValueWrapper.getItems().addAll((Collection) wrappers);
		return containerValueWrapper;
	}
	
	protected List<PrismContainerValueWrapper<C>> createValuesWrapper(PrismContainerWrapper<C> itemWrapper, PrismContainer<C> item, WrapperContext context) throws SchemaException {
		List<PrismContainerValueWrapper<C>> pvWrappers = new ArrayList<>();
		
//		PrismContainerDefinition<C> definition = item.getDefinition();
		
		if (item.getValues() == null || item.getValues().isEmpty()) {
			PrismContainerValueWrapper<C> valueWrapper = createValueWrapper(itemWrapper, item.createNewValue(), ValueStatus.ADDED, context);
			pvWrappers.add(valueWrapper);
			return pvWrappers;
		}
		
		for (PrismContainerValue<C> pcv : item.getValues()) {
			PrismContainerValueWrapper<C> valueWrapper = createValueWrapper(itemWrapper, pcv, ValueStatus.NOT_CHANGED, context);
			pvWrappers.add(valueWrapper);
		}
		
		return pvWrappers;
	
	}

	/**
	 * 
	 * match single value containers which contains a looot of other conainers, e.g. policy rule, policy action, notification configuration, etc
	 */
	@Override
	public boolean match(ItemDefinition<?> def) {
		QName defName = def.getTypeName();
		
		if (!(def instanceof PrismContainerDefinition)) {
			return false;
		}
		
		PrismContainerDefinition<?> containerDef = (PrismContainerDefinition<?>) def;
		
		if (containerDef.isMultiValue()) {
			return false;
		}
		
		List<? extends ItemDefinition> defs = containerDef.getDefinitions();
		int containers = 0;
		for (ItemDefinition<?> itemDef : defs) {
			if (itemDef instanceof PrismContainerDefinition<?> && itemDef.isMultiValue()) {			
				containers++;
			}
		}
		
		if (containers > 2) {
			return true;
		}
		
		return false;
		
//		if (def.isSingleValue() && )
//		return PolicyConstraintPresentationType.COMPLEX_TYPE.equals(defName) 
//				|| StatePolicyConstraintType.COMPLEX_TYPE.equals(defName)
//				|| HasAssignmentPolicyConstraintType.COMPLEX_TYPE.equals(defName)
//				|| ExclusionPolicyConstraintType.COMPLEX_TYPE.equals(defName)
//				|| PolicyConstraintsType.COMPLEX_TYPE.equals(defName);
	}

	@Override
	@PostConstruct
	public void register() {
		registry.addToRegistry(this);
	}

	@Override
	public int getOrder() {
		return 110;
	}

	@Override
	public PrismContainerValueWrapper<C> createContainerValueWrapper(PrismContainerWrapper<C> objectWrapper,
			PrismContainerValue<C> objectValue, ValueStatus status) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public PrismContainerWrapper<C> createWrapper(Item childContainer, ItemStatus status, WrapperContext context)
			throws SchemaException {
		// TODO Auto-generated method stub
		return null;
	}
	

}
