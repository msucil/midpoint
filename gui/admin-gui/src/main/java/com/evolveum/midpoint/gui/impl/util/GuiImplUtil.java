/**
 * Copyright (c) 2017 Evolveum
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
package com.evolveum.midpoint.gui.impl.util;

import com.evolveum.midpoint.gui.api.prism.ShadowWrapper;
import com.evolveum.midpoint.gui.impl.prism.PrismContainerValueWrapper;
import com.evolveum.midpoint.prism.Containerable;
import com.evolveum.midpoint.prism.path.ItemPath;
import com.evolveum.midpoint.web.page.admin.users.dto.UserDtoStatus;
import com.evolveum.midpoint.xml.ns._public.common.common_3.AbstractFormItemType;

/**
 * Class for misc GUI util methods (impl).
 *
 * @author semancik
 *
 */
public class GuiImplUtil {

	public static ItemPath getItemPath(AbstractFormItemType aItem) {
		if (aItem != null && aItem.getBinding() != null && aItem.getBinding().getPath() != null) {
			return aItem.getBinding().getPath().getItemPath();
		} else {
			return null;
		}
	}
	
	public static <C extends Containerable> String getObjectStatus(PrismContainerValueWrapper<C> object) {
		
		if(object.getParent()  instanceof ShadowWrapper) {
				if(((ShadowWrapper)object.getParent()).getProjectionStatus().equals(UserDtoStatus.DELETE)) {
					return "danger";
				}
				if(((ShadowWrapper)object.getParent()).getProjectionStatus().equals(UserDtoStatus.UNLINK)) {
					return "warning";
				}
		}
		
		switch (object.getStatus()) {
        case ADDED:
            return "success";
        case DELETED:
            return "danger";
        case NOT_CHANGED:
        default:
            return null;
		}
	}

}
