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

package com.evolveum.midpoint.web.component.wizard.resource;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.extensions.markup.html.tabs.AbstractTab;
import org.apache.wicket.extensions.markup.html.tabs.ITab;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.model.Model;
import org.jetbrains.annotations.NotNull;

import com.evolveum.midpoint.gui.api.model.NonEmptyLoadableModel;
import com.evolveum.midpoint.gui.api.page.PageBase;
import com.evolveum.midpoint.gui.api.prism.ItemStatus;
import com.evolveum.midpoint.gui.api.prism.PrismContainerWrapper;
import com.evolveum.midpoint.gui.api.util.WebComponentUtil;
import com.evolveum.midpoint.gui.api.util.WebModelServiceUtils;
import com.evolveum.midpoint.gui.impl.factory.WrapperContext;
import com.evolveum.midpoint.prism.ItemDefinition;
import com.evolveum.midpoint.prism.PrismContainer;
import com.evolveum.midpoint.prism.PrismContainerDefinition;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.delta.ItemDelta;
import com.evolveum.midpoint.prism.delta.ObjectDelta;
import com.evolveum.midpoint.prism.schema.PrismSchema;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.schema.util.ConnectorTypeUtil;
import com.evolveum.midpoint.schema.util.ResourceTypeUtil;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.util.exception.SystemException;
import com.evolveum.midpoint.util.logging.LoggingUtils;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.web.component.AjaxSubmitButton;
import com.evolveum.midpoint.web.component.TabbedPanel;
import com.evolveum.midpoint.web.component.util.VisibleEnableBehaviour;
import com.evolveum.midpoint.web.component.wizard.WizardStep;
import com.evolveum.midpoint.web.page.admin.resources.PageResourceWizard;
import com.evolveum.midpoint.web.page.admin.resources.component.TestConnectionResultPanel;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ConnectorConfigurationType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ConnectorType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ResourceType;

/**
 * @author lazyman
 * @author mederly
 */
public class ConfigurationStep extends WizardStep {

	private static final Trace LOGGER = TraceManager.getTrace(ConfigurationStep.class);

    private static final String DOT_CLASS = ConfigurationStep.class.getName() + ".";
    private static final String TEST_CONNECTION = DOT_CLASS + "testConnection";
    private static final String OPERATION_SAVE = DOT_CLASS + "saveResource";
    private static final String OPERATION_CREATE_CONFIGURATION_WRAPPERS = "createConfigurationWrappers";

    private static final String ID_CONFIGURATION = "configuration";
    private static final String ID_TEST_CONNECTION = "testConnection";
	private static final String ID_MAIN = "main";

	final private NonEmptyLoadableModel<PrismObject<ResourceType>> resourceModelNoFetch;
	final private NonEmptyLoadableModel<List<PrismContainerWrapper<?>>> configurationPropertiesModel;
	final private PageResourceWizard parentPage;

    public ConfigurationStep(NonEmptyLoadableModel<PrismObject<ResourceType>> modelNoFetch, final PageResourceWizard parentPage) {
        super(parentPage);
        this.resourceModelNoFetch = modelNoFetch;
		this.parentPage = parentPage;

        this.configurationPropertiesModel = new NonEmptyLoadableModel<List<PrismContainerWrapper<?>>>(false) {
            
        	private static final long serialVersionUID = 1L;

			@Override
			@NotNull
            protected List<PrismContainerWrapper<?>> load() {
				try {
					return createConfigContainerWrappers();
				} catch (SchemaException e) {
					throw new SystemException(e.getMessage(), e);
				}
            }
		};
		parentPage.registerDependentModel(configurationPropertiesModel);

        initLayout();
    }

	@NotNull
	private List<PrismContainerWrapper<?>> createConfigContainerWrappers() throws SchemaException {
		PrismObject<ResourceType> resource = resourceModelNoFetch.getObject();
		PrismContainer<ConnectorConfigurationType> configuration = resource.findContainer(ResourceType.F_CONNECTOR_CONFIGURATION);
		
		List<PrismContainerWrapper<?>> containerWrappers = new ArrayList<>();

		if(parentPage.isNewResource()) {
			return containerWrappers;
		}
		
		if (configuration == null) {
			PrismObject<ConnectorType> connector = ResourceTypeUtil.getConnectorIfPresent(resource);
			if (connector == null) {
				throw new IllegalStateException("No resolved connector object in resource object");
			}
			ConnectorType connectorType = connector.asObjectable();
			PrismSchema schema;
			try {
				schema = ConnectorTypeUtil.parseConnectorSchema(connectorType, parentPage.getPrismContext());
			} catch (SchemaException e) {
				throw new SystemException("Couldn't parse connector schema: " + e.getMessage(), e);
			}
			PrismContainerDefinition<ConnectorConfigurationType> definition = ConnectorTypeUtil.findConfigurationContainerDefinition(connectorType, schema);
			// Fixing (errorneously) set maxOccurs = unbounded. See MID-2317 and related issues.
			PrismContainerDefinition<ConnectorConfigurationType> definitionFixed = definition.clone();
			definitionFixed.toMutable().setMaxOccurs(1);
			configuration = definitionFixed.instantiate();
		}

		List<PrismContainerDefinition> containerDefinitions = getSortedConfigContainerDefinitions(configuration);
		Task task = getPageBase().createSimpleTask(OPERATION_CREATE_CONFIGURATION_WRAPPERS);
		
		for (PrismContainerDefinition<?> containerDef : containerDefinitions) {
			PrismContainer container = configuration.findContainer(containerDef.getName());
			ItemStatus status = ItemStatus.NOT_CHANGED;
			if (container == null) {
				status = ItemStatus.ADDED;
				container = configuration.findOrCreateContainer(containerDef.getName());
			}

			WrapperContext ctx = new WrapperContext(task, getResult());
			ctx.setReadOnly(parentPage.isReadOnly());
			PrismContainerWrapper<?> containerWrapper = getPageBase().createItemWrapper(container, status, ctx);
			containerWrappers.add(containerWrapper);
		}
		return containerWrappers;
	}

	@NotNull
	private List<PrismContainerDefinition> getSortedConfigContainerDefinitions(PrismContainer<ConnectorConfigurationType> configuration) {
		List<PrismContainerDefinition> relevantDefinitions = new ArrayList<>();
		for (ItemDefinition<?> def : configuration.getDefinition().getDefinitions()) {
			if (def instanceof PrismContainerDefinition) {
				relevantDefinitions.add((PrismContainerDefinition) def);
			}
		}
		Collections.sort(relevantDefinitions, new Comparator<PrismContainerDefinition>() {
			@Override
			public int compare(PrismContainerDefinition o1, PrismContainerDefinition o2) {
				int ord1 = o1.getDisplayOrder() != null ? o1.getDisplayOrder() : Integer.MAX_VALUE;
				int ord2 = o2.getDisplayOrder() != null ? o2.getDisplayOrder() : Integer.MAX_VALUE;
				return Integer.compare(ord1, ord2);
			}
		});
		return relevantDefinitions;
	}

//     @Override
//     protected void onConfigure() {
//             updateConfigurationTabs();
//     }

	private void initLayout() {
    	com.evolveum.midpoint.web.component.form.Form form = new com.evolveum.midpoint.web.component.form.Form<>(ID_MAIN, true);
        form.setOutputMarkupId(true);
        add(form);

		form.add(WebComponentUtil.createTabPanel(ID_CONFIGURATION, parentPage, createConfigurationTabs(), null));

		AjaxSubmitButton testConnection = new AjaxSubmitButton(ID_TEST_CONNECTION,
                createStringResource("ConfigurationStep.button.testConnection")) {

            @Override
            protected void onError(final AjaxRequestTarget target) {
                WebComponentUtil.refreshFeedbacks(form, target);
            }

            @Override
            protected void onSubmit(AjaxRequestTarget target) {
                testConnectionPerformed(target);
            }
        };
		testConnection.add(new VisibleEnableBehaviour() {
			@Override
			public boolean isVisible() {
				return !parentPage.isReadOnly();
			}
		});
        add(testConnection);
    }

	private List<ITab> createConfigurationTabs() {
		final com.evolveum.midpoint.web.component.form.Form form = getForm();
		List<ITab> tabs = new ArrayList<>();
		List<PrismContainerWrapper<?>> wrappers = configurationPropertiesModel.getObject();
		for (final PrismContainerWrapper<?> wrapper : wrappers) {
			String tabName = getString(wrapper.getDisplayName(), null, wrapper.getDisplayName());
			tabs.add(new AbstractTab(new Model<>(tabName)) {
				private static final long serialVersionUID = 1L;

				@Override
				public WebMarkupContainer getPanel(String panelId) {
					try {
						return getPageBase().initItemPanel(panelId, wrapper.getTypeName(), Model.of(wrapper), null);
					} catch (SchemaException e) {
						LOGGER.error("Cannot create panel for {}, reason: {}", wrapper.getTypeName(), e.getMessage(), e);
						getSession().error("Cannot create panel for " + wrapper.getTypeName() + ", reason: " + e.getMessage());
						return null;
					}
				}
			});
		}
		
		return tabs;
	}
	
	public void updateConfigurationTabs() {
		
		TabbedPanel<ITab> tabbedPanel = getConfigurationTabbedPanel();
		List<ITab> tabs = tabbedPanel.getTabs().getObject();
		tabs.clear();

		tabs.addAll(createConfigurationTabs());
		if (tabs.size() == 0){
			return;
		}
		int i = tabbedPanel.getSelectedTab();
		if (i < 0 || i > tabs.size()) {
			i = 0;
		}
		tabbedPanel.setSelectedTab(i);
	}

	@SuppressWarnings("unchecked")
	private com.evolveum.midpoint.web.component.form.Form getForm() {
		return (com.evolveum.midpoint.web.component.form.Form) get(ID_MAIN);
	}

	@SuppressWarnings("unchecked")
	private TabbedPanel<ITab> getConfigurationTabbedPanel() {
		return (TabbedPanel<ITab>) get(createComponentPath(ID_MAIN, ID_CONFIGURATION));
	}

	// copied from PageResource, TODO deduplicate
	private void testConnectionPerformed(AjaxRequestTarget target) {
		parentPage.refreshIssues(null);
		if (parentPage.isReadOnly() || !isComplete()) {
			return;
		}
		saveChanges();

		PageBase page = getPageBase();
		TestConnectionResultPanel testConnectionPanel = new TestConnectionResultPanel(page.getMainPopupBodyId(), resourceModelNoFetch.getObject().getOid(), getPage());
		testConnectionPanel.setOutputMarkupId(true);
		page.showMainPopup(testConnectionPanel, target);
//		page.showResult(result, "Test connection failed", false);
		target.add(page.getFeedbackPanel());
		target.add(getForm());
	}

	@Override
    public void applyState() {
		parentPage.refreshIssues(null);
		if (parentPage.isReadOnly() || !isComplete()) {
			return;
		}
		saveChanges();
    }

    private void saveChanges() {
        Task task = parentPage.createSimpleTask(OPERATION_SAVE);
        OperationResult result = task.getResult();
		boolean saved = false;
        try {
            List<PrismContainerWrapper<?>> wrappers = configurationPropertiesModel.getObject();
            
            ObjectDelta delta = parentPage.getPrismContext().deltaFactory().object()
					.createEmptyModifyDelta(ResourceType.class, parentPage.getEditedResourceOid()
					);
            
            
            
            for (PrismContainerWrapper<?> wrapper : wrappers) {
            	Collection<ItemDelta> wrapperDetla = wrapper.getDelta();
            	if (wrapperDetla == null || wrapperDetla.isEmpty()) {
            		continue;
            	}
            	delta.addModifications(wrapperDetla);
            	
            }
			
//			for (ContainerWrapperImpl wrapper : wrappers) {
//				wrapper.collectModifications(delta);
//			}
			parentPage.getPrismContext().adopt(delta);
			if (!delta.isEmpty()) {
				parentPage.logDelta(delta);
				WebModelServiceUtils.save(delta, result, parentPage);
				parentPage.resetModels();
				saved = true;
			}
        } catch (Exception ex) {
            LoggingUtils.logUnexpectedException(LOGGER, "Error occurred during saving changes", ex);
            result.recordFatalError("Couldn't save configuration changes.", ex);
        } finally {
            result.computeStatusIfUnknown();
            setResult(result);
        }

		if (parentPage.showSaveResultInPage(saved, result)) {
            parentPage.showResult(result);
        }

		configurationPropertiesModel.reset();
		updateConfigurationTabs();

	}
}
