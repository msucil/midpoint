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
package com.evolveum.midpoint.gui.impl.prism;

import java.util.ArrayList;
import java.util.List;

import org.apache.wicket.Component;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.form.OnChangeAjaxBehavior;
import org.apache.wicket.behavior.AttributeAppender;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.list.ListItem;
import org.apache.wicket.markup.html.list.ListView;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.PropertyModel;
import org.apache.wicket.model.StringResourceModel;

import com.evolveum.midpoint.gui.api.component.BasePanel;
import com.evolveum.midpoint.gui.api.model.LoadableModel;
import com.evolveum.midpoint.prism.Containerable;
import com.evolveum.midpoint.prism.PrismContainerDefinition;
import com.evolveum.midpoint.web.component.AjaxButton;
import com.evolveum.midpoint.web.component.dialog.Popupable;
import com.evolveum.midpoint.web.component.form.CheckFormGroup;
import com.evolveum.midpoint.web.page.admin.configuration.component.EmptyOnBlurAjaxFormUpdatingBehaviour;
import com.evolveum.midpoint.web.page.admin.configuration.component.EmptyOnChangeAjaxFormUpdatingBehavior;

/**
 * @author katka
 *
 */
public abstract class ListContainersPopup<C extends Containerable, CV extends PrismContainerValueWrapper<C>> extends BasePanel<CV> implements Popupable {

	
	private static final long serialVersionUID = 1L;
	
	private static final String ID_SELECTED = "selected";
	private static final String ID_DEFINITION = "definition";
	private static final String ID_SELECT = "select";
	private static final String ID_CONTAINERS = "containers";
	
	
	public ListContainersPopup(String id, IModel<CV> model) {
		super(id, model);
	}
	
	@Override
	protected void onInitialize() {
		super.onInitialize();
		initLayout();
	}
	
	private void initLayout() {
		
		IModel<List<ContainersPopupDto>> popupModel = new LoadableModel<List<ContainersPopupDto>>(false) {

			private static final long serialVersionUID = 1L;

			@Override
			protected List<ContainersPopupDto> load() {
				List<PrismContainerDefinition<C>> defs = getModelObject().getChildContainers();
				List<ContainersPopupDto> modelObject = new ArrayList<>(defs.size());

				defs.forEach(def -> modelObject.add(new ContainersPopupDto(false, def)));
				return modelObject;
			}
		};
		
		ListView<ContainersPopupDto> listView = new ListView<ContainersPopupDto>(ID_CONTAINERS, popupModel) {

			private static final long serialVersionUID = 1L;
			
			@Override
			protected void populateItem(ListItem<ContainersPopupDto> item) {
				
				CheckFormGroup checkFormGroup = new CheckFormGroup(ID_SELECTED, new PropertyModel<Boolean>(item.getModel(), "selected"), 
						new StringResourceModel("ListContainersPopup.selected"), "col-md-2", "col-md-10") {
					
					protected boolean getLabelVisible() {
						return false;
					};
					
				};
//				checkFormGroup.getCheck().add(new EmptyOnBlurAjaxFormUpdatingBehaviour());
				checkFormGroup.getCheck().add(new EmptyOnChangeAjaxFormUpdatingBehavior());
//				checkFormGroup.getCheck().add(new OnChangeAjaxBehavior() {
//					
//					@Override
//					protected void onUpdate(AjaxRequestTarget target) {
//						item.getModelObject().setSelected(!item.getModelObject().isSelected());
//					}
//				});
				checkFormGroup.add(AttributeAppender.append("class", " checkbox-without-margin-bottom "));
				checkFormGroup.setOutputMarkupId(true);
				item.add(checkFormGroup);
				
				String displayNameKey = item.getModelObject() != null ? item.getModelObject().getDisplayName() : "";
				Label definition = new Label(ID_DEFINITION, new StringResourceModel(displayNameKey));
				definition.setOutputMarkupId(true);
				item.add(definition);
			}
			
			
		};
		listView.setOutputMarkupId(true);
		listView.setReuseItems(true);
		add(listView);
		
		
		AjaxButton select = new AjaxButton(ID_SELECT, new StringResourceModel("ListContainerPopup.select")) {
			
			private static final long serialVersionUID = 1L;

			@Override
			public void onClick(AjaxRequestTarget target) {
				ListView<ContainersPopupDto> listView = (ListView<ContainersPopupDto>) ListContainersPopup.this.get(ID_CONTAINERS);
				List<PrismContainerDefinition<?>> selected = new ArrayList<>();
				listView.getModelObject().forEach(child -> {
					if (child.isSelected()) {
						selected.add(child.getDef());
					}
					
				});
				processSelectedChildren(target, selected);
			}
		};
		select.setOutputMarkupId(true);
		add(select);
	}
	
	protected abstract void processSelectedChildren(AjaxRequestTarget target, List<PrismContainerDefinition<?>> selected);
	
		@Override
	public int getWidth() {
		return 20;
	}

	@Override
	public int getHeight() {
		return 0;
	}

	@Override
	public String getWidthUnit() {
		 return "%";
	}

	@Override
	public String getHeightUnit() {
		 return "%";
	}

	@Override
	public StringResourceModel getTitle() {
		return new StringResourceModel("ListContainersPopup.availableContainers");
	}

	@Override
	public Component getComponent() {
		return this;
	}
	
	
}

