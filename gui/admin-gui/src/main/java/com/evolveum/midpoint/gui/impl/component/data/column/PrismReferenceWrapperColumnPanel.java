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
package com.evolveum.midpoint.gui.impl.component.data.column;

import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.model.IModel;

import com.evolveum.midpoint.gui.api.util.WebComponentUtil;
import com.evolveum.midpoint.gui.impl.component.data.column.AbstractItemWrapperColumn.ColumnType;
import com.evolveum.midpoint.gui.impl.error.ErrorPanel;
import com.evolveum.midpoint.gui.impl.factory.ItemRealValueModel;
import com.evolveum.midpoint.gui.impl.prism.PrismContainerValueWrapper;
import com.evolveum.midpoint.gui.impl.prism.PrismPropertyValueWrapper;
import com.evolveum.midpoint.gui.impl.prism.PrismPropertyWrapper;
import com.evolveum.midpoint.gui.impl.prism.PrismReferenceWrapper;
import com.evolveum.midpoint.gui.impl.prism.PrismValueWrapper;
import com.evolveum.midpoint.prism.PrismReferenceValue;
import com.evolveum.midpoint.prism.Referencable;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.web.component.data.column.LinkPanel;

/**
 * @author katka
 *
 */
public class PrismReferenceWrapperColumnPanel<R extends Referencable> extends AbstractItemWrapperColumnPanel<PrismReferenceWrapper<R>, PrismValueWrapper<R, PrismReferenceValue>> {

	private static final long serialVersionUID = 1L;
	private static final transient Trace LOGGER = TraceManager.getTrace(PrismReferenceWrapperColumnPanel.class);
	
	PrismReferenceWrapperColumnPanel(String id, IModel<PrismReferenceWrapper<R>> model, ColumnType columnType) {
		super(id, model, columnType);
	}
	
	@Override
	protected String createLabel(PrismValueWrapper<R, PrismReferenceValue> object) {
		return WebComponentUtil.getReferencedObjectDisplayNamesAndNames(object.getRealValue(), false); 
	}

	@Override
	protected Panel createValuePanel(String id, IModel<PrismReferenceWrapper<R>> model, PrismValueWrapper<R, PrismReferenceValue> object) {
		
		Panel panel;
		try {
			panel = getPageBase().initItemPanel(id, model.getObject().getTypeName(), model, null);
		} catch (SchemaException e) {
			LOGGER.error("Cannot create panel for {}", model.getObject());
			getSession().error("Cannot create panel for: " + model.getObject());
			return new ErrorPanel(id, createStringResource("PropertyPropertyWrapperColumnPanel.cannot.create.panel"));
		}
		
		return panel;
	}

	@Override
	protected Panel createLink(String id, IModel<PrismValueWrapper<R, PrismReferenceValue>> object) {
		LinkPanel linkPanel = new LinkPanel(id, new IModel<String>() {

			@Override
			public String getObject() {
				return createLabel(object.getObject());
			}
			
		}) {
			private static final long serialVersionUID = 1L;

			@Override
			public void onClick(AjaxRequestTarget target) {
				PrismReferenceWrapperColumnPanel.this.onClick(target, getModelObject().getParent());
			}

		};
		return linkPanel;
	}
	
	protected void onClick(AjaxRequestTarget target, PrismContainerValueWrapper<?> rowModel) {
	}
}
