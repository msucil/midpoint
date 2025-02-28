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

import org.apache.wicket.AttributeModifier;
import org.apache.wicket.Component;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.LambdaModel;

import com.evolveum.midpoint.prism.PrismProperty;
import com.evolveum.midpoint.prism.PrismPropertyDefinition;
import com.evolveum.midpoint.prism.PrismPropertyValue;

/**
 * @author katka
 *
 */
public class PrismPropertyHeaderPanel<T> extends ItemHeaderPanel<PrismPropertyValue<T>, PrismProperty<T>, PrismPropertyDefinition<T>, PrismPropertyWrapper<T>>{

	private static final long serialVersionUID = 1L;

	
	/**
	 * @param id
	 * @param model
	 */
	public PrismPropertyHeaderPanel(String id, IModel<PrismPropertyWrapper<T>> model) {
		super(id, model);
	}
	
	@Override
	protected Component createTitle(IModel<String> label) {
        Label displayName = new Label(ID_LABEL, label);
        
        return displayName;
        
	}
	
	@Override
	protected void initButtons() {
		// nothing to do
		
	}

}
