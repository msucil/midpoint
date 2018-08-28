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
package com.evolveum.midpoint.web.component.prism.show;

import com.evolveum.midpoint.gui.api.component.tabs.PanelTab;
import com.evolveum.midpoint.gui.api.util.WebComponentUtil;
import com.evolveum.midpoint.gui.api.util.WebModelServiceUtils;
import com.evolveum.midpoint.model.api.ModelInteractionService;
import com.evolveum.midpoint.model.api.context.ModelContext;
import com.evolveum.midpoint.model.api.context.ModelProjectionContext;
import com.evolveum.midpoint.model.api.visualizer.Scene;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.delta.ObjectDelta;
import com.evolveum.midpoint.repo.common.ObjectResolver;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.security.api.AuthorizationConstants;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.util.DebugUtil;
import com.evolveum.midpoint.util.exception.ExpressionEvaluationException;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.util.exception.SystemException;
import com.evolveum.midpoint.util.logging.LoggingUtils;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.web.application.AuthorizationAction;
import com.evolveum.midpoint.web.application.PageDescriptor;
import com.evolveum.midpoint.web.component.AjaxButton;
import com.evolveum.midpoint.web.component.TabbedPanel;
import com.evolveum.midpoint.web.component.breadcrumbs.Breadcrumb;
import com.evolveum.midpoint.web.component.breadcrumbs.BreadcrumbPageInstance;
import com.evolveum.midpoint.web.component.util.VisibleBehaviour;
import com.evolveum.midpoint.web.component.wf.ApprovalProcessesPreviewPanel;
import com.evolveum.midpoint.web.page.admin.PageAdmin;
import com.evolveum.midpoint.web.page.admin.PageAdminObjectDetails;
import com.evolveum.midpoint.web.page.admin.roles.PageAdminRoles;
import com.evolveum.midpoint.web.page.admin.services.PageAdminServices;
import com.evolveum.midpoint.web.page.admin.users.PageAdminUsers;
import com.evolveum.midpoint.web.page.admin.workflow.EvaluatedTriggerGroupListPanel;
import com.evolveum.midpoint.web.page.admin.workflow.dto.ApprovalProcessExecutionInformationDto;
import com.evolveum.midpoint.web.page.admin.workflow.dto.EvaluatedTriggerGroupDto;
import com.evolveum.midpoint.web.util.OnePageParameterEncoder;
import com.evolveum.midpoint.xml.ns._public.common.common_3.AbstractRoleType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ApprovalSchemaExecutionInformationType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ObjectType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.PolicyRuleEnforcerHookPreviewOutputType;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.extensions.markup.html.tabs.ITab;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.WebPage;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.model.AbstractReadOnlyModel;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.apache.commons.collections.CollectionUtils.addIgnoreNull;

/**
 * @author mederly
 */
@PageDescriptor(url = "/admin/previewChanges", encoder = OnePageParameterEncoder.class, action = {
        @AuthorizationAction(actionUri = PageAdminUsers.AUTH_USERS_ALL,
                label = PageAdminUsers.AUTH_USERS_ALL_LABEL,
                description = PageAdminUsers.AUTH_USERS_ALL_DESCRIPTION),
        @AuthorizationAction(actionUri = AuthorizationConstants.AUTZ_UI_USER_URL,
                label = "PageUser.auth.user.label",
                description = "PageUser.auth.user.description"),
		@AuthorizationAction(actionUri = PageAdminRoles.AUTH_ROLE_ALL, label = PageAdminRoles.AUTH_ROLE_ALL_LABEL, description = PageAdminRoles.AUTH_ROLE_ALL_DESCRIPTION),
		@AuthorizationAction(actionUri = AuthorizationConstants.AUTZ_UI_ROLE_URL, label = "PageRole.auth.role.label", description = "PageRole.auth.role.description"),
        @AuthorizationAction(actionUri = PageAdminUsers.AUTH_ORG_ALL, label = PageAdminUsers.AUTH_ORG_ALL_LABEL, description = PageAdminUsers.AUTH_ORG_ALL_DESCRIPTION),
		@AuthorizationAction(actionUri = AuthorizationConstants.AUTZ_UI_ORG_UNIT_URL, label = "PageOrgUnit.auth.orgUnit.label", description = "PageOrgUnit.auth.orgUnit.description"),
		@AuthorizationAction(actionUri = PageAdminServices.AUTH_SERVICES_ALL, label = PageAdminServices.AUTH_SERVICES_ALL_LABEL, description = PageAdminServices.AUTH_SERVICES_ALL_DESCRIPTION),
		@AuthorizationAction(actionUri = AuthorizationConstants.AUTZ_UI_SERVICE_URL, label = "PageService.auth.role.label", description = "PageService.auth.role.description")
})
public class PagePreviewChanges<O extends ObjectType> extends PageAdmin {
	private static final long serialVersionUID = 1L;

	private static final String ID_TABBED_PANEL = "tabbedPanel";
	private static final String ID_CONTINUE_EDITING = "continueEditing";
	private static final String ID_SAVE = "save";

	private static final Trace LOGGER = TraceManager.getTrace(PagePreviewChanges.class);

	private Map<PrismObject<O>, ModelContext<O>> modelContextMap;
	private ModelInteractionService modelInteractionService;

	public PagePreviewChanges(Map<PrismObject<O>, ModelContext<O>> modelContextMap, ModelInteractionService modelInteractionService) {
		this.modelContextMap = modelContextMap;
		this.modelInteractionService = modelInteractionService;

		initLayout();
	}


	private void initLayout() {
		Form mainForm = new com.evolveum.midpoint.web.component.form.Form("mainForm");
		mainForm.setMultiPart(true);
		add(mainForm);

		List<ITab> tabs = createTabs();
		TabbedPanel<ITab> previewChangesTabbedPanel = WebComponentUtil.createTabPanel(ID_TABBED_PANEL, this, tabs, null);
		previewChangesTabbedPanel.setOutputMarkupId(true);
		mainForm.add(previewChangesTabbedPanel);

		initButtons(mainForm);
	}

	private void initButtons(Form mainForm) {
		AjaxButton cancel = new AjaxButton(ID_CONTINUE_EDITING, createStringResource("PagePreviewChanges.button.continueEditing")) {
			@Override
			public void onClick(AjaxRequestTarget target) {
				cancelPerformed(target);
			}
		};
		mainForm.add(cancel);

		AjaxButton save = new AjaxButton(ID_SAVE, createStringResource("PagePreviewChanges.button.save")) {
			@Override
			public void onClick(AjaxRequestTarget target) {
				savePerformed(target);
			}
		};
		//save.add(new EnableBehaviour(() -> violationsEmpty()));           // does not work as expected (MID-4252)

		save.add(new VisibleBehaviour(() -> violationsEmpty()));            // so hiding the button altogether
		mainForm.add(save);
	}

	//TODO relocate the logic from the loop to some util method, code repeats in PreviewChangesTabPanel
	private boolean violationsEmpty() {
		for (ModelContext<O> modelContext : modelContextMap.values()) {
			PolicyRuleEnforcerHookPreviewOutputType enforcements = modelContext != null
					? modelContext.getHookPreviewResult(PolicyRuleEnforcerHookPreviewOutputType.class)
					: null;
			List<EvaluatedTriggerGroupDto> triggerGroups = enforcements != null
					? Collections.singletonList(EvaluatedTriggerGroupDto.initializeFromRules(enforcements.getRule(), false, null))
					: Collections.emptyList();
			if (!EvaluatedTriggerGroupDto.isEmpty(triggerGroups)){
				return false;
			}
		}
		return true;
	}

	private List<ITab> createTabs(){
		List<ITab> tabs = new ArrayList<>();
		modelContextMap.forEach((object, modelContext) -> {

			tabs.add(
					new PanelTab(getTabPanelTitleModel(object)){

						private static final long serialVersionUID = 1L;

						@Override
						public WebMarkupContainer createPanel(String panelId) {
							return new PreviewChangesTabPanel(panelId, Model.of(modelContext));
						}
					});
		});
		return tabs;
	}

	private IModel<String> getTabPanelTitleModel(PrismObject<? extends ObjectType> object){
		return Model.of(WebComponentUtil.getEffectiveName(object, AbstractRoleType.F_DISPLAY_NAME));
	}


	private void cancelPerformed(AjaxRequestTarget target) {
		redirectBack();
	}

	private void savePerformed(AjaxRequestTarget target) {
		Breadcrumb bc = redirectBack();
		if (bc instanceof BreadcrumbPageInstance) {
			BreadcrumbPageInstance bcpi = (BreadcrumbPageInstance) bc;
			WebPage page = bcpi.getPage();
			if (page instanceof PageAdminObjectDetails) {
				((PageAdminObjectDetails) page).setSaveOnConfigure(true);
			} else {
				error("Couldn't save changes - unexpected referring page: " + page);
			}
		} else {
			error("Couldn't save changes - no instance for referring page; breadcrumb is " + bc);
		}
	}

	@Override
	protected void createBreadcrumb() {
		createInstanceBreadcrumb();
	}
}
