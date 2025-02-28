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
package com.evolveum.midpoint.gui.impl.factory;

import java.io.Serializable;

import javax.annotation.PostConstruct;
import javax.annotation.Priority;

import org.apache.wicket.markup.html.panel.Panel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.evolveum.midpoint.gui.api.component.path.ItemPathDto;
import com.evolveum.midpoint.gui.api.component.path.ItemPathPanel;
import com.evolveum.midpoint.gui.api.factory.AbstractGuiComponentFactory;
import com.evolveum.midpoint.gui.api.prism.ItemWrapper;
import com.evolveum.midpoint.gui.api.registry.GuiComponentRegistry;
import com.evolveum.midpoint.gui.impl.prism.PrismPropertyWrapper;
import com.evolveum.prism.xml.ns._public.types_3.ItemPathType;

/**
 * @author katka
 *
 */
@Component
@Priority(1000)
public class ItemPathPanelFactory extends AbstractGuiComponentFactory<ItemPathType> implements Serializable {

	private static final long serialVersionUID = 1L;

	@Autowired private transient GuiComponentRegistry registry;

	@PostConstruct
	public void register() {
		registry.addToRegistry(this);
	}
	
	@Override
	public <IW extends ItemWrapper> boolean match(IW wrapper) {
		return ItemPathType.COMPLEX_TYPE.equals(wrapper.getTypeName());
	}

	@Override
	protected Panel getPanel(PrismPropertyPanelContext<ItemPathType> panelCtx) {
		return new ItemPathPanel(panelCtx.getComponentId(), (ItemPathType) panelCtx.getRealValueModel().getObject()) {

			private static final long serialVersionUID = 1L;

			@Override
			protected void onUpdate(ItemPathDto itemPathDto) {
				panelCtx.getRealValueModel().setObject(new ItemPathType(itemPathDto.toItemPath()));

			}
		};
	}	
	
}
