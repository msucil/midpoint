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
package com.evolveum.midpoint.web.page.admin.server.handlers;

import org.apache.wicket.model.IModel;
import org.apache.wicket.model.PropertyModel;

import com.evolveum.midpoint.gui.api.component.BasePanel;
import com.evolveum.midpoint.gui.impl.prism.PrismContainerPanel;
import com.evolveum.midpoint.web.page.admin.server.PageTaskEdit;
import com.evolveum.midpoint.web.page.admin.server.handlers.dto.GenericHandlerDto;

/**
 * @author mederly
 */
public class GenericHandlerPanel extends BasePanel<GenericHandlerDto> {
	private static final long serialVersionUID = 1L;

	public static final String ID_CONTAINER = "container";

	public GenericHandlerPanel(String id, IModel<GenericHandlerDto> model, PageTaskEdit parentPage) {
		super(id, model);
		initLayout(parentPage);
		setOutputMarkupId(true);
	}

	//TODO refactor to use registry
	private void initLayout(final PageTaskEdit parentPage) {
		PrismContainerPanel containerPanel = new PrismContainerPanel(
				ID_CONTAINER, new PropertyModel<>(getModel(), GenericHandlerDto.F_CONTAINER), null);
		add(containerPanel);

	}

}
