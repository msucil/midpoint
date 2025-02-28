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
package com.evolveum.midpoint.web.page.admin.roles;

import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.security.api.AuthorizationConstants;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.web.application.AuthorizationAction;
import com.evolveum.midpoint.web.application.PageDescriptor;
import com.evolveum.midpoint.web.component.FocusSummaryPanel;
import com.evolveum.midpoint.web.component.objectdetails.AbstractObjectMainPanel;
import com.evolveum.midpoint.web.component.objectdetails.AbstractRoleMainPanel;
import com.evolveum.midpoint.web.component.progress.ProgressReportingAwarePage;
import com.evolveum.midpoint.web.page.admin.PageAdminAbstractRole;
import com.evolveum.midpoint.web.page.admin.roles.component.RoleSummaryPanel;
import com.evolveum.midpoint.web.util.OnePageParameterEncoder;
import com.evolveum.midpoint.xml.ns._public.common.common_3.OrgType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.RoleType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.UserType;

import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.model.Model;
import org.apache.wicket.request.mapper.parameter.PageParameters;

/**
 * @author shood
 * @author semancik
 */
@PageDescriptor(url = "/admin/role", encoder = OnePageParameterEncoder.class, action = {
		@AuthorizationAction(actionUri = AuthorizationConstants.AUTZ_UI_ROLES_ALL_URL, label = "PageAdminRoles.auth.roleAll.label", description = "PageAdminRoles.auth.roleAll.description"),
		@AuthorizationAction(actionUri = AuthorizationConstants.AUTZ_UI_ROLE_URL, label = "PageRole.auth.role.label", description = "PageRole.auth.role.description") })
public class PageRole extends PageAdminAbstractRole<RoleType> implements ProgressReportingAwarePage {
	private static final long serialVersionUID = 1L;

	public static final String AUTH_ROLE_ALL = AuthorizationConstants.AUTZ_UI_ROLES_ALL_URL;
	public static final String AUTH_ROLE_ALL_LABEL = "PageAdminRoles.auth.roleAll.label";
	public static final String AUTH_ROLE_ALL_DESCRIPTION = "PageAdminRoles.auth.roleAll.description";

	private static final Trace LOGGER = TraceManager.getTrace(PageRole.class);

	public PageRole() {
		super();
	}

	public PageRole(PageParameters parameters) {
		super(parameters);
	}

	public PageRole(final PrismObject<RoleType> role) {
		super(role);
	}

	public PageRole(final PrismObject<RoleType> userToEdit, boolean isNewObject) {
		super(userToEdit, isNewObject);
	}
	
	public PageRole(final PrismObject<RoleType> abstractRole, boolean isNewObject, boolean isReadonly) {
		super(abstractRole, isNewObject, isReadonly);
	}
	
	@Override
	protected RoleType createNewObject() {
		return new RoleType();
	}

	@Override
    public Class<RoleType> getCompileTimeClass() {
		return RoleType.class;
	}

	@Override
	protected Class getRestartResponsePage() {
		return PageRoles.class;
	}

	@Override
	protected FocusSummaryPanel<RoleType> createSummaryPanel() {
    	return new RoleSummaryPanel(ID_SUMMARY_PANEL, Model.of(getObjectModel().getObject().getObject().asObjectable()), this);
    }

	@Override
	protected AbstractObjectMainPanel<RoleType> createMainPanel(String id) {
		return new AbstractRoleMainPanel<RoleType>(id, getObjectModel(), getProjectionModel(), this) {
			private static final long serialVersionUID = 1L;

			@Override
			protected boolean isFocusHistoryPage(){
				return PageRole.this.isFocusHistoryPage();
			}

			@Override
			protected void viewObjectHistoricalDataPerformed(AjaxRequestTarget target, PrismObject<RoleType> object, String date){
				PageRole.this.navigateToNext(new PageRoleHistory(object, date));
			}
		};
	}
}
