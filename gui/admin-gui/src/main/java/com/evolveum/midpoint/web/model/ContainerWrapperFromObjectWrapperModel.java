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

package com.evolveum.midpoint.web.model;

import java.util.List;

import org.apache.commons.lang.Validate;
import org.apache.wicket.model.IModel;

import com.evolveum.midpoint.gui.api.prism.PrismContainerWrapper;
import com.evolveum.midpoint.gui.api.prism.PrismObjectWrapper;
import com.evolveum.midpoint.gui.impl.prism.PrismContainerValueWrapper;
import com.evolveum.midpoint.prism.Containerable;
import com.evolveum.midpoint.prism.path.ItemPath;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ObjectType;

/**
 * Model that returns property real values. This implementation works on ObjectWrapper models (not PrismObject).
 *
 * Simple implementation, now it can't handle multivalue properties.
 *
 * @author lazyman
 * @author semancik
 */
@Deprecated
public class ContainerWrapperFromObjectWrapperModel<C extends Containerable,O extends ObjectType> extends AbstractWrapperModel<PrismContainerWrapper<C> ,O> {

   private static final long serialVersionUID = 1L;

	private static final Trace LOGGER = TraceManager.getTrace(ContainerWrapperFromObjectWrapperModel.class);

    private ItemPath path;

    public ContainerWrapperFromObjectWrapperModel(IModel<PrismObjectWrapper<O>> model, ItemPath path) {
    	super(model);
        Validate.notNull(path, "Item path must not be null.");
        this.path = path;
    }


    @Override
    public void detach() {
    }

	@Override
	public PrismContainerWrapper<C> getObject() {
//		PrismContainerWrapper<C> containerWrapper = getWrapper().findContainer(path);
//		return containerWrapper;
		return null;
	}

	@Override
	public void setObject(com.evolveum.midpoint.gui.api.prism.PrismContainerWrapper<C> arg0) {
		throw new UnsupportedOperationException("ContainerWrapperFromObjectWrapperModel.setObject called");

	}

	public IModel<? extends List<PrismContainerValueWrapper<C>>> getValuesModel() {
		return new IModel<List<PrismContainerValueWrapper<C>>>() {

			@Override
			public void detach() {
				
			}

			@Override
			public List<PrismContainerValueWrapper<C>> getObject() {
				return ContainerWrapperFromObjectWrapperModel.this.getObject().getValues();
			}

			@Override
			public void setObject(List<PrismContainerValueWrapper<C>> object) {
				throw new UnsupportedOperationException();
			}
			
		};
	}

}
