/*
 * Copyright (c) 2016-2018 Evolveum
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.evolveum.midpoint.web.page.self;

import com.evolveum.midpoint.gui.api.component.BasePanel;
import com.evolveum.midpoint.gui.api.model.LoadableModel;
import com.evolveum.midpoint.gui.api.util.WebComponentUtil;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.query.ObjectFilter;
import com.evolveum.midpoint.prism.query.ObjectQuery;
import com.evolveum.midpoint.schema.constants.ObjectTypes;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.schema.util.ObjectQueryUtil;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.web.component.AjaxButton;
import com.evolveum.midpoint.web.component.assignment.*;
import com.evolveum.midpoint.web.component.data.ObjectDataProvider;
import com.evolveum.midpoint.web.component.input.RelationDropDownChoicePanel;
import com.evolveum.midpoint.web.component.search.SearchFactory;
import com.evolveum.midpoint.web.component.search.SearchPanel;
import com.evolveum.midpoint.web.component.util.VisibleBehaviour;
import com.evolveum.midpoint.web.component.util.VisibleEnableBehaviour;
import com.evolveum.midpoint.web.page.admin.users.dto.UserDtoStatus;
import com.evolveum.midpoint.web.session.RoleCatalogStorage;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;
import org.apache.wicket.AttributeModifier;
import org.apache.wicket.ajax.AjaxChannel;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.attributes.AjaxRequestAttributes;
import org.apache.wicket.behavior.AttributeAppender;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.repeater.Item;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;

import javax.xml.namespace.QName;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by honchar.
 */
public abstract class AbstractShoppingCartTabPanel<R extends AbstractRoleType> extends BasePanel {
    private static final long serialVersionUID = 1L;

    private static final String ID_SHOPPING_CART_CONTAINER = "shoppingCartContainer";
    private static final String ID_SHOPPING_CART_ITEMS_PANEL = "shoppingCartItemsPanel";
    private static final String ID_SEARCH_FORM = "searchForm";
    private static final String ID_SEARCH = "search";
    private static final String ID_ADD_ALL_BUTTON = "addAllButton";
    private static final String ID_GO_TO_SHOPPING_CART_BUTTON = "goToShoppingCart";
    private static final String ID_PARAMETERS_PANEL = "parametersPanel";
    private static final String ID_RELATION_CONTAINER = "relationContainer";
    private static final String ID_RELATION = "relation";
    private static final String ID_TARGET_USER_PANEL = "targetUserPanel";

    private static final String DOT_CLASS = AbstractShoppingCartTabPanel.class.getName() + ".";
    private static final String OPERATION_LOAD_ASSIGNABLE_ROLES = DOT_CLASS + "loadAssignableRoles";
    private static final String OPERATION_LOAD_ASSIGNMENTS_LIMIT = DOT_CLASS + "loadAssignmentsLimit";
    private static final Trace LOGGER = TraceManager.getTrace(AbstractShoppingCartTabPanel.class);

    private RoleManagementConfigurationType roleManagementConfig;

    public AbstractShoppingCartTabPanel(String id, RoleManagementConfigurationType roleManagementConfig){
        super(id);
        this.roleManagementConfig = roleManagementConfig;
    }

    @Override
    protected void onInitialize(){
        super.onInitialize();
        initLayout();
    }

    private void initLayout(){
        setOutputMarkupId(true);

        initLeftSidePanel();

        WebMarkupContainer shoppingCartContainer = new WebMarkupContainer(ID_SHOPPING_CART_CONTAINER);
        shoppingCartContainer.setOutputMarkupId(true);
        appendItemsPanelStyle(shoppingCartContainer);
        add(shoppingCartContainer);

        initSearchPanel(shoppingCartContainer);
        initShoppingCartItemsPanel(shoppingCartContainer);
        initParametersPanel(shoppingCartContainer);
    }

    protected void initLeftSidePanel(){
    }

    private void initSearchPanel(WebMarkupContainer shoppingCartContainer) {
        final Form searchForm = new com.evolveum.midpoint.web.component.form.Form(ID_SEARCH_FORM);
        searchForm.setOutputMarkupId(true);

        SearchPanel search = new SearchPanel(ID_SEARCH,
                Model.of(getRoleCatalogStorage().getSearch() != null ? getRoleCatalogStorage().getSearch() :
                        SearchFactory.createSearch((Class<R>)WebComponentUtil.qnameToClass(getPageBase().getPrismContext(), getQueryType()), getPageBase())),
                false) {
            private static final long serialVersionUID = 1L;

            @Override
            public void searchPerformed(ObjectQuery query, AjaxRequestTarget target) {
                AbstractShoppingCartTabPanel.this.searchPerformed(query, target);
            }
        };
        searchForm.add(search);
        shoppingCartContainer.add(searchForm);
    }

    protected void searchPerformed(ObjectQuery query, AjaxRequestTarget target) {
        getRoleCatalogStorage().setSearch(getSearchPanel().getModelObject());
        target.add(AbstractShoppingCartTabPanel.this);
    }

    private void initShoppingCartItemsPanel(WebMarkupContainer shoppingCartContainer){
        GridViewComponent<ObjectDataProvider<AssignmentEditorDto, AbstractRoleType>> catalogItemsGrid =
                new GridViewComponent<ObjectDataProvider<AssignmentEditorDto, AbstractRoleType>>(ID_SHOPPING_CART_ITEMS_PANEL,
                new LoadableModel<ObjectDataProvider<AssignmentEditorDto, AbstractRoleType>>() {
                    @Override
                    protected ObjectDataProvider<AssignmentEditorDto, AbstractRoleType> load() {
                        return getTabPanelProvider();
                    }
                }) {
            private static final long serialVersionUID = 1L;

            @Override
            protected void populateItem(Item item) {
                item.add(new RoleCatalogItemButton(getCellItemId(), item.getModel()){
                    private static final long serialVersionUID = 1L;

                    @Override
                    protected void assignmentAddedToShoppingCartPerformed(AjaxRequestTarget target){
                        int assignmentsLimit = getRoleCatalogStorage().getAssignmentRequestLimit();
                        if (AssignmentsUtil.isShoppingCartAssignmentsLimitReached(assignmentsLimit, AbstractShoppingCartTabPanel.this.getPageBase())) {
                            target.add(AbstractShoppingCartTabPanel.this);
                        }
                        AbstractShoppingCartTabPanel.this.assignmentAddedToShoppingCartPerformed(target);
                    }

                    @Override
                    protected QName getNewAssignmentRelation(){
                        return AbstractShoppingCartTabPanel.this.getNewAssignmentRelation();
                    }
                });
            }
        };
        catalogItemsGrid.add(new VisibleEnableBehaviour(){
            private static final long serialVersionUID = 1L;
            @Override
            public boolean isVisible(){
                return isShoppingCartItemsPanelVisible();
            }
        });
        catalogItemsGrid.setOutputMarkupId(true);
        shoppingCartContainer.add(catalogItemsGrid);
    }

    private void initParametersPanel(WebMarkupContainer shoppingCartContainer){
        WebMarkupContainer parametersPanel = new WebMarkupContainer(ID_PARAMETERS_PANEL);
        parametersPanel.setOutputMarkupId(true);
        shoppingCartContainer.add(parametersPanel);

        initTargetUserSelectionPanel(parametersPanel);
        initRelationPanel(parametersPanel);
        initButtonsPanel(parametersPanel);
    }

    private void initTargetUserSelectionPanel(WebMarkupContainer parametersPanel){
        UserSelectionButton targetUserPanel = new UserSelectionButton(ID_TARGET_USER_PANEL,
                new IModel<List<UserType>>() {
                    @Override
                    public List<UserType> getObject() {
                        return getRoleCatalogStorage().getTargetUserList();
                    }
                },
                true, createStringResource("AssignmentCatalogPanel.selectTargetUser")){
            private static final long serialVersionUID = 1L;

            @Override
            protected String getUserButtonLabel(){
                return getTargetUserSelectionButtonLabel(getModelObject());
            }

            @Override
            protected String getTargetUserButtonClass(){
                return "btn-sm";
            }

            @Override
            protected void onDeleteSelectedUsersPerformed(AjaxRequestTarget target){
                super.onDeleteSelectedUsersPerformed(target);
                getRoleCatalogStorage().setTargetUserList(new ArrayList<>());

                target.add(AbstractShoppingCartTabPanel.this);
//                target.add(parametersPanel.get(ID_TARGET_USER_PANEL));
            }

            @Override
            protected void multipleUsersSelectionPerformed(AjaxRequestTarget target, List<UserType> usersList){
                getRoleCatalogStorage().setTargetUserList(usersList);
                target.add(AbstractShoppingCartTabPanel.this);
//                target.add(parametersPanel.get(ID_TARGET_USER_PANEL));
            }

        };
        targetUserPanel.setOutputMarkupId(true);
        parametersPanel.add(targetUserPanel);
    }

    private void initRelationPanel(WebMarkupContainer parametersPanel){
        WebMarkupContainer relationContainer = new WebMarkupContainer(ID_RELATION_CONTAINER);
        relationContainer.setOutputMarkupId(true);
        parametersPanel.add(relationContainer);

        List<QName> availableRelations = WebComponentUtil.getCategoryRelationChoices(AreaCategoryType.SELF_SERVICE, getPageBase());
        relationContainer.add(new RelationDropDownChoicePanel(ID_RELATION, getRoleCatalogStorage().getSelectedRelation(),
                availableRelations, false){
            private static final long serialVersionUID = 1L;

            @Override
            protected void onValueChanged(AjaxRequestTarget target){
                getRoleCatalogStorage().setSelectedRelation(getRelationValue());
            }

            @Override
            protected IModel<String> getRelationLabelModel(){
                return Model.of();
            }
        });
    }

    private void initButtonsPanel(WebMarkupContainer parametersPanel){
        AjaxButton addAllButton = new AjaxButton(ID_ADD_ALL_BUTTON, createStringResource("AbstractShoppingCartTabPanel.addAllButton")) {
            private static final long serialVersionUID = 1L;

            @Override
            public void onClick(AjaxRequestTarget ajaxRequestTarget) {
                AbstractShoppingCartTabPanel.this.addAllAssignmentsPerformed(ajaxRequestTarget);
            }

            @Override
            protected void updateAjaxAttributes(AjaxRequestAttributes attributes) {
                super.updateAjaxAttributes(attributes);
                attributes.setEventPropagation(AjaxRequestAttributes.EventPropagation.BUBBLE);
            }
        };
        addAllButton.add(new VisibleEnableBehaviour(){
            private static final long serialVersionUID = 1L;

            @Override
            public boolean isVisible(){
                ObjectDataProvider provider = getGridViewComponent().getProvider();
                return provider != null && provider.size() > 0;

            }

            @Override
            public boolean isEnabled() {
                int assignmentsLimit = AssignmentsUtil.loadAssignmentsLimit(new OperationResult(OPERATION_LOAD_ASSIGNMENTS_LIMIT),
                        AbstractShoppingCartTabPanel.this.getPageBase());
                return !AssignmentsUtil.isShoppingCartAssignmentsLimitReached(assignmentsLimit, AbstractShoppingCartTabPanel.this.getPageBase());
            }
        });
        addAllButton.add(AttributeAppender.append("title",
                AssignmentsUtil.getShoppingCartAssignmentsLimitReachedTitleModel(getPageBase())));
        parametersPanel.add(addAllButton);

        AjaxButton goToShoppingCartButton = new AjaxButton(ID_GO_TO_SHOPPING_CART_BUTTON, createStringResource("AbstractShoppingCartTabPanel.goToShoppingCartButton")) {
            private static final long serialVersionUID = 1L;

            @Override
            protected void updateAjaxAttributes(AjaxRequestAttributes attributes) {
                attributes.setChannel(new AjaxChannel("blocking", AjaxChannel.Type.ACTIVE));
            }

            @Override
            public void onClick(AjaxRequestTarget ajaxRequestTarget) {
                AbstractShoppingCartTabPanel.this.getPageBase().navigateToNext(new PageAssignmentsList(true));
            }
        };
        goToShoppingCartButton.setOutputMarkupId(true);
        goToShoppingCartButton.add(new VisibleBehaviour(() -> {
            boolean isShoppingCartEmpty = AbstractShoppingCartTabPanel.this.getRoleCatalogStorage().getAssignmentShoppingCart().size() == 0;
            return !isShoppingCartEmpty;
        }));
        parametersPanel.add(goToShoppingCartButton);
    }

    private String getTargetUserSelectionButtonLabel(List<UserType> usersList){
        if (usersList == null || usersList.size() == 0){
            return createStringResource("AssignmentCatalogPanel.requestForMe").getString();
        } else if (usersList.size() == 1){
            if (usersList.get(0).getOid().equals(getPageBase().getPrincipalUser().getOid())){
                return createStringResource("AssignmentCatalogPanel.requestForMe").getString();
            } else {
                return usersList.get(0).getName().getOrig();
            }
        } else {
            return createStringResource("AssignmentCatalogPanel.requestForMultiple",
                    usersList.size()).getString();
        }
    }

    private QName getRelationParameterValue(){
        return getRelationDropDown().getRelationValue();
    }

    private RelationDropDownChoicePanel getRelationDropDown(){
        return (RelationDropDownChoicePanel)get(createComponentPath(ID_SHOPPING_CART_CONTAINER, ID_PARAMETERS_PANEL, ID_RELATION_CONTAINER, ID_RELATION));
    }

    private void addAllAssignmentsPerformed(AjaxRequestTarget target){
        List<AssignmentEditorDto> availableProviderData = getGridViewComponent().getProvider().getAvailableData();

        if (availableProviderData != null){
            int assignmentsLimit = AssignmentsUtil.loadAssignmentsLimit(new OperationResult(OPERATION_LOAD_ASSIGNMENTS_LIMIT),
                    getPageBase());
            int addedAssignmentsCount = availableProviderData.size() + getRoleCatalogStorage().getAssignmentShoppingCart().size();
            if (assignmentsLimit >= 0 && addedAssignmentsCount > assignmentsLimit) {
                warn(createStringResource("AssignmentPanel.assignmentsLimitReachedWarning", assignmentsLimit).getString());
                target.add(AbstractShoppingCartTabPanel.this.getPageBase().getFeedbackPanel());
                return;
            }


            availableProviderData.forEach(newAssignment -> {
                AssignmentEditorDto assignmentToAdd = newAssignment.clone();
                assignmentToAdd.getTargetRef().setRelation(getNewAssignmentRelation());
                getRoleCatalogStorage().getAssignmentShoppingCart().add(assignmentToAdd);
            });

            target.add(AbstractShoppingCartTabPanel.this);
            assignmentAddedToShoppingCartPerformed(target);
        }
    }

    private ObjectDataProvider<AssignmentEditorDto, AbstractRoleType> getTabPanelProvider() {
        ObjectDataProvider provider = new ObjectDataProvider<AssignmentEditorDto, AbstractRoleType>(AbstractShoppingCartTabPanel.this,
                AbstractRoleType.class) {
            private static final long serialVersionUID = 1L;

            @Override
            public AssignmentEditorDto createDataObjectWrapper(PrismObject<AbstractRoleType> obj) {

                AssignmentEditorDto dto = AssignmentEditorDto.createDtoFromObject(obj.asObjectable(), UserDtoStatus.ADD, getPageBase());
                if (!getRoleCatalogStorage().isMultiUserRequest()) {
                    dto.setAlreadyAssigned(isAlreadyAssigned(obj, dto));
                    dto.setDefualtAssignmentConstraints(roleManagementConfig == null ? null : roleManagementConfig.getDefaultAssignmentConstraints());
                }
                return dto;
            }

            @Override
            public ObjectQuery getQuery() {
                return createContentQuery();
            }
        };
        return provider;
    }

    private boolean isAlreadyAssigned(PrismObject<AbstractRoleType> obj, AssignmentEditorDto assignmentDto){
        UserType user = getTargetUser();
        if (user == null || user.getAssignment() == null){
            return false;
        }
        boolean isAssigned = false;
        List<QName> assignedRelationsList = new ArrayList<>();
        for (AssignmentType assignment : user.getAssignment()){
            if (assignment.getTargetRef() != null && assignment.getTargetRef().getOid().equals(obj.getOid())){
                isAssigned = true;
                assignedRelationsList.add(assignment.getTargetRef().getRelation());
            }
        }
        assignmentDto.setAssignedRelationsList(assignedRelationsList);
        return isAssigned;
    }

    protected boolean isShoppingCartItemsPanelVisible(){
        return true;
    }

    protected void appendItemsPanelStyle(WebMarkupContainer container){
        container.add(AttributeAppender.append("class", "col-md-12"));
    }

    protected ObjectQuery createContentQuery() {
        ObjectQuery memberQuery = getPrismContext().queryFactory().createQuery();
        memberQuery.addFilter(getAssignableRolesFilter());
        if (getQueryType() != null && !AbstractRoleType.COMPLEX_TYPE.equals(getQueryType())){
            ObjectFilter typeFilter = ObjectQueryUtil.filterAnd(getPrismContext().queryFactory().createType(getQueryType(), null), memberQuery.getFilter(),
		            getPrismContext());
            memberQuery.addFilter(typeFilter);
        }

        SearchPanel searchPanel = getSearchPanel();
        ObjectQuery searchQuery = searchPanel.getModelObject().createObjectQuery(getPageBase().getPrismContext());
        if (searchQuery != null && searchQuery.getFilter() != null) {
            memberQuery.addFilter(searchQuery.getFilter());
        }

        return memberQuery;
    }

    private SearchPanel getSearchPanel(){
        return (SearchPanel) get(createComponentPath(ID_SHOPPING_CART_CONTAINER, ID_SEARCH_FORM, ID_SEARCH));
    }

    private ObjectFilter getAssignableRolesFilter() {
        Task task = getPageBase().createSimpleTask(OPERATION_LOAD_ASSIGNABLE_ROLES);
        OperationResult result = task.getResult();
        return WebComponentUtil.getAssignableRolesFilter(getTargetUser().asPrismObject(), (Class) ObjectTypes.getObjectTypeClass(getQueryType()),
                WebComponentUtil.AssignmentOrder.ASSIGNMENT, result, task, getPageBase());
    }

    protected abstract QName getQueryType();

    private UserType getTargetUser(){
        if (getRoleCatalogStorage().isSelfRequest()){
            return getPageBase().getPrincipalUser();
        }
        return getRoleCatalogStorage().getTargetUserList().get(0);
    }

    protected void assignmentAddedToShoppingCartPerformed(AjaxRequestTarget target){
        getPageBase().reloadShoppingCartIcon(target);
        target.add(AbstractShoppingCartTabPanel.this);
    }

    protected QName getNewAssignmentRelation() {
        return WebComponentUtil.getDefaultRelationOrFail();
    }

    protected RoleCatalogStorage getRoleCatalogStorage(){
        return getPageBase().getSessionStorage().getRoleCatalog();
    }

    protected GridViewComponent getGridViewComponent(){
        return (GridViewComponent)get(createComponentPath(ID_SHOPPING_CART_CONTAINER, ID_SHOPPING_CART_ITEMS_PANEL));
    }
}
