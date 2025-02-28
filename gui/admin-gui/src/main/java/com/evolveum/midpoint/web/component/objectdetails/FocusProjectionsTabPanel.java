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
package com.evolveum.midpoint.web.component.objectdetails;

import java.util.ArrayList;
import java.util.List;

import javax.xml.datatype.XMLGregorianCalendar;
import javax.xml.namespace.QName;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.Validate;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.extensions.markup.html.repeater.data.table.IColumn;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.list.ListItem;
import org.apache.wicket.markup.html.panel.Fragment;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.model.PropertyModel;

import com.evolveum.midpoint.common.refinery.RefinedObjectClassDefinition;
import com.evolveum.midpoint.common.refinery.RefinedResourceSchema;
import com.evolveum.midpoint.common.refinery.RefinedResourceSchemaImpl;
import com.evolveum.midpoint.gui.api.GuiStyleConstants;
import com.evolveum.midpoint.gui.api.component.DisplayNamePanel;
import com.evolveum.midpoint.gui.api.component.ObjectBrowserPanel;
import com.evolveum.midpoint.gui.api.model.LoadableModel;
import com.evolveum.midpoint.gui.api.page.PageBase;
import com.evolveum.midpoint.gui.api.prism.ItemStatus;
import com.evolveum.midpoint.gui.api.prism.PrismContainerWrapper;
import com.evolveum.midpoint.gui.api.prism.PrismObjectWrapper;
import com.evolveum.midpoint.gui.api.prism.ShadowWrapper;
import com.evolveum.midpoint.gui.api.util.WebComponentUtil;
import com.evolveum.midpoint.gui.api.util.WebModelServiceUtils;
import com.evolveum.midpoint.gui.impl.component.MultivalueContainerDetailsPanel;
import com.evolveum.midpoint.gui.impl.component.MultivalueContainerListPanelWithDetailsPanel;
import com.evolveum.midpoint.gui.impl.component.data.column.PrismPropertyWrapperColumn;
import com.evolveum.midpoint.gui.impl.component.data.column.PrismReferenceWrapperColumn;
import com.evolveum.midpoint.gui.impl.component.data.column.AbstractItemWrapperColumn.ColumnType;
import com.evolveum.midpoint.gui.impl.component.icon.CompositedIcon;
import com.evolveum.midpoint.gui.impl.component.icon.CompositedIconBuilder;
import com.evolveum.midpoint.gui.impl.component.icon.IconCssStyle;
import com.evolveum.midpoint.gui.impl.component.data.column.CompositedIconColumn;
import com.evolveum.midpoint.gui.impl.factory.ItemRealValueModel;
import com.evolveum.midpoint.gui.impl.factory.PrismObjectWrapperFactory;
import com.evolveum.midpoint.gui.impl.factory.WrapperContext;
import com.evolveum.midpoint.gui.impl.prism.PrismContainerValueWrapper;
import com.evolveum.midpoint.gui.impl.prism.PrismObjectValueWrapper;
import com.evolveum.midpoint.gui.impl.prism.PrismPropertyWrapper;
import com.evolveum.midpoint.gui.impl.prism.PrismValueWrapper;
import com.evolveum.midpoint.gui.impl.prism.ShadowPanel;
import com.evolveum.midpoint.prism.PrismContainer;
import com.evolveum.midpoint.prism.PrismContainerDefinition;
import com.evolveum.midpoint.prism.PrismContainerValue;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.PrismObjectDefinition;
import com.evolveum.midpoint.prism.PrismPropertyDefinition;
import com.evolveum.midpoint.prism.PrismPropertyValue;
import com.evolveum.midpoint.prism.PrismReferenceDefinition;
import com.evolveum.midpoint.prism.path.ItemPath;
import com.evolveum.midpoint.prism.query.ObjectQuery;
import com.evolveum.midpoint.schema.constants.SchemaConstants;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.util.logging.LoggingUtils;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.web.component.data.column.CheckBoxHeaderColumn;
import com.evolveum.midpoint.web.component.data.column.ColumnMenuAction;
import com.evolveum.midpoint.web.component.data.column.InlineMenuButtonColumn;
import com.evolveum.midpoint.web.component.dialog.ConfirmationPanel;
import com.evolveum.midpoint.web.component.dialog.Popupable;
import com.evolveum.midpoint.web.component.form.Form;
import com.evolveum.midpoint.web.component.menu.cog.ButtonInlineMenuItem;
import com.evolveum.midpoint.web.component.menu.cog.InlineMenuItem;
import com.evolveum.midpoint.web.component.menu.cog.InlineMenuItemAction;
import com.evolveum.midpoint.web.component.search.SearchItemDefinition;
import com.evolveum.midpoint.web.model.PrismContainerWrapperModel;
import com.evolveum.midpoint.web.model.PrismPropertyWrapperModel;
import com.evolveum.midpoint.web.page.admin.PageAdminFocus;
import com.evolveum.midpoint.web.page.admin.users.dto.UserDtoStatus;
import com.evolveum.midpoint.web.session.PageStorage;
import com.evolveum.midpoint.web.session.UserProfileStorage;
import com.evolveum.midpoint.web.session.UserProfileStorage.TableId;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ActivationStatusType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ActivationType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.FocusType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.IconType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.LayerType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.LockoutStatusType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ObjectType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ResourceType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ShadowKindType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ShadowType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.TriggerType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.UserType;

/**
 * @author semancik
 * @author skublik
 */
public class FocusProjectionsTabPanel<F extends FocusType> extends AbstractObjectTabPanel<F> {
	private static final long serialVersionUID = 1L;

//	private static final String ID_SHADOW_LIST = "shadowList";
	private static final String ID_SHADOW_TABLE = "shadowTable";
//	private static final String ID_SHADOWS = "shadows";
//	private static final String ID_SHADOWS_CONTAINER = "shadowContainer";
//	private static final String ID_SHADOW_HEADER = "shadowHeader";
//	private static final String ID_SHADOW = "shadow";
	private static final String ID_SHADOW_PANEL = "shadowPanel";
//	private static final String ID_SHADOW_MENU = "shadowMenu";
//	private static final String ID_SHADOW_CHECK_ALL = "shadowCheckAll";
	protected static final String ID_SPECIFIC_CONTAINERS_FRAGMENT = "specificContainersFragment";
	
	private static final String DOT_CLASS = FocusProjectionsTabPanel.class.getName() + ".";
	private static final String OPERATION_ADD_ACCOUNT = DOT_CLASS + "addShadow";

	private static final Trace LOGGER = TraceManager.getTrace(FocusProjectionsTabPanel.class);

	private LoadableModel<List<ShadowWrapper>> projectionModel;
	
	public FocusProjectionsTabPanel(String id, Form mainForm, LoadableModel<PrismObjectWrapper<F>> focusModel,
			LoadableModel<List<ShadowWrapper>> projectionModel) {
		super(id, mainForm, focusModel);
		Validate.notNull(projectionModel, "Null projection model");
		this.projectionModel = projectionModel;
	}
	
	@Override
	protected void onInitialize() {
		super.onInitialize();
		initLayout();
	}

	private PrismObjectDefinition<ShadowType> getShadowDefinition() {
		return getPrismContext().getSchemaRegistry().findObjectDefinitionByCompileTimeClass(ShadowType.class);
	}
	
	private void initLayout() {

		TableId tableId = UserProfileStorage.TableId.FOCUS_PROJECTION_TABLE;
    	PageStorage pageStorage = getPageBase().getSessionStorage().getFocusProjectionTableStorage();
    	
    	
    	MultivalueContainerListPanelWithDetailsPanel<ShadowType, F> multivalueContainerListPanel =
				new MultivalueContainerListPanelWithDetailsPanel<ShadowType, F>(ID_SHADOW_TABLE, getShadowDefinition(),
						tableId, pageStorage) {
			
    		
			private static final long serialVersionUID = 1L;

					@Override
					protected IModel<List<PrismContainerValueWrapper<ShadowType>>> loadValuesModel() {
						return new LoadableModel<List<PrismContainerValueWrapper<ShadowType>>>(false) {
							
							private static final long serialVersionUID = 1L;
							
							@Override
							protected List<PrismContainerValueWrapper<ShadowType>> load() {
								List<PrismContainerValueWrapper<ShadowType>> items = new ArrayList<PrismContainerValueWrapper<ShadowType>>();
								for (ShadowWrapper projection : projectionModel.getObject()) {
									items.add(projection.getValue());
								}
								return items;
							}
						};
						
					}
					
					@Override
					protected List<PrismContainerValueWrapper<ShadowType>> postSearch(
							List<PrismContainerValueWrapper<ShadowType>> items) {
						
						return items;
					}

			@Override
			protected void newItemPerformed(AjaxRequestTarget target) {
				List<QName> supportedTypes = new ArrayList<>(1);
				supportedTypes.add(ResourceType.COMPLEX_TYPE);
				PageBase pageBase = FocusProjectionsTabPanel.this.getPageBase();
				ObjectBrowserPanel<ResourceType> resourceSelectionPanel = new ObjectBrowserPanel<ResourceType>(
						pageBase.getMainPopupBodyId(), ResourceType.class, supportedTypes, true,
						pageBase) {

					private static final long serialVersionUID = 1L;

					@Override
					protected void addPerformed(AjaxRequestTarget target, QName type,
												List<ResourceType> selected) {
						FocusProjectionsTabPanel.this.addSelectedAccountPerformed(target,
								selected);
					}
				};
				resourceSelectionPanel.setOutputMarkupId(true);
				pageBase.showMainPopup(resourceSelectionPanel,
						target);
			}
			
			@Override
			protected void initPaging() {
				FocusProjectionsTabPanel.this.initPaging();
			}
			
			@Override
			protected boolean enableActionNewObject() {
				PrismObjectDefinition<F> def = getObjectWrapper().getObject().getDefinition();
				PrismReferenceDefinition ref = def.findReferenceDefinition(UserType.F_LINK_REF);
				return (ref.canRead() && ref.canAdd());
			}
			
			@Override
			protected ObjectQuery createQuery() {
			   return null;
			}
			
			@Override
			protected List<IColumn<PrismContainerValueWrapper<ShadowType>, String>> createColumns() {
				return initBasicColumns();
			}

			@Override
			public void itemPerformedForDefaultAction(AjaxRequestTarget target,
					IModel<PrismContainerValueWrapper<ShadowType>> rowModel,
					List<PrismContainerValueWrapper<ShadowType>> listItems) {

				if(rowModel != null && rowModel.getObject() != null 
						&& ((ShadowWrapper)rowModel.getObject().getParent()).isLoadWithNoFetch()) {
					((PageAdminFocus) getPage()).loadFullShadow((PrismObjectValueWrapper)rowModel.getObject(), target);
				}
				
				if(listItems != null) {
					listItems.forEach(value -> {
						if(((ShadowWrapper)value.getParent()).isLoadWithNoFetch()) {
							((PageAdminFocus) getPage()).loadFullShadow((PrismObjectValueWrapper)value, target);
						} 
					});
				}
				super.itemPerformedForDefaultAction(target, rowModel, listItems);
			}

			@Override
			protected List<SearchItemDefinition> initSearchableItems(
					PrismContainerDefinition<ShadowType> containerDef) {
				List<SearchItemDefinition> defs = new ArrayList<>();
				
				return defs;
			}

			@Override
			protected MultivalueContainerDetailsPanel<ShadowType> getMultivalueContainerDetailsPanel(
					ListItem<PrismContainerValueWrapper<ShadowType>> item) {
				return FocusProjectionsTabPanel.this.getMultivalueContainerDetailsPanel(item);
			}
		};
		add(multivalueContainerListPanel);
		setOutputMarkupId(true);
	}
	
	private void initPaging() {
    	getPageBase().getSessionStorage().getFocusProjectionTableStorage().setPaging(
    			getPrismContext().queryFactory().createPaging(0, (int) ((PageBase)getPage()).getItemsPerPage(UserProfileStorage.TableId.FOCUS_PROJECTION_TABLE)));
    }
	
	private MultivalueContainerDetailsPanel<ShadowType> getMultivalueContainerDetailsPanel(
			ListItem<PrismContainerValueWrapper<ShadowType>> item) {
    	MultivalueContainerDetailsPanel<ShadowType> detailsPanel = new  MultivalueContainerDetailsPanel<ShadowType>(MultivalueContainerListPanelWithDetailsPanel.ID_ITEM_DETAILS, item.getModel()) {

			private static final long serialVersionUID = 1L;

			@Override
			protected DisplayNamePanel<ShadowType> createDisplayNamePanel(String displayNamePanelId) {
				ItemRealValueModel<ShadowType> displayNameModel = 
						new ItemRealValueModel<ShadowType>(item.getModel());
				return new DisplayNamePanel<ShadowType>(displayNamePanelId, displayNameModel) {
					
					@Override
					protected IModel<String> getKindIntentLabelModel() {
						return WebComponentUtil.getResourceLabelModel(getModelObject(), getPageBase());
					}
					
					@Override
					protected IModel<String> getDescriptionLabelModel() {
						return WebComponentUtil.getResourceAttributesLabelModel(getModelObject(), getPageBase());
					}
					
					@Override
					protected String createImageModel() {
						if (getModelObject() == null){
							return "";
						}
						return WebComponentUtil.createShadowIcon(getModelObject().asPrismObject());
					}
					
				};
			}
			
			@Override
			protected void addBasicContainerValuePanel(String idPanel) {
				add(new WebMarkupContainer(idPanel));
			}
			
			@Override
			protected WebMarkupContainer getSpecificContainers(String contentAreaId) {
				Fragment specificContainers = new Fragment(contentAreaId, ID_SPECIFIC_CONTAINERS_FRAGMENT, FocusProjectionsTabPanel.this);

				ShadowPanel shadowPanel = new ShadowPanel(ID_SHADOW_PANEL, getParentModel(getModel()));
				specificContainers.add(shadowPanel);
				return specificContainers;
			}
		};
		return detailsPanel;
	}
	
	private IModel<ShadowWrapper> getParentModel(IModel<PrismContainerValueWrapper<ShadowType>> model){
		return new PropertyModel<ShadowWrapper>(model, "parent");
	}
	
	private String createTriggerTooltip(PrismContainer<TriggerType> container) {
        if (container == null || container.isEmpty()) {
            return null;
        }

        List<String> triggers = new ArrayList<>();
        for (PrismContainerValue<TriggerType> val : container.getValues()) {
        	XMLGregorianCalendar time = val.getPropertyRealValue(TriggerType.F_TIMESTAMP, XMLGregorianCalendar.class);
        
        	if (time == null) {
        		triggers.add(getString("CheckTableHeader.triggerUnknownTime"));
        	} else {
				triggers.add(getString("CheckTableHeader.triggerPlanned", WebComponentUtil.formatDate(time)));
        	}
        }

        return StringUtils.join(triggers, '\n');
    }
	
	private CompositedIcon createAccountIcon(IModel<PrismContainerValueWrapper<ShadowType>> prismContainerValue) {
        PrismObject obj = ((PrismObjectWrapper)prismContainerValue.getObject().getParent()).getObject();
        PrismContainer<TriggerType> container = obj.findContainer(ObjectType.F_TRIGGER);
        String iconCssClass = WebComponentUtil.createShadowIcon(prismContainerValue.getObject().getNewValue());
        CompositedIconBuilder builder = new CompositedIconBuilder();
        String title = createTriggerTooltip(container);
        if(StringUtils.isNotBlank(title)) {
        	IconType icon = new IconType();
        	icon.setCssClass("fa fa-clock-o " + GuiStyleConstants.BLUE_COLOR);
        	builder.appendLayerIcon(icon, IconCssStyle.TOP_RIGHT_FOR_COLUMN_STYLE);
        }
        builder.setBasicIcon(iconCssClass, IconCssStyle.BOTTOM_RIGHT_FOR_COLUMN_STYLE);
        
        IModel<PrismValueWrapper> deadStatus = new PropertyModel(PrismPropertyWrapperModel.fromContainerValueWrapper(
         		prismContainerValue, ItemPath.create(ShadowType.F_DEAD)), "value");
        if(deadStatus != null && Boolean.TRUE.equals(deadStatus.getObject().getRealValue())) {
        	IconType icon = new IconType();
        	icon.setCssClass("fa fa-times-circle " + GuiStyleConstants.RED_COLOR);
        	builder.appendLayerIcon(icon, IconCssStyle.BOTTOM_RIGHT_FOR_COLUMN_STYLE);
        	builder.setTitle(getPageBase().createStringResource("FocusProjectionsTabPanel.deadShadow").getString()
        			+ (StringUtils.isNotBlank(title) ? ("\n" + title) : ""));
        	return builder.build();
        }
        
        IModel<PrismValueWrapper> lockoutStatus = new PropertyModel(PrismPropertyWrapperModel.fromContainerValueWrapper(
        		prismContainerValue, ItemPath.create(ShadowType.F_ACTIVATION, ActivationType.F_LOCKOUT_STATUS)), "value");
        IModel<PrismValueWrapper> lockoutTimeStatus = new PropertyModel(PrismPropertyWrapperModel.fromContainerValueWrapper(
         		prismContainerValue, ItemPath.create(ShadowType.F_ACTIVATION, ActivationType.F_LOCKOUT_EXPIRATION_TIMESTAMP)), "value");
        if((lockoutStatus != null && LockoutStatusType.LOCKED.equals(lockoutStatus.getObject().getRealValue()))
        		|| (lockoutTimeStatus.getObject() != null && lockoutTimeStatus.getObject().getRealValue() != null
        		&& getPageBase().getClock().isPast(((XMLGregorianCalendar)lockoutTimeStatus.getObject().getRealValue())))) {
        	IconType icon = new IconType();
        	icon.setCssClass("fa fa-lock " + GuiStyleConstants.RED_COLOR);
        	builder.appendLayerIcon(icon, IconCssStyle.BOTTOM_RIGHT_FOR_COLUMN_STYLE);
        	builder.setTitle(getPageBase().createStringResource("LockoutStatusType.LOCKED").getString()
        			+ (StringUtils.isNotBlank(title) ? ("\n" + title) : ""));
        	return builder.build();
        }
        
        IModel<PrismValueWrapper> status = new PropertyModel(PrismPropertyWrapperModel.fromContainerValueWrapper(
        		prismContainerValue, ItemPath.create(ShadowType.F_ACTIVATION, ActivationType.F_ADMINISTRATIVE_STATUS)), "value");
        ActivationStatusType value = null;
        if (status != null) {
				value = (ActivationStatusType) status.getObject().getRealValue();
        }
        builder.setTitle(getPageBase().createStringResource("ActivationStatusType." +value).getString()
        		+ (StringUtils.isNotBlank(title) ? ("\n" + title) : ""));
        if(value == null) {
        	IconType icon = new IconType();
        	icon.setCssClass("fa fa-question " + GuiStyleConstants.RED_COLOR);
        	builder.appendLayerIcon(icon, IconCssStyle.BOTTOM_RIGHT_FOR_COLUMN_STYLE);
        	return builder.build();
        }
        if (ActivationStatusType.DISABLED.equals(value)) {
        	IconType icon = new IconType();
        	icon.setCssClass("fe fe-slash " + GuiStyleConstants.RED_COLOR);
        	builder.appendLayerIcon(icon, IconCssStyle.CENTER_FOR_COLUMN_STYLE);
            return builder.build();
        }
        
        if (ActivationStatusType.ARCHIVED.equals(value)) {
        	IconType icon = new IconType();
        	icon.setCssClass("fa fa-archive " + GuiStyleConstants.RED_COLOR);
        	builder.appendLayerIcon(icon, IconCssStyle.BOTTOM_RIGHT_FOR_COLUMN_STYLE);
            return builder.build();
        }
        return builder.build();
    }
	
	private List<IColumn<PrismContainerValueWrapper<ShadowType>, String>> initBasicColumns() {
		
		IModel<PrismContainerDefinition<ShadowType>> shadowDef = Model.of(getShadowDefinition());
		
		List<IColumn<PrismContainerValueWrapper<ShadowType>, String>> columns = new ArrayList<>();
		columns.add(new CheckBoxHeaderColumn<>());
		columns.add(new CompositedIconColumn<PrismContainerValueWrapper<ShadowType>>(Model.of("")) {

			private static final long serialVersionUID = 1L;

			@Override
			protected CompositedIcon getCompositedIcon(IModel<PrismContainerValueWrapper<ShadowType>> rowModel) {
				return createAccountIcon(rowModel);
			}
			
		});
		
		columns.add(new PrismPropertyWrapperColumn<ShadowType, String>(shadowDef, ShadowType.F_NAME, ColumnType.LINK, getPageBase()){
			private static final long serialVersionUID = 1L;

			@Override
			protected void onClick(AjaxRequestTarget target, IModel<PrismContainerValueWrapper<ShadowType>> rowModel) {
				getMultivalueContainerListPanel().itemDetailsPerformed(target, rowModel);
				target.add(getFeedbackPanel());
			}
		});
		columns.add(new PrismReferenceWrapperColumn(shadowDef, ShadowType.F_RESOURCE_REF, ColumnType.STRING, getPageBase()));
		columns.add(new PrismPropertyWrapperColumn<ShadowType, String>(shadowDef, ShadowType.F_OBJECT_CLASS, ColumnType.STRING, getPageBase()));
		columns.add(new PrismPropertyWrapperColumn<ShadowType, String>(shadowDef, ShadowType.F_KIND, ColumnType.STRING, getPageBase()));
		columns.add(new PrismPropertyWrapperColumn<ShadowType, String>(shadowDef, ShadowType.F_INTENT, ColumnType.STRING, getPageBase()));
		
		columns.add(new InlineMenuButtonColumn<>(createShadowMenu(), getPageBase()));
		
		return columns;
	}
	
	private MultivalueContainerListPanelWithDetailsPanel<ShadowType, F> getMultivalueContainerListPanel(){
		return ((MultivalueContainerListPanelWithDetailsPanel<ShadowType, F>)get(ID_SHADOW_TABLE));
	}
	

	
	private void addSelectedAccountPerformed(AjaxRequestTarget target, List<ResourceType> newResources) {
		getPageBase().hideMainPopup(target);

		if (newResources.isEmpty()) {
			warn(getString("pageUser.message.noResourceSelected"));
			return;
		}

		for (ResourceType resource : newResources) {
			try {
				ShadowType shadow = new ShadowType();
				shadow.setResource(resource);
				ResourceType usedResource = resource;
				
				RefinedResourceSchema refinedSchema = RefinedResourceSchemaImpl.getRefinedSchema(
						resource.asPrismObject(), LayerType.PRESENTATION, getPrismContext());
				if (refinedSchema == null) {
					Task task = getPageBase().createSimpleTask(FocusPersonasTabPanel.class.getSimpleName() + ".loadResource");
					OperationResult result = task.getResult();
					PrismObject<ResourceType> loadedResource = WebModelServiceUtils.loadObject(ResourceType.class, resource.getOid(), getPageBase(), task, result);
					result.recomputeStatus();
					
					refinedSchema = RefinedResourceSchemaImpl.getRefinedSchema(
							loadedResource, LayerType.PRESENTATION, getPrismContext());
					
					if (refinedSchema == null) {
						error(getString("pageAdminFocus.message.couldntCreateAccountNoSchema",
								resource.getName()));
						continue;
					}
					
//					shadow.setResource(loadedResource.asObjectable());
					usedResource = loadedResource.asObjectable();
				}
				if (LOGGER.isTraceEnabled()) {
					LOGGER.trace("Refined schema for {}\n{}", resource, refinedSchema.debugDump());
				}

				RefinedObjectClassDefinition accountDefinition = refinedSchema
						.getDefaultRefinedDefinition(ShadowKindType.ACCOUNT);
				if (accountDefinition == null) {
					error(getString("pageAdminFocus.message.couldntCreateAccountNoAccountSchema",
							resource.getName()));
					continue;
				}
//				shadow.asPrismContainer().findOrCreateContainer(ShadowType.F_ATTRIBUTES).applyDefinition(accountDefinition.toResourceAttributeContainerDefinition());
				QName objectClass = accountDefinition.getObjectClassDefinition().getTypeName();
				shadow.setResource(usedResource);
				shadow.setObjectClass(objectClass);
				shadow.setIntent(accountDefinition.getObjectClassDefinition().getIntent());
				shadow.setKind(accountDefinition.getObjectClassDefinition().getKind());
				getPrismContext().adopt(shadow);

				Task task = getPageBase().createSimpleTask(OPERATION_ADD_ACCOUNT);
				PrismObjectWrapperFactory<ShadowType> factory = getPageBase().getRegistry().getObjectWrapperFactory(shadow.asPrismContainer().getDefinition());
				WrapperContext context = new WrapperContext(task, task.getResult());
				ShadowWrapper wrappernew = (ShadowWrapper) factory.createObjectWrapper(shadow.asPrismContainer(), ItemStatus.ADDED, context);
				if (task.getResult() != null
						&& !WebComponentUtil.isSuccessOrHandledError(task.getResult())) {
					showResult(task.getResult(), false);
				}

				wrappernew.setProjectionStatus(UserDtoStatus.ADD);
				projectionModel.getObject().add(wrappernew);
			} catch (Exception ex) {
				error(getString("pageAdminFocus.message.couldntCreateAccount", resource.getName(),
						ex.getMessage()));
				LoggingUtils.logUnexpectedException(LOGGER, "Couldn't create account", ex);
			}
		}
		target.add(get(ID_SHADOW_TABLE));
	}
	
	private IModel<PrismContainerWrapper<ShadowType>> createEmptyShadowWrapperModel() {
		ShadowType shadow = new ShadowType();
		ShadowWrapper wrapper = null;
		Task task = getPageBase().createSimpleTask("create empty shadow wrapper");
		try {
			getPageBase().getPrismContext().adopt(shadow);
			wrapper = ((PageAdminFocus) getPage()).loadShadowWrapper(shadow.asPrismContainer(), task, task.getResult());
		} catch (SchemaException e) {
			getPageBase().showResult(task.getResult(), "pageAdminFocus.message.couldntCreateShadowWrapper");
			LOGGER.error("Couldn't create shadow wrapper", e);
		}
		final ShadowWrapper ret = wrapper;
		return new IModel<PrismContainerWrapper<ShadowType>>() {

			@Override
			public PrismContainerWrapper<ShadowType> getObject() {
				return ret;
			}
		};
	}

	private List<InlineMenuItem> createShadowMenu() {
		List<InlineMenuItem> items = new ArrayList<>();

		PrismObjectDefinition<F> def = getObjectWrapper().getObject().getDefinition();
		PrismReferenceDefinition ref = def.findReferenceDefinition(UserType.F_LINK_REF);
		ButtonInlineMenuItem item;
		PrismPropertyDefinition<ActivationStatusType> administrativeStatus = def
				.findPropertyDefinition(SchemaConstants.PATH_ACTIVATION_ADMINISTRATIVE_STATUS);
		if (administrativeStatus.canRead() && administrativeStatus.canModify()) {
			item = new ButtonInlineMenuItem(createStringResource("pageAdminFocus.button.enable")) {
				private static final long serialVersionUID = 1L;

				@Override
				public InlineMenuItemAction initAction() {
					return new ColumnMenuAction() {
						private static final long serialVersionUID = 1L;

						@Override
						public void onClick(AjaxRequestTarget target) {
							updateShadowActivation(target, getMultivalueContainerListPanel()
									.getPerformedSelectedItems(getRowModel()), true);
						}
					};
				}
				
				@Override
				public String getButtonIconCssClass() {
					return "fa fa-check";
				}
			};
			items.add(item);
			item = new ButtonInlineMenuItem(createStringResource("pageAdminFocus.button.disable")) {
				private static final long serialVersionUID = 1L;

				@Override
				public InlineMenuItemAction initAction() {
					return new ColumnMenuAction() {
						private static final long serialVersionUID = 1L;

						@Override
						public void onClick(AjaxRequestTarget target) {
							updateShadowActivation(target, getMultivalueContainerListPanel()
									.getPerformedSelectedItems(getRowModel()), false);
						}
					};
				}
				
				@Override
				public String getButtonIconCssClass() {
					return "fa fa-ban";
				}
			};
			items.add(item);
		}
		if (ref.canRead() && ref.canAdd()) {
			item = new ButtonInlineMenuItem(createStringResource("pageAdminFocus.button.unlink")) {
				private static final long serialVersionUID = 1L;

				@Override
				public InlineMenuItemAction initAction() {
					return new ColumnMenuAction() {
						private static final long serialVersionUID = 1L;

						@Override
						public void onClick(AjaxRequestTarget target) {
							unlinkProjectionPerformed(target,getMultivalueContainerListPanel()
									.getPerformedSelectedItems(getRowModel()));
						}
					};
				}
				
				@Override
				public String getButtonIconCssClass() {
					return GuiStyleConstants.CLASS_UNASSIGN;
				}
			};
			items.add(item);
		}
		PrismPropertyDefinition<LockoutStatusType> locakoutStatus = def.findPropertyDefinition(SchemaConstants.PATH_ACTIVATION_LOCKOUT_STATUS);
		if (locakoutStatus.canRead() && locakoutStatus.canModify()) {
			item = new ButtonInlineMenuItem(createStringResource("pageAdminFocus.button.unlock")) {
				private static final long serialVersionUID = 1L;

				@Override
				public InlineMenuItemAction initAction() {
					return new ColumnMenuAction() {
						private static final long serialVersionUID = 1L;

						@Override
						public void onClick(AjaxRequestTarget target) {
							unlockShadowPerformed(target,getMultivalueContainerListPanel()
									.getPerformedSelectedItems(getRowModel()));
						}
					};
				}
				
				@Override
				public String getButtonIconCssClass() {
					return "fa fa-unlock";
				}
			};
			items.add(item);
		}
		if (administrativeStatus.canRead() && administrativeStatus.canModify()) {
//			items.add(new InlineMenuItem());
			item = new ButtonInlineMenuItem(createStringResource("pageAdminFocus.button.delete")) {
				private static final long serialVersionUID = 1L;

				@Override
				public InlineMenuItemAction initAction() {
					return new ColumnMenuAction() {
						private static final long serialVersionUID = 1L;

						@Override
						public void onClick(AjaxRequestTarget target) {
							deleteProjectionPerformed(target, getMultivalueContainerListPanel()
									.getPerformedSelectedItems(getRowModel()));
						}
					};
				}

				@Override
				public String getButtonIconCssClass() {
					return GuiStyleConstants.CLASS_DELETE_MENU_ITEM;
				}
			};
			items.add(item);
		}
		item = new ButtonInlineMenuItem(createStringResource("PageBase.button.edit")) {
			private static final long serialVersionUID = 1L;

			@Override
			public String getButtonIconCssClass() {
				return GuiStyleConstants.CLASS_EDIT_MENU_ITEM;
			}

			@Override
			public InlineMenuItemAction initAction() {
				return new ColumnMenuAction() {
					private static final long serialVersionUID = 1L;

					@Override
					public void onClick(AjaxRequestTarget target) {
						getMultivalueContainerListPanel().itemPerformedForDefaultAction(target,
								getRowModel(), getMultivalueContainerListPanel().getSelectedItems());
						target.add(getFeedbackPanel());
					}
				};
			}
		};
		items.add(item);
		return items;
	}

	private void deleteProjectionPerformed(AjaxRequestTarget target,
			List<PrismContainerValueWrapper<ShadowType>> selected) {
		if (!isAnyProjectionSelected(target, selected)) {
			return;
		}

		showModalWindow(getDeleteProjectionPopupContent(selected),
				target);
	}

	private boolean isAnyProjectionSelected(AjaxRequestTarget target,
			List<PrismContainerValueWrapper<ShadowType>> selected) {
		if (selected.isEmpty()) {
			warn(getString("pageAdminFocus.message.noAccountSelected"));
			target.add(getFeedbackPanel());
			return false;
		}

		return true;
	}

	private void updateShadowActivation(AjaxRequestTarget target,
			List<PrismContainerValueWrapper<ShadowType>> accounts, boolean enabled) {
		
		if (!isAnyProjectionSelected(target, accounts)) {
			return;
		}

		for (PrismContainerValueWrapper<ShadowType> account : accounts) {
//			if (!account.isLoadedOK()) {
//				continue;
//			}
			try {
//				ObjectWrapperOld<ShadowType> wrapper = account.getObjectOld();
//				PrismObjectWrapper<ShadowType> wrapper = account.getObject();
				PrismContainerWrapper<ActivationType> activation = account
						.findContainer(ShadowType.F_ACTIVATION);
				if (activation == null) {
					warn(getString("pageAdminFocus.message.noActivationFound"));
					continue;
				}

				PrismPropertyWrapper enabledProperty = (PrismPropertyWrapper) activation.getValues().iterator().next()
						.findProperty(ActivationType.F_ADMINISTRATIVE_STATUS);
				if (enabledProperty == null || enabledProperty.getValues().size() != 1) {
					warn(getString("pageAdminFocus.message.noEnabledPropertyFound", account.getDisplayName()));
					continue;
				}
				PrismValueWrapper value = (PrismValueWrapper) enabledProperty.getValues().get(0);
				ActivationStatusType status = enabled ? ActivationStatusType.ENABLED
						: ActivationStatusType.DISABLED;
				((PrismPropertyValue) value.getNewValue()).setValue(status);
				
			} catch (SchemaException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

//			wrapper.setSelected(false);
		}
		info(getString("pageAdminFocus.message.updated." + enabled));
		target.add(getFeedbackPanel(), getMultivalueContainerListPanel());
	}

	private void unlockShadowPerformed(AjaxRequestTarget target,
			List<PrismContainerValueWrapper<ShadowType>> selected) {
		if (!isAnyProjectionSelected(target, selected)) {
			return;
		}

		for (PrismContainerValueWrapper<ShadowType> account : selected) {
//			if (!account.isLoadedOK()) {
//				continue;
//			}
			try {
//				ObjectWrapperOld<ShadowType> wrapper = account.getObjectOld();
//				PrismObjectWrapper<ShadowType> wrapper = account.getObject();
//				wrapper.setSelected(false);

				PrismContainerWrapper<ActivationType> activation = account.findContainer(ShadowType.F_ACTIVATION);
				if (activation == null) {
					warn(getString("pageAdminFocus.message.noActivationFound", account.getDisplayName()));
					continue;
				}

				PrismPropertyWrapper lockedProperty = (PrismPropertyWrapper) activation.getValues().iterator().next().findProperty(ActivationType.F_LOCKOUT_STATUS);
				if (lockedProperty == null || lockedProperty.getValues().size() != 1) {
					warn(getString("pageAdminFocus.message.noLockoutStatusPropertyFound"));
					continue;
				}
				PrismValueWrapper value = (PrismValueWrapper) lockedProperty.getValues().get(0);
				((PrismPropertyValue) value.getNewValue()).setValue(LockoutStatusType.NORMAL);
			} catch (SchemaException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}// TODO only for really unlocked accounts
		}
		info(getString("pageAdminFocus.message.unlocked"));	
		target.add(getFeedbackPanel(), getMultivalueContainerListPanel());
	}

	private void unlinkProjectionPerformed(AjaxRequestTarget target,
			List<PrismContainerValueWrapper<ShadowType>> selected) {
		if (!isAnyProjectionSelected(target, selected)) {
			return;
		}

		for (PrismContainerValueWrapper projection : selected) {
			if (UserDtoStatus.ADD.equals(((ShadowWrapper)projection.getParent()).getProjectionStatus())) {
				continue;
			}
			((ShadowWrapper)projection.getParent()).setProjectionStatus(UserDtoStatus.UNLINK);
		}
		target.add(getMultivalueContainerListPanel());
	}

	private Popupable getDeleteProjectionPopupContent(List<PrismContainerValueWrapper<ShadowType>> selected) {
		ConfirmationPanel dialog = new ConfirmationPanel(getPageBase().getMainPopupBodyId(),
				new IModel<String>() {
					private static final long serialVersionUID = 1L;

					@Override
					public String getObject() {
						return createStringResource("pageAdminFocus.message.deleteAccountConfirm",
								selected.size()).getString();
					}
				}) {
			private static final long serialVersionUID = 1L;

			@Override
			public void yesPerformed(AjaxRequestTarget target) {
				deleteAccountConfirmedPerformed(target, selected);
			}
		};
		return dialog;
	}

	private void deleteAccountConfirmedPerformed(AjaxRequestTarget target,
			List<PrismContainerValueWrapper<ShadowType>> selected) {
		List<ShadowWrapper> accounts = projectionModel.getObject();
		for (PrismContainerValueWrapper<ShadowType> account : selected) {
			if (UserDtoStatus.ADD.equals(((ShadowWrapper)account.getParent()).getProjectionStatus())) {
				accounts.remove(account.getParent());
			} else {
				((ShadowWrapper)account.getParent()).setProjectionStatus(UserDtoStatus.DELETE);
			}
		}
		target.add(getMultivalueContainerListPanel());
	}
}
