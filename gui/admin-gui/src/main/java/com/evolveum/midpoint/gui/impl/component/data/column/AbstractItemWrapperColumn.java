/*
 * Copyright (c) 2018 Evolveum
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

import org.apache.commons.lang.Validate;
import org.apache.wicket.Component;
import org.apache.wicket.extensions.markup.html.repeater.data.grid.ICellPopulator;
import org.apache.wicket.extensions.markup.html.repeater.data.table.AbstractColumn;
import org.apache.wicket.extensions.markup.html.repeater.data.table.export.IExportableColumn;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.list.ListItem;
import org.apache.wicket.markup.html.list.ListView;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.markup.repeater.Item;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.PropertyModel;

import com.evolveum.midpoint.gui.api.page.PageBase;
import com.evolveum.midpoint.gui.api.prism.ItemWrapper;
import com.evolveum.midpoint.gui.api.prism.PrismContainerWrapper;
import com.evolveum.midpoint.gui.impl.prism.PrismContainerValueWrapper;
import com.evolveum.midpoint.gui.impl.prism.PrismValueWrapper;
import com.evolveum.midpoint.prism.Containerable;
import com.evolveum.midpoint.prism.PrismContainerDefinition;
import com.evolveum.midpoint.prism.path.ItemPath;

/**
 * @author skublik
 */
public abstract class AbstractItemWrapperColumn<C extends Containerable, VW extends PrismValueWrapper> extends AbstractColumn<PrismContainerValueWrapper<C>, String> implements IExportableColumn<PrismContainerValueWrapper<C>, String>{

	public enum ColumnType {
		LINK,
		STRING,
		VALUE;
	}
	
	private static final long serialVersionUID = 1L;
	protected ItemPath itemName;
	
	private ColumnType columnType;
	
	private static final String ID_VALUE = "value";
	
	
	private IModel<? extends PrismContainerDefinition<C>> mainModel = null;
	
	AbstractItemWrapperColumn(IModel<? extends PrismContainerDefinition<C>> mainModel, ItemPath itemName, ColumnType columnType) {
		super(null);
		Validate.notNull(mainModel, "no model");
		Validate.notNull(mainModel.getObject(), "no ContainerWrappe from model");
		Validate.notNull(itemName, "no qName");
		this.mainModel = mainModel;
		this.itemName = itemName;
		this.columnType = columnType;
	}
	
	
	@Override
	public Component getHeader(String componentId) {
		return createHeader(componentId, mainModel);
	}

	@Override
	public void populateItem(Item<ICellPopulator<PrismContainerValueWrapper<C>>> cellItem, String componentId,
			IModel<PrismContainerValueWrapper<C>> rowModel) {
		
		cellItem.add(createColumnPanel(componentId, (IModel) getDataModel(rowModel)));
		
	}
	
	protected abstract Component createHeader(String componentId, IModel<? extends PrismContainerDefinition<C>> mainModel);
	protected abstract <IW extends ItemWrapper> Component createColumnPanel(String componentId, IModel<IW> rowModel);
	
	
	public ColumnType getColumnType() {
		return columnType;
	}
	
	
}
