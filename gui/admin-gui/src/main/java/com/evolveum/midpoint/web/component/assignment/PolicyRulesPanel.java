/*
 * Copyright (c) 2018 Evolveum
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
package com.evolveum.midpoint.web.component.assignment;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.extensions.markup.html.repeater.data.table.IColumn;
import org.apache.wicket.model.IModel;

import com.evolveum.midpoint.gui.api.prism.PrismContainerWrapper;
import com.evolveum.midpoint.gui.impl.component.data.column.AbstractItemWrapperColumn.ColumnType;
import com.evolveum.midpoint.gui.impl.component.data.column.PrismContainerWrapperColumn;
import com.evolveum.midpoint.gui.impl.component.data.column.PrismPropertyWrapperColumn;
import com.evolveum.midpoint.gui.impl.prism.PrismContainerValueWrapper;
import com.evolveum.midpoint.model.api.AssignmentObjectRelation;
import com.evolveum.midpoint.prism.PrismContainerDefinition;
import com.evolveum.midpoint.prism.PrismContainerValue;
import com.evolveum.midpoint.prism.path.ItemPath;
import com.evolveum.midpoint.prism.query.ObjectQuery;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.web.component.search.SearchFactory;
import com.evolveum.midpoint.web.component.search.SearchItemDefinition;
import com.evolveum.midpoint.web.session.UserProfileStorage;
import com.evolveum.midpoint.web.session.UserProfileStorage.TableId;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ActivationType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.AreaCategoryType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.AssignmentType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ExclusionPolicyConstraintType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.PolicyConstraintsType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.PolicyRuleType;

/**
 * Created by honchar.
 * @author katkav
 */
public class PolicyRulesPanel extends AssignmentPanel {

    private static final long serialVersionUID = 1L;

	private static final Trace LOGGER = TraceManager.getTrace(PolicyRulesPanel.class);

    public PolicyRulesPanel(String id, IModel<PrismContainerWrapper<AssignmentType>> assignmentContainerWrapperModel){
        super(id, assignmentContainerWrapperModel);

    }

    protected List<IColumn<PrismContainerValueWrapper<AssignmentType>, String>> initColumns() {
        List<IColumn<PrismContainerValueWrapper<AssignmentType>, String>> columns = new ArrayList<>();


        columns.add(new PrismContainerWrapperColumn<AssignmentType>(getModel(), ItemPath.create(AssignmentType.F_POLICY_RULE, PolicyRuleType.F_POLICY_CONSTRAINTS), getPageBase()));
        
        columns.add(new PrismPropertyWrapperColumn<AssignmentType, String>(getModel(), ItemPath.create(AssignmentType.F_POLICY_RULE, PolicyRuleType.F_POLICY_SITUATION), ColumnType.STRING, getPageBase()));
        
        columns.add(new PrismContainerWrapperColumn<AssignmentType>(getModel(), ItemPath.create(AssignmentType.F_POLICY_RULE, PolicyRuleType.F_POLICY_ACTIONS), getPageBase()));
        
        columns.add(new PrismPropertyWrapperColumn<AssignmentType, Integer>(getModel(), AssignmentType.F_ORDER, ColumnType.STRING, getPageBase()));

        return columns;
    }

	@Override
	protected void initCustomPaging() {
		getAssignmentsTabStorage().setPaging(getPrismContext().queryFactory()
				.createPaging(0, ((int) getParentPage().getItemsPerPage(UserProfileStorage.TableId.POLICY_RULES_TAB_TABLE))));

	}

	@Override
	protected TableId getTableId() {
		return UserProfileStorage.TableId.POLICY_RULES_TAB_TABLE;
	}

	@Override
	protected void newAssignmentClickPerformed(AjaxRequestTarget target, AssignmentObjectRelation assignmentTargetRelation) {
        PrismContainerValue<AssignmentType> newAssignment = getModelObject().getItem().createNewValue();
        try {
			newAssignment.findOrCreateContainer(AssignmentType.F_POLICY_RULE);
		} catch (SchemaException e) {
			LOGGER.error("Cannot create policy rule assignment: {}", e.getMessage(), e);
			getSession().error("Cannot create policyRule assignment.");
			target.add(getPageBase().getFeedbackPanel());
			return;
		}
        PrismContainerValueWrapper<AssignmentType> newAssignmentWrapper = getMultivalueContainerListPanel().createNewItemContainerValueWrapper(newAssignment, getModelObject(), target);
        getMultivalueContainerListPanel().itemDetailsPerformed(target, Arrays.asList(newAssignmentWrapper));
	}

	@Override
	protected ObjectQuery createObjectQuery() {
        return getParentPage().getPrismContext().queryFor(AssignmentType.class)
                .exists(AssignmentType.F_POLICY_RULE)
                .build();
    }

	@Override
	protected List<SearchItemDefinition> createSearchableItems(PrismContainerDefinition<AssignmentType> containerDef) {
		List<SearchItemDefinition> defs = new ArrayList<>();

		SearchFactory.addSearchPropertyDef(containerDef, ItemPath.create(AssignmentType.F_ACTIVATION, ActivationType.F_ADMINISTRATIVE_STATUS), defs);
		SearchFactory.addSearchPropertyDef(containerDef, ItemPath.create(AssignmentType.F_ACTIVATION, ActivationType.F_EFFECTIVE_STATUS), defs);
		SearchFactory.addSearchPropertyDef(containerDef, ItemPath.create(AssignmentType.F_POLICY_RULE, PolicyRuleType.F_NAME), defs);
		SearchFactory.addSearchRefDef(containerDef,
				ItemPath.create(AssignmentType.F_POLICY_RULE, PolicyRuleType.F_POLICY_CONSTRAINTS,
						PolicyConstraintsType.F_EXCLUSION, ExclusionPolicyConstraintType.F_TARGET_REF), defs, AreaCategoryType.POLICY, getPageBase());

		defs.addAll(SearchFactory.createExtensionDefinitionList(containerDef));

		return defs;
	}

}
