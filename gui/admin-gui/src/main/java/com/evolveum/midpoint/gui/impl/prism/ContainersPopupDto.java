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

import java.io.Serializable;

import com.evolveum.midpoint.prism.PrismContainerDefinition;

/**
 * @author katka
 *
 */
public class ContainersPopupDto implements Serializable {
	
	private static final long serialVersionUID = 1L;
	
	private boolean selected;
	private PrismContainerDefinition<?> def;
	
	public ContainersPopupDto(boolean selected, PrismContainerDefinition<?> def) {
		this.selected = selected;
		this.def = def;
	}
	
	public String getDisplayName() {
		if (def.getDisplayName() != null) {
			return def.getDisplayName();
		}
		
		return def.getName().getLocalPart();
	}
	
	public PrismContainerDefinition<?> getDef() {
		return def;
	}
	
	public boolean isSelected() {
		return selected;
	}
	
	public void setSelected(boolean selected) {
		this.selected = selected;
	}
	
}