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
package com.evolveum.midpoint.gui.impl.prism;

import org.apache.wicket.Component;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.markup.html.AjaxLink;
import org.apache.wicket.behavior.AttributeAppender;
import org.apache.wicket.model.IModel;

import com.evolveum.midpoint.gui.api.GuiStyleConstants;
import com.evolveum.midpoint.gui.api.component.togglebutton.ToggleIconButton;
import com.evolveum.midpoint.gui.api.prism.PrismContainerWrapper;
import com.evolveum.midpoint.gui.impl.factory.WrapperContext;
import com.evolveum.midpoint.prism.Containerable;
import com.evolveum.midpoint.prism.PrismContainer;
import com.evolveum.midpoint.prism.PrismContainerDefinition;
import com.evolveum.midpoint.prism.PrismContainerValue;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.web.component.AjaxButton;
import com.evolveum.midpoint.web.component.prism.ValueStatus;
import com.evolveum.midpoint.web.component.util.VisibleBehaviour;

/**
 * @author katka
 *
 */
public class PrismContainerHeaderPanel<C extends Containerable> extends ItemHeaderPanel<PrismContainerValue<C>, PrismContainer<C>, PrismContainerDefinition<C>, PrismContainerWrapper<C>> {

	private static final long serialVersionUID = 1L;

	private static final String ID_ADD_BUTTON = "addButton";
	private static final String ID_EXPAND_COLLAPSE_FRAGMENT = "expandCollapseFragment";
    private static final String ID_EXPAND_COLLAPSE_BUTTON = "expandCollapseButton";
	
	
	public PrismContainerHeaderPanel(String id, IModel<PrismContainerWrapper<C>> model) {
		super(id, model);
	}

	@Override
	protected void initButtons() {
		 AjaxLink<Void> addButton = new AjaxLink<Void>(ID_ADD_BUTTON) {
				private static final long serialVersionUID = 1L;

				@Override
	            public void onClick(AjaxRequestTarget target) {
	                addValue(target);
	            }
	        };
	        addButton.add(new VisibleBehaviour(() -> isAddButtonVisible()));
	        add(addButton);
	        
	        
	        initExpandCollapseButton();
	        //TODO: sorting
	}
		
	private void addValue(AjaxRequestTarget target) {
		PrismContainerWrapper<C> parentWrapper = getModelObject();
		WrapperContext ctx = new WrapperContext(null, null);
		ctx.setShowEmpty(true);
		try {
			PrismContainerValueWrapper<C> valueWrapper = getPageBase().createValueWrapper(parentWrapper, parentWrapper.getItem().createNewValue(), ValueStatus.ADDED, ctx);
			parentWrapper.getValues().add(valueWrapper);
		} catch (SchemaException e) {
			// TODO error handling
		}
		PrismContainerPanel parentPanel = findParent(PrismContainerPanel.class);
		target.add(parentPanel);
 	}
	
	
	
	private boolean isAddButtonVisible() {
		return getModelObject() != null && getModelObject().isExpanded() && getModelObject().isMultiValue();
	}

	@Override
	protected Component createTitle(IModel<String> label) {
		AjaxButton labelComponent = new AjaxButton(ID_LABEL, label) {
			private static final long serialVersionUID = 1L;
			@Override
			public void onClick(AjaxRequestTarget target) {
				onExpandClick(target);
			}
		};
		labelComponent.setOutputMarkupId(true);
		labelComponent.add(AttributeAppender.append("style", "cursor: pointer;"));
		return labelComponent;
	}
	
	
	protected void initExpandCollapseButton() {
		ToggleIconButton<?> expandCollapseButton = new ToggleIconButton<Void>(ID_EXPAND_COLLAPSE_BUTTON,
				GuiStyleConstants.CLASS_ICON_EXPAND_CONTAINER, GuiStyleConstants.CLASS_ICON_COLLAPSE_CONTAINER) {
			
			private static final long serialVersionUID = 1L;

			@Override
			public void onClick(AjaxRequestTarget target) {
				onExpandClick(target);
			}
						
			@Override
			public boolean isOn() {
				return PrismContainerHeaderPanel.this.getModelObject() != null && PrismContainerHeaderPanel.this.getModelObject().isExpanded();
			}
        };
        expandCollapseButton.setOutputMarkupId(true);
        add(expandCollapseButton);
	}
	
	private void onExpandClick(AjaxRequestTarget target) {
		
		PrismContainerWrapper<C> wrapper = getModelObject();
		wrapper.setExpanded(!wrapper.isExpanded());
		target.add(getParent());
	}

	
}
