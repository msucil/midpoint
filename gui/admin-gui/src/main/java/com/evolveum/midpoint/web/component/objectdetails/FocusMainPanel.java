/**
 * Copyright (c) 2015-2018 Evolveum
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
package com.evolveum.midpoint.web.component.objectdetails;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.Validate;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.extensions.markup.html.tabs.ITab;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.model.IModel;
import org.apache.wicket.util.string.StringValue;

import com.evolveum.midpoint.gui.api.ComponentConstants;
import com.evolveum.midpoint.gui.api.component.tabs.CountablePanelTab;
import com.evolveum.midpoint.gui.api.component.tabs.PanelTab;
import com.evolveum.midpoint.gui.api.model.LoadableModel;
import com.evolveum.midpoint.gui.api.page.PageBase;
import com.evolveum.midpoint.gui.api.prism.ItemStatus;
import com.evolveum.midpoint.gui.api.prism.PrismContainerWrapper;
import com.evolveum.midpoint.gui.api.prism.PrismObjectWrapper;
import com.evolveum.midpoint.gui.api.prism.ShadowWrapper;
import com.evolveum.midpoint.gui.api.util.WebComponentUtil;
import com.evolveum.midpoint.gui.impl.prism.PrismContainerValueWrapper;
import com.evolveum.midpoint.model.api.ModelAuthorizationAction;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.query.ObjectQuery;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.util.exception.SystemException;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.web.component.form.Form;
import com.evolveum.midpoint.web.component.prism.ValueStatus;
import com.evolveum.midpoint.web.page.admin.PageAdminObjectDetails;
import com.evolveum.midpoint.web.page.admin.server.dto.TaskDtoProvider;
import com.evolveum.midpoint.web.page.admin.server.dto.TaskDtoProviderOptions;
import com.evolveum.midpoint.web.util.OnePageParameterEncoder;
import com.evolveum.midpoint.xml.ns._public.common.common_3.AssignmentType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.FocusType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.FormSpecificationType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ObjectFormType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.TaskExecutionStatusType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.TaskType;

/**
 * @author semancik
 *
 */
public class FocusMainPanel<F extends FocusType> extends AssignmentHolderTypeMainPanel<F> {

	private static final long serialVersionUID = 1L;

	private static final Trace LOGGER = TraceManager.getTrace(FocusMainPanel.class);

	private LoadableModel<List<ShadowWrapper>> projectionModel;
	private TaskDtoProvider taskDtoProvider;

	public FocusMainPanel(String id, LoadableModel<PrismObjectWrapper<F>> objectModel,
			LoadableModel<List<ShadowWrapper>> projectionModel,
			PageAdminObjectDetails<F> parentPage) {
		super(id, objectModel, parentPage);
		Validate.notNull(projectionModel, "Null projection model");
		this.projectionModel = projectionModel;
		initLayout(parentPage);
	}

	private void initLayout(final PageAdminObjectDetails<F> parentPage) {
		getMainForm().setMultiPart(true);

		taskDtoProvider = new TaskDtoProvider(parentPage, TaskDtoProviderOptions.minimalOptions());
		taskDtoProvider.setQuery(createTaskQuery(null, parentPage));
	}

	@Override
	protected void onInitialize() {
		super.onInitialize();
		StringValue oidValue = getPage().getPageParameters().get(OnePageParameterEncoder.PARAMETER);

		taskDtoProvider.setQuery(createTaskQuery(oidValue != null ? oidValue.toString() : null, (PageBase)getPage()));
	}

	private ObjectQuery createTaskQuery(String oid, PageBase page) {
		if (oid == null) {
			oid = "non-existent"; // TODO !!!!!!!!!!!!!!!!!!!!
		}
		return page.getPrismContext().queryFor(TaskType.class)
				.item(TaskType.F_OBJECT_REF).ref(oid)
				.and()
					.block()
						.not().item(TaskType.F_EXECUTION_STATUS).eq(TaskExecutionStatusType.CLOSED)
					.endBlock()
				.and().item(TaskType.F_PARENT).isNull()
				.build();
	}

	@Override
	protected List<ITab> createTabs(final PageAdminObjectDetails<F> parentPage) {
		List<ITab> tabs = super.createTabs(parentPage);

		List<ObjectFormType> objectFormTypes = parentPage.getObjectFormTypes();
		// default tabs are always added to component structure, visibility is decided later in
		// visible behavior based on adminGuiConfiguration
		addDefaultTabs(parentPage, tabs);
        addSpecificTabs(parentPage, tabs);
		if (objectFormTypes == null) {
			return tabs;
		}

		for (ObjectFormType objectFormType : objectFormTypes) {
			final FormSpecificationType formSpecificationType = objectFormType.getFormSpecification();
			if (formSpecificationType == null){
				continue;
			}
			String title = formSpecificationType.getTitle();
			if (title == null) {
				title = "pageAdminFocus.extended";
			}

			if (StringUtils.isEmpty(formSpecificationType.getPanelClass())) {
				continue;
			}

			tabs.add(
					new PanelTab(parentPage.createStringResource(title)) {
						private static final long serialVersionUID = 1L;

						@Override
						public WebMarkupContainer createPanel(String panelId) {
							return createTabPanel(panelId, formSpecificationType, parentPage);
						}
					});
		}

		return tabs;
	}

	protected WebMarkupContainer createTabPanel(String panelId, FormSpecificationType formSpecificationType,
			PageAdminObjectDetails<F> parentPage) {
		String panelClassName = formSpecificationType.getPanelClass();

		Class<?> panelClass;
		try {
			panelClass = Class.forName(panelClassName);
		} catch (ClassNotFoundException e) {
			throw new SystemException("Panel class '"+panelClassName+"' as specified in admin GUI configuration was not found", e);
		}
		if (AbstractObjectTabPanel.class.isAssignableFrom(panelClass)) {
			Constructor<?> constructor;
			try {
				constructor = panelClass.getConstructor(String.class, Form.class, LoadableModel.class, LoadableModel.class, PageBase.class);
			} catch (NoSuchMethodException | SecurityException e) {
				throw new SystemException("Unable to locate constructor (String,Form,LoadableModel,LoadableModel,LoadableModel,PageBase) in "+panelClass+": "+e.getMessage(), e);
			}
			AbstractObjectTabPanel<F> tabPanel;
			try {
				tabPanel = (AbstractObjectTabPanel<F>) constructor.newInstance(panelId, getMainForm(), getObjectModel(), parentPage);
			} catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
				throw new SystemException("Error instantiating "+panelClass+": "+e.getMessage(), e);
			}
			return tabPanel;
		} else if (AbstractObjectTabPanel.class.isAssignableFrom(panelClass)) {
			Constructor<?> constructor;
			try {
				constructor = panelClass.getConstructor(String.class, Form.class, LoadableModel.class, PageBase.class);
			} catch (NoSuchMethodException | SecurityException e) {
				throw new SystemException("Unable to locate constructor (String,Form,LoadableModel,PageBase) in "+panelClass+": "+e.getMessage(), e);
			}
			AbstractObjectTabPanel<F> tabPanel;
			try {
				tabPanel = (AbstractObjectTabPanel<F>) constructor.newInstance(panelId, getMainForm(), getObjectModel(), parentPage);
			} catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
				throw new SystemException("Error instantiating "+panelClass+": "+e.getMessage(), e);
			}
			return tabPanel;

		} else {
			throw new UnsupportedOperationException("Tab panels that are not subclasses of AbstractObjectTabPanel or AbstractFocusTabPanel are not supported yet (got "+panelClass+")");
		}
	}

	protected WebMarkupContainer createFocusDetailsTabPanel(String panelId, PageAdminObjectDetails<F> parentPage) {
		return new AssignmentHolderTypeDetailsTabPanel<F>(panelId, getMainForm(), getObjectModel());
	}

	protected WebMarkupContainer createFocusProjectionsTabPanel(String panelId, PageAdminObjectDetails<F> parentPage) {
		return new FocusProjectionsTabPanel<>(panelId, getMainForm(), getObjectModel(), projectionModel);
	}

	protected WebMarkupContainer createObjectHistoryTabPanel(String panelId, PageAdminObjectDetails<F> parentPage) {
		return new ObjectHistoryTabPanel<F>(panelId, getMainForm(), getObjectModel()){
			protected void currentStateButtonClicked(AjaxRequestTarget target, PrismObject<F> object, String date){
				viewObjectHistoricalDataPerformed(target, object, date);
			}
		};
	}

	protected void viewObjectHistoricalDataPerformed(AjaxRequestTarget target, PrismObject<F> object, String date){
	}

	protected IModel<PrismObject<F>> unwrapModel() {
		return new IModel<PrismObject<F>>() {

				@Override
			public PrismObject<F> getObject() {
				return getObjectWrapper().getObject();
			}
		};
	}

	protected void addSpecificTabs(final PageAdminObjectDetails<F> parentPage, List<ITab> tabs) {
    }

    private void addDefaultTabs(final PageAdminObjectDetails<F> parentPage, List<ITab> tabs) {

		tabs.add(1,
                new CountablePanelTab(parentPage.createStringResource("pageAdminFocus.projections"),
						getTabVisibility(ComponentConstants.UI_FOCUS_TAB_PROJECTIONS_URL, false, parentPage)){

                	private static final long serialVersionUID = 1L;

					@Override
					public WebMarkupContainer createPanel(String panelId) {
						return createFocusProjectionsTabPanel(panelId, parentPage);
					}

					@Override
					public String getCount() {
						return Integer.toString(projectionModel.getObject() == null ? 0 : projectionModel.getObject().size());
					}
				});

		if (WebComponentUtil.isAuthorized(ModelAuthorizationAction.AUDIT_READ.getUrl()) && getObjectWrapper().getStatus() != ItemStatus.ADDED){
			tabs.add(
					new PanelTab(parentPage.createStringResource("pageAdminFocus.objectHistory"),
							getTabVisibility(ComponentConstants.UI_FOCUS_TAB_OBJECT_HISTORY_URL, false, parentPage)){

						private static final long serialVersionUID = 1L;

						@Override
						public WebMarkupContainer createPanel(String panelId) {
							return createObjectHistoryTabPanel(panelId, parentPage);
						}
					});
		}

		tabs.add(
				new CountablePanelTab(parentPage.createStringResource("pageAdminFocus.tasks"),
						getTabVisibility(ComponentConstants.UI_FOCUS_TAB_TASKS_URL, false, parentPage)){

					private static final long serialVersionUID = 1L;

					@Override
					public WebMarkupContainer createPanel(String panelId) {
						return new FocusTasksTabPanel<>(panelId, getMainForm(), getObjectModel(), taskDtoProvider);
					}

					@Override
					public String getCount() {
						return Long.toString(taskDtoProvider == null ? 0L : taskDtoProvider.size());
					}
				});

	}

	@Override
	@Deprecated
    protected boolean areSavePreviewButtonsEnabled() {
		PrismObjectWrapper<F> focusWrapper = getObjectModel().getObject();
		PrismContainerWrapper<AssignmentType> assignmentsWrapper;
		try {
			assignmentsWrapper = focusWrapper.findContainer(FocusType.F_ASSIGNMENT);
		} catch (SchemaException e) {
			LOGGER.error("Cannot find assignment wrapper: {}", e.getMessage());
			return false;
		}
		return isAssignmentsModelChanged(assignmentsWrapper);
    }

    protected boolean isAssignmentsModelChanged(PrismContainerWrapper<AssignmentType> assignmentsWrapper){
		if (assignmentsWrapper != null) {
			for (PrismContainerValueWrapper<AssignmentType> assignmentWrapper : assignmentsWrapper.getValues()) {
				if (ValueStatus.DELETED.equals(assignmentWrapper.getStatus()) ||
						ValueStatus.ADDED.equals(assignmentWrapper.getStatus())) {
					return true;
				}
			}
		}
		return false;
	}

}
