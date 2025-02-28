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
package com.evolveum.midpoint.web.component.data.column;

import com.evolveum.midpoint.gui.api.GuiStyleConstants;
import com.evolveum.midpoint.gui.api.page.PageBase;
import com.evolveum.midpoint.gui.api.util.WebComponentUtil;
import com.evolveum.midpoint.gui.api.util.WebModelServiceUtils;
import com.evolveum.midpoint.gui.impl.prism.PrismContainerValueWrapper;
import com.evolveum.midpoint.prism.Containerable;
import com.evolveum.midpoint.prism.PrismProperty;
import com.evolveum.midpoint.prism.PrismReferenceValue;
import com.evolveum.midpoint.prism.path.ItemPath;
import com.evolveum.midpoint.prism.xml.XmlTypeConverter;
import com.evolveum.midpoint.schema.constants.SchemaConstants;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.schema.util.CaseTypeUtil;
import com.evolveum.midpoint.schema.util.ShadowUtil;
import com.evolveum.midpoint.schema.util.ApprovalContextUtil;
import com.evolveum.midpoint.web.component.DateLabelComponent;
import com.evolveum.midpoint.web.component.util.SelectableBean;
import com.evolveum.midpoint.web.page.admin.cases.PageCases;
import com.evolveum.midpoint.web.util.TooltipBehavior;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;
import org.apache.commons.lang.StringUtils;
import org.apache.wicket.AttributeModifier;
import org.apache.wicket.Component;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.behavior.AttributeAppender;
import org.apache.wicket.extensions.markup.html.repeater.data.grid.ICellPopulator;
import org.apache.wicket.extensions.markup.html.repeater.data.table.AbstractColumn;
import org.apache.wicket.extensions.markup.html.repeater.data.table.IColumn;
import org.apache.wicket.extensions.markup.html.repeater.data.table.PropertyColumn;
import org.apache.wicket.extensions.markup.html.repeater.data.table.export.AbstractExportableColumn;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.repeater.Item;
import org.apache.wicket.markup.repeater.RepeatingView;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.model.PropertyModel;
import org.apache.wicket.model.StringResourceModel;

import javax.xml.datatype.XMLGregorianCalendar;
import javax.xml.namespace.QName;
import java.util.*;

import static com.evolveum.midpoint.gui.api.util.WebComponentUtil.dispatchToObjectDetailsPage;

public class ColumnUtils {

	public static <T> List<IColumn<T, String>> createColumns(List<ColumnTypeDto<String>> columns) {
		List<IColumn<T, String>> tableColumns = new ArrayList<>();
		for (ColumnTypeDto<String> column : columns) {
			PropertyColumn<T, String> tableColumn = null;
			if (column.isSortable()) {
				tableColumn = createPropertyColumn(column.getColumnName(), column.getSortableColumn(),
						column.getColumnValue(), column.isMultivalue());

			} else {
				tableColumn = new PropertyColumn<>(createStringResource(column.getColumnName()),
                    column.getColumnValue());
			}
			tableColumns.add(tableColumn);

		}
		return tableColumns;
	}

	private static <T> PropertyColumn<T, String> createPropertyColumn(String name, String sortableProperty,
			final String expression, final boolean multivalue) {

		return new PropertyColumn<T, String>(createStringResource(name), sortableProperty, expression) {
			private static final long serialVersionUID = 1L;

			@Override
			public void populateItem(Item item, String componentId, IModel rowModel) {
				if (multivalue) {
					IModel<List> values = new PropertyModel<>(rowModel, expression);
					RepeatingView repeater = new RepeatingView(componentId);
					for (final Object task : values.getObject()) {
						repeater.add(new Label(repeater.newChildId(), task.toString()));
					}
					item.add(repeater);
					return;
				}

				super.populateItem(item, componentId, rowModel);
			}
		};

	}

	public static <O extends ObjectType> List<IColumn<SelectableBean<O>, String>> getDefaultColumns(Class<? extends O> type) {
		if (type == null) {
			return getDefaultUserColumns();
		}

		if (type.equals(UserType.class)) {
			return getDefaultUserColumns();
		} else if (RoleType.class.equals(type)) {
			return getDefaultRoleColumns();
		} else if (OrgType.class.equals(type)) {
			return getDefaultOrgColumns();
		} else if (ServiceType.class.equals(type)) {
			return getDefaultServiceColumns();
		} else if (type.equals(TaskType.class)) {
			return getDefaultTaskColumns();
		} else if (type.equals(ResourceType.class)) {
			return getDefaultResourceColumns();
		} else {
			return new ArrayList<>();
//			throw new UnsupportedOperationException("Will be implemented eventually");
		}
	}

	public static <O extends ObjectType> IColumn<SelectableBean<O>, String> createIconColumn(PageBase pageBase){

		return new IconColumn<SelectableBean<O>>(createIconColumnHeaderModel()) {

			@Override
			protected DisplayType getIconDisplayType(IModel<SelectableBean<O>> rowModel){
				if (rowModel.getObject().getValue() instanceof ArchetypeType && ((ArchetypeType)rowModel.getObject().getValue()).getArchetypePolicy() != null) {
					return ((ArchetypeType)rowModel.getObject().getValue()).getArchetypePolicy().getDisplay();
				}
				DisplayType displayType = WebComponentUtil.getArchetypePolicyDisplayType(rowModel.getObject().getValue(), pageBase);
				if (displayType != null){
					String disabledStyle = "";
					if (rowModel.getObject().getValue() instanceof FocusType) {
						disabledStyle = WebComponentUtil.getIconEnabledDisabled(((FocusType)rowModel.getObject().getValue()).asPrismObject());
						if (displayType.getIcon() != null && StringUtils.isNotEmpty(displayType.getIcon().getCssClass()) &&
								disabledStyle != null){
							displayType.getIcon().setCssClass(displayType.getIcon().getCssClass() + " " + disabledStyle);
							displayType.getIcon().setColor("");
						}
					}
				} else {
					displayType = WebComponentUtil.createDisplayType(getIconColumnValue(rowModel), "", getIconColumnTitle(rowModel));
				}
				return displayType;
			}

//			@Override
//			public IModel<String> getDataModel(IModel<SelectableBean<O>> rowModel) {
//				return getIconColumnDataModel(rowModel);
//			}
		};

	}

	private static <T extends ObjectType> String getIconColumnValue(IModel<SelectableBean<T>> rowModel) {
		if (rowModel == null || rowModel.getObject() == null || rowModel.getObject().getValue() == null) {
			return "";
		}

		T object = rowModel.getObject().getValue();

		Class<T> type = (Class<T>) object.getClass();
		if (type.equals(ObjectType.class)) {
			return WebComponentUtil.createDefaultIcon(object.asPrismObject());
		} else if (type.equals(UserType.class)) {
			return WebComponentUtil.createUserIcon(object.asPrismContainer());
		} else if (RoleType.class.equals(type)) {
			return WebComponentUtil.createRoleIcon(object.asPrismContainer());
		} else if (OrgType.class.equals(type)) {
			return WebComponentUtil.createOrgIcon(object.asPrismContainer());
		} else if (ServiceType.class.equals(type)) {
			return WebComponentUtil.createServiceIcon(object.asPrismContainer());
		} else if (ShadowType.class.equals(type)) {
			if (object == null) {
				return WebComponentUtil.createErrorIcon(rowModel.getObject().getResult());
			} else {
				return WebComponentUtil.createShadowIcon(object.asPrismContainer());
			}
		} else if (type.equals(TaskType.class)) {
			return WebComponentUtil.createTaskIcon(object.asPrismContainer());
		} else if (type.equals(ResourceType.class)) {
			return WebComponentUtil.createResourceIcon(object.asPrismContainer());
		} else if (type.equals(AccessCertificationDefinitionType.class)) {
			return GuiStyleConstants.CLASS_OBJECT_CERT_DEF_ICON + " " + GuiStyleConstants.CLASS_ICON_STYLE_NORMAL;
		} else if (type.equals(CaseType.class)) {
			return GuiStyleConstants.EVO_CASE_OBJECT_ICON;
		} else if (type.equals(CaseWorkItemType.class)) {
			return GuiStyleConstants.CLASS_OBJECT_WORK_ITEM_ICON;
		} else if (ShadowType.class.equals(type)) {
			return GuiStyleConstants.EVO_ARCHETYPE_TYPE_ICON;
		}

		return "";
	}

	private static <T extends ObjectType> IModel<String> getIconColumnDataModel(IModel<SelectableBean<T>> rowModel){
		Class<T> type = (Class<T>) rowModel.getObject().getValue().getClass();
		if (ShadowType.class.equals(type)) {
				T shadow = rowModel.getObject().getValue();
				if (shadow == null){
					return null;
				}
				return ShadowUtil.isProtected(shadow.asPrismContainer()) ?
						createStringResource("ThreeStateBooleanPanel.true") : createStringResource("ThreeStateBooleanPanel.false");

		}
		return Model.of();
	}

	private static <T extends ObjectType> String getIconColumnTitle(IModel<SelectableBean<T>> rowModel){
		if (rowModel == null || rowModel.getObject() == null){
			return null;
		}
		if (rowModel.getObject().getResult() != null && rowModel.getObject().getResult().isFatalError()){
			OperationResult result = rowModel.getObject().getResult();
			return result.getUserFriendlyMessage() != null ?
					result.getUserFriendlyMessage().getFallbackMessage() : result.getMessage();
		}
		Class<T> type = (Class<T>)rowModel.getObject().getValue().getClass();
		T object = rowModel.getObject().getValue();
		if (object == null && !ShadowType.class.equals(type)){
			return null;
		} else if (type.equals(UserType.class)) {
			String iconClass = object != null ? WebComponentUtil.createUserIcon(object.asPrismContainer()) : null;
			String compareStringValue = GuiStyleConstants.CLASS_OBJECT_USER_ICON + " " + GuiStyleConstants.CLASS_ICON_STYLE;
			String titleValue = "";
			if (iconClass != null &&
					iconClass.startsWith(compareStringValue) &&
					iconClass.length() > compareStringValue.length()){
				titleValue = iconClass.substring(compareStringValue.length());
			}
			return createStringResource("ColumnUtils.getUserIconColumn.createTitleModel." + titleValue) == null ?
					"" : createStringResource("ColumnUtils.getUserIconColumn.createTitleModel." + titleValue).getString();
		} else {
			return object.asPrismContainer().getDefinition().getTypeName().getLocalPart();
		}
	}

	private static IModel<String> createIconColumnHeaderModel() {
		return new Model<String>() {
			@Override
			public String getObject() {
				return "";
			}
		};
	}

	public static StringResourceModel createStringResource(String resourceKey, Object... objects) {
		return new StringResourceModel(resourceKey).setModel(new Model<String>()).setDefaultValue(resourceKey)
				.setParameters(objects);
	}

	public static <T extends ObjectType> List<IColumn<SelectableBean<T>, String>> getDefaultUserColumns() {
		List<IColumn<SelectableBean<T>, String>> columns = new ArrayList<>();

		List<ColumnTypeDto<String>> columnsDefs = Arrays.asList(
				new ColumnTypeDto<String>("UserType.givenName", UserType.F_GIVEN_NAME.getLocalPart(),
						SelectableBean.F_VALUE + ".givenName.orig", false),
				new ColumnTypeDto<String>("UserType.familyName", UserType.F_FAMILY_NAME.getLocalPart(),
						SelectableBean.F_VALUE + ".familyName.orig", false),
				new ColumnTypeDto<String>("UserType.fullName", UserType.F_FULL_NAME.getLocalPart(),
						SelectableBean.F_VALUE + ".fullName.orig", false),
				new ColumnTypeDto<String>("UserType.emailAddress", UserType.F_EMAIL_ADDRESS.getLocalPart(),
						SelectableBean.F_VALUE + ".emailAddress", false)

		);
		columns.addAll(ColumnUtils.<SelectableBean<T>>createColumns(columnsDefs));

		return columns;

	}

	public static <T extends ObjectType> List<IColumn<SelectableBean<T>, String>> getDefaultTaskColumns() {
		List<IColumn<SelectableBean<T>, String>> columns = new ArrayList<>();

		columns.add(
				new AbstractColumn<SelectableBean<T>, String>(createStringResource("TaskType.kind")) {
					private static final long serialVersionUID = 1L;

					@Override
					public void populateItem(Item<ICellPopulator<SelectableBean<T>>> cellItem,
							String componentId, IModel<SelectableBean<T>> rowModel) {
						SelectableBean<TaskType> object = (SelectableBean<TaskType>) rowModel.getObject();
						PrismProperty<ShadowKindType> pKind = object.getValue() != null ?
								object.getValue().asPrismObject().findProperty(
										ItemPath.create(TaskType.F_EXTENSION, SchemaConstants.MODEL_EXTENSION_KIND))
								: null;
						if (pKind != null) {
							cellItem.add(new Label(componentId, WebComponentUtil
									.createLocalizedModelForEnum(pKind.getRealValue(), cellItem)));
						} else {
							cellItem.add(new Label(componentId));
						}

					}

				});

		columns.add(new AbstractColumn<SelectableBean<T>, String>(
				createStringResource("TaskType.intent")) {

			@Override
			public void populateItem(Item<ICellPopulator<SelectableBean<T>>> cellItem,
					String componentId, IModel<SelectableBean<T>> rowModel) {
				SelectableBean<TaskType> object = (SelectableBean<TaskType>) rowModel.getObject();
				PrismProperty<String> pIntent = object.getValue() != null ?
						object.getValue().asPrismObject().findProperty(
								ItemPath.create(TaskType.F_EXTENSION, SchemaConstants.MODEL_EXTENSION_INTENT))
						: null;
				if (pIntent != null) {
					cellItem.add(new Label(componentId, pIntent.getRealValue()));
				} else {
					cellItem.add(new Label(componentId));
				}
			}

		});

		columns.add(new AbstractColumn<SelectableBean<T>, String>(
				createStringResource("TaskType.objectClass")) {

			@Override
			public void populateItem(Item<ICellPopulator<SelectableBean<T>>> cellItem,
					String componentId, IModel<SelectableBean<T>> rowModel) {
				SelectableBean<TaskType> object = (SelectableBean<TaskType>) rowModel.getObject();
				PrismProperty<QName> pObjectClass = object.getValue() != null ?
						object.getValue().asPrismObject().findProperty(
								ItemPath.create(TaskType.F_EXTENSION, SchemaConstants.MODEL_EXTENSION_OBJECTCLASS))
						: null;
				if (pObjectClass != null) {
					cellItem.add(new Label(componentId, pObjectClass.getRealValue().getLocalPart()));
				} else {
					cellItem.add(new Label(componentId, ""));
				}

			}

		});

		List<ColumnTypeDto<String>> columnsDefs = Arrays.asList(
				new ColumnTypeDto<String>("TaskType.executionStatus", TaskType.F_EXECUTION_STATUS.getLocalPart(),
						SelectableBean.F_VALUE + ".executionStatus", false));
		columns.addAll(ColumnUtils.<SelectableBean<T>>createColumns(columnsDefs));

		return columns;

	}

	public static <T extends ObjectType> List<IColumn<SelectableBean<T>, String>> getDefaultRoleColumns() {
		List<IColumn<SelectableBean<T>, String>> columns = new ArrayList<>();


		columns.addAll((Collection)getDefaultAbstractRoleColumns(true));

		return columns;
	}

	public static <T extends ObjectType> List<IColumn<SelectableBean<T>, String>> getDefaultServiceColumns() {
		List<IColumn<SelectableBean<T>, String>> columns = new ArrayList<>();

		columns.addAll((Collection)getDefaultAbstractRoleColumns(true));

		return columns;
	}

	public static <T extends ObjectType> List<IColumn<SelectableBean<T>, String>> getDefaultOrgColumns() {
		List<IColumn<SelectableBean<T>, String>> columns = new ArrayList<>();

		columns.addAll((Collection)getDefaultAbstractRoleColumns(true));

		return columns;
	}
	
	public static <T extends ObjectType> List<IColumn<SelectableBean<T>, String>> getDefaultArchetypeColumns() {
		List<IColumn<SelectableBean<T>, String>> columns = new ArrayList<>();

		columns.addAll((Collection)getDefaultAbstractRoleColumns(true));

		return columns;
	}
	

	public static <T extends AbstractRoleType> List<IColumn<SelectableBean<T>, String>> getDefaultAbstractRoleColumns(boolean showAccounts) {

		String sortByDisplayName = null;
		String sortByIdentifer = null;
		sortByDisplayName = AbstractRoleType.F_DISPLAY_NAME.getLocalPart();
		sortByIdentifer = AbstractRoleType.F_IDENTIFIER.getLocalPart();
		List<ColumnTypeDto<String>> columnsDefs = Arrays.asList(
				new ColumnTypeDto<String>("AbstractRoleType.displayName",
						sortByDisplayName,
						SelectableBean.F_VALUE + ".displayName", false),
				new ColumnTypeDto<String>("AbstractRoleType.description",
						null,
						SelectableBean.F_VALUE + ".description", false),
				new ColumnTypeDto<String>("AbstractRoleType.identifier", sortByIdentifer,
						SelectableBean.F_VALUE + ".identifier", false)

		);
		List<IColumn<SelectableBean<T>, String>> columns = createColumns(columnsDefs);

		if (showAccounts) {
			IColumn<SelectableBean<T>, String> column = new AbstractExportableColumn<SelectableBean<T>, String>(
					createStringResource("pageUsers.accounts")) {

				@Override
				public void populateItem(Item<ICellPopulator<SelectableBean<T>>> cellItem,
										 String componentId, IModel<SelectableBean<T>> model) {
					cellItem.add(new Label(componentId,
							model.getObject().getValue() != null ?
									model.getObject().getValue().getLinkRef().size() : null));
				}

				@Override
				public IModel<String> getDataModel(IModel<SelectableBean<T>> rowModel) {
					return Model.of(rowModel.getObject().getValue() != null ?
							Integer.toString(rowModel.getObject().getValue().getLinkRef().size()) : "");
				}


			};

			columns.add(column);
		}
		return columns;

	}

	public static <T extends ObjectType> List<IColumn<SelectableBean<T>, String>> getDefaultResourceColumns() {
		List<IColumn<SelectableBean<T>, String>> columns = new ArrayList<>();

		List<ColumnTypeDto<String>> columnsDefs = Arrays.asList(
				new ColumnTypeDto<String>("AbstractRoleType.description", null,
						SelectableBean.F_VALUE + ".description", false)

		);

		columns.addAll(ColumnUtils.<SelectableBean<T>>createColumns(columnsDefs));

		return columns;

	}

	public static List<IColumn<PrismContainerValueWrapper<CaseWorkItemType>, String>> getDefaultWorkItemColumns(PageBase pageBase, boolean isFullView){
		List<IColumn<PrismContainerValueWrapper<CaseWorkItemType>, String>> columns = new ArrayList<>();
		columns.add(new AbstractExportableColumn<PrismContainerValueWrapper<CaseWorkItemType>, String>(
				createStringResource("WorkItemsPanel.stage")) {
			private static final long serialVersionUID = 1L;

			@Override
			public void populateItem(Item<ICellPopulator<PrismContainerValueWrapper<CaseWorkItemType>>> cellItem,
									 String componentId, IModel<PrismContainerValueWrapper<CaseWorkItemType>> rowModel) {
				cellItem.add(new Label(componentId, ApprovalContextUtil.getStageInfo(unwrapRowModel(rowModel))));
			}

			@Override
			public IModel<String> getDataModel(IModel<PrismContainerValueWrapper<CaseWorkItemType>> rowModel) {
				return Model.of(ApprovalContextUtil.getStageInfo(unwrapRowModel(rowModel)));
			}


		});
		columns.add(new LinkColumn<PrismContainerValueWrapper<CaseWorkItemType>>(createStringResource("WorkItemsPanel.object")) {
			private static final long serialVersionUID = 1L;

			@Override
			protected IModel<String> createLinkModel(IModel<PrismContainerValueWrapper<CaseWorkItemType>> rowModel) {
				CaseWorkItemType caseWorkItemType = unwrapRowModel(rowModel);
				CaseType caseType = CaseTypeUtil.getCase(caseWorkItemType);
				return Model.of(WebModelServiceUtils.resolveReferenceName(caseType.getObjectRef(), pageBase));
			}

			@Override
			public void onClick(AjaxRequestTarget target, IModel<PrismContainerValueWrapper<CaseWorkItemType>> rowModel) {
				CaseWorkItemType caseWorkItemType = unwrapRowModel(rowModel);
				CaseType caseType = CaseTypeUtil.getCase(caseWorkItemType);

				dispatchToObjectDetailsPage(caseType.getObjectRef(), pageBase, false);
			}

			@Override
			public void populateItem(Item<ICellPopulator<PrismContainerValueWrapper<CaseWorkItemType>>> cellItem, String componentId,
									 final IModel<PrismContainerValueWrapper<CaseWorkItemType>> rowModel) {
				super.populateItem(cellItem, componentId, rowModel);
				Component c = cellItem.get(componentId);

				CaseWorkItemType caseWorkItemType = unwrapRowModel(rowModel);
				CaseType caseType = CaseTypeUtil.getCase(caseWorkItemType);
				PrismReferenceValue refVal = caseType.getObjectRef().asReferenceValue();
				String descriptionValue = refVal.getObject() != null ?
						refVal.getObject().asObjectable().getDescription() : "";

				c.add(new AttributeAppender("title", descriptionValue));
			}
		});
		columns.add(new LinkColumn<PrismContainerValueWrapper<CaseWorkItemType>>(createStringResource("WorkItemsPanel.target")) {
			private static final long serialVersionUID = 1L;

			@Override
			protected IModel<String> createLinkModel(IModel<PrismContainerValueWrapper<CaseWorkItemType>> rowModel) {
				CaseWorkItemType caseWorkItemType = unwrapRowModel(rowModel);
				CaseType caseType = CaseTypeUtil.getCase(caseWorkItemType);
				return Model.of(WebModelServiceUtils.resolveReferenceName(caseType.getTargetRef(), pageBase));
			}

			@Override
			public void onClick(AjaxRequestTarget target, IModel<PrismContainerValueWrapper<CaseWorkItemType>> rowModel) {
				CaseWorkItemType caseWorkItemType = unwrapRowModel(rowModel);
				CaseType caseType = CaseTypeUtil.getCase(caseWorkItemType);
				dispatchToObjectDetailsPage(caseType.getTargetRef(), pageBase, false);
			}

			@Override
			public void populateItem(Item<ICellPopulator<PrismContainerValueWrapper<CaseWorkItemType>>> cellItem, String componentId,
									 final IModel<PrismContainerValueWrapper<CaseWorkItemType>> rowModel) {
				super.populateItem(cellItem, componentId, rowModel);
				Component c = cellItem.get(componentId);

				CaseWorkItemType caseWorkItemType = unwrapRowModel(rowModel);
				CaseType caseType = CaseTypeUtil.getCase(caseWorkItemType);
				PrismReferenceValue refVal = caseType.getTargetRef().asReferenceValue();
				String descriptionValue = refVal.getObject() != null ?
						refVal.getObject().asObjectable().getDescription() : "";

				c.add(new AttributeAppender("title", descriptionValue));
			}
		});
		if (isFullView) {
			columns.add(new AbstractExportableColumn<PrismContainerValueWrapper<CaseWorkItemType>, String>(
					createStringResource("WorkItemsPanel.actors")) {
				private static final long serialVersionUID = 1L;

				@Override
				public void populateItem(Item<ICellPopulator<PrismContainerValueWrapper<CaseWorkItemType>>> cellItem,
										 String componentId, IModel<PrismContainerValueWrapper<CaseWorkItemType>> rowModel) {

					String assignee = WebComponentUtil.getReferencedObjectNames(unwrapRowModel(rowModel).getAssigneeRef(), false);
					cellItem.add(new Label(componentId,
							assignee != null ? assignee : WebComponentUtil.getReferencedObjectNames(unwrapRowModel(rowModel).getCandidateRef(), true)));
				}

				@Override
				public IModel<String> getDataModel(IModel<PrismContainerValueWrapper<CaseWorkItemType>> rowModel) {
					String assignee = WebComponentUtil.getReferencedObjectNames(unwrapRowModel(rowModel).getAssigneeRef(), false);
					return Model.of(assignee != null ? assignee : WebComponentUtil.getReferencedObjectNames(unwrapRowModel(rowModel).getCandidateRef(), true));
				}
			});
		}
		columns.add(new AbstractExportableColumn<PrismContainerValueWrapper<CaseWorkItemType>, String>(
				createStringResource("WorkItemsPanel.created")) {
			private static final long serialVersionUID = 1L;

			@Override
			public void populateItem(Item<ICellPopulator<PrismContainerValueWrapper<CaseWorkItemType>>> cellItem,
									 String componentId, IModel<PrismContainerValueWrapper<CaseWorkItemType>> rowModel) {
				cellItem.add(new Label(componentId,
						WebComponentUtil.getShortDateTimeFormattedValue(unwrapRowModel(rowModel).getCreateTimestamp(), pageBase)));
			}

			@Override
			public IModel<String> getDataModel(IModel<PrismContainerValueWrapper<CaseWorkItemType>> rowModel) {
				return Model.of(WebComponentUtil.getShortDateTimeFormattedValue(unwrapRowModel(rowModel).getCreateTimestamp(), pageBase));
			}
		});
		if (isFullView) {
			columns.add(new AbstractColumn<PrismContainerValueWrapper<CaseWorkItemType>, String>(createStringResource("WorkItemsPanel.started")) {
				private static final long serialVersionUID = 1L;

				@Override
				public void populateItem(Item<ICellPopulator<PrismContainerValueWrapper<CaseWorkItemType>>> cellItem, String componentId,
										 final IModel<PrismContainerValueWrapper<CaseWorkItemType>> rowModel) {
					cellItem.add(new DateLabelComponent(componentId, new IModel<Date>() {
						private static final long serialVersionUID = 1L;

						@Override
						public Date getObject() {
							CaseWorkItemType workItem = rowModel.getObject().getRealValue();
							CaseType caseType = CaseTypeUtil.getCase(workItem);
							return XmlTypeConverter.toDate(CaseTypeUtil.getStartTimestamp(caseType));
						}
					}, WebComponentUtil.getShortDateTimeFormat(pageBase)));
				}
			});
		}
		columns.add(new AbstractExportableColumn<PrismContainerValueWrapper<CaseWorkItemType>, String>(
				createStringResource("WorkItemsPanel.deadline")) {
			private static final long serialVersionUID = 1L;

			@Override
			public void populateItem(Item<ICellPopulator<PrismContainerValueWrapper<CaseWorkItemType>>> cellItem,
									 String componentId, IModel<PrismContainerValueWrapper<CaseWorkItemType>> rowModel) {
				cellItem.add(new Label(componentId,
						WebComponentUtil.getShortDateTimeFormattedValue(unwrapRowModel(rowModel).getDeadline(), pageBase)));
			}

			@Override
			public IModel<String> getDataModel(IModel<PrismContainerValueWrapper<CaseWorkItemType>> rowModel) {
				return Model.of(WebComponentUtil.getShortDateTimeFormattedValue(unwrapRowModel(rowModel).getDeadline(),
						pageBase));
			}
		});
		columns.add(new AbstractExportableColumn<PrismContainerValueWrapper<CaseWorkItemType>, String>(
				createStringResource("WorkItemsPanel.escalationLevel")) {
			private static final long serialVersionUID = 1L;

			@Override
			public void populateItem(Item<ICellPopulator<PrismContainerValueWrapper<CaseWorkItemType>>> cellItem,
									 String componentId, IModel<PrismContainerValueWrapper<CaseWorkItemType>> rowModel) {
				cellItem.add(new Label(componentId, ApprovalContextUtil.getEscalationLevelInfo(unwrapRowModel(rowModel))));
			}

			@Override
			public IModel<String> getDataModel(IModel<PrismContainerValueWrapper<CaseWorkItemType>> rowModel) {
				return Model.of(ApprovalContextUtil.getEscalationLevelInfo(unwrapRowModel(rowModel)));
			}
		});
		return columns;
	}

	public static List<IColumn<SelectableBean<CaseType>, String>> getDefaultCaseColumns(PageBase pageBase) {

		List<IColumn<SelectableBean<CaseType>, String>> columns = new ArrayList<IColumn<SelectableBean<CaseType>, String>>();

		IColumn column = new PropertyColumn(createStringResource("pageCases.table.description"), "value.description");
		columns.add(column);

		column = new AbstractColumn<SelectableBean<CaseType>, String>(createStringResource("pageCases.table.objectRef"), "objectRef"){
			@Override
			public void populateItem(Item<ICellPopulator<SelectableBean<CaseType>>> item, String componentId, IModel<SelectableBean<CaseType>> rowModel) {
				item.add(new Label(componentId, new IModel<String>() {
					@Override
					public String getObject() {
						CaseType caseModelObject = rowModel.getObject().getValue();
						if (caseModelObject == null || caseModelObject.getObjectRef() == null) {
							return "";
						}
						return WebComponentUtil.getEffectiveName(caseModelObject.getObjectRef(), AbstractRoleType.F_DISPLAY_NAME, pageBase,
								pageBase.getClass().getSimpleName() + "." + "loadCaseObjectRefName");
					}
				}));
			}
		};
		columns.add(column);

		column = new AbstractColumn<SelectableBean<CaseType>, String>(createStringResource("pageCases.table.actors")){
			@Override
			public void populateItem(Item<ICellPopulator<SelectableBean<CaseType>>> item, String componentId, IModel<SelectableBean<CaseType>> rowModel) {
				item.add(new Label(componentId, new IModel<String>() {
					@Override
					public String getObject() {
						String actors = null;
						SelectableBean<CaseType> caseModel = rowModel.getObject();
						if (caseModel != null) {
							CaseType caseIntance = caseModel.getValue();
							if (caseIntance != null) {
								List<CaseWorkItemType> caseWorkItemTypes = caseIntance.getWorkItem();
								List<String> actorsList = new ArrayList<String>();
								for (CaseWorkItemType caseWorkItem : caseWorkItemTypes) {
									List<ObjectReferenceType> assignees = caseWorkItem.getAssigneeRef();
									for (ObjectReferenceType actor : assignees) {
										actorsList.add(WebComponentUtil.getEffectiveName(actor, AbstractRoleType.F_DISPLAY_NAME, pageBase,
												pageBase.getClass().getSimpleName() + "." + "loadCaseActorsNames"));
									}
								}
								actors = String.join(", ", actorsList);
							}
						}
						return actors;
					}
				}));
			}
		};
		columns.add(column);

		column = new AbstractColumn<SelectableBean<CaseType>, String>(
				createStringResource("pageCases.table.openTimestamp"),
				MetadataType.F_CREATE_TIMESTAMP.getLocalPart()) {

			private static final long serialVersionUID = 1L;

			@Override
			public void populateItem(Item<ICellPopulator<SelectableBean<CaseType>>> cellItem,
									 String componentId, final IModel<SelectableBean<CaseType>> rowModel) {
				CaseType object = rowModel.getObject().getValue();
				MetadataType metadata = object != null ? object.getMetadata() : null;
				XMLGregorianCalendar createdCal = metadata != null ? metadata.getCreateTimestamp() : null;
				final Date created;
				if (createdCal != null) {
					created = createdCal.toGregorianCalendar().getTime();
//                    cellItem.add(AttributeModifier.replace("title", WebComponentUtil.getLocalizedDate(created, DateLabelComponent.LONG_MEDIUM_STYLE)));
//                    cellItem.add(new TooltipBehavior());
				} else {
					created = null;
				}
				cellItem.add(new Label(componentId, new IModel<String>() {
					@Override
					public String getObject() {
						return WebComponentUtil.getShortDateTimeFormattedValue(created, pageBase);
					}
				}));
			}
		};
		columns.add(column);

		column = new PropertyColumn<SelectableBean<CaseType>, String>(createStringResource("pageCases.table.closeTimestamp"), CaseType.F_CLOSE_TIMESTAMP.getLocalPart(), "value.closeTimestamp") {
			@Override
			public void populateItem(Item<ICellPopulator<SelectableBean<CaseType>>> cellItem,
									 String componentId, final IModel<SelectableBean<CaseType>> rowModel) {
				CaseType object = rowModel.getObject().getValue();
				XMLGregorianCalendar closedCal = object != null ? object.getCloseTimestamp() : null;
				final Date closed;
				if (closedCal != null) {
					closed = closedCal.toGregorianCalendar().getTime();
                    cellItem.add(AttributeModifier.replace("title", WebComponentUtil.getLocalizedDate(closed, DateLabelComponent.LONG_MEDIUM_STYLE)));
                    cellItem.add(new TooltipBehavior());
				} else {
					closed = null;
				}
				cellItem.add(new Label(componentId, new IModel<String>() {
					@Override
					public String getObject() {
						return WebComponentUtil.getShortDateTimeFormattedValue(closed, pageBase);
					}
				}));
			}
		};
		columns.add(column);

		column = new PropertyColumn<SelectableBean<CaseType>, String>(createStringResource("pageCases.table.state"), CaseType.F_STATE.getLocalPart(), "value.state");
		columns.add(column);

		column = new AbstractExportableColumn<SelectableBean<CaseType>, String>(
				createStringResource("pageCases.table.workitems")) {

			@Override
			public void populateItem(Item<ICellPopulator<SelectableBean<CaseType>>> cellItem,
									 String componentId, IModel<SelectableBean<CaseType>> model) {
				cellItem.add(new Label(componentId,
						model.getObject().getValue() != null && model.getObject().getValue().getWorkItem() != null ?
								model.getObject().getValue().getWorkItem().size() : null));
			}

			@Override
			public IModel<String> getDataModel(IModel<SelectableBean<CaseType>> rowModel) {
				return Model.of(rowModel.getObject().getValue() != null && rowModel.getObject().getValue().getWorkItem() != null ?
						Integer.toString(rowModel.getObject().getValue().getWorkItem().size()) : "");
			}


		};
		columns.add(column);
		return columns;
	}

	public static <C extends Containerable> C unwrapRowModel(IModel<PrismContainerValueWrapper<C>> rowModel){
		return rowModel.getObject().getRealValue();
	}
}
