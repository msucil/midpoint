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
package com.evolveum.midpoint.web.page.admin.cases;

import com.evolveum.midpoint.gui.api.component.BasePanel;
import com.evolveum.midpoint.gui.api.component.MainObjectListPanel;
import com.evolveum.midpoint.model.api.authentication.CompiledObjectCollectionView;
import com.evolveum.midpoint.prism.query.ObjectFilter;
import com.evolveum.midpoint.prism.query.ObjectQuery;
import com.evolveum.midpoint.web.component.data.column.CheckBoxHeaderColumn;
import com.evolveum.midpoint.web.component.data.column.ColumnUtils;
import com.evolveum.midpoint.web.component.menu.cog.InlineMenuItem;
import com.evolveum.midpoint.web.component.util.SelectableBean;
import com.evolveum.midpoint.web.session.UserProfileStorage;
import com.evolveum.midpoint.web.util.OnePageParameterEncoder;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.extensions.markup.html.repeater.data.table.IColumn;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.request.mapper.parameter.PageParameters;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Created by honchar
 */
//TODO very-very temporary panel. to be refactored/unified with PageCases table
public class CasesListPanel extends BasePanel<CaseType> {
    private static final long serialVersionUID = 1L;

    private static final String ID_CASES_TABLE = "casesTable";

    public CasesListPanel(String id){
        super(id);
    }

    @Override
    protected void onInitialize(){
        super.onInitialize();
        initLayout();
    }

    private void initLayout(){
        MainObjectListPanel<CaseType, CompiledObjectCollectionView> table = new MainObjectListPanel<CaseType, CompiledObjectCollectionView>(ID_CASES_TABLE,
                CaseType.class, UserProfileStorage.TableId.PAGE_CASE_CHILD_CASES_TAB, Collections.emptyList(), getPageBase()) {

            @Override
            protected void objectDetailsPerformed(AjaxRequestTarget target, CaseType caseInstance) {
                PageParameters pageParameters = new PageParameters();
                pageParameters.add(OnePageParameterEncoder.PARAMETER, caseInstance.getOid());
                CasesListPanel.this.getPageBase().navigateToNext(PageCase.class, pageParameters);
            }

            @Override
            protected IColumn<SelectableBean<CaseType>, String> createCheckboxColumn() {
                if (isDashboard()){
                    return null;
                } else {
                    return super.createCheckboxColumn();
                }
            }


            @Override
            protected List<IColumn<SelectableBean<CaseType>, String>> createColumns() {
                return ColumnUtils.getDefaultCaseColumns(CasesListPanel.this.getPageBase());
            }

            @Override
            protected boolean isCreateNewObjectEnabled(){
                return false;
            }

            @Override
            protected ObjectQuery addFilterToContentQuery(ObjectQuery query) {
                if (query == null) {
                    query = CasesListPanel.this.getPageBase().getPrismContext().queryFor(CaseType.class)
                            .build();
                }
                ObjectFilter casesFilter = getCasesFilter();
                if (casesFilter != null){
                    query.addFilter(casesFilter);
                }
                return query;
            }

            @Override
            protected WebMarkupContainer createTableButtonToolbar(String id) {
                return null;
            }

            @Override
            protected List<InlineMenuItem> createInlineMenu(){
                return new ArrayList<>();
            }

            @Override
            protected WebMarkupContainer createHeader(String headerId) {
                if (!isDashboard()){
                    return super.createHeader(headerId);
                } else {
                    WebMarkupContainer headerContainer = new WebMarkupContainer(headerId);
                    headerContainer.setVisible(false);
                    return headerContainer;
                }
            }

            @Override
            protected boolean hideFooterIfSinglePage(){
                return CasesListPanel.this.isDashboard();
            }

        };
        table.setOutputMarkupId(true);
        add(table);
    }

    protected ObjectFilter getCasesFilter(){
        return null;
    }

    protected boolean isDashboard(){
        return false;
    }

 }
