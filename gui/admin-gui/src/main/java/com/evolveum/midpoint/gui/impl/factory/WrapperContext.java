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
package com.evolveum.midpoint.gui.impl.factory;

import com.evolveum.midpoint.gui.api.prism.ItemStatus;
import com.evolveum.midpoint.schema.ResourceShadowDiscriminator;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.xml.ns._public.common.common_3.AuthorizationPhaseType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ResourceType;

/**
 * @author katka
 *
 */
public class WrapperContext {

	private AuthorizationPhaseType authzPhase = AuthorizationPhaseType.REQUEST;
	private Task task;
	private OperationResult result;
	
	private boolean createIfEmpty;
	
	private Boolean readOnly;
	private boolean showEmpty;
	
	private ItemStatus objectStatus;
	
	//Shadow related attributes
	private ResourceType resource;
	private ResourceShadowDiscriminator discriminator;
	
	//used e.g. for metadata - opertionsla attributes but want to create wrappers for them
	private boolean createOperational;
	
	public WrapperContext(Task task, OperationResult result) {
		this.task = task;
		this.result = result != null ? result : new OperationResult("temporary");       // TODO !!!
	}
	
	public AuthorizationPhaseType getAuthzPhase() {
		return authzPhase;
	}
	public Task getTask() {
		return task;
	}
	public OperationResult getResult() {
		return result;
	}
	public void setAuthzPhase(AuthorizationPhaseType authzPhase) {
		this.authzPhase = authzPhase;
	}
	public void setTask(Task task) {
		this.task = task;
	}
	public void setResult(OperationResult result) {
		this.result = result;
	}
	
	public boolean isCreateIfEmpty() {
		return createIfEmpty;
	}
	
	public void setCreateIfEmpty(boolean createIfEmpty) {
		this.createIfEmpty = createIfEmpty;
	};
	
	public Boolean getReadOnly() {
		return readOnly;
	}
	
	public void setReadOnly(Boolean readOnly) {
		this.readOnly = readOnly;
	}


	public ResourceType getResource() {
		return resource;
	}

	public ResourceShadowDiscriminator getDiscriminator() {
		return discriminator;
	}
	
	public void setResource(ResourceType resource) {
		this.resource = resource;
	}

	public void setDiscriminator(ResourceShadowDiscriminator discriminator) {
		this.discriminator = discriminator;
	}
	
	public boolean isShowEmpty() {
		return showEmpty;
	}
	
	public void setShowEmpty(boolean showEmpty) {
		this.showEmpty = showEmpty;
	}
	
	
	public ItemStatus getObjectStatus() {
		return objectStatus;
	}
	
	public void setObjectStatus(ItemStatus objectStatus) {
		this.objectStatus = objectStatus;
	}
	
	public boolean isCreateOperational() {
		return createOperational;
	}
	
	public void setCreateOperational(boolean createOperational) {
		this.createOperational = createOperational;
	}
}
