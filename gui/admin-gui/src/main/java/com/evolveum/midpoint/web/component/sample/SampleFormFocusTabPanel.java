/**
 * Copyright (c) 2016-2018 Evolveum
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
package com.evolveum.midpoint.web.component.sample;

import java.util.ArrayList;
import java.util.List;

import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.basic.Label;

import com.evolveum.midpoint.gui.api.model.LoadableModel;
import com.evolveum.midpoint.gui.api.prism.PrismObjectWrapper;
import com.evolveum.midpoint.gui.api.prism.ShadowWrapper;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.util.exception.CommunicationException;
import com.evolveum.midpoint.util.exception.ConfigurationException;
import com.evolveum.midpoint.util.exception.ExpressionEvaluationException;
import com.evolveum.midpoint.util.exception.ObjectNotFoundException;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.util.exception.SecurityViolationException;
import com.evolveum.midpoint.util.logging.LoggingUtils;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.web.component.assignment.SimpleRoleSelector;
import com.evolveum.midpoint.web.component.form.Form;
import com.evolveum.midpoint.web.component.objectdetails.AbstractObjectTabPanel;
import com.evolveum.midpoint.web.model.PrismContainerWrapperModel;
import com.evolveum.midpoint.xml.ns._public.common.common_3.AssignmentHolderType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.AssignmentType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.FocusType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.RoleType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.UserType;
import com.evolveum.prism.xml.ns._public.types_3.PolyStringType;

/**
 * Sample showing a custom focus form that displays semi-static form.
 *
 * @author Radovan Semancik
 *
 */
public class SampleFormFocusTabPanel<F extends FocusType> extends AbstractObjectTabPanel<F> {
	private static final long serialVersionUID = 1L;
	
	private static final String DOT_CLASS = SampleFormFocusTabPanel.class.getName() + ".";
    private static final String OPERATION_SEARCH_ROLES = DOT_CLASS + "searchRoles";

    private static final String ID_HEADER = "header";

    private static final String ID_PROP_NAME = "propName";
    private static final String ID_PROP_FULL_NAME = "propFullName";

    private static final String ID_ROLES = "roles";

    private static final Trace LOGGER = TraceManager.getTrace(SampleFormFocusTabPanel.class);

    public SampleFormFocusTabPanel(String id, Form<PrismObjectWrapper<F>> mainForm,
                                   LoadableModel<PrismObjectWrapper<F>> focusWrapperModel,
                                   LoadableModel<List<ShadowWrapper>> projectionModel) {
        super(id, mainForm, focusWrapperModel);

    }

    @Override
    protected void onInitialize() {
    	super.onInitialize();
    	initLayout();
    }

    private void initLayout() {
        add(new Label(ID_HEADER, "Object details"));
        WebMarkupContainer body = new WebMarkupContainer("body");
        add(body);

        addPrismPropertyPanel(body, ID_PROP_NAME, PolyStringType.COMPLEX_TYPE, FocusType.F_NAME);
        addPrismPropertyPanel(body, ID_PROP_FULL_NAME, PolyStringType.COMPLEX_TYPE, UserType.F_FULL_NAME);

        // TODO: create proxy for these operations
        Task task = getPageBase().createSimpleTask(OPERATION_SEARCH_ROLES);
        List<PrismObject<RoleType>> availableRoles;
        try {
            availableRoles = getPageBase().getModelService().searchObjects(RoleType.class, null, null, task, task.getResult());
        } catch (SchemaException | ObjectNotFoundException | SecurityViolationException | CommunicationException |
        		ConfigurationException | ExpressionEvaluationException e) {
            task.getResult().recordFatalError(e);
            LoggingUtils.logUnexpectedException(LOGGER, "Couldn't load roles", e);
            availableRoles = new ArrayList<>();
            // TODO: better error reporting
        }

        PrismContainerWrapperModel<F, AssignmentType> assignmentsModel = PrismContainerWrapperModel.fromContainerWrapper(getObjectWrapperModel(), AssignmentHolderType.F_ASSIGNMENT);
        add(new SimpleRoleSelector<F,RoleType>(ID_ROLES, assignmentsModel, availableRoles));
    }

}
