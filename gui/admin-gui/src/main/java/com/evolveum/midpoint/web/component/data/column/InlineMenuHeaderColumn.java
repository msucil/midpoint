/*
 * Copyright (c) 2010-2017 Evolveum
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

package com.evolveum.midpoint.web.component.data.column;

import com.evolveum.midpoint.web.component.menu.cog.InlineMenu;
import com.evolveum.midpoint.web.component.menu.cog.InlineMenuItem;
import org.apache.wicket.Component;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;

import java.io.Serializable;
import java.util.List;

/**
 * @author lazyman
 */
public class InlineMenuHeaderColumn<T extends InlineMenuable> extends InlineMenuColumn<T> {

    private IModel<List<InlineMenuItem>> menuItems;

    public InlineMenuHeaderColumn(List<InlineMenuItem> menuItems) {
        super(null);
        this.menuItems = new Model((Serializable) menuItems);
    }

    @Override
    public Component getHeader(String componentId) {
        InlineMenu inlineMenu = new com.evolveum.midpoint.web.component.menu.cog.InlineMenu(componentId, menuItems);
        inlineMenu.setOutputMarkupPlaceholderTag(true);
        inlineMenu.setOutputMarkupId(true);
        return inlineMenu;
    }
}
