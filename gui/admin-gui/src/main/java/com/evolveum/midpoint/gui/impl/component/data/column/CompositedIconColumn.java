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

import com.evolveum.midpoint.gui.impl.component.icon.CompositedIcon;
import org.apache.commons.lang3.StringUtils;
import org.apache.wicket.extensions.markup.html.repeater.data.grid.ICellPopulator;
import org.apache.wicket.extensions.markup.html.repeater.data.table.AbstractColumn;
import org.apache.wicket.extensions.markup.html.repeater.data.table.export.IExportableColumn;
import org.apache.wicket.markup.repeater.Item;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;

/**
 * @author skublik
 */
public abstract class CompositedIconColumn<T> extends AbstractColumn<T, String> implements IExportableColumn<T, String> {
    private static final long serialVersionUID = 1L;

    public CompositedIconColumn(IModel<String> displayModel) {
        super(displayModel);
    }

    @Override
    public String getCssClass() {
        IModel<String> display = getDisplayModel();
        if (display != null && StringUtils.isNotEmpty(display.getObject())) {
            return null;
        }

        return "composited-icon";
    }

    @Override
    public void populateItem(Item<ICellPopulator<T>> cellItem, String componentId, IModel<T> rowModel) {
        cellItem.add(new CompositedIconPanel(componentId, getCompositedIcon(rowModel)));
    }

    protected abstract CompositedIcon getCompositedIcon(final IModel<T> rowModel);
    
    @Override
    public IModel<String> getDataModel(IModel<T> rowModel) {
        return Model.of("");
    }
    
    
}
