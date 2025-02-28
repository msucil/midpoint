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
package com.evolveum.midpoint.gui.api.util;

import static com.evolveum.midpoint.gui.api.page.PageBase.createStringResourceStatic;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import javax.xml.datatype.XMLGregorianCalendar;
import javax.xml.namespace.QName;

import com.evolveum.midpoint.model.api.visualizer.Scene;
import com.evolveum.midpoint.schema.*;
import com.evolveum.midpoint.schema.util.*;
import com.evolveum.midpoint.web.component.prism.show.SceneDto;
import com.evolveum.midpoint.web.component.prism.show.SceneUtil;
import com.evolveum.midpoint.web.page.admin.cases.PageCase;
import com.evolveum.midpoint.web.page.admin.server.dto.TaskChangesDto;
import com.evolveum.midpoint.web.page.admin.workflow.dto.EvaluatedTriggerGroupDto;
import com.evolveum.midpoint.wf.util.ChangesByState;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.Validate;
import org.apache.commons.lang.math.NumberUtils;
import org.apache.commons.lang.time.DurationFormatUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.validator.routines.checkdigit.VerhoeffCheckDigit;
import org.apache.wicket.Application;
import org.apache.wicket.Component;
import org.apache.wicket.Localizer;
import org.apache.wicket.MarkupContainer;
import org.apache.wicket.Page;
import org.apache.wicket.PageReference;
import org.apache.wicket.Session;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.form.AjaxFormComponentUpdatingBehavior;
import org.apache.wicket.ajax.markup.html.form.AjaxSubmitLink;
import org.apache.wicket.authroles.authentication.AuthenticatedWebApplication;
import org.apache.wicket.authroles.authorization.strategies.role.Roles;
import org.apache.wicket.behavior.Behavior;
import org.apache.wicket.core.request.handler.RenderPageRequestHandler;
import org.apache.wicket.datetime.PatternDateConverter;
import org.apache.wicket.extensions.markup.html.repeater.data.table.DataTable;
import org.apache.wicket.extensions.markup.html.tabs.ITab;
import org.apache.wicket.feedback.IFeedback;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.CheckBox;
import org.apache.wicket.markup.html.form.DropDownChoice;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.FormComponent;
import org.apache.wicket.markup.html.form.IChoiceRenderer;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.markup.repeater.data.IDataProvider;
import org.apache.wicket.model.AbstractReadOnlyModel;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.model.StringResourceModel;
import org.apache.wicket.request.IRequestHandler;
import org.apache.wicket.request.cycle.RequestCycle;
import org.apache.wicket.request.mapper.parameter.PageParameters;
import org.apache.wicket.util.string.StringValue;
import org.apache.wicket.util.visit.IVisit;
import org.apache.wicket.util.visit.IVisitor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joda.time.format.DateTimeFormat;

import com.evolveum.midpoint.common.refinery.CompositeRefinedObjectClassDefinition;
import com.evolveum.midpoint.common.refinery.RefinedAssociationDefinition;
import com.evolveum.midpoint.common.refinery.RefinedObjectClassDefinition;
import com.evolveum.midpoint.common.refinery.RefinedResourceSchema;
import com.evolveum.midpoint.common.refinery.RefinedResourceSchemaImpl;
import com.evolveum.midpoint.gui.api.GuiStyleConstants;
import com.evolveum.midpoint.gui.api.SubscriptionType;
import com.evolveum.midpoint.gui.api.component.MainObjectListPanel;
import com.evolveum.midpoint.gui.api.model.LoadableModel;
import com.evolveum.midpoint.gui.api.model.NonEmptyModel;
import com.evolveum.midpoint.gui.api.model.ReadOnlyValueModel;
import com.evolveum.midpoint.gui.api.page.PageBase;
import com.evolveum.midpoint.gui.api.prism.ItemWrapper;
import com.evolveum.midpoint.gui.api.prism.PrismContainerWrapper;
import com.evolveum.midpoint.gui.impl.component.icon.CompositedIconBuilder;
import com.evolveum.midpoint.gui.impl.component.icon.IconCssStyle;
import com.evolveum.midpoint.gui.impl.factory.WrapperContext;
import com.evolveum.midpoint.gui.impl.page.admin.configuration.component.ComponentLoggerType;
import com.evolveum.midpoint.gui.impl.page.admin.configuration.component.StandardLoggerType;
import com.evolveum.midpoint.gui.impl.prism.PrismContainerValueWrapper;
import com.evolveum.midpoint.gui.impl.prism.PrismPropertyValueWrapper;
import com.evolveum.midpoint.gui.impl.prism.PrismPropertyWrapper;
import com.evolveum.midpoint.model.api.AssignmentObjectRelation;
import com.evolveum.midpoint.model.api.ModelExecuteOptions;
import com.evolveum.midpoint.model.api.ModelInteractionService;
import com.evolveum.midpoint.model.api.RoleSelectionSpecification;
import com.evolveum.midpoint.model.api.authentication.CompiledObjectCollectionView;
import com.evolveum.midpoint.model.api.util.ResourceUtils;
import com.evolveum.midpoint.prism.Containerable;
import com.evolveum.midpoint.prism.Objectable;
import com.evolveum.midpoint.prism.PrismConstants;
import com.evolveum.midpoint.prism.PrismContainer;
import com.evolveum.midpoint.prism.PrismContainerValue;
import com.evolveum.midpoint.prism.PrismContext;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.PrismObjectDefinition;
import com.evolveum.midpoint.prism.PrismProperty;
import com.evolveum.midpoint.prism.PrismPropertyDefinition;
import com.evolveum.midpoint.prism.PrismPropertyValue;
import com.evolveum.midpoint.prism.Referencable;
import com.evolveum.midpoint.prism.Revivable;
import com.evolveum.midpoint.prism.crypto.EncryptionException;
import com.evolveum.midpoint.prism.crypto.Protector;
import com.evolveum.midpoint.prism.delta.ObjectDelta;
import com.evolveum.midpoint.prism.delta.PropertyDelta;
import com.evolveum.midpoint.prism.path.ItemName;
import com.evolveum.midpoint.prism.path.ItemPath;
import com.evolveum.midpoint.prism.path.ItemPathCollectionsUtil;
import com.evolveum.midpoint.prism.polystring.PolyString;
import com.evolveum.midpoint.prism.query.AndFilter;
import com.evolveum.midpoint.prism.query.ObjectFilter;
import com.evolveum.midpoint.prism.query.ObjectPaging;
import com.evolveum.midpoint.prism.query.ObjectQuery;
import com.evolveum.midpoint.prism.query.OrFilter;
import com.evolveum.midpoint.prism.query.builder.S_FilterEntryOrEmpty;
import com.evolveum.midpoint.prism.util.PolyStringUtils;
import com.evolveum.midpoint.prism.xml.XmlTypeConverter;
import com.evolveum.midpoint.schema.constants.ObjectTypes;
import com.evolveum.midpoint.schema.constants.RelationTypes;
import com.evolveum.midpoint.schema.constants.SchemaConstants;
import com.evolveum.midpoint.schema.processor.ResourceSchema;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.schema.result.OperationResultStatus;
import com.evolveum.midpoint.security.api.AuthorizationConstants;
import com.evolveum.midpoint.security.api.MidPointPrincipal;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.task.api.TaskBinding;
import com.evolveum.midpoint.task.api.TaskCategory;
import com.evolveum.midpoint.task.api.TaskExecutionStatus;
import com.evolveum.midpoint.util.DOMUtil;
import com.evolveum.midpoint.util.DebugUtil;
import com.evolveum.midpoint.util.DisplayableValue;
import com.evolveum.midpoint.util.LocalizableMessage;
import com.evolveum.midpoint.util.LocalizableMessageList;
import com.evolveum.midpoint.util.MiscUtil;
import com.evolveum.midpoint.util.QNameUtil;
import com.evolveum.midpoint.util.SingleLocalizableMessage;
import com.evolveum.midpoint.util.exception.CommunicationException;
import com.evolveum.midpoint.util.exception.ConfigurationException;
import com.evolveum.midpoint.util.exception.ExpressionEvaluationException;
import com.evolveum.midpoint.util.exception.ObjectAlreadyExistsException;
import com.evolveum.midpoint.util.exception.ObjectNotFoundException;
import com.evolveum.midpoint.util.exception.PolicyViolationException;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.util.exception.SecurityViolationException;
import com.evolveum.midpoint.util.exception.SystemException;
import com.evolveum.midpoint.util.logging.LoggingUtils;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.web.component.DateLabelComponent;
import com.evolveum.midpoint.web.component.TabbedPanel;
import com.evolveum.midpoint.web.component.breadcrumbs.Breadcrumb;
import com.evolveum.midpoint.web.component.breadcrumbs.BreadcrumbPageClass;
import com.evolveum.midpoint.web.component.breadcrumbs.BreadcrumbPageInstance;
import com.evolveum.midpoint.web.component.data.BaseSortableDataProvider;
import com.evolveum.midpoint.web.component.data.SelectableBeanObjectDataProvider;
import com.evolveum.midpoint.web.component.data.Table;
import com.evolveum.midpoint.web.component.data.column.ColumnMenuAction;
import com.evolveum.midpoint.web.component.input.DisplayableValueChoiceRenderer;
import com.evolveum.midpoint.web.component.input.DropDownChoicePanel;
import com.evolveum.midpoint.web.component.menu.cog.InlineMenuItem;
import com.evolveum.midpoint.web.component.menu.cog.InlineMenuItemAction;
import com.evolveum.midpoint.web.component.prism.ContainerStatus;
import com.evolveum.midpoint.web.component.prism.InputPanel;
import com.evolveum.midpoint.web.component.prism.ItemVisibility;
import com.evolveum.midpoint.web.component.prism.ValueStatus;
import com.evolveum.midpoint.web.component.util.Selectable;
import com.evolveum.midpoint.web.component.util.SelectableBean;
import com.evolveum.midpoint.web.component.util.VisibleEnableBehaviour;
import com.evolveum.midpoint.web.page.PageDialog;
import com.evolveum.midpoint.web.page.admin.configuration.component.EmptyOnBlurAjaxFormUpdatingBehaviour;
import com.evolveum.midpoint.web.page.admin.configuration.component.EmptyOnChangeAjaxFormUpdatingBehavior;
import com.evolveum.midpoint.web.page.admin.reports.PageReport;
import com.evolveum.midpoint.web.page.admin.reports.PageReports;
import com.evolveum.midpoint.web.page.admin.resources.PageResource;
import com.evolveum.midpoint.web.page.admin.resources.PageResourceWizard;
import com.evolveum.midpoint.web.page.admin.resources.PageResources;
import com.evolveum.midpoint.web.page.admin.roles.PageRole;
import com.evolveum.midpoint.web.page.admin.roles.PageRoles;
import com.evolveum.midpoint.web.page.admin.server.PageTaskEdit;
import com.evolveum.midpoint.web.page.admin.server.dto.OperationResultStatusPresentationProperties;
import com.evolveum.midpoint.web.page.admin.services.PageService;
import com.evolveum.midpoint.web.page.admin.services.PageServices;
import com.evolveum.midpoint.web.page.admin.users.PageOrgUnit;
import com.evolveum.midpoint.web.page.admin.users.PageUser;
import com.evolveum.midpoint.web.page.admin.users.PageUsers;
import com.evolveum.midpoint.web.page.admin.valuePolicy.PageValuePolicy;
import com.evolveum.midpoint.web.security.MidPointApplication;
import com.evolveum.midpoint.web.security.SecurityUtils;
import com.evolveum.midpoint.web.session.SessionStorage;
import com.evolveum.midpoint.web.session.UserProfileStorage.TableId;
import com.evolveum.midpoint.web.util.DateValidator;
import com.evolveum.midpoint.web.util.InfoTooltipBehavior;
import com.evolveum.midpoint.web.util.ObjectTypeGuiDescriptor;
import com.evolveum.midpoint.web.util.OnePageParameterEncoder;
import com.evolveum.midpoint.xml.ns._public.model.scripting_3.ScriptingExpressionType;
import com.evolveum.midpoint.xml.ns._public.resource.capabilities_3.CapabilityType;
import com.evolveum.prism.xml.ns._public.query_3.QueryType;
import com.evolveum.prism.xml.ns._public.types_3.ObjectDeltaType;
import com.evolveum.prism.xml.ns._public.types_3.PolyStringType;
import com.evolveum.prism.xml.ns._public.types_3.ProtectedStringType;

/**
 * Utility class containing miscellaneous methods used mostly in Wicket
 * components.
 *
 * @author lazyman
 */
public final class WebComponentUtil {

	private static final Trace LOGGER = TraceManager.getTrace(WebComponentUtil.class);

	private static final String KEY_BOOLEAN_NULL = "Boolean.NULL";
	private static final String KEY_BOOLEAN_TRUE = "Boolean.TRUE";
	private static final String KEY_BOOLEAN_FALSE = "Boolean.FALSE";

	/**
	 * To be used only for tests when there's no MidpointApplication.
	 * (Quite a hack. Replace eventually by a more serious solution.)
	 */
	private static RelationRegistry staticallyProvidedRelationRegistry;

	private static Map<Class<?>, Class<? extends PageBase>> objectDetailsPageMap;
	private static Map<Class<?>, Class<? extends PageBase>> createNewObjectPageMap;

	static {
		objectDetailsPageMap = new HashMap<>();
		objectDetailsPageMap.put(UserType.class, PageUser.class);
		objectDetailsPageMap.put(OrgType.class, PageOrgUnit.class);
		objectDetailsPageMap.put(RoleType.class, PageRole.class);
		objectDetailsPageMap.put(ServiceType.class, PageService.class);
		objectDetailsPageMap.put(ResourceType.class, PageResource.class);
		objectDetailsPageMap.put(TaskType.class, PageTaskEdit.class);
		objectDetailsPageMap.put(ReportType.class, PageReport.class);
		objectDetailsPageMap.put(ValuePolicyType.class, PageValuePolicy.class);
		objectDetailsPageMap.put(CaseType.class, PageCase.class);
	}

	static{
		createNewObjectPageMap = new HashMap<>();
		createNewObjectPageMap.put(ResourceType.class, PageResourceWizard.class);
	}

	// only pages that support 'advanced search' are currently listed here (TODO: generalize)
	private static Map<Class<?>, Class<? extends PageBase>> objectListPageMap;

	static {
		objectListPageMap = new HashMap<>();
		objectListPageMap.put(UserType.class, PageUsers.class);
		objectListPageMap.put(RoleType.class, PageRoles.class);
		objectListPageMap.put(ServiceType.class, PageServices.class);
		objectListPageMap.put(ResourceType.class, PageResources.class);
	}

	private static Map<Class<?>, String> storageKeyMap;

	static {
		storageKeyMap = new HashMap<>();
		storageKeyMap.put(PageUsers.class, SessionStorage.KEY_USERS);
		storageKeyMap.put(PageResources.class, SessionStorage.KEY_RESOURCES);
		storageKeyMap.put(PageReports.class, SessionStorage.KEY_REPORTS);
		storageKeyMap.put(PageRoles.class, SessionStorage.KEY_ROLES);
		storageKeyMap.put(PageServices.class, SessionStorage.KEY_SERVICES);
	}

	private static Map<TableId, String> storageTableIdMap;

	static {
		storageTableIdMap = new HashMap<>();
		storageTableIdMap.put(TableId.PAGE_RESOURCE_ACCOUNTS_PANEL_REPOSITORY_MODE, SessionStorage.KEY_RESOURCE_ACCOUNT_CONTENT + SessionStorage.KEY_RESOURCE_PAGE_REPOSITORY_CONTENT);
		storageTableIdMap.put(TableId.PAGE_RESOURCE_ACCOUNTS_PANEL_RESOURCE_MODE, SessionStorage.KEY_RESOURCE_ACCOUNT_CONTENT + SessionStorage.KEY_RESOURCE_PAGE_RESOURCE_CONTENT);
		storageTableIdMap.put(TableId.PAGE_RESOURCE_ENTITLEMENT_PANEL_REPOSITORY_MODE, SessionStorage.KEY_RESOURCE_ENTITLEMENT_CONTENT + SessionStorage.KEY_RESOURCE_PAGE_REPOSITORY_CONTENT);
		storageTableIdMap.put(TableId.PAGE_RESOURCE_ENTITLEMENT_PANEL_RESOURCE_MODE, SessionStorage.KEY_RESOURCE_ENTITLEMENT_CONTENT + SessionStorage.KEY_RESOURCE_PAGE_RESOURCE_CONTENT);
		storageTableIdMap.put(TableId.PAGE_RESOURCE_GENERIC_PANEL_REPOSITORY_MODE, SessionStorage.KEY_RESOURCE_GENERIC_CONTENT + SessionStorage.KEY_RESOURCE_PAGE_REPOSITORY_CONTENT);
		storageTableIdMap.put(TableId.PAGE_RESOURCE_GENERIC_PANEL_RESOURCE_MODE, SessionStorage.KEY_RESOURCE_GENERIC_CONTENT + SessionStorage.KEY_RESOURCE_PAGE_RESOURCE_CONTENT);
		storageTableIdMap.put(TableId.PAGE_RESOURCE_OBJECT_CLASS_PANEL, SessionStorage.KEY_RESOURCE_OBJECT_CLASS_CONTENT);
		storageTableIdMap.put(TableId.ROLE_MEMEBER_PANEL, SessionStorage.KEY_ROLE_MEMEBER_PANEL);
		storageTableIdMap.put(TableId.ORG_MEMEBER_PANEL, SessionStorage.KEY_ORG_MEMEBER_PANEL);
		storageTableIdMap.put(TableId.SERVICE_MEMEBER_PANEL, SessionStorage.KEY_SERVICE_MEMEBER_PANEL);

	}

	private static final Map<String, LoggingComponentType> componentMap = new HashMap<>();

	static {
		componentMap.put("com.evolveum.midpoint", LoggingComponentType.ALL);
		componentMap.put("com.evolveum.midpoint.model", LoggingComponentType.MODEL);
		componentMap.put("com.evolveum.midpoint.provisioning", LoggingComponentType.PROVISIONING);
		componentMap.put("com.evolveum.midpoint.repo", LoggingComponentType.REPOSITORY);
		componentMap.put("com.evolveum.midpoint.web", LoggingComponentType.WEB);
		componentMap.put("com.evolveum.midpoint.gui", LoggingComponentType.GUI);
		componentMap.put("com.evolveum.midpoint.task", LoggingComponentType.TASKMANAGER);
		componentMap.put("com.evolveum.midpoint.model.sync",
				LoggingComponentType.RESOURCEOBJECTCHANGELISTENER);
		componentMap.put("com.evolveum.midpoint.wf", LoggingComponentType.WORKFLOWS);
		componentMap.put("com.evolveum.midpoint.notifications", LoggingComponentType.NOTIFICATIONS);
		componentMap.put("com.evolveum.midpoint.certification", LoggingComponentType.ACCESS_CERTIFICATION);
		componentMap.put("com.evolveum.midpoint.security", LoggingComponentType.SECURITY);
	}

	public enum AssignmentOrder{

		ASSIGNMENT(0),
		INDUCEMENT(1);

		private int order;

		AssignmentOrder(int order){
			this.order = order;
		}

		public int getOrder() {
			return order;
		}
	}

	public static String nl2br(String text) {
		if (text == null) {
			return null;
		}
		return StringEscapeUtils.escapeHtml4(text).replace("\n", "<br/>");
	}

	public static String getTypeLocalized(ObjectReferenceType ref) {
		ObjectTypes type = ref != null ? ObjectTypes.getObjectTypeFromTypeQName(ref.getType()) : null;
		ObjectTypeGuiDescriptor descriptor = ObjectTypeGuiDescriptor.getDescriptor(type);
		if (descriptor == null) {
			return null;
		}
		return createStringResourceStatic(null, descriptor.getLocalizationKey()).getString();
	}

	public static String getReferencedObjectNames(List<ObjectReferenceType> refs, boolean showTypes) {
		return refs.stream()
				.map(ref -> emptyIfNull(getName(ref)) + (showTypes ? (" (" + emptyIfNull(getTypeLocalized(ref)) + ")") : ""))
				.collect(Collectors.joining(", "));
	}

	private static String emptyIfNull(String s) {
		return s != null ? s : "";
	}

	public static String getReferencedObjectDisplayNamesAndNames(List<ObjectReferenceType> refs, boolean showTypes) {
		return refs.stream()
				.map(ref -> emptyIfNull(getDisplayNameAndName(ref)) + (showTypes ? (" (" + emptyIfNull(getTypeLocalized(ref)) + ")") : ""))
				.collect(Collectors.joining(", "));
	}
	
	public static String getReferencedObjectDisplayNamesAndNames(Referencable ref, boolean showTypes) {
		if (ref == null){
			return "";
		}
		String name = ref.getTargetName() == null ? "" : ref.getTargetName().getOrig();
		StringBuilder sb = new StringBuilder(name);
		if(showTypes) {
			sb.append(" (");
			ObjectTypes type = ObjectTypes.getObjectTypeFromTypeQName(ref.getType());
			ObjectTypeGuiDescriptor descriptor = ObjectTypeGuiDescriptor.getDescriptor(type);
			if (descriptor == null) {
				return null;
			}
			sb.append(emptyIfNull(createStringResourceStatic(null, descriptor.getLocalizationKey()).getString())).append(")");
		}
		return sb.toString();
	}

	public static <O extends ObjectType> List<O> loadReferencedObjectList(List<ObjectReferenceType> refList, String operation, PageBase pageBase){
		List<O> loadedObjectsList = new ArrayList<>();
		if (refList == null){
			return loadedObjectsList;
		}
		refList.forEach(objectRef -> {
			OperationResult result = new OperationResult(operation);
			PrismObject<O> loadedObject = WebModelServiceUtils.resolveReferenceNoFetch(objectRef, pageBase, pageBase.createSimpleTask(operation), result);
			if (loadedObject != null) {
				loadedObjectsList.add(loadedObject.asObjectable());
			}
		});
		return loadedObjectsList;
	}

	public static ObjectFilter getShadowTypeFilterForAssociation(ConstructionType construction, String operation, PageBase pageBase){
		PrismContext prismContext = pageBase.getPrismContext();
		if (construction == null){
			return null;
		}
		PrismObject<ResourceType> resource = WebComponentUtil.getConstructionResource(construction, operation, pageBase);
		if (resource == null){
			return null;
		}

		ObjectQuery query = prismContext.queryFactory().createQuery();
		try {
			RefinedResourceSchema refinedResourceSchema = RefinedResourceSchema.getRefinedSchema(resource);
			RefinedObjectClassDefinition oc = refinedResourceSchema.getRefinedDefinition(construction.getKind(), construction.getIntent());
			if (oc == null){
				return null;
			}
			Collection<RefinedAssociationDefinition> refinedAssociationDefinitions = oc.getAssociationDefinitions();

			for (RefinedAssociationDefinition refinedAssociationDefinition : refinedAssociationDefinitions) {
				S_FilterEntryOrEmpty atomicFilter = prismContext.queryFor(ShadowType.class);
				List<ObjectFilter> orFilterClauses = new ArrayList<>();
				refinedAssociationDefinition.getIntents()
						.forEach(intent -> orFilterClauses.add(atomicFilter.item(ShadowType.F_INTENT).eq(intent).buildFilter()));
				OrFilter intentFilter = prismContext.queryFactory().createOr(orFilterClauses);

				AndFilter filter = (AndFilter) atomicFilter.item(ShadowType.F_KIND).eq(refinedAssociationDefinition.getKind()).and()
						.item(ShadowType.F_RESOURCE_REF).ref(resource.getOid(), ResourceType.COMPLEX_TYPE).buildFilter();
				filter.addCondition(intentFilter);
				query.setFilter(filter);        // TODO this overwrites existing filter (created in previous cycle iteration)... is it OK? [med]
			}
		} catch (SchemaException ex) {
			LOGGER.error("Couldn't create query filter for ShadowType for association: {}" , ex.getErrorTypeMessage());
		}
		return query.getFilter();
	}

	public static void addAjaxOnUpdateBehavior(WebMarkupContainer container) {
        container.visitChildren(new IVisitor<Component, Object>() {
            @Override
            public void component(Component component, IVisit<Object> objectIVisit) {
                if (component instanceof InputPanel) {
                    addAjaxOnBlurUpdateBehaviorToComponent(((InputPanel) component).getBaseFormComponent());
                } else if (component instanceof FormComponent) {
                    addAjaxOnBlurUpdateBehaviorToComponent(component);
                }
            }
        });
    }

	private static void addAjaxOnBlurUpdateBehaviorToComponent(final Component component) {
        component.setOutputMarkupId(true);
        component.add(new AjaxFormComponentUpdatingBehavior("blur") {

            @Override
            protected void onUpdate(AjaxRequestTarget target) {
            }
        });
    }

	public static String resolveLocalizableMessage(LocalizableMessageType localizableMessage, Component component) {
		if (localizableMessage == null) {
			return null;
		}
		return resolveLocalizableMessage(LocalizationUtil.toLocalizableMessage(localizableMessage), component);
	}

	public static String resolveLocalizableMessage(LocalizableMessage localizableMessage, Component component) {
		if (localizableMessage == null) {
			return null;
		} else if (localizableMessage instanceof SingleLocalizableMessage) {
			return resolveLocalizableMessage((SingleLocalizableMessage) localizableMessage, component);
		} else if (localizableMessage instanceof LocalizableMessageList) {
			return resolveLocalizableMessage((LocalizableMessageList) localizableMessage, component);
		} else {
			throw new AssertionError("Unsupported localizable message type: " + localizableMessage);
		}
	}

	private static String resolveLocalizableMessage(SingleLocalizableMessage localizableMessage, Component component) {
		if (localizableMessage == null) {
			return null;
		}
		while (localizableMessage.getFallbackLocalizableMessage() != null) {
			if (localizableMessage.getKey() != null) {
				Localizer localizer = Application.get().getResourceSettings().getLocalizer();
				if (localizer.getStringIgnoreSettings(localizableMessage.getKey(), component, null, null) != null) {
					break; // the key exists => we can use the current localizableMessage
				}
			}
			if (localizableMessage.getFallbackLocalizableMessage() instanceof SingleLocalizableMessage) {
				localizableMessage = (SingleLocalizableMessage) localizableMessage.getFallbackLocalizableMessage();
			} else {
				return resolveLocalizableMessage(localizableMessage.getFallbackLocalizableMessage(), component);
			}
		}
		String key = localizableMessage.getKey() != null ? localizableMessage.getKey() : localizableMessage.getFallbackMessage();
		StringResourceModel stringResourceModel = new StringResourceModel(key, component)
				.setModel(new Model<String>())
				.setDefaultValue(localizableMessage.getFallbackMessage())
				.setParameters(resolveArguments(localizableMessage.getArgs(), component));
		String rv = stringResourceModel.getString();
		//System.out.println("GUI: Resolving [" + key + "]: to [" + rv + "]");
		return rv;
	}

	// todo deduplicate with similar method in LocalizationServiceImpl
	private static String resolveLocalizableMessage(LocalizableMessageList msgList, Component component) {
		String separator = resolveIfPresent(msgList.getSeparator(), component);
		String prefix = resolveIfPresent(msgList.getPrefix(), component);
		String suffix = resolveIfPresent(msgList.getPostfix(), component);
		return msgList.getMessages().stream()
				.map(m -> resolveLocalizableMessage(m, component))
				.collect(Collectors.joining(separator, prefix, suffix));
	}

	private static String resolveIfPresent(LocalizableMessage msg, Component component) {
		return msg != null ? resolveLocalizableMessage(msg, component) : "";
	}

	private static Object[] resolveArguments(Object[] args, Component component) {
		if (args == null) {
			return null;
		}
		Object[] rv = new Object[args.length];
		for (int i = 0; i < args.length; i++) {
			if (args[i] instanceof LocalizableMessage) {
				rv[i] = resolveLocalizableMessage(((LocalizableMessage) args[i]), component);
			} else {
				rv[i] = args[i];
			}
		}
		return rv;
	}

	// TODO add other classes; probably move to some enum
	@Nullable
	public static String getAuthorizationActionForTargetClass(Class targetClass) {
	    if (UserType.class.equals(targetClass)) {
		    return AuthorizationConstants.AUTZ_UI_USER_URL;
	    } else if (OrgType.class.equals(targetClass)) {
	        return AuthorizationConstants.AUTZ_UI_ORG_UNIT_URL;
	    } else if (RoleType.class.equals(targetClass)) {
		    return AuthorizationConstants.AUTZ_UI_ROLE_URL;
	    } else if (ServiceType.class.equals(targetClass)) {
		    return AuthorizationConstants.AUTZ_UI_SERVICE_URL;
	    } else if (ResourceType.class.equals(targetClass)) {
		    return AuthorizationConstants.AUTZ_UI_RESOURCE_URL;
	    } else {
		    return null;
	    }
	}

	public static void safeResultCleanup(OperationResult result, Trace logger) {
		try {
			result.cleanupResultDeeply();
		} catch (Throwable t) {
			LoggingUtils.logUnexpectedException(logger, "Couldn't clean up the operation result", t);
		}
	}

	/**
	 * Default list view setting should never be needed. Always check setting for specific
	 * object type (and archetype).
	 */
	@Deprecated
	public static CompiledObjectCollectionView getDefaultGuiObjectListType(PageBase pageBase) {
		return pageBase.getCompiledUserProfile().getDefaultObjectCollectionView();
	}

	public enum Channel {
		// TODO: move this to schema component
		LIVE_SYNC(SchemaConstants.CHANGE_CHANNEL_LIVE_SYNC_URI),
		RECONCILIATION(SchemaConstants.CHANGE_CHANNEL_RECON_URI),
		DISCOVERY(SchemaConstants.CHANGE_CHANNEL_DISCOVERY_URI),
		WEB_SERVICE(SchemaConstants.CHANNEL_WEB_SERVICE_URI),
		IMPORT(SchemaConstants.CHANNEL_OBJECT_IMPORT_URI),
		REST(SchemaConstants.CHANNEL_REST_URI),
		INIT(SchemaConstants.CHANNEL_GUI_INIT_URI),
		USER(SchemaConstants.CHANNEL_GUI_USER_URI),
		SELF_REGISTRATION(SchemaConstants.CHANNEL_GUI_SELF_REGISTRATION_URI),
		RESET_PASSWORD(SchemaConstants.CHANNEL_GUI_RESET_PASSWORD_URI);

		private String channel;

		Channel(String channel) {
			this.channel = channel;
		}

		public String getChannel() {
			return channel;
		}
	}

	public static DateValidator getRangeValidator(Form<?> form, ItemPath path) {
        DateValidator validator = null;
        List<DateValidator> validators = form.getBehaviors(DateValidator.class);
        if (validators != null) {
            for (DateValidator val : validators) {
                if (path.equivalent(val.getIdentifier())) {
                    validator = val;
                    break;
                }
            }
        }

        if (validator == null) {
            validator = new DateValidator();
            validator.setIdentifier(path);
            form.add(validator);
        }

        return validator;
    }
	
	public static boolean isItemVisible(List<ItemPath> visibleItems, ItemPath itemToBeFound) {
			return ItemPathCollectionsUtil.containsSubpathOrEquivalent(visibleItems, itemToBeFound);
	
	}

	public static Class<?> qnameToClass(PrismContext prismContext, QName type) {
		return prismContext.getSchemaRegistry().determineCompileTimeClass(type);
	}

	public static <T extends ObjectType> Class<T> qnameToClass(PrismContext prismContext, QName type, Class<T> returnType) {
		return returnType = prismContext.getSchemaRegistry().determineCompileTimeClass(type);
	}

	public static <T extends ObjectType> QName classToQName(PrismContext prismContext, Class<T> clazz) {
		return prismContext.getSchemaRegistry().findObjectDefinitionByCompileTimeClass(clazz).getTypeName();
	}

	public static TaskType createSingleRecurrenceTask(String taskName, QName applicableType, ObjectQuery query,
			ObjectDelta delta, ModelExecuteOptions options, String category, PageBase pageBase) throws SchemaException {

		TaskType task = new TaskType(pageBase.getPrismContext());

		MidPointPrincipal owner = SecurityUtils.getPrincipalUser();

		ObjectReferenceType ownerRef = new ObjectReferenceType();
		ownerRef.setOid(owner.getOid());
		ownerRef.setType(owner.getUser().COMPLEX_TYPE);
		task.setOwnerRef(ownerRef);

		task.setBinding(TaskBindingType.LOOSE);
		task.setCategory(category);
		task.setExecutionStatus(TaskExecutionStatusType.RUNNABLE);
		task.setRecurrence(TaskRecurrenceType.SINGLE);
		task.setThreadStopAction(ThreadStopActionType.RESTART);
		task.setHandlerUri(pageBase.getTaskService().getHandlerUriForCategory(category));
		ScheduleType schedule = new ScheduleType();
		schedule.setMisfireAction(MisfireActionType.EXECUTE_IMMEDIATELY);
		task.setSchedule(schedule);

		task.setName(WebComponentUtil.createPolyFromOrigString(taskName));

		PrismObject<TaskType> prismTask = task.asPrismObject();
		QueryType queryType = pageBase.getQueryConverter().createQueryType(query);
		prismTask.findOrCreateProperty(SchemaConstants.PATH_MODEL_EXTENSION_OBJECT_QUERY).addRealValue(queryType);
		prismTask.findOrCreateProperty(SchemaConstants.PATH_MODEL_EXTENSION_OBJECT_TYPE).setRealValue(applicableType);

		if (delta != null) {
			ObjectDeltaType deltaBean = DeltaConvertor.toObjectDeltaType(delta);
			prismTask.findOrCreateProperty(SchemaConstants.PATH_MODEL_EXTENSION_OBJECT_DELTA).setRealValue(deltaBean);
		}

		if (options != null) {
			prismTask.findOrCreateProperty(SchemaConstants.PATH_MODEL_EXTENSION_EXECUTE_OPTIONS)
					.setRealValue(options.toModelExecutionOptionsType());
		}
		return task;
	}
	
	public static void executeBulkAction(PageBase pageBase, ScriptingExpressionType script, Task task, OperationResult result ) 
			throws SchemaException, SecurityViolationException, ObjectNotFoundException, ExpressionEvaluationException, 
			CommunicationException, ConfigurationException{
		
		pageBase.getScriptingService().evaluateExpressionInBackground(script, task, result);
	}
	
	public static void executeMemberOperation(Task operationalTask, QName type, ObjectQuery memberQuery,
			ScriptingExpressionType script, OperationResult parentResult, PageBase pageBase) throws SchemaException {

		MidPointPrincipal owner = SecurityUtils.getPrincipalUser();
		operationalTask.setOwner(owner.getUser().asPrismObject());
		
		operationalTask.setBinding(TaskBinding.LOOSE);
		operationalTask.setInitialExecutionStatus(TaskExecutionStatus.RUNNABLE);
		operationalTask.setThreadStopAction(ThreadStopActionType.RESTART);
		ScheduleType schedule = new ScheduleType();
		schedule.setMisfireAction(MisfireActionType.EXECUTE_IMMEDIATELY);
		operationalTask.makeSingle(schedule);
		operationalTask.setName(WebComponentUtil.createPolyFromOrigString(parentResult.getOperation()));
	
		try {
			executeBulkAction(pageBase, script, operationalTask, parentResult);
			parentResult.recordInProgress();
			parentResult.setBackgroundTaskOid(operationalTask.getOid());
			pageBase.showResult(parentResult);
		} catch (ObjectNotFoundException | SchemaException
			| ExpressionEvaluationException | CommunicationException | ConfigurationException
			| SecurityViolationException e) {
			parentResult.recordFatalError(pageBase.createStringResource("WebComponentUtil.message.startPerformed.fatalError.submit").getString(), e);
	        LoggingUtils.logUnexpectedException(LOGGER, "Couldn't submit bulk action to execution", e);
		}
	}

	public static boolean isAuthorized(String... action) {
		if (action == null || action.length == 0) {
			return true;
		}
		List<String> actions = Arrays.asList(action);
		return isAuthorized(actions);
	}

	public static boolean isAuthorized(Collection<String> actions) {
		if (actions == null || actions.isEmpty()) {
			return true;
		}
		Roles roles = new Roles(AuthorizationConstants.AUTZ_ALL_URL);
		roles.add(AuthorizationConstants.AUTZ_GUI_ALL_URL);
		roles.add(AuthorizationConstants.AUTZ_GUI_ALL_DEPRECATED_URL);
		roles.addAll(actions);
		if (((AuthenticatedWebApplication) AuthenticatedWebApplication.get()).hasAnyRole(roles)) {
			return true;
		}
		return false;
	}

	// TODO: move to util component
	public static Integer safeLongToInteger(Long l) {
		if (l == null) {
			return null;
		}

		if (l > Integer.MAX_VALUE || l < Integer.MIN_VALUE) {
			throw new IllegalArgumentException(
					"Couldn't transform long '" + l + "' to int, too big or too small.");
		}

		return (int) l.longValue();
	}

	// TODO: move to schema component
	public static List<QName> createObjectTypeList() {

		List<QName> types = new ArrayList<>(ObjectTypes.values().length);
		for (ObjectTypes t : ObjectTypes.values()) {
			types.add(t.getTypeQName());
		}

		return types.stream().sorted((type1, type2) -> {
				Validate.notNull(type1);
				Validate.notNull(type2);

				return String.CASE_INSENSITIVE_ORDER.compare(QNameUtil.qNameToUri(type1), QNameUtil.qNameToUri(type2));


		}).collect(Collectors.toList());

	}

	public static List<QName> createAssignmentHolderTypeQnamesList() {

		List<ObjectTypes> objectTypes = createAssignmentHolderTypesList();
		List<QName> types = new ArrayList<>();
		objectTypes.forEach(objectType -> {
			types.add(objectType.getTypeQName());
		});

		return types.stream().sorted((type1, type2) -> {
				Validate.notNull(type1);
				Validate.notNull(type2);

				return String.CASE_INSENSITIVE_ORDER.compare(QNameUtil.qNameToUri(type1), QNameUtil.qNameToUri(type2));


		}).collect(Collectors.toList());

	}

	public static List<ObjectTypes> createAssignmentHolderTypesList(){
		List<ObjectTypes> objectTypes = new ArrayList<>();
		for (ObjectTypes t : ObjectTypes.values()) {
			if (AssignmentHolderType.class.isAssignableFrom(t.getClassDefinition())) {
				objectTypes.add(t);
			}
		}
		return objectTypes.stream().sorted((type1, type2) -> {
			Validate.notNull(type1);
			Validate.notNull(type2);

			return String.CASE_INSENSITIVE_ORDER.compare(QNameUtil.qNameToUri(type1.getTypeQName()), QNameUtil.qNameToUri(type2.getTypeQName()));


		}).collect(Collectors.toList());
	}

	// TODO: move to schema component
	public static List<QName> createFocusTypeList() {
		return createFocusTypeList(false);
	}
	
	public static List<QName> createFocusTypeList(boolean includeAbstractType) {
		List<QName> focusTypeList = new ArrayList<>();

		focusTypeList.add(UserType.COMPLEX_TYPE);
		focusTypeList.add(OrgType.COMPLEX_TYPE);
		focusTypeList.add(RoleType.COMPLEX_TYPE);
		focusTypeList.add(ServiceType.COMPLEX_TYPE);
		
		if (includeAbstractType) {
			focusTypeList.add(FocusType.COMPLEX_TYPE);
		}

		return focusTypeList;
	}

	// TODO: move to schema component
	public static List<QName> createAbstractRoleTypeList() {
		List<QName> focusTypeList = new ArrayList<>();

		focusTypeList.add(AbstractRoleType.COMPLEX_TYPE);
		focusTypeList.add(OrgType.COMPLEX_TYPE);
		focusTypeList.add(RoleType.COMPLEX_TYPE);
		focusTypeList.add(ServiceType.COMPLEX_TYPE);

		return focusTypeList;
	}

	public static List<ObjectTypes> createAssignableTypesList() {
		List<ObjectTypes> focusTypeList = new ArrayList<>();

		focusTypeList.add(ObjectTypes.RESOURCE);
		focusTypeList.add(ObjectTypes.ORG);
		focusTypeList.add(ObjectTypes.ROLE);
		focusTypeList.add(ObjectTypes.SERVICE);

		return focusTypeList;
	}
	
	public static List<QName> createSupportedTargetTypeList(QName targetTypeFromDef) {
		 if (targetTypeFromDef == null || ObjectType.COMPLEX_TYPE.equals(targetTypeFromDef)) {
    		 return createObjectTypeList();
    	 } 
		 
		 if (AbstractRoleType.COMPLEX_TYPE.equals(targetTypeFromDef)) {
    		 return createAbstractRoleTypeList();
    	 } 
		 
		 if (FocusType.COMPLEX_TYPE.equals(targetTypeFromDef)) {
    		 return createFocusTypeList();
    	 } 

		 if (AssignmentHolderType.COMPLEX_TYPE.equals(targetTypeFromDef)) {
			 return createAssignmentHolderTypeQnamesList();
		 }

		 return Arrays.asList(targetTypeFromDef);
	}

	/**
	 * Takes a collection of object types (classes) that may contain abstract types. Returns a collection
	 * that only contain concrete types.
	 * @param <O> common supertype for all the types in the collections
	 *
	 * TODO: move to schema component
	 */
	public static <O extends ObjectType> List<QName> resolveObjectTypesToQNames(Collection<Class<? extends O>> types, PrismContext prismContext) {
		if (types == null) {
			return null;
		}
		List<QName> concreteTypes = new ArrayList<>(types.size());
		for (Class<? extends O> type: types) {
			if (type == null || type.equals(ObjectType.class)) {
				MiscUtil.addAllIfNotPresent(concreteTypes, createObjectTypeList());
			} else if (type.equals(FocusType.class)) {
				MiscUtil.addAllIfNotPresent(concreteTypes, createFocusTypeList());
	    	} else if (type.equals(AbstractRoleType.class)) {
	    		MiscUtil.addAllIfNotPresent(concreteTypes, createAbstractRoleTypeList());
	    	} else {
	    		MiscUtil.addIfNotPresent(concreteTypes, classToQName(prismContext, type));
	    	}
		}
		return concreteTypes;
	}

	public static <T extends Enum> IModel<String> createLocalizedModelForEnum(T value, Component comp) {
		String key = value != null ? value.getClass().getSimpleName() + "." + value.name() : "";
		return new StringResourceModel(key, comp, null);
	}

	public static <T extends Enum> IModel<List<T>> createReadonlyModelFromEnum(final Class<T> type) {
		return new IModel<List<T>>() {

			@Override
			public List<T> getObject() {
				List<T> list = new ArrayList<>();
				Collections.addAll(list, type.getEnumConstants());

				return list;
			}
		};
	}

	// use for small enums only
	@NotNull
	public static <T extends Enum> IModel<List<T>> createReadonlyValueModelFromEnum(@NotNull Class<T> type, @NotNull Predicate<T> filter) {
		return new ReadOnlyValueModel<>(
				Arrays.stream(type.getEnumConstants())
						.filter(filter)
						.collect(Collectors.toList()));
	}

	public static List<String> createTaskCategoryList() {
		List<String> categories = new ArrayList<>();

		// todo change to something better and add i18n
		// TaskManager manager = getTaskManager();
		// List<String> list = manager.getAllTaskCategories();
		// if (list != null) {
		// Collections.sort(list);
		// for (String item : list) {
		// if (item != TaskCategory.IMPORT_FROM_FILE && item !=
		// TaskCategory.WORKFLOW) {
		// categories.add(item);
		// }
		// }
		// }
		categories.add(TaskCategory.LIVE_SYNCHRONIZATION);
		categories.add(TaskCategory.RECONCILIATION);
		categories.add(TaskCategory.IMPORTING_ACCOUNTS);
		categories.add(TaskCategory.RECOMPUTATION);
		categories.add(TaskCategory.DEMO);
		// TODO: what about other categories?
		// categories.add(TaskCategory.ACCESS_CERTIFICATION);
		// categories.add(TaskCategory.BULK_ACTIONS);
		// categories.add(TaskCategory.CUSTOM);
		// categories.add(TaskCategory.EXECUTE_CHANGES);
		// categories.add(TaskCategory.IMPORT_FROM_FILE);
		// categories.add(TaskCategory.IMPORT_FROM_FILE);
		return categories;
	}

	public static IModel<String> createCategoryNameModel(final Component component,
			final IModel<String> categorySymbolModel) {
		return new IModel<String>() {
			@Override
			public String getObject() {
				return createStringResourceStatic(component,
						"pageTasks.category." + categorySymbolModel.getObject()).getString();
			}
		};
	}

	public static ObjectReferenceType createObjectRef(String oid, String name, QName type) {
		ObjectReferenceType ort = new ObjectReferenceType();
		ort.setOid(oid);
		ort.setTargetName(createPolyFromOrigString(name));
		ort.setType(type);
		return ort;
	}

	// public static DropDownChoicePanel createActivationStatusPanel(String id,
	// final IModel<ActivationStatusType> model,
	// final Component component) {
	// return new DropDownChoicePanel(id, model,
	// WebMiscUtil.createReadonlyModelFromEnum(ActivationStatusType.class),
	// new IChoiceRenderer<ActivationStatusType>() {
	//
	// @Override
	// public Object getDisplayValue(ActivationStatusType object) {
	// return WebMiscUtil.createLocalizedModelForEnum(object,
	// component).getObject();
	// }
	//
	// @Override
	// public String getIdValue(ActivationStatusType object, int index) {
	// return Integer.toString(index);
	// }
	// }, true);
	// }

	public static <E extends Enum> DropDownChoicePanel createEnumPanel(Class clazz, String id,
			final IModel<E> model, final Component component) {
		return createEnumPanel(clazz, id, model, component, true);

	}
	public static <E extends Enum> DropDownChoicePanel createEnumPanel(Class clazz, String id,
			final IModel<E> model, final Component component, boolean allowNull) {
		return createEnumPanel(clazz, id, WebComponentUtil.createReadonlyModelFromEnum(clazz),
				model, component, allowNull );
	}

	public static <E extends Enum> DropDownChoicePanel<E> createEnumPanel(Class<E> clazz, String id,
			IModel<List<E>> choicesList, final IModel<E> model, final Component component, boolean allowNull) {
		return createEnumPanel(clazz, id, choicesList, model, component, allowNull, null);
	}

	public static <E extends Enum> DropDownChoicePanel<E> createEnumPanel(Class<E> clazz, String id,
			IModel<List<E>> choicesList, final IModel<E> model, final Component component, boolean allowNull, String nullValidDisplayValue) {
		return new DropDownChoicePanel<E>(id, model, choicesList, getEnumChoiceRenderer(component)
            , allowNull){

			private static final long serialVersionUID = 1L;

			@Override
			protected String getNullValidDisplayValue() {
				return nullValidDisplayValue != null && StringUtils.isNotEmpty(nullValidDisplayValue.trim()) ?
						nullValidDisplayValue : super.getNullValidDisplayValue();
			}
		};
	}

	public static <E extends Enum> IChoiceRenderer<E> getEnumChoiceRenderer(Component component){
		return new IChoiceRenderer<E>() {

			private static final long serialVersionUID = 1L;

			@Override
			public E getObject(String id, IModel<? extends List<? extends E>> choices) {
				if (StringUtils.isBlank(id)) {
					return null;
				}
				return choices.getObject().get(Integer.parseInt(id));
			}

			@Override
			public Object getDisplayValue(E object) {
				return WebComponentUtil.createLocalizedModelForEnum(object, component).getObject();
			}

			@Override
			public String getIdValue(E object, int index) {
				return Integer.toString(index);
			}
		};
	}

	public static <T> DropDownChoicePanel createEnumPanel(final Collection<T> allowedValues, String id,
			final IModel model, final Component component) {
		// final Class clazz = model.getObject().getClass();
		final Object o = model.getObject();

		final IModel<List<DisplayableValue>> enumModelValues = new AbstractReadOnlyModel<List<DisplayableValue>>() {

			private static final long serialVersionUID = 1L;

			@Override
			public List<DisplayableValue> getObject() {
				return getDisplayableValues(allowedValues);
			}

		};

		return new DropDownChoicePanel(id, model, enumModelValues, new DisplayableValueChoiceRenderer(getDisplayableValues(allowedValues)), true);

	}
	public static DropDownChoicePanel createEnumPanel(final PrismPropertyDefinition def, String id,
			final IModel model, final Component component) {
		// final Class clazz = model.getObject().getClass();
		final Object o = model.getObject();

		final IModel<List<DisplayableValue>> enumModelValues = new IModel<List<DisplayableValue>>() {

			private static final long serialVersionUID = 1L;

			@Override
			public List<DisplayableValue> getObject() {
				return getDisplayableValues(def.getAllowedValues());
			}

		};

		return new DropDownChoicePanel(id, model, enumModelValues, new DisplayableValueChoiceRenderer(getDisplayableValues(def.getAllowedValues())), true);


//		@Override
//		public Object getObject(String id, IModel choices) {
//			if (StringUtils.isBlank(id)) {
//				return null;
//			}
//			return ((List) choices.getObject()).get(Integer.parseInt(id));
//		}
//
//		@Override
//		public Object getDisplayValue(Object object) {
//			if (object instanceof DisplayableValue) {
//				return ((DisplayableValue) object).getLabel();
//			}
//			for (DisplayableValue v : enumModelValues.getObject()) {
//				if (object.equals(v.getValue())) {
//					return v.getLabel();
//				}
//			}
//			return object;
//
//		}
//
//		@Override
//		public String getIdValue(Object object, int index) {
//            if (object instanceof String && enumModelValues != null && enumModelValues.getObject() != null) {
//                List<DisplayableValue> enumValues = enumModelValues.getObject();
//                for (DisplayableValue v : enumValues) {
//                    if (object.equals(v.getValue())) {
//                        return String.valueOf(enumValues.indexOf(v));
//                    }
//                }
//            }
//            return String.valueOf(index);
//		}
	}


	private static <T> List<DisplayableValue> getDisplayableValues(Collection<T> allowedValues) {
		List<DisplayableValue> values = null;
		if (allowedValues != null) {
			values = new ArrayList<>(allowedValues.size());
			for (T v : allowedValues) {
				if (v instanceof DisplayableValue) {
					values.add(((DisplayableValue) v));
				}
			}
		}
		return values;
	}

	public static <T> TextField<T> createAjaxTextField(String id, IModel<T> model) {
		TextField<T> textField = new TextField<>(id, model);
		textField.add(new EmptyOnBlurAjaxFormUpdatingBehaviour());
		return textField;
	}

	public static CheckBox createAjaxCheckBox(String id, IModel<Boolean> model) {
		CheckBox checkBox = new CheckBox(id, model);
		checkBox.add(new EmptyOnChangeAjaxFormUpdatingBehavior());
		return checkBox;
	}

	public static String getName(ObjectType object) {
		if (object == null) {
			return null;
		}

		return getName(object.asPrismObject());
	}

	public static String getEffectiveName(ObjectType object, QName propertyName) {
		if (object == null) {
			return null;
		}

		return getEffectiveName(object.asPrismObject(), propertyName);
	}

	public static <O extends ObjectType> String getEffectiveName(PrismObject<O> object, QName propertyName) {
		if (object == null) {
			return null;
		}

		PrismProperty prop = object.findProperty(ItemName.fromQName(propertyName));

		if (prop != null) {
			Object realValue = prop.getRealValue();
			if (prop.getDefinition().getTypeName().equals(DOMUtil.XSD_STRING)) {
				return (String) realValue;
			} else if (realValue instanceof PolyString) {
				return WebComponentUtil.getOrigStringFromPoly((PolyString) realValue);
			}
		}

		PolyString name = getValue(object, ObjectType.F_NAME, PolyString.class);

		return name != null ? name.getOrig() : null;
	}

	public static <O extends ObjectType> String getName(ObjectReferenceType ref, PageBase pageBase, String operation) {
		String name = getName(ref);
		if (StringUtils.isEmpty(name) || name.equals(ref.getOid())) {
			String oid = ref.getOid();
			Collection<SelectorOptions<GetOperationOptions>> options = SelectorOptions
					.createCollection(GetOperationOptions.createNoFetch());
			Class<O> type = (Class<O>) ObjectType.class;
			PrismObject<O> object = WebModelServiceUtils.loadObject(type, oid, pageBase,
					pageBase.createSimpleTask(operation), new OperationResult(operation));
			if (object != null) {
				name = object.getName().getOrig();
			}
		}
		return name;
	}

	public static <O extends ObjectType> String getDisplayNameOrName(ObjectReferenceType ref, PageBase pageBase, String operation) {
		String name = getName(ref);
		if (StringUtils.isEmpty(name) || name.equals(ref.getOid())) {
			String oid = ref.getOid();
			Collection<SelectorOptions<GetOperationOptions>> options = SelectorOptions
					.createCollection(GetOperationOptions.createNoFetch());
			Class<O> type = ref.getType() != null ? (Class<O>)qnameToClass(pageBase.getPrismContext(), ref.getType())  : (Class<O>) ObjectType.class;
			PrismObject<O> object = WebModelServiceUtils.loadObject(type, oid, pageBase,
					pageBase.createSimpleTask(operation), new OperationResult(operation));
			if (object != null) {
				name = getDisplayNameOrName(object);
			}
		}
		return name;
	}

	public static <O extends ObjectType> String getEffectiveName(ObjectReferenceType ref, QName propertyName, PageBase pageBase, String operation) {
		PrismObject<O> object = WebModelServiceUtils.loadObject(ref, pageBase,
				pageBase.createSimpleTask(operation), new OperationResult(operation));

		if (object == null) {
			return "Not Found";
		}

		return getEffectiveName(object, propertyName);

	}

	public static String getName(ObjectReferenceType ref) {
		if (ref == null) {
			return null;
		}
		if (ref.getTargetName() != null) {
			return getOrigStringFromPoly(ref.getTargetName());
		}
		if (ref.asReferenceValue().getObject() != null) {
			return getName(ref.asReferenceValue().getObject());
		}
		return ref.getOid();
	}

	public static String getName(PrismObject object) {
		if (object == null) {
			return null;
		}
		PolyString name = getValue(object, ObjectType.F_NAME, PolyString.class);

		return name != null ? name.getOrig() : null;
	}
	
	public static <C extends Containerable> String getDisplayName(PrismContainerValue<C> prismContainerValue) {
		if (prismContainerValue == null) {
			return "ContainerPanel.containerProperties";
		}

		String displayName = null;

		if (prismContainerValue.canRepresent(LifecycleStateType.class)) {
			LifecycleStateType lifecycleStateType = (LifecycleStateType) prismContainerValue.asContainerable();
			String name = lifecycleStateType.getDisplayName();
			if (name == null || name.isEmpty()) {
				Class<C> cvalClass = prismContainerValue.getCompileTimeClass();
				name = lifecycleStateType.getName();
			}

			if (name != null && !name.isEmpty()) {
				displayName = name;
			}
		} else if (prismContainerValue.canRepresent(PropertyConstraintType.class)) {
			PropertyConstraintType propertyConstraintType = (PropertyConstraintType) prismContainerValue.asContainerable();
			String path = "";
			if (propertyConstraintType.getPath() != null) {
				path = propertyConstraintType.getPath().getItemPath().toString();
			}

			if (path != null && !path.isEmpty()) {
				displayName = path;
			}
		} else if (prismContainerValue.canRepresent(AssignmentType.class)) {
			AssignmentType assignmentType = (AssignmentType) prismContainerValue.asContainerable();
			if (assignmentType.getTargetRef() != null) {
				ObjectReferenceType assignmentTargetRef = assignmentType.getTargetRef();
				displayName = getName(assignmentTargetRef) + " - " + normalizeRelation(assignmentTargetRef.getRelation()).getLocalPart();
			} else {
				displayName = "AssignmentTypeDetailsPanel.containerTitle";
			}
		} else if (prismContainerValue.canRepresent(ExclusionPolicyConstraintType.class)) {
			ExclusionPolicyConstraintType exclusionConstraint = (ExclusionPolicyConstraintType) prismContainerValue.asContainerable();
			String exclusionConstraintName = (exclusionConstraint.getName() != null ? exclusionConstraint.getName() :
					exclusionConstraint.asPrismContainerValue().getParent().getPath().last()) + " - "
					+ StringUtils.defaultIfEmpty(getName(exclusionConstraint.getTargetRef()), "");
			displayName = StringUtils.isNotEmpty(exclusionConstraintName) && StringUtils.isNotEmpty(getName(exclusionConstraint.getTargetRef())) ? exclusionConstraintName : "ExclusionPolicyConstraintType.details";
		} else if (prismContainerValue.canRepresent(AbstractPolicyConstraintType.class)) {
			AbstractPolicyConstraintType constraint = (AbstractPolicyConstraintType) prismContainerValue.asContainerable();
			String constraintName = constraint.getName();
			if (StringUtils.isNotEmpty(constraintName)) {
				displayName = constraintName;
			} else {
				displayName = constraint.asPrismContainerValue().getParent().getPath().last().toString() + ".details";
			}
		} else if (prismContainerValue.canRepresent(RichHyperlinkType.class)) {
			RichHyperlinkType richHyperlink = (RichHyperlinkType) prismContainerValue.asContainerable();
			String label = richHyperlink.getLabel();
			String description = richHyperlink.getDescription();
			String targetUrl = richHyperlink.getTargetUrl();
			if (StringUtils.isNotEmpty(label)) {
				displayName = label + (StringUtils.isNotEmpty(description) ? (" - " + description) : "");
			} else if (StringUtils.isNotEmpty(targetUrl)) {
				displayName = targetUrl;
			}
		} else if (prismContainerValue.canRepresent(UserInterfaceFeatureType.class)) {
			UserInterfaceFeatureType userInterfaceFeature = (UserInterfaceFeatureType) prismContainerValue.asContainerable();
			String identifier = userInterfaceFeature.getIdentifier();
			if (StringUtils.isNotEmpty(identifier)) {
				displayName = identifier;
			}
		} else if (prismContainerValue.canRepresent(GuiObjectColumnType.class)) {
			GuiObjectColumnType guiObjectColumn = (GuiObjectColumnType) prismContainerValue.asContainerable();
			String name = guiObjectColumn.getName();
			if (StringUtils.isNotEmpty(name)) {
				displayName = name;
			}
		} else if (prismContainerValue.canRepresent(GuiObjectListViewType.class)) {
			GuiObjectListViewType guiObjectListView = (GuiObjectListViewType) prismContainerValue.asContainerable();
			String name = guiObjectListView.getName();
			if (StringUtils.isNotEmpty(name)) {
				displayName = name;
			}
		} else if (prismContainerValue.canRepresent(GenericPcpAspectConfigurationType.class)) {
			GenericPcpAspectConfigurationType genericPcpAspectConfiguration = (GenericPcpAspectConfigurationType) prismContainerValue.asContainerable();
			String name = genericPcpAspectConfiguration.getName();
			if (StringUtils.isNotEmpty(name)) {
				displayName = name;
			}
		} else if (prismContainerValue.canRepresent(RelationDefinitionType.class)) {
			RelationDefinitionType relationDefinition = (RelationDefinitionType) prismContainerValue.asContainerable();
			if (relationDefinition.getRef() != null) {
				String name = (relationDefinition.getRef().getLocalPart());
				String description = relationDefinition.getDescription();
				if (StringUtils.isNotEmpty(name)) {
					displayName = name + (StringUtils.isNotEmpty(description) ? (" - " + description) : "");
				}
			}
		} else if (prismContainerValue.canRepresent(ResourceItemDefinitionType.class)) {
			ResourceItemDefinitionType resourceItemDefinition = (ResourceItemDefinitionType) prismContainerValue.asContainerable();
			if (resourceItemDefinition.getDisplayName() != null && !resourceItemDefinition.getDisplayName().isEmpty()) {
				displayName = resourceItemDefinition.getDisplayName();
			}
		} else if (prismContainerValue.canRepresent(MappingType.class)) {
			MappingType mapping = (MappingType) prismContainerValue.asContainerable();
			if (mapping.getName() != null && !mapping.getName().isEmpty()) {
				String name = mapping.getName();
				String description = mapping.getDescription();
				displayName = name + (StringUtils.isNotEmpty(description) ? (" - " + description) : "");
			}
		} else {

			Class<C> cvalClass = prismContainerValue.getCompileTimeClass();
			if (cvalClass != null) {
				displayName = cvalClass.getSimpleName() + ".details";
			} else {
				displayName = "ContainerPanel.containerProperties";
			}
		}


		String escaped = org.apache.commons.lang.StringEscapeUtils.escapeHtml(displayName);
		return escaped;
	}

	public static QName normalizeRelation(QName relation) {
		return getRelationRegistry().normalizeRelation(relation);
	}

	public static String getDisplayNameOrName(PrismObject object) {
		if (object == null) {
			return null;
		}
		
		String displayName = getDisplayName(object);
		return displayName != null ? displayName : getName(object);
	}

	public static String getDisplayNameOrName(ObjectReferenceType ref) {
		if (ref == null) {
			return null;
		}
		String displayName = getDisplayName(ref);
		return displayName != null ? displayName : getName(ref);
	}

	// <display-name> (<name>) OR simply <name> if there's no display name
	private static String getDisplayNameAndName(ObjectReferenceType ref) {
		if (ref == null) {
			return null;
		}
		String displayName = getDisplayName(ref);
		String name = getName(ref);
		return displayName != null ? displayName + " (" + name + ")" : name;
	}

	public static String getDisplayName(ObjectReferenceType ref) {
		return PolyString.getOrig(ObjectTypeUtil.getDisplayName(ref));
	}

	public static String getDisplayName(PrismObject object) {
		return PolyString.getOrig(ObjectTypeUtil.getDisplayName(object));
	}

	public static String getIdentification(ObjectType object) {
		if (object == null) {
			return null;
		}
		return getName(object.asPrismObject()) + " (" + object.getOid() + ")";
	}

	public static PolyStringType createPolyFromOrigString(String str) {
		if (str == null) {
			return null;
		}

		PolyStringType poly = new PolyStringType();
		poly.setOrig(str);

		return poly;
	}

	public static String getOrigStringFromPoly(PolyString str) {
		return str != null ? str.getOrig() : null;
	}

	public static String getOrigStringFromPoly(PolyStringType str) {
		return str != null ? str.getOrig() : null;
	}

	public static <T> T getValue(PrismContainerValue object, QName propertyName, Class<T> type) {
		if (object == null) {
			return null;
		}

		PrismProperty property = object.findProperty(ItemName.fromQName(propertyName));
		if (property == null || property.isEmpty()) {
			return null;
		}

		return (T) property.getRealValue(type);
	}

	public static <T> T getContainerValue(PrismContainerValue object, QName containerName, Class<T> type) {
		if (object == null) {
			return null;
		}

		PrismContainer container = object.findContainer(containerName);
		if (container == null || container.isEmpty()) {
			return null;
		}

		PrismContainerValue containerValue = container.getValue();

		if (containerValue == null || containerValue.isEmpty()) {
			return null;
		}

		return (T) containerValue.getValue();
	}

	public static <T> T getValue(PrismContainer object, QName propertyName, Class<T> type) {
		if (object == null) {
			return null;
		}

		return getValue(object.getValue(), propertyName, type);
	}

	public static Locale getLocaleFromString(String localeString) {
		if (localeString == null) {
			return null;
		}
		localeString = localeString.trim();
		if (localeString.toLowerCase().equals("default")) {
			return Locale.getDefault();
		}

		// Extract language
		int languageIndex = localeString.indexOf('_');
		String language = null;
		if (languageIndex == -1) {
			// No further "_" so is "{language}" only
			return new Locale(localeString, "");
		} else {
			language = localeString.substring(0, languageIndex);
		}

		// Extract country
		int countryIndex = localeString.indexOf('_', languageIndex + 1);
		String country = null;
		if (countryIndex == -1) {
			// No further "_" so is "{language}_{country}"
			country = localeString.substring(languageIndex + 1);
			return new Locale(language, country);
		} else {
			// Assume all remaining is the variant so is
			// "{language}_{country}_{variant}"
			country = localeString.substring(languageIndex + 1, countryIndex);
			String variant = localeString.substring(countryIndex + 1);
			return new Locale(language, country, variant);
		}
	}

	public static void encryptCredentials(ObjectDelta delta, boolean encrypt, MidPointApplication app) {
		if (delta == null || delta.isEmpty()) {
			return;
		}

		PropertyDelta propertyDelta = delta.findPropertyDelta(SchemaConstants.PATH_CREDENTIALS_PASSWORD_VALUE);
		if (propertyDelta == null) {
			return;
		}

		Collection<PrismPropertyValue<ProtectedStringType>> values = propertyDelta
				.getValues(ProtectedStringType.class);
		for (PrismPropertyValue<ProtectedStringType> value : values) {
			ProtectedStringType string = value.getValue();
			encryptProtectedString(string, encrypt, app);
		}
	}

	public static void encryptCredentials(PrismObject object, boolean encrypt, MidPointApplication app) {
		PrismContainer password = object.findContainer(SchemaConstants.PATH_CREDENTIALS_PASSWORD);
		if (password == null) {
			return;
		}
		PrismProperty protectedStringProperty = password.findProperty(PasswordType.F_VALUE);
		if (protectedStringProperty == null
				|| protectedStringProperty.getRealValue(ProtectedStringType.class) == null) {
			return;
		}

		ProtectedStringType string = (ProtectedStringType) protectedStringProperty
				.getRealValue(ProtectedStringType.class);

		encryptProtectedString(string, encrypt, app);
	}

	public static void encryptProtectedString(ProtectedStringType string, boolean encrypt,
			MidPointApplication app) {
		if (string == null) {
			return;
		}
		Protector protector = app.getProtector();
		try {
			if (encrypt) {
				if (StringUtils.isEmpty(string.getClearValue())) {
					return;
				}
				protector.encrypt(string);
			} else {
				if (string.getEncryptedDataType() == null) {
					return;
				}
				protector.decrypt(string);
			}
		} catch (EncryptionException ex) {
			LoggingUtils.logUnexpectedException(LOGGER, "Couldn't encrypt protected string", ex);
		} catch (SchemaException e) {
			LoggingUtils.logUnexpectedException(LOGGER, "Couldn't encrypt/decrypt protected string", e);
		}
	}

	public static <T extends Selectable> List<T> getSelectedData(Table table) {
		DataTable dataTable = table.getDataTable();
		BaseSortableDataProvider<T> provider = (BaseSortableDataProvider<T>) dataTable.getDataProvider();

		List<T> selected = new ArrayList<>();
		for (T bean : provider.getAvailableData()) {
			if (bean.isSelected()) {
				selected.add(bean);
			}
		}

		return selected;
	}

	public static void clearProviderCache(IDataProvider provider){
		if (provider == null){
			return;
		}
		if (provider instanceof BaseSortableDataProvider){
			((BaseSortableDataProvider)provider).clearCache();
		}
		if (provider instanceof SelectableBeanObjectDataProvider) {
			((SelectableBeanObjectDataProvider) provider).clearSelectedObjects();
		}
	}

	public static Collection<ObjectDelta<? extends ObjectType>> createDeltaCollection(
			ObjectDelta<? extends ObjectType>... deltas) {
		Collection<ObjectDelta<? extends ObjectType>> collection = new ArrayList<>();
		for (ObjectDelta delta : deltas) {
			collection.add(delta);
		}

		return collection;
	}

	public static boolean showResultInPage(OperationResult result) {
		if (result == null) {
			return false;
		}

		return !result.isSuccess() && !result.isHandledError() && !result.isInProgress();
	}

	public static String formatDate(XMLGregorianCalendar calendar) {
		if (calendar == null) {
			return null;
		}

		return formatDate(XmlTypeConverter.toDate(calendar));
	}

	public static String formatDate(Date date) {
		return formatDate(null, date);
	}

	public static String formatDate(String format, Date date) {
		if (date == null) {
			return null;
		}

		if (StringUtils.isEmpty(format)) {
			format = "EEEE, d. MMM yyyy HH:mm:ss";
		}
		Locale locale = Session.get().getLocale();
		if (locale == null) {
			locale = Locale.US;
		}
		SimpleDateFormat dateFormat = new SimpleDateFormat(format, locale);
		return dateFormat.format(date);
	}

	public static String getLocalizedDatePattern(String style) {
		return DateTimeFormat.patternForStyle(style, getCurrentLocale());
	}

	public static Locale getCurrentLocale() {
		Locale locale = Session.get().getLocale();
		if (locale == null) {
			locale = Locale.getDefault();
		}
		return locale;
	}

	public static String getLocalizedDate(XMLGregorianCalendar date, String style) {
		return getLocalizedDate(XmlTypeConverter.toDate(date), style);
	}

	public static String getLocalizedDate(Date date, String style) {
		if (date == null) {
			return null;
		}
		PatternDateConverter converter = new PatternDateConverter(getLocalizedDatePattern(style), true);
		return converter.convertToString(date, WebComponentUtil.getCurrentLocale());
	}

	public static String getShortDateTimeFormattedValue(XMLGregorianCalendar date, PageBase pageBase) {
		return getShortDateTimeFormattedValue(XmlTypeConverter.toDate(date), pageBase);
	}

	public static String getShortDateTimeFormattedValue(Date date, PageBase pageBase) {
		if (date == null) {
			return "";
		}
		String shortDateTimeFortam = getShortDateTimeFormat(pageBase);
		return getLocalizedDate(date, shortDateTimeFortam);
	}

	public static String getLongDateTimeFormattedValue(XMLGregorianCalendar date, PageBase pageBase) {
		return getLongDateTimeFormattedValue(XmlTypeConverter.toDate(date), pageBase);
	}

	public static String getLongDateTimeFormattedValue(Date date, PageBase pageBase) {
		if (date == null) {
			return "";
		}
		String longDateTimeFormat = getLongDateTimeFormat(pageBase);
		return getLocalizedDate(date, longDateTimeFormat);
	}

	public static String getShortDateTimeFormat(PageBase pageBase){
		AdminGuiConfigurationDisplayFormatsType displayFormats = pageBase.getCompiledUserProfile().getDisplayFormats();
		if (displayFormats == null || StringUtils.isEmpty(displayFormats.getShortDateTimeFormat())){
			return DateLabelComponent.SHORT_MEDIUM_STYLE;
		} else {
			return displayFormats.getShortDateTimeFormat();
		}
	}

	public static String getLongDateTimeFormat(PageBase pageBase){
		AdminGuiConfigurationDisplayFormatsType displayFormats = pageBase.getCompiledUserProfile().getDisplayFormats();
		if (displayFormats == null || StringUtils.isEmpty(displayFormats.getLongDateTimeFormat())){
			return DateLabelComponent.LONG_MEDIUM_STYLE;
		} else {
			return displayFormats.getLongDateTimeFormat();
		}
	}

	public static boolean isActivationEnabled(PrismObject object) {
		Validate.notNull(object);

		PrismContainer activation = object.findContainer(UserType.F_ACTIVATION); // this
																					// is
																					// equal
																					// to
																					// account
																					// activation...
		if (activation == null) {
			return false;
		}

		ActivationStatusType status = (ActivationStatusType) activation
				.getPropertyRealValue(ActivationType.F_ADMINISTRATIVE_STATUS, ActivationStatusType.class);
		if (status == null) {
			return false;
		}

		// todo imrove with activation dates...
		return ActivationStatusType.ENABLED.equals(status);
	}

	public static boolean isSuccessOrHandledError(OperationResult result) {
		if (result == null) {
			return false;
		}

		return result.isSuccess() || result.isHandledError();
	}

	public static boolean isSuccessOrHandledError(OperationResultType resultType) {
		if (resultType == null) {
			return false;
		}
		return resultType.getStatus() == OperationResultStatusType.SUCCESS || resultType.getStatus() == OperationResultStatusType.HANDLED_ERROR;
	}

	public static boolean isSuccessOrHandledErrorOrWarning(OperationResult result) {
		if (result == null) {
			return false;
		}

		return result.isSuccess() || result.isHandledError() || result.isWarning();
	}

	public static boolean isSuccessOrHandledErrorOrInProgress(OperationResult result) {
		if (result == null) {
			return false;
		}

		return result.isSuccess() || result.isHandledError() || result.isInProgress();
	}

	public static <T extends ObjectType> String createDefaultIcon(T object) {
		return createDefaultIcon(object.asPrismObject());
	}

	public static <T extends ObjectType> String createDefaultIcon(PrismObject<T> object) {
		Class<T> type = object.getCompileTimeClass();
		if (type.equals(UserType.class)) {
			return createUserIcon((PrismObject<UserType>) object);
		} else if (RoleType.class.equals(type)) {
			return createRoleIcon((PrismObject<RoleType>) object);
		} else if (OrgType.class.equals(type)) {
			return createOrgIcon((PrismObject<OrgType>) object);
		} else if (ServiceType.class.equals(type)) {
			return createServiceIcon((PrismObject<ServiceType>) object);
		} else if (type.equals(TaskType.class)) {
			return createTaskIcon((PrismObject<TaskType>) object);
		} else if (type.equals(ResourceType.class)) {
			return createResourceIcon((PrismObject<ResourceType>) object);
		}

		return "";
	}

	// TODO reconcile with ObjectTypeGuiDescriptor
	public static <T extends ObjectType> String createDefaultColoredIcon(QName objectType) {
		if (objectType == null) {
			return "";
		} else if (QNameUtil.match(UserType.COMPLEX_TYPE, objectType) || QNameUtil.match(PersonaConstructionType.COMPLEX_TYPE, objectType)) {
			return GuiStyleConstants.CLASS_OBJECT_USER_ICON_COLORED;
		} else if (QNameUtil.match(RoleType.COMPLEX_TYPE, objectType)) {
			return GuiStyleConstants.CLASS_OBJECT_ROLE_ICON_COLORED;
		} else if (QNameUtil.match(OrgType.COMPLEX_TYPE, objectType)) {
			return GuiStyleConstants.CLASS_OBJECT_ORG_ICON_COLORED;
		} else if (QNameUtil.match(ServiceType.COMPLEX_TYPE, objectType)) {
			return GuiStyleConstants.CLASS_OBJECT_SERVICE_ICON_COLORED;
		} else if (QNameUtil.match(TaskType.COMPLEX_TYPE, objectType)) {
			return GuiStyleConstants.CLASS_OBJECT_TASK_ICON_COLORED;
		} else if (QNameUtil.match(ResourceType.COMPLEX_TYPE, objectType) || QNameUtil.match(ConstructionType.COMPLEX_TYPE, objectType)) {
			return GuiStyleConstants.CLASS_OBJECT_RESOURCE_ICON_COLORED;
		} else if (QNameUtil.match(AccessCertificationCampaignType.COMPLEX_TYPE, objectType)) {
			return GuiStyleConstants.CLASS_OBJECT_CERT_CAMPAIGN_ICON_COLORED;
		} else if (QNameUtil.match(AccessCertificationDefinitionType.COMPLEX_TYPE, objectType)) {
			return GuiStyleConstants.CLASS_OBJECT_CERT_DEF_ICON_COLORED;
		} else if (QNameUtil.match(CaseWorkItemType.COMPLEX_TYPE, objectType)) {
			return GuiStyleConstants.CLASS_OBJECT_WORK_ITEM_ICON_COLORED;
		} else if (QNameUtil.match(ShadowType.COMPLEX_TYPE, objectType)) {
			return GuiStyleConstants.CLASS_OBJECT_SHADOW_ICON_COLORED;
		} else if (QNameUtil.match(PolicyRuleType.COMPLEX_TYPE, objectType)) {
			return GuiStyleConstants.CLASS_POLICY_RULES_ICON_COLORED;
		} else if (QNameUtil.match(ObjectPolicyConfigurationType.COMPLEX_TYPE, objectType) || QNameUtil.match(GlobalPolicyRuleType.COMPLEX_TYPE, objectType)
				|| QNameUtil.match(FileAppenderConfigurationType.COMPLEX_TYPE, objectType) || QNameUtil.match(SyslogAppenderConfigurationType.COMPLEX_TYPE, objectType)) {
			return GuiStyleConstants.CLASS_SYSTEM_CONFIGURATION_ICON_COLORED;
		} else {
			return "";
		}
	}

	// TODO reconcile with ObjectTypeGuiDescriptor
	public static <T extends ObjectType> String createDefaultBlackIcon(QName objectType) {
		if (objectType == null) {
			return "";
		} else if (QNameUtil.match(UserType.COMPLEX_TYPE, objectType) || QNameUtil.match(PersonaConstructionType.COMPLEX_TYPE, objectType)) {
			return GuiStyleConstants.CLASS_OBJECT_USER_ICON;
		} else if (QNameUtil.match(RoleType.COMPLEX_TYPE, objectType)) {
			return GuiStyleConstants.CLASS_OBJECT_ROLE_ICON;
		} else if (QNameUtil.match(OrgType.COMPLEX_TYPE, objectType)) {
			return GuiStyleConstants.CLASS_OBJECT_ORG_ICON;
		} else if (QNameUtil.match(ServiceType.COMPLEX_TYPE, objectType)) {
			return GuiStyleConstants.CLASS_OBJECT_SERVICE_ICON;
		} else if (QNameUtil.match(TaskType.COMPLEX_TYPE, objectType)) {
			return GuiStyleConstants.CLASS_OBJECT_TASK_ICON;
		} else if (QNameUtil.match(ResourceType.COMPLEX_TYPE, objectType) || QNameUtil.match(ConstructionType.COMPLEX_TYPE, objectType)) {
			return GuiStyleConstants.CLASS_OBJECT_RESOURCE_ICON;
		} else if (QNameUtil.match(AccessCertificationCampaignType.COMPLEX_TYPE, objectType)) {
			return GuiStyleConstants.CLASS_OBJECT_CERT_CAMPAIGN_ICON;
		} else if (QNameUtil.match(AccessCertificationDefinitionType.COMPLEX_TYPE, objectType)) {
			return GuiStyleConstants.CLASS_OBJECT_CERT_DEF_ICON;
		} else if (QNameUtil.match(CaseWorkItemType.COMPLEX_TYPE, objectType)) {
			return GuiStyleConstants.CLASS_OBJECT_WORK_ITEM_ICON;
		} else if (QNameUtil.match(ShadowType.COMPLEX_TYPE, objectType)) {
			return GuiStyleConstants.CLASS_OBJECT_SHADOW_ICON;
		} else if (QNameUtil.match(PolicyRuleType.COMPLEX_TYPE, objectType)) {
			return GuiStyleConstants.CLASS_POLICY_RULES_ICON;
		} else if (QNameUtil.match(SystemConfigurationType.COMPLEX_TYPE, objectType)) {
			return GuiStyleConstants.CLASS_SYSTEM_CONFIGURATION_ICON;
		} else if (QNameUtil.match(MappingType.COMPLEX_TYPE, objectType)) {
			//TODO fix icon style for mapping type
			return "";
		} else {
			return "";
		}
	}

	public static <T extends ObjectType> String getBoxCssClasses(QName objectType) {
		if (QNameUtil.match(UserType.COMPLEX_TYPE, objectType)) {
			return GuiStyleConstants.CLASS_OBJECT_USER_BOX_CSS_CLASSES;
		} else if (QNameUtil.match(RoleType.COMPLEX_TYPE, objectType)) {
			return GuiStyleConstants.CLASS_OBJECT_ROLE_BOX_CSS_CLASSES;
		} else if (QNameUtil.match(OrgType.COMPLEX_TYPE, objectType)) {
			return GuiStyleConstants.CLASS_OBJECT_ORG_BOX_CSS_CLASSES;
		} else if (QNameUtil.match(ServiceType.COMPLEX_TYPE, objectType)) {
			return GuiStyleConstants.CLASS_OBJECT_SERVICE_BOX_CSS_CLASSES;
		} else if (QNameUtil.match(TaskType.COMPLEX_TYPE, objectType)) {
			return GuiStyleConstants.CLASS_OBJECT_TASK_BOX_CSS_CLASSES;
		} else if (QNameUtil.match(ResourceType.COMPLEX_TYPE, objectType)) {
			return GuiStyleConstants.CLASS_OBJECT_RESOURCE_BOX_CSS_CLASSES;
		} else {
			return "";
		}
	}

	public static <T extends ObjectType> String getBoxThinCssClasses(QName objectType) {
		if (QNameUtil.match(UserType.COMPLEX_TYPE, objectType)) {
			return GuiStyleConstants.CLASS_OBJECT_USER_BOX_THIN_CSS_CLASSES;
		} else if (QNameUtil.match(RoleType.COMPLEX_TYPE, objectType)) {
			return GuiStyleConstants.CLASS_OBJECT_ROLE_BOX_THIN_CSS_CLASSES;
		} else if (QNameUtil.match(OrgType.COMPLEX_TYPE, objectType)) {
			return GuiStyleConstants.CLASS_OBJECT_ORG_BOX_THIN_CSS_CLASSES;
		} else if (QNameUtil.match(ServiceType.COMPLEX_TYPE, objectType)) {
			return GuiStyleConstants.CLASS_OBJECT_SERVICE_BOX_THIN_CSS_CLASSES;
		} else if (QNameUtil.match(TaskType.COMPLEX_TYPE, objectType)) {
			return GuiStyleConstants.CLASS_OBJECT_TASK_BOX_THIN_CSS_CLASSES;
		} else if (QNameUtil.match(ResourceType.COMPLEX_TYPE, objectType)) {
			return GuiStyleConstants.CLASS_OBJECT_RESOURCE_BOX_THIN_CSS_CLASSES;
		} else {
			return "";
		}
	}

	// can this implementation be made more efficient? [pm]
	@SuppressWarnings("WeakerAccess")
	public static boolean isOfKind(QName relation, RelationKindType kind) {
		return getRelationRegistry().isOfKind(relation, kind);
	}

	protected static RelationRegistry getRelationRegistry() {
		if (staticallyProvidedRelationRegistry != null) {
			return staticallyProvidedRelationRegistry;
		} else {
			return MidPointApplication.get().getRelationRegistry();
		}
	}

	public static boolean isManagerRelation(QName relation) {
		return isOfKind(relation, RelationKindType.MANAGER);
	}

	public static boolean isDefaultRelation(QName relation) {
		return getRelationRegistry().isDefault(relation);
	}

	@SuppressWarnings("WeakerAccess")
	public static QName getDefaultRelation() {
		return getRelationRegistry().getDefaultRelation();
	}

	@NotNull
	public static QName getDefaultRelationOrFail() {
		QName relation = getDefaultRelation();
		if (relation != null) {
			return relation;
		} else {
			throw new IllegalStateException("No default relation is defined");
		}
	}

	@SuppressWarnings("WeakerAccess")
	@Nullable
	public static QName getDefaultRelationFor(RelationKindType kind) {
		return getRelationRegistry().getDefaultRelationFor(kind);
	}

	@NotNull
	public static QName getDefaultRelationOrFail(RelationKindType kind) {
		QName relation = getDefaultRelationFor(kind);
		if (relation != null) {
			return relation;
		} else {
			throw new IllegalStateException("No default relation for kind " + kind);
		}
	}

	@NotNull
	public static String getRelationHeaderLabelKey(QName relation) {
		String label = getRelationHeaderLabelKeyIfKnown(relation);
		if (label != null) {
			return label;
		} else {
			return relation != null ? relation.getLocalPart() : "default";
		}
	}

	@Nullable
	public static String getRelationHeaderLabelKeyIfKnown(QName relation) {
		RelationDefinitionType definition = getRelationRegistry().getRelationDefinition(relation);
		if (definition != null && definition.getDisplay() != null && definition.getDisplay().getLabel() != null) {
			return definition.getDisplay().getLabel().getOrig();
		} else {
			return null;
		}
	}

	public static String createUserIcon(PrismObject<UserType> object) {
		UserType user = object.asObjectable();

		// if user has superuser role assigned, it's superuser
		boolean isEndUser = false;
		for (AssignmentType assignment : user.getAssignment()) {
			ObjectReferenceType targetRef = assignment.getTargetRef();
			if (targetRef == null) {
				continue;
			}
			if (StringUtils.equals(targetRef.getOid(), SystemObjectsType.ROLE_SUPERUSER.value())) {
				return GuiStyleConstants.CLASS_OBJECT_USER_ICON + " "
						+ GuiStyleConstants.CLASS_ICON_STYLE_PRIVILEGED;
			}
			if (StringUtils.equals(targetRef.getOid(), SystemObjectsType.ROLE_END_USER.value())) {
				isEndUser = true;
			}
		}

		boolean isManager = false;
		for (ObjectReferenceType parentOrgRef : user.getParentOrgRef()) {
			if (isManagerRelation(parentOrgRef.getRelation())) {
				isManager = true;
				break;
			}
		}

		String additionalStyle = getIconEnabledDisabled(object);
		if (additionalStyle == null) {
			// Set manager and end-user icon only as a last resort. All other
			// colors have priority.
			if (isManager) {
				additionalStyle = GuiStyleConstants.CLASS_ICON_STYLE_MANAGER;
			} else if (isEndUser) {
				additionalStyle = GuiStyleConstants.CLASS_ICON_STYLE_END_USER;
			} else {
				additionalStyle = GuiStyleConstants.CLASS_ICON_STYLE_NORMAL;
			}
		}
		return GuiStyleConstants.CLASS_OBJECT_USER_ICON + " " + additionalStyle;
	}

	public static String createRoleIcon(PrismObject<RoleType> object) {
		for (AuthorizationType authorization : object.asObjectable().getAuthorization()) {
			if (authorization.getAction().contains(AuthorizationConstants.AUTZ_ALL_URL)) {
				return GuiStyleConstants.CLASS_OBJECT_ROLE_ICON + " "
						+ GuiStyleConstants.CLASS_ICON_STYLE_PRIVILEGED;
			}
		}

		return getIconEnabledDisabled(object, GuiStyleConstants.CLASS_OBJECT_ROLE_ICON);
	}

	public static String createOrgIcon(PrismObject<OrgType> object) {
		return getIconEnabledDisabled(object, GuiStyleConstants.CLASS_OBJECT_ORG_ICON);
	}

	public static String createServiceIcon(PrismObject<ServiceType> object) {
		return getIconEnabledDisabled(object, GuiStyleConstants.CLASS_OBJECT_SERVICE_ICON);
	}

	private static <F extends FocusType> String getIconEnabledDisabled(PrismObject<F> object,
			String baseIcon) {
		String additionalStyle = getIconEnabledDisabled(object);
		if (additionalStyle == null) {
			return baseIcon + " " + GuiStyleConstants.CLASS_ICON_STYLE_NORMAL;
		} else {
			return baseIcon + " " + additionalStyle;
		}
	}

	public static <F extends FocusType> String getIconEnabledDisabled(PrismObject<F> object) {
		ActivationType activation = object.asObjectable().getActivation();
		if (activation != null) {
			if (ActivationStatusType.DISABLED.equals(activation.getEffectiveStatus())) {
				return GuiStyleConstants.CLASS_ICON_STYLE_DISABLED;
			} else if (ActivationStatusType.ARCHIVED.equals(activation.getEffectiveStatus())) {
				return GuiStyleConstants.CLASS_ICON_STYLE_ARCHIVED;
			}
		}

		return null;
	}

	public static String createResourceIcon(PrismObject<ResourceType> object) {
		OperationalStateType operationalState = object.asObjectable().getOperationalState();
		if (operationalState != null) {
			AvailabilityStatusType lastAvailabilityStatus = operationalState.getLastAvailabilityStatus();
			if (lastAvailabilityStatus == AvailabilityStatusType.UP) {
				return GuiStyleConstants.CLASS_OBJECT_RESOURCE_ICON + " "
						+ GuiStyleConstants.CLASS_ICON_STYLE_UP;
			}
			if (lastAvailabilityStatus == AvailabilityStatusType.DOWN) {
				return GuiStyleConstants.CLASS_OBJECT_RESOURCE_ICON + " "
						+ GuiStyleConstants.CLASS_ICON_STYLE_DOWN;
			}

			if (lastAvailabilityStatus == AvailabilityStatusType.BROKEN) {
				return GuiStyleConstants.CLASS_OBJECT_RESOURCE_ICON + " "
						+ GuiStyleConstants.CLASS_ICON_STYLE_BROKEN;
			}
		}
		return GuiStyleConstants.CLASS_OBJECT_RESOURCE_ICON + " " + GuiStyleConstants.CLASS_ICON_STYLE_NORMAL;
	}

	public static String createTaskIcon(PrismObject<TaskType> object) {
		return GuiStyleConstants.CLASS_OBJECT_TASK_ICON + " " + GuiStyleConstants.CLASS_ICON_STYLE_NORMAL;
	}

	public static String createShadowIcon(PrismContainerValue<ShadowType> prismContainerValue) {
		return createShadowIcon(((ShadowType)prismContainerValue.getRealValue()).asPrismContainer());
	}

	public static String createShadowIcon(PrismObject<ShadowType> object) {
		ShadowType shadow = object.asObjectable();

		if (ShadowUtil.isProtected(object)) {
			return GuiStyleConstants.CLASS_SHADOW_ICON_PROTECTED;
		}

		ShadowKindType kind = shadow.getKind();
		if (kind == null) {
			return GuiStyleConstants.CLASS_SHADOW_ICON_UNKNOWN;
		}

		switch (kind) {
			case ACCOUNT:
				return GuiStyleConstants.CLASS_SHADOW_ICON_ACCOUNT;
			case GENERIC:
				return GuiStyleConstants.CLASS_SHADOW_ICON_GENERIC;
			case ENTITLEMENT:
				return GuiStyleConstants.CLASS_SHADOW_ICON_ENTITLEMENT;

		}

		return GuiStyleConstants.CLASS_SHADOW_ICON_UNKNOWN;
	}

	public static <AHT extends AssignmentHolderType, O extends ObjectType> void initNewObjectWithReference(PageBase pageBase, O targetObject, QName type, Collection<QName> relations) throws SchemaException {
		List<ObjectReferenceType> newReferences = new ArrayList<>();
		for (QName relation : relations) {
			newReferences.add(ObjectTypeUtil.createObjectRef(targetObject, relation));
		}
		initNewObjectWithReference(pageBase, type, newReferences);
	}

	public static <AHT extends AssignmentHolderType> void initNewObjectWithReference(PageBase pageBase, QName type, List<ObjectReferenceType> newReferences) throws SchemaException {
		PrismContext prismContext = pageBase.getPrismContext();
		PrismObjectDefinition<AHT> def = prismContext.getSchemaRegistry().findObjectDefinitionByType(type);
		PrismObject<AHT> obj = def.instantiate();
		AHT assignmentHolder = obj.asObjectable();
		if (newReferences != null) {
			newReferences.stream().forEach(ref -> {
				AssignmentType assignment = new AssignmentType();
				assignment.setTargetRef(ref);
				assignmentHolder.getAssignment().add(assignment);

				// Set parentOrgRef in any case. This is not strictly correct.
				// The parentOrgRef should be added by the projector. But
				// this is needed to successfully pass through security
				// TODO: fix MID-3234
				if (ref.getType() != null && OrgType.COMPLEX_TYPE.equals(ref.getType())) {
					assignmentHolder.getParentOrgRef().add(ref.clone());
				}
			});
		}

		WebComponentUtil.dispatchToObjectDetailsPage(obj, true, pageBase);
	}

	public static String createUserIconTitle(PrismObject<UserType> object) {
		UserType user = object.asObjectable();

		// if user has superuser role assigned, it's superuser
		for (AssignmentType assignment : user.getAssignment()) {
			ObjectReferenceType targetRef = assignment.getTargetRef();
			if (targetRef == null) {
				continue;
			}
			if (StringUtils.equals(targetRef.getOid(), SystemObjectsType.ROLE_SUPERUSER.value())) {
				return "user.superuser";
			}
		}

		ActivationType activation = user.getActivation();
		if (activation != null) {
			if (ActivationStatusType.DISABLED.equals(activation.getEffectiveStatus())) {
				return "ActivationStatusType.DISABLED";
			} else if (ActivationStatusType.ARCHIVED.equals(activation.getEffectiveStatus())) {
				return "ActivationStatusType.ARCHIVED";
			}
		}

		return null;
	}

	public static String createErrorIcon(OperationResult result) {
		OperationResultStatus status = result.getStatus();
		OperationResultStatusPresentationProperties icon = OperationResultStatusPresentationProperties
				.parseOperationalResultStatus(status);
		return icon.getIcon() + " fa-lg";
	}

	public static double getSystemLoad() {
		java.lang.management.OperatingSystemMXBean operatingSystemMXBean = ManagementFactory.getOperatingSystemMXBean();
//		RuntimeMXBean runtimeMXBean = ManagementFactory.getRuntimeMXBean();
		return operatingSystemMXBean.getSystemLoadAverage();
//		int availableProcessors = operatingSystemMXBean.getAvailableProcessors();
//		long prevUpTime = runtimeMXBean.getUptime();
//		long prevProcessCpuTime = operatingSystemMXBean.getProcessCpuTime();
//
//		try {
//			Thread.sleep(150);
//		} catch (Exception ignored) {
//			// ignored
//		}
//
//		operatingSystemMXBean = ManagementFactory
//				.getOperatingSystemMXBean();
//		long upTime = runtimeMXBean.getUptime();
//		long processCpuTime = operatingSystemMXBean.getProcessCpuTime();
//		long elapsedCpu = processCpuTime - prevProcessCpuTime;
//		long elapsedTime = upTime - prevUpTime;
//
//		double cpuUsage = Math.min(99F, elapsedCpu / (elapsedTime * 10000F * availableProcessors));
//
//		return cpuUsage;
	}


	public static double getMaxRam() {
		int MB = 1024 * 1024;

		MemoryMXBean mBean = ManagementFactory.getMemoryMXBean();
		long maxHeap = mBean.getHeapMemoryUsage().getMax();
		long maxNonHeap = mBean.getNonHeapMemoryUsage().getMax();

		return (maxHeap + maxNonHeap) / MB;
	}

	public static double getRamUsage() {
		int MB = 1024 * 1024;

		MemoryMXBean mBean = ManagementFactory.getMemoryMXBean();
		long usedHead = mBean.getHeapMemoryUsage().getUsed();
		long usedNonHeap = mBean.getNonHeapMemoryUsage().getUsed();

		return (usedHead + usedNonHeap) / MB;
	}

	/**
	 * Checks table if has any selected rows ({@link Selectable} interface
	 * dtos), adds "single" parameter to selected items if it's not null. If
	 * table has no selected rows warn message is added to feedback panel, and
	 * feedback is refreshed through {@link AjaxRequestTarget}
	 *
	 * @param target
	 * @param single
	 *            this parameter is used for row actions when action must be
	 *            done only on chosen row.
	 * @param table
	 * @param page
	 * @param nothingWarnMessage
	 * @param <T>
	 * @return
	 */
	public static <T extends Selectable> List<T> isAnythingSelected(AjaxRequestTarget target, T single,
			Table table, PageBase page, String nothingWarnMessage) {
		List<T> selected;
		if (single != null) {
			selected = new ArrayList<>();
			selected.add(single);
		} else {
			selected = WebComponentUtil.getSelectedData(table);
			if (selected.isEmpty()) {
				page.warn(page.getString(nothingWarnMessage));
				target.add(page.getFeedbackPanel());
			}
		}

		return selected;
	}

	public static void refreshFeedbacks(MarkupContainer component, final AjaxRequestTarget target) {
		component.visitChildren(IFeedback.class, new IVisitor<Component, Void>() {

			@Override
			public void component(final Component component, final IVisit<Void> visit) {
				target.add(component);
			}
		});
	}

	/*
	 * Methods used for providing prismContext into various objects.
	 */
	public static void revive(LoadableModel<?> loadableModel, PrismContext prismContext)
			throws SchemaException {
		if (loadableModel != null) {
			loadableModel.revive(prismContext);
		}
	}

	public static void reviveObject(Object object, PrismContext prismContext) throws SchemaException {
		if (object == null) {
			return;
		}
		if (object instanceof Collection) {
			for (Object item : (Collection) object) {
				reviveObject(item, prismContext);
			}
		} else if (object instanceof Revivable) {
			((Revivable) object).revive(prismContext);
		}
	}

	// useful for components other than those inheriting from PageBase
	public static PrismContext getPrismContext(Component component) {
		return ((MidPointApplication) component.getApplication()).getPrismContext();
	}


	public static List<String> getChannelList() {
		List<String> channels = new ArrayList<>();

		for (Channel channel : Channel.values()) {
			channels.add(channel.getChannel());
		}

		return channels;
	}

	public static List<QName> getMatchingRuleList() {
		List<QName> list = new ArrayList<>();

		list.add(PrismConstants.DEFAULT_MATCHING_RULE_NAME);
		list.add(PrismConstants.STRING_IGNORE_CASE_MATCHING_RULE_NAME);
		list.add(PrismConstants.POLY_STRING_STRICT_MATCHING_RULE_NAME);
		list.add(PrismConstants.POLY_STRING_ORIG_MATCHING_RULE_NAME);
		list.add(PrismConstants.POLY_STRING_NORM_MATCHING_RULE_NAME);
		list.add(PrismConstants.DISTINGUISHED_NAME_MATCHING_RULE_NAME);
		list.add(PrismConstants.EXCHANGE_EMAIL_ADDRESSES_MATCHING_RULE_NAME);
		list.add(PrismConstants.UUID_MATCHING_RULE_NAME);
		list.add(PrismConstants.XML_MATCHING_RULE_NAME);

		return list;
	}

	public static boolean isObjectOrgManager(PrismObject<? extends ObjectType> object) {
		if (object == null || object.asObjectable() == null) {
			return false;
		}

		ObjectType objectType = object.asObjectable();
		List<ObjectReferenceType> parentOrgRefs = objectType.getParentOrgRef();

		for (ObjectReferenceType ref : parentOrgRefs) {
			if (isManagerRelation(ref.getRelation())) {
				return true;
			}
		}

		return false;
	}

	public static String createHumanReadableByteCount(long bytes) {
		int unit = 1024;
		if (bytes < unit)
			return bytes + "B";
		int exp = (int) (Math.log(bytes) / Math.log(unit));
		char pre = "KMGTPE".charAt(exp - 1);
		return String.format("%.1f%sB", bytes / Math.pow(unit, exp), pre);
	}

	public static void setCurrentPage(Table table, ObjectPaging paging) {
		if (table == null) {
			return;
		}

		if (paging == null) {
			table.getDataTable().setCurrentPage(0);
			return;
		}

		long itemsPerPage = table.getDataTable().getItemsPerPage();
		long page = ((paging.getOffset() + itemsPerPage) / itemsPerPage) - 1;
		if (page < 0) {
			page = 0;
		}

		table.getDataTable().setCurrentPage(page);
	}

	public static PageBase getPageBase(Component component) {
		Page page = component.getPage();
		if (page instanceof PageBase) {
			return (PageBase) page;
		} else if (page instanceof PageDialog) {
			return ((PageDialog) page).getPageBase();
		} else {
			throw new IllegalStateException("Couldn't determine page base for " + page);
		}
	}

	public static <T extends Component> T theSameForPage(T object, PageReference containingPageReference) {
		Page containingPage = containingPageReference.getPage();
		if (containingPage == null) {
			return object;
		}
		String path = object.getPageRelativePath();
		T retval = (T) containingPage.get(path);
		if (retval == null) {
			return object;
			// throw new IllegalStateException("There is no component like " +
			// object + " (path '" + path + "') on " + containingPage);
		}
		return retval;
	}

	public static String debugHandler(IRequestHandler handler) {
		if (handler == null) {
			return null;
		}
		if (handler instanceof RenderPageRequestHandler) {
			return "RenderPageRequestHandler(" + ((RenderPageRequestHandler) handler).getPageClass().getName()
					+ ")";
		} else {
			return handler.toString();
		}
	}

	// todo specify functionality of this method
	public static ItemPath joinPath(ItemPath path1, ItemPath path2, PrismContext prismContext) {
		ItemPath path = ItemPath.emptyIfNull(path1);
		ItemPath deltaPath = ItemPath.emptyIfNull(path2);
		List<Object> newPath = new ArrayList<>();

		Object firstDeltaSegment = deltaPath.first();
		for (Object seg : path.getSegments()) {
			if (ItemPath.segmentsEquivalent(seg, firstDeltaSegment)) {
				break;
			}
			newPath.add(seg);
		}
		newPath.addAll(deltaPath.getSegments());

		return ItemPath.create(newPath);
	}

	public static <T extends ObjectType> T getObjectFromReference(ObjectReferenceType ref, Class<T> type) {
		if (ref == null || ref.asReferenceValue().getObject() == null) {
			return null;
		}
		Objectable object = ref.asReferenceValue().getObject().asObjectable();
		if (!type.isAssignableFrom(object.getClass())) {
			throw new IllegalStateException("Got " + object.getClass() + " when expected " + type + ": "
					+ ObjectTypeUtil.toShortString(ref, true));
		}
		return (T) object;
	}

	public static void dispatchToObjectDetailsPage(ObjectReferenceType objectRef, Component component, boolean failIfUnsupported) {
		if (objectRef == null) {
			return; // should not occur
		}
		Validate.notNull(objectRef.getOid(), "No OID in objectRef");
		Validate.notNull(objectRef.getType(), "No type in objectRef");
		Class<? extends ObjectType> targetClass = ObjectTypes.getObjectTypeFromTypeQName(objectRef.getType()).getClassDefinition();
		dispatchToObjectDetailsPage(targetClass, objectRef.getOid(), component, failIfUnsupported);
	}

	public static void dispatchToObjectDetailsPage(PrismObject obj, Component component) {
		dispatchToObjectDetailsPage(obj, false, component);
	}

	// shows the actual object that is passed via parameter (not its state in repository)
	public static void dispatchToObjectDetailsPage(PrismObject obj, boolean isNewObject, Component component) {
		Class newObjectPageClass = isNewObject ? getNewlyCreatedObjectPage(obj.getCompileTimeClass()) : getObjectDetailsPage(obj.getCompileTimeClass());
		if (newObjectPageClass == null) {
			throw new IllegalArgumentException("Cannot determine details page for "+obj.getCompileTimeClass());
		}

		Constructor constructor;
		try {
			PageBase page;
			if (ResourceType.class.equals(obj.getCompileTimeClass())) {
				constructor = newObjectPageClass.getConstructor(PageParameters.class);
				page = (PageBase) constructor.newInstance(new PageParameters());

			} else {
				constructor = newObjectPageClass.getConstructor(PrismObject.class, boolean.class);
				page = (PageBase) constructor.newInstance(obj, isNewObject);

			}
			if (component.getPage() instanceof PageBase) {
				// this way we have correct breadcrumbs
				PageBase pb = (PageBase) component.getPage();
				pb.navigateToNext(page);
			} else {
				component.setResponsePage(page);
			}
		} catch (NoSuchMethodException | SecurityException e) {
			throw new SystemException("Unable to locate constructor (PrismObject) in " + newObjectPageClass

					+ ": " + e.getMessage(), e);
		} catch (InstantiationException | IllegalAccessException | IllegalArgumentException
				| InvocationTargetException e) {
			throw new SystemException("Error instantiating " + newObjectPageClass + ": " + e.getMessage(), e);
		}
	}

	public static void dispatchToObjectDetailsPage(Class<? extends ObjectType> objectClass, String oid, Component component, boolean failIfUnsupported) {
		PageParameters parameters = new PageParameters();
		parameters.add(OnePageParameterEncoder.PARAMETER, oid);
		Class<? extends PageBase> page = getObjectDetailsPage(objectClass);
		if (page != null) {
			((PageBase) component.getPage()).navigateToNext(page, parameters);
		} else if (failIfUnsupported) {
			throw new SystemException("Cannot determine details page for "+objectClass);
		}
	}

	public static boolean hasDetailsPage(PrismObject<?> object) {
		Class<?> clazz = object.getCompileTimeClass();
		return hasDetailsPage(clazz);
	}

	public static boolean hasDetailsPage(Class<?> clazz) {
		return objectDetailsPageMap.containsKey(clazz);
	}

	public static boolean hasDetailsPage(ObjectReferenceType ref) {
		if (ref == null) {
			return false;
		}
		ObjectTypes t = ObjectTypes.getObjectTypeFromTypeQName(ref.getType());
		if (t == null) {
			return false;
		}
		return hasDetailsPage(t.getClassDefinition());
	}

	public static String getStorageKeyForPage(Class<?> pageClass) {
		return storageKeyMap.get(pageClass);
	}

	public static String getStorageKeyForTableId(TableId tableId) {
		return storageTableIdMap.get(tableId);
	}

	public static Class<? extends PageBase> getObjectDetailsPage(Class<? extends ObjectType> type) {
		return objectDetailsPageMap.get(type);
	}

	public static Class<? extends PageBase> getNewlyCreatedObjectPage(Class<? extends ObjectType> type) {
	    if (ResourceType.class.equals(type)) {
            return createNewObjectPageMap.get(type);
        } else {
            return objectDetailsPageMap.get(type);
        }
	}

	public static Class<? extends PageBase> getObjectListPage(Class<? extends ObjectType> type) {
		return objectListPageMap.get(type);
	}

	public static String getStorageKeyForObjectClass(Class<? extends ObjectType> type) {
		Class<? extends PageBase> listPageClass = getObjectListPage(type);
		if (listPageClass == null) {
			return null;
		}
		return getStorageKeyForPage(listPageClass);
	}

	@NotNull
	public static TabbedPanel<ITab> createTabPanel(
			String id, final PageBase parentPage, final List<ITab> tabs, TabbedPanel.RightSideItemProvider provider) {
		return createTabPanel(id, parentPage, tabs, provider, null);
	}

	@NotNull
	public static TabbedPanel<ITab> createTabPanel(
			String id, final PageBase parentPage, final List<ITab> tabs, TabbedPanel.RightSideItemProvider provider,
			final String tabChangeParameter) {

		TabbedPanel<ITab> tabPanel = new TabbedPanel<ITab>(id, tabs, provider) {
			private static final long serialVersionUID = 1L;

			@Override
			protected void onTabChange(int index) {
				if (tabChangeParameter != null) {
					parentPage.updateBreadcrumbParameters(tabChangeParameter, index);
				}
			}

			@Override
			protected WebMarkupContainer newLink(String linkId, final int index) {
				return new AjaxSubmitLink(linkId) {
					private static final long serialVersionUID = 1L;

					@Override
					protected void onError(AjaxRequestTarget target) {
						super.onError(target);
						target.add(parentPage.getFeedbackPanel());
					}

					@Override
					protected void onSubmit(AjaxRequestTarget target) {
						super.onSubmit(target);

						setSelectedTab(index);
						if (target != null) {
							target.add(findParent(TabbedPanel.class));
						}
					}

				};
			}
		};
		tabPanel.setOutputMarkupId(true);
		return tabPanel;
	}

	public static Component createHelp(String id) {
		Label helpLabel = new Label(id);
		helpLabel.add(new InfoTooltipBehavior());
		return helpLabel;
	}

	public static String debugDumpComponentTree(Component c) {
		StringBuilder sb = new StringBuilder();
		debugDumpComponentTree(sb, c, 0);
		return sb.toString();

	}

	private static void debugDumpComponentTree(StringBuilder sb, Component c, int level) {
		DebugUtil.indentDebugDump(sb, level);
		sb.append(c).append("\n");
		if (c instanceof MarkupContainer) {
			for (Component sub : (MarkupContainer) c) {
				debugDumpComponentTree(sb, sub, level + 1);
			}
		}
	}

	public static String exceptionToString(String message, Exception e) {
		StringWriter sw = new StringWriter();
		PrintWriter pw = new PrintWriter(sw);
		pw.println(message);
		e.printStackTrace(pw);
		pw.close();
		return sw.toString();
	}

	public static Behavior visibleIfFalse(final NonEmptyModel<Boolean> model) {
		return new VisibleEnableBehaviour() {
			private static final long serialVersionUID = 1L;

			@Override
			public boolean isVisible() {
				return !model.getObject();
			}
		};
	}

	public static Behavior enabledIfFalse(final NonEmptyModel<Boolean> model) {
		return new VisibleEnableBehaviour() {
			private static final long serialVersionUID = 1L;

			@Override
			public boolean isEnabled() {
				return !model.getObject();
			}
		};
	}

	public static String getStringParameter(PageParameters params, String key) {
		if (params == null || params.get(key) == null) {
			return null;
		}

		StringValue value = params.get(key);
		if (StringUtils.isBlank(value.toString())) {
			return null;
		}

		return value.toString();
	}

	public static Integer getIntegerParameter(PageParameters params, String key) {
		if (params == null || params.get(key) == null) {
			return null;
		}

		StringValue value = params.get(key);
		if (!StringUtils.isNumeric(value.toString())) {
			return null;
		}

		return value.toInteger();
	}

	public static boolean isSubscriptionIdCorrect(String subscriptionId){
		if (StringUtils.isEmpty(subscriptionId)) {
			return false;
		}
		if (!NumberUtils.isDigits(subscriptionId)){
			return false;
		}
		if (subscriptionId.length() < 11){
			return false;
		}
		String subscriptionType = subscriptionId.substring(0, 2);
		boolean isTypeCorrect = false;
		for (SubscriptionType type : SubscriptionType.values()){
			if (type.getSubscriptionType().equals(subscriptionType)){
				isTypeCorrect = true;
				break;
			}
		}
		if (!isTypeCorrect){
			return false;
		}
		String substring1 = subscriptionId.substring(2, 4);
		String substring2 = subscriptionId.substring(4, 6);
		try {
			if (Integer.parseInt(substring1) < 1 || Integer.parseInt(substring1) > 12) {
				return false;
			}

			SimpleDateFormat dateFormat = new SimpleDateFormat("yy");
			String currentYear = dateFormat.format(Calendar.getInstance().getTime());
			if (Integer.parseInt(substring2) < Integer.parseInt(currentYear)){
				return false;
			}

			String expDateStr = subscriptionId.substring(2, 6);
			dateFormat = new SimpleDateFormat("MMyy");
			Date expDate = dateFormat.parse(expDateStr);
			Calendar expireCalendarValue = Calendar.getInstance();
			expireCalendarValue.setTime(expDate);
			expireCalendarValue.add(Calendar.MONTH, 1);
			Date currentDate = new Date(System.currentTimeMillis());
			if (expireCalendarValue.getTime().before(currentDate) || expireCalendarValue.getTime().equals(currentDate)) {
				return false;
			}
		} catch (Exception ex) {
			return false;
		}
		VerhoeffCheckDigit checkDigit = new VerhoeffCheckDigit();
		if (checkDigit.isValid(subscriptionId)){
			return true;
		}
		return false;
	}

	public static void setSelectedTabFromPageParameters(TabbedPanel tabbed, PageParameters params, String paramName) {
		IModel<List> tabsModel = tabbed.getTabs();

		Integer tabIndex = getIntegerParameter(params, paramName);
		if (tabIndex == null || tabIndex < 0 || tabIndex >= tabsModel.getObject().size()) {
			return;
		}

		tabbed.setSelectedTab(tabIndex);
	}

	public static boolean getElementVisibility(UserInterfaceElementVisibilityType visibilityType){
		return getElementVisibility(visibilityType, new ArrayList<>());
	}

	public static boolean getElementVisibility(UserInterfaceElementVisibilityType visibilityType, List<String> requiredAuthorizations){
		if (UserInterfaceElementVisibilityType.HIDDEN.equals(visibilityType) ||
				UserInterfaceElementVisibilityType.VACANT.equals(visibilityType)){
			return false;
		}
		if (UserInterfaceElementVisibilityType.VISIBLE.equals(visibilityType)){
			return true;
		}
		if (UserInterfaceElementVisibilityType.AUTOMATIC.equals(visibilityType)){
			if (WebComponentUtil.isAuthorized(requiredAuthorizations)){
				return true;
			} else {
				return false;
			}
		}
		return true;
	}

	public static <AR extends AbstractRoleType> IModel<String> createAbstractRoleConfirmationMessage(String actionName,
			ColumnMenuAction action, MainObjectListPanel<AR, CompiledObjectCollectionView> abstractRoleTable, PageBase pageBase) {
		List<AR> selectedRoles =  new ArrayList<>();
		if (action.getRowModel() == null) {
			selectedRoles.addAll(abstractRoleTable.getSelectedObjects());
		} else {
			selectedRoles.add(((SelectableBean<AR>) action.getRowModel().getObject()).getValue());
		}
		OperationResult result = new OperationResult("Search Members");
		boolean atLeastOneWithMembers = false;
		for (AR selectedRole : selectedRoles) {
			ObjectQuery query = pageBase.getPrismContext().queryFor(FocusType.class)
					.item(FocusType.F_ROLE_MEMBERSHIP_REF)// TODO MID-3581
							.ref(ObjectTypeUtil.createObjectRef(selectedRole, pageBase.getPrismContext()).asReferenceValue())
					.maxSize(1)
					.build();
			List<PrismObject<FocusType>> members = WebModelServiceUtils.searchObjects(FocusType.class, query, result, pageBase);
			if (CollectionUtils.isNotEmpty(members)) {
				atLeastOneWithMembers = true;
				break;
			}
		}
		String members = atLeastOneWithMembers ? ".members" : "";
		ObjectTypes objectType = ObjectTypes.getObjectType(abstractRoleTable.getType());
		String propertyKeyPrefix = ObjectTypes.SERVICE.equals(objectType) ? "pageServices" : "pageRoles";

		if (action.getRowModel() == null) {
			return pageBase.createStringResource(propertyKeyPrefix + ".message.confirmationMessageForMultipleObject" + members,
					actionName, abstractRoleTable.getSelectedObjectsCount());
		} else {
			return pageBase.createStringResource(propertyKeyPrefix + ".message.confirmationMessageForSingleObject" + members,
					actionName, ((ObjectType)((SelectableBean)action.getRowModel().getObject()).getValue()).getName());
		}
	}

	public static DisplayType getNewObjectDisplayTypeFromCollectionView(CompiledObjectCollectionView view, PageBase pageBase){
		DisplayType displayType = view != null ? view.getDisplay() : null;
		if (displayType == null){
			displayType = WebComponentUtil.createDisplayType(GuiStyleConstants.CLASS_ADD_NEW_OBJECT, "green", "");
		}
		if (PolyStringUtils.isEmpty(displayType.getTooltip()) && !PolyStringUtils.isEmpty(displayType.getLabel())){
			StringBuilder sb = new StringBuilder();
			sb.append(pageBase.createStringResource("MainObjectListPanel.newObject").getString());
			sb.append(" ");
			sb.append(displayType.getLabel().getOrig().toLowerCase());
			displayType.setTooltip(WebComponentUtil.createPolyFromOrigString(sb.toString()));
		}
		return view != null ? view.getDisplay() : null;
	}
	
	public static List<ItemPath> getShadowItemsToShow() {
		return Arrays.asList(
				ShadowType.F_ATTRIBUTES,
				SchemaConstants.PATH_ACTIVATION,
				SchemaConstants.PATH_PASSWORD,
				ShadowType.F_ASSOCIATION);
	}

	public static ItemVisibility checkShadowActivationAndPasswordVisibility(ItemWrapper<?, ?, ?,?> itemWrapper,
																	 ShadowType shadowType) {
//
		ResourceType resource = shadowType.getResource();

		if (resource == null) {
			//TODO: what to return if we don't have resource available?
			return ItemVisibility.AUTO;
		}
		
		CompositeRefinedObjectClassDefinition ocd = null;

		try {
			RefinedResourceSchema resourceSchema = RefinedResourceSchema.getRefinedSchema(resource.asPrismObject());
			ocd = resourceSchema.determineCompositeObjectClassDefinition(shadowType.asPrismObject());
		} catch (SchemaException e) {
			LOGGER.error("Cannot find refined definition for {} in {}", shadowType, resource);
		}
		ResourceObjectTypeDefinitionType resourceObjectTypeDefinitionType = ResourceTypeUtil.findObjectTypeDefinition(resource.asPrismObject(), shadowType.getKind(), shadowType.getIntent());


		if (SchemaConstants.PATH_ACTIVATION.equivalent(itemWrapper.getPath())) {
			if (ResourceTypeUtil.isActivationCapabilityEnabled(resource, resourceObjectTypeDefinitionType)) {
				return ItemVisibility.AUTO;
			} else {
				return ItemVisibility.HIDDEN;
			}
		}
		
		if (SchemaConstants.PATH_ACTIVATION_ADMINISTRATIVE_STATUS.equivalent(itemWrapper.getPath())) {
			if (ResourceTypeUtil.isActivationStatusCapabilityEnabled(resource, resourceObjectTypeDefinitionType)) {
				return ItemVisibility.AUTO;
			} else {
				return ItemVisibility.HIDDEN;
			}
		}
		
		if (SchemaConstants.PATH_ACTIVATION_LOCKOUT_STATUS.equivalent(itemWrapper.getPath())) {
			if (ResourceTypeUtil.isActivationLockoutStatusCapabilityEnabled(resource, resourceObjectTypeDefinitionType)) {
				return ItemVisibility.AUTO;
			} else {
				return ItemVisibility.HIDDEN;
			}
		}
		
		if (SchemaConstants.PATH_ACTIVATION_VALID_FROM.equivalent(itemWrapper.getPath())) {
			if (ResourceTypeUtil.isActivationValidityFromCapabilityEnabled(resource, resourceObjectTypeDefinitionType)) {
				return ItemVisibility.AUTO;
			} else {
				return ItemVisibility.HIDDEN;
			}
		}

		if (SchemaConstants.PATH_ACTIVATION_VALID_TO.equivalent(itemWrapper.getPath())) {
			if (ResourceTypeUtil.isActivationValidityToCapabilityEnabled(resource, resourceObjectTypeDefinitionType)) {
				return ItemVisibility.AUTO;
			} else {
				return ItemVisibility.HIDDEN;
			}
		}
		
		if (SchemaConstants.PATH_PASSWORD.equivalent(itemWrapper.getPath())) {
			if (ResourceTypeUtil.isPasswordCapabilityEnabled(resource, resourceObjectTypeDefinitionType)) {
				return ItemVisibility.AUTO;
			} else {
				return ItemVisibility.HIDDEN;
			}
		}
		
		if (ShadowType.F_ASSOCIATION.equivalent(itemWrapper.getPath())) {
			if (ocd != null && CollectionUtils.isNotEmpty(ocd.getAssociationDefinitions())) {
				return ItemVisibility.AUTO;
			} else {
				return ItemVisibility.HIDDEN;
			}
		}
		
		return ItemVisibility.AUTO;

	}

	public static boolean isAssociationSupported(ShadowType shadowType) {
		ResourceType resource = shadowType.getResource();

		if (resource == null) {
			//TODO: what to return if we don't have resource available?
			return false;
		}

		CompositeRefinedObjectClassDefinition ocd = null;

		try {
			RefinedResourceSchema resourceSchema = RefinedResourceSchema.getRefinedSchema(resource.asPrismObject());
			ocd = resourceSchema.determineCompositeObjectClassDefinition(shadowType.asPrismObject());
		} catch (SchemaException e) {
			LOGGER.error("Cannot find refined definition for {} in {}", shadowType, resource);
		}

		if (ocd == null) {
			return false;
		}

		return CollectionUtils.isNotEmpty(ocd.getAssociationDefinitions());
	}

	private static boolean isCapabilityEnabled(CapabilityType cap) {
		if (cap == null) {
			return false;
		}

		return BooleanUtils.isNotFalse(cap.isEnabled());
	}

	public static void refreshResourceSchema(@NotNull PrismObject<ResourceType> resource, String operation, AjaxRequestTarget target, PageBase pageBase){
		Task task = pageBase.createSimpleTask(operation);
		OperationResult parentResult = new OperationResult(operation);

		try {
			ResourceUtils.deleteSchema(resource, pageBase.getModelService(), pageBase.getPrismContext(), task, parentResult);
			pageBase.getModelService().testResource(resource.getOid(), task);					// try to load fresh scehma
		} catch (ObjectAlreadyExistsException | ObjectNotFoundException | SchemaException
				| ExpressionEvaluationException | CommunicationException | ConfigurationException
				| PolicyViolationException | SecurityViolationException e) {
			LoggingUtils.logUnexpectedException(LOGGER, "Error refreshing resource schema", e);
			parentResult.recordFatalError(pageBase.createStringResource("WebComponentUtil.message.refreshResourceSchema.fatalError").getString(), e);
		}

		parentResult.computeStatus();
		pageBase.showResult(parentResult, "pageResource.refreshSchema.failed");
		target.add(pageBase.getFeedbackPanel());
	}

	public static List<QName> getCategoryRelationChoices(AreaCategoryType category, ModelServiceLocator pageBase){
		List<QName> relationsList = new ArrayList<>();
		List<RelationDefinitionType> defList = getRelationDefinitions(pageBase);
		defList.forEach(def -> {
			if (def.getCategory() != null && def.getCategory().contains(category)) {
				relationsList.add(def.getRef());
			}
		});
		return relationsList;
	}
	
	public static List<QName> getAllRelations(ModelServiceLocator pageBase) {
		List<RelationDefinitionType> allRelationDefinitions = getRelationDefinitions(pageBase);
		List<QName> allRelationsQName = new ArrayList<>(allRelationDefinitions.size());
		allRelationDefinitions.forEach(relation -> allRelationsQName.add(relation.getRef()));
		return allRelationsQName;
	}

	@NotNull
	public static List<RelationDefinitionType> getRelationDefinitions(ModelServiceLocator pageBase) {
		return pageBase.getModelInteractionService().getRelationDefinitions();
	}

	public static RelationDefinitionType getRelationDefinition(QName relation) {
		return getRelationRegistry().getRelationDefinition(relation);
	}

	public static <T> DropDownChoice createTriStateCombo(String id, IModel<Boolean> model) {
		final IChoiceRenderer<T> renderer = new IChoiceRenderer<T>() {


			@Override
			public T getObject(String id, IModel<? extends List<? extends T>> choices) {
				return id != null ? choices.getObject().get(Integer.parseInt(id)) : null;
			}

			@Override
			public String getDisplayValue(T object) {
				String key;
				if (object == null) {
					key = KEY_BOOLEAN_NULL;
				} else {
					Boolean b = (Boolean) object;
					key = b ? KEY_BOOLEAN_TRUE : KEY_BOOLEAN_FALSE;
				}

				StringResourceModel model = PageBase.createStringResourceStatic(null, key);

				return model.getString();
			}

			@Override
			public String getIdValue(T object, int index) {
				return Integer.toString(index);
			}



		};

		DropDownChoice dropDown = new DropDownChoice(id, model, createChoices(), renderer) {

			@Override
			protected CharSequence getDefaultChoice(String selectedValue) {
				StringResourceModel model = PageBase.createStringResourceStatic(null, KEY_BOOLEAN_NULL);

				return model.getString();
			}
		};
		dropDown.setNullValid(true);

		return dropDown;
	}

	public static boolean isAllNulls(Iterable<?> array) {
		return StreamSupport.stream(array.spliterator(), true).allMatch(o -> o == null);
	}

	public static ObjectFilter createAssociationShadowRefFilter(RefinedAssociationDefinition refinedAssocationDefinition, PrismContext prismContext,
																String resourceOid){
		S_FilterEntryOrEmpty atomicFilter = prismContext.queryFor(ShadowType.class);
		List<ObjectFilter> orFilterClauses = new ArrayList<>();
		refinedAssocationDefinition.getIntents()
				.forEach(intent -> orFilterClauses.add(atomicFilter.item(ShadowType.F_INTENT).eq(intent).buildFilter()));
		OrFilter intentFilter = prismContext.queryFactory().createOr(orFilterClauses);

		AndFilter filter = (AndFilter) atomicFilter.item(ShadowType.F_KIND).eq(refinedAssocationDefinition.getKind()).and()
				.item(ShadowType.F_RESOURCE_REF).ref(resourceOid, ResourceType.COMPLEX_TYPE).buildFilter();
		filter.addCondition(intentFilter);
		return filter;
	}

	private static IModel<List<Boolean>> createChoices() {
		return new IModel<List<Boolean>>() {

			@Override
			public List<Boolean> getObject() {
				List<Boolean> list = new ArrayList<>();
				list.add(null);
				list.add(Boolean.TRUE);
				list.add(Boolean.FALSE);

				return list;
			}
		};
	}
	
	public static LookupTableType createAppenderChoices(PageBase pageBase, Task task, OperationResult result) {
        return createAppenderChoices(WebModelServiceUtils.loadSystemConfigurationAsPrismObject(pageBase, task, result).getRealValue().getLogging().getAppender());
	}

	private static LookupTableType createAppenderChoices(List<AppenderConfigurationType> appenders) {
		LookupTableType lookupTable = new LookupTableType();
        List<LookupTableRowType> list = lookupTable.createRowList();

        for (AppenderConfigurationType appender : appenders) {
        		LookupTableRowType row = new LookupTableRowType();
        		String name = appender.getName();
        		row.setKey(name);
        		row.setValue(name);
        		row.setLabel(new PolyStringType(name));
        		list.add(row);
        }
        return lookupTable;
	}

	public static LookupTableType createLoggerPackageChoices(PageBase pageBase) {
		LookupTableType lookupTable = new LookupTableType();
        List<LookupTableRowType> list = lookupTable.createRowList();
        IModel<List<StandardLoggerType>> standardLoggers = WebComponentUtil.createReadonlyModelFromEnum(StandardLoggerType.class);
    	IModel<List<LoggingComponentType>> componentLoggers = WebComponentUtil.createReadonlyModelFromEnum(LoggingComponentType.class);

    	for(StandardLoggerType standardLogger : standardLoggers.getObject()) {
    		LookupTableRowType row = new LookupTableRowType();
    		row.setKey(standardLogger.getValue());
    		row.setValue(standardLogger.getValue());
    		row.setLabel(new PolyStringType(pageBase.createStringResource("StandardLoggerType." + standardLogger.name()).getString()));
    		list.add(row);
    	}
    	for(LoggingComponentType componentLogger : componentLoggers.getObject()) {
    		LookupTableRowType row = new LookupTableRowType();
    			String value = ComponentLoggerType.getPackageByValue(componentLogger);
    		row.setKey(value);
    		row.setValue(value);
    		row.setLabel(new PolyStringType(pageBase.createStringResource("LoggingComponentType." + componentLogger.name()).getString()));
    		list.add(row);
    	}
        return lookupTable;
	}

	public static Class getPreviousPageClass(PageBase parentPage){
		List<Breadcrumb> breadcrumbs = parentPage.getBreadcrumbs();
		if (breadcrumbs == null || breadcrumbs.size() < 2){
			return null;
		}
		Breadcrumb previousBreadcrumb = breadcrumbs.get(breadcrumbs.size() - 2);
		Class page = null;
		if (previousBreadcrumb instanceof BreadcrumbPageClass){
			page = ((BreadcrumbPageClass) previousBreadcrumb).getPage();
		} else if (previousBreadcrumb instanceof BreadcrumbPageInstance){
			page = ((BreadcrumbPageInstance) previousBreadcrumb).getPage().getClass();
		}
		return page;
	}

	@NotNull
	public static List<InlineMenuItem> createMenuItemsFromActions(@NotNull List<GuiActionType> actions, String operation,
			PageBase pageBase, @NotNull Supplier<Collection<? extends ObjectType>> selectedObjectsSupplier) {
		List<InlineMenuItem> menuItems = new ArrayList<>();
		actions.forEach(action -> {
			if (action.getTaskTemplateRef() == null) {
				return;
			}
			String templateOid = action.getTaskTemplateRef().getOid();
			if (StringUtils.isEmpty(templateOid)) {
				return;
			}
			String label = action.getDisplay() != null && PolyStringUtils.isNotEmpty(action.getDisplay().getLabel()) ?
					action.getDisplay().getLabel().getOrig() : action.getName();
			menuItems.add(new InlineMenuItem(Model.of(label)) {
				private static final long serialVersionUID = 1L;

				@Override
                public InlineMenuItemAction initAction() {
					return new InlineMenuItemAction() {
						private static final long serialVersionUID = 1L;

						@Override
						public void onClick(AjaxRequestTarget target) {
							OperationResult result = new OperationResult(operation);
							try {
								Collection<String> oids = CollectionUtils.emptyIfNull(selectedObjectsSupplier.get())
										.stream()
										.filter(o -> o.getOid() != null)
										.map(o -> o.getOid())
										.collect(Collectors.toSet());
								if (!oids.isEmpty()) {
									Map<QName, Object> extensionValues = prepareExtensionValues(oids);
									TaskType executorTask = pageBase.getModelInteractionService().submitTaskFromTemplate(
											templateOid, extensionValues, pageBase.createSimpleTask(operation), result);
									result.recordInProgress(); // this should be probably have been done in submitTaskFromTemplate
									result.setBackgroundTaskOid(executorTask.getOid());
								} else {
									result.recordWarning(pageBase.createStringResource("WebComponentUtil.message.createMenuItemsFromActions.warning").getString());
								}
							} catch (Exception ex) {
								result.recordFatalError(result.getOperation(), ex);
								target.add(pageBase.getFeedbackPanel());
							} finally {
								pageBase.showResult(result);
								target.add(pageBase.getFeedbackPanel());
							}
						}
					};
				}

				/**
				 * Extension values are task-dependent. Therefore, in the future we will probably make
				 * this behaviour configurable. For the time being we assume that the task template will be
				 * of "iterative task handler" type and so it will expect mext:objectQuery extension property.
				 */

				@NotNull
				private Map<QName, Object> prepareExtensionValues(Collection<String> oids) throws SchemaException {
					Map<QName, Object> extensionValues = new HashMap<>();
					PrismContext prismContext = pageBase.getPrismContext();
					ObjectQuery objectQuery = prismContext.queryFor(ObjectType.class)
							.id(oids.toArray(new String[0]))
							.build();
					QueryType queryBean = pageBase.getQueryConverter().createQueryType(objectQuery);
					extensionValues.put(SchemaConstants.MODEL_EXTENSION_OBJECT_QUERY, queryBean);
					return extensionValues;
				}
			});
		});
		return menuItems;
	}

	@SuppressWarnings("unused")
	public static RelationRegistry getStaticallyProvidedRelationRegistry() {
		return staticallyProvidedRelationRegistry;
	}

	public static void setStaticallyProvidedRelationRegistry(RelationRegistry staticallyProvidedRelationRegistry) {
		WebComponentUtil.staticallyProvidedRelationRegistry = staticallyProvidedRelationRegistry;
	}

	public static ObjectFilter getAssignableRolesFilter(PrismObject<? extends FocusType> focusObject, Class<? extends AbstractRoleType> type, AssignmentOrder assignmentOrder,
														OperationResult result, Task task, PageBase pageBase) {
		ObjectFilter filter = null;
		LOGGER.debug("Loading objects which can be assigned");
		try {
			ModelInteractionService mis = pageBase.getModelInteractionService();
			RoleSelectionSpecification roleSpec =
					mis.getAssignableRoleSpecification(focusObject, type, assignmentOrder.getOrder(), task, result);
			filter = roleSpec.getFilter();
		} catch (Exception ex) {
			LoggingUtils.logUnexpectedException(LOGGER, "Couldn't load available roles", ex);
			result.recordFatalError(pageBase.createStringResource("WebComponentUtil.message.getAssignableRolesFilter.fatalError").getString(), ex);
		} finally {
			result.recomputeStatus();
		}
		if (!result.isSuccess() && !result.isHandledError()) {
			pageBase.showResult(result);
		}
		return filter;
	}

	public static String formatDurationWordsForLocal(long durationMillis, boolean suppressLeadingZeroElements,
	        boolean suppressTrailingZeroElements, PageBase pageBase){
		
		String duration = DurationFormatUtils.formatDurationWords(durationMillis, suppressLeadingZeroElements, suppressTrailingZeroElements);
		
		duration = StringUtils.replaceOnce(duration, "seconds", pageBase.createStringResource("WebComponentUtil.formatDurationWordsForLocal.seconds").getString());
        duration = StringUtils.replaceOnce(duration, "minutes", pageBase.createStringResource("WebComponentUtil.formatDurationWordsForLocal.minutes").getString());
        duration = StringUtils.replaceOnce(duration, "hours", pageBase.createStringResource("WebComponentUtil.formatDurationWordsForLocal.hours").getString());
        duration = StringUtils.replaceOnce(duration, "days", pageBase.createStringResource("WebComponentUtil.formatDurationWordsForLocal.days").getString());
        duration = StringUtils.replaceOnce(duration, "second", pageBase.createStringResource("WebComponentUtil.formatDurationWordsForLocal.second").getString());
        duration = StringUtils.replaceOnce(duration, "minute", pageBase.createStringResource("WebComponentUtil.formatDurationWordsForLocal.minute").getString());
        duration = StringUtils.replaceOnce(duration, "hour", pageBase.createStringResource("WebComponentUtil.formatDurationWordsForLocal.hour").getString());
        duration = StringUtils.replaceOnce(duration, "day", pageBase.createStringResource("WebComponentUtil.formatDurationWordsForLocal.day").getString());
		
		return duration;
	}

//	@Deprecated
//	public static <IW extends ItemWrapperOld> IModel<String> getDisplayName(final IModel<IW> model, Component component) {
//        return new IModel<String>() {
//        	private static final long serialVersionUID = 1L;
//
//            @Override
//            public String getObject() {
//            	ItemWrapperOld wrapper = model.getObject();
//                String displayName = wrapper.getDisplayName();
//                // TODO: this is maybe not needed any more. wrapper.getDisplayName() is supposed to return localized string
//                // TODO: however, we have not tested all the scenarios, therefore let's leave it like this for now
//                String displayNameValueByKey = PageBase.createStringResourceStatic(component, displayName).getString();
//                return StringUtils.isEmpty(displayNameValueByKey) ?
//                		component.getString(displayName, null, displayName) : displayNameValueByKey;
//            }
//        };
//    }
//

//	public static <IW extends ItemWrapper<V, I, ID>> IModel<String> getDisplayName(final IModel<IW> model, Component component) {
//        return new IModel<String>() {
//        	private static final long serialVersionUID = 1L;
//
//            @Override
//            public String getObject() {
//            	ItemWrapperOld wrapper = model.getObject();
//                String displayName = wrapper.getDisplayName();
//                // TODO: this is maybe not needed any more. wrapper.getDisplayName() is supposed to return localized string
//                // TODO: however, we have not tested all the scenarios, therefore let's leave it like this for now
//                String displayNameValueByKey = PageBase.createStringResourceStatic(component, displayName).getString();
//                return StringUtils.isEmpty(displayNameValueByKey) ?
//                		component.getString(displayName, null, displayName) : displayNameValueByKey;
//            }
//        };
//    }

	public static List<QName> loadResourceObjectClassValues(ResourceType resource, PageBase pageBase){
		try {
			ResourceSchema schema = RefinedResourceSchemaImpl.getResourceSchema(resource, pageBase.getPrismContext());
			if (schema != null) {
				return schema.getObjectClassList();
			}
		} catch (SchemaException|RuntimeException e){
			LoggingUtils.logUnexpectedException(LOGGER, "Couldn't load object class list from resource.", e);
			pageBase.error("Couldn't load object class list from resource.");
		}
		return new ArrayList<>();
	}

	public static List<RefinedAssociationDefinition> getRefinedAssociationDefinition(ResourceType resource, ShadowKindType kind, String intent){
		List<RefinedAssociationDefinition> associationDefinitions = new ArrayList<>();

		try {

			if (resource == null) {
				return associationDefinitions;
			}
			RefinedResourceSchema refinedResourceSchema = RefinedResourceSchema.getRefinedSchema(resource.asPrismObject());
			RefinedObjectClassDefinition oc = refinedResourceSchema.getRefinedDefinition(kind, intent);
			if (oc == null) {
				LOGGER.debug("Association for {}/{} not supported by resource {}", kind, intent, resource);
				return associationDefinitions;
			}
			associationDefinitions.addAll(oc.getAssociationDefinitions());

			if (CollectionUtils.isEmpty(associationDefinitions)) {
				LOGGER.debug("Association for {}/{} not supported by resource {}", kind, intent, resource);
				return associationDefinitions;
			}
		} catch (Exception ex) {
			LOGGER.error("Association for {}/{} not supported by resource {}", kind, intent, resource, ex.getLocalizedMessage());
		}
		return associationDefinitions;
	}

	public static String getAssociationDisplayName(RefinedAssociationDefinition assocDef) {
		if (assocDef == null){
			return "";
		}
		StringBuilder sb = new StringBuilder();
		if (assocDef.getDisplayName() != null) {
			sb.append(assocDef.getDisplayName()).append(", ");
		}
		if (assocDef.getResourceObjectAssociationType() != null && assocDef.getResourceObjectAssociationType().getRef() != null) {
			sb.append("ref: ").append(assocDef.getResourceObjectAssociationType().getRef().getItemPath().toString());
		}
		return sb.toString();
	}

	@Deprecated
	public static ExpressionType getAssociationExpression(PrismContainerValueWrapper<AssignmentType> assignmentValueWrapper, PageBase pageBase) {
		return getAssociationExpression(assignmentValueWrapper, false, null, pageBase);
	}

	//TODO refactor..
	@Deprecated
	public static ExpressionType getAssociationExpression(PrismContainerValueWrapper<AssignmentType> assignmentValueWrapper,
			boolean createIfNotExist, PrismContext prismContext, PageBase pageBase) {
		if (assignmentValueWrapper == null){
			return null;
		}
		if (createIfNotExist && prismContext == null) {
			throw new IllegalArgumentException("createIfNotExist is set but prismContext is null");
		}
		PrismContainerWrapper<ResourceObjectAssociationType> association;
		try {
			association = assignmentValueWrapper
					.findContainer(ItemPath.create(AssignmentType.F_CONSTRUCTION, ConstructionType.F_ASSOCIATION));
		} catch (SchemaException e) {
			LOGGER.error("Cannot find association wrapper, reason: {}", e.getMessage(), e);
			pageBase.getSession().error("Cannot find association wrapper, reason: " + e.getMessage());
			return null;
		}

		if (association == null || association.getValues() == null || association.getValues().size() == 0){
			return null;
		}
		//FIXME HACK not to add empty association value
		if (ContainerStatus.ADDING.equals(association.getStatus())){
			association.getItem().clear();
		}
		PrismContainerValueWrapper<ResourceObjectAssociationType> associationValueWrapper = association.getValues().get(0);
		PrismPropertyWrapper<ExpressionType> expressionWrapper;
		try {
			expressionWrapper = associationValueWrapper.findProperty(ItemPath.create(ResourceObjectAssociationType.F_OUTBOUND, MappingType.F_EXPRESSION));
		} catch (SchemaException e) {
			LOGGER.error("Cannot find expression wrapper, reason: {}", e.getMessage(), e);
			pageBase.getSession().error("Cannot find expression wrapper, reason: " + e.getMessage());
			return null;
		}

		if (expressionWrapper == null){
			return null;
		}
		List<PrismPropertyValueWrapper<ExpressionType>> expressionValues = expressionWrapper.getValues();
		if (expressionValues == null || expressionValues.size() == 0){
			return null;
		}
		try {
			ExpressionType expression = expressionValues.get(0).getRealValue();
			if (expression == null && createIfNotExist) {
				expression = new ExpressionType();
				PrismPropertyValue<ExpressionType> exp = prismContext.itemFactory().createPropertyValue(expression);
				WrapperContext context = new WrapperContext(null, null);
				PrismPropertyValueWrapper<ExpressionType> val = (PrismPropertyValueWrapper<ExpressionType>) pageBase
						.createValueWrapper(expressionWrapper, exp, ValueStatus.ADDED, context);
				// ValueWrapperOld<ExpressionType> val = new
				// ValueWrapperOld<>(expressionWrapper, exp, prismContext);
				expressionValues.remove(0);
				expressionValues.add(0, val);
			}
		} catch (SchemaException e) {
			// TODO erro handling
			return null;
		}
		return expressionValues.get(0).getRealValue();
	}

	public static PrismObject<ResourceType> getConstructionResource(ConstructionType construction, String operation, PageBase pageBase){
		ResourceType resource = construction.getResource();
		if (resource != null){
			return resource.asPrismObject();
		}
		ObjectReferenceType resourceRef = construction.getResourceRef();
		OperationResult result = new OperationResult(operation);
		Task task = pageBase.createSimpleTask(operation);
		return WebModelServiceUtils.resolveReferenceNoFetch(resourceRef, pageBase, task, result);
	}

	public static <O extends ObjectType> ArchetypePolicyType getArchetypeSpecification(PrismObject<O> object, ModelServiceLocator locator){
		if (object == null || object.asObjectable() == null){
			return null;
		}
		String objectName = object.asObjectable().getName() != null ? object.asObjectable().getName().getOrig() : "Unknown";
		OperationResult result = new OperationResult("loadArchetypeSpecificationFor" + objectName);
		if (!object.canRepresent(AssignmentHolderType.class)) {
			return null;
		}
		ArchetypePolicyType spec = null;
		try {
			spec = locator.getModelInteractionService().determineArchetypePolicy((PrismObject<? extends AssignmentHolderType>) object, result);
		} catch (SchemaException | ConfigurationException ex){
			result.recordPartialError(ex.getLocalizedMessage());
			LOGGER.error("Cannot load ArchetypeInteractionSpecification for object ", object, ex.getLocalizedMessage());
		}
		return spec;
	}

	public static String getIconCssClass(DisplayType displayType){
		if (displayType == null || displayType.getIcon() == null){
			return null;
		}
		return displayType.getIcon().getCssClass();
	}

	public static String getIconColor(DisplayType displayType){
		if (displayType == null || displayType.getIcon() == null){
			return null;
		}
		return displayType.getIcon().getColor();
	}

	public static String getDisplayTypeTitle(DisplayType displayType){
		if (displayType == null || displayType.getTooltip() == null){
			return null;
		}
		return displayType.getTooltip().getOrig();
	}

	public static DisplayType createDisplayType(String iconCssClass){
		return createDisplayType(iconCssClass, "", "");
	}

	public static DisplayType createDisplayType(String iconCssClass, String iconColor, String title){
		DisplayType displayType = new DisplayType();
		IconType icon = new IconType();
		icon.setCssClass(iconCssClass);
		icon.setColor(iconColor);
		displayType.setIcon(icon);

		displayType.setTooltip(createPolyFromOrigString(title));
		return displayType;
	}

	public static DisplayType createDisplayType(String iconCssClass, PolyStringType title){
		DisplayType displayType = new DisplayType();
		IconType icon = new IconType();
		icon.setCssClass(iconCssClass);
		displayType.setIcon(icon);

		displayType.setTooltip(title);
		return displayType;
	}

	public static IconType createIconType(String iconStyle){
		return createIconType(iconStyle, "");
	}

	public static IconType createIconType(String iconStyle, String color){
		IconType icon = new IconType();
		icon.setCssClass(iconStyle);
		icon.setColor(color);
		return icon;
	}


	public static CompositedIconBuilder getAssignmentRelationIconBuilder(PageBase pageBase, AssignmentObjectRelation relationSpec,
																		 IconType relationIcon, IconType actionButtonIcon){
		CompositedIconBuilder builder = new CompositedIconBuilder();
		if (relationSpec == null){
			if (actionButtonIcon == null){
				return null;
			}
			builder.setBasicIcon(actionButtonIcon, IconCssStyle.IN_ROW_STYLE)
					.appendColorHtmlValue(actionButtonIcon.getColor());
			return builder;
		}
		DisplayType objectTypeDisplay = null;
		if (CollectionUtils.isNotEmpty(relationSpec.getArchetypeRefs())){
			try {
				String operation = pageBase.getClass().getSimpleName() + "." + "loadArchetypeObject";
				ArchetypeType archetype = pageBase.getModelObjectResolver().resolve(relationSpec.getArchetypeRefs().get(0), ArchetypeType.class,
						null, null, pageBase.createSimpleTask(operation),
						new OperationResult(operation));
				if (archetype != null && archetype.getArchetypePolicy() != null){
					objectTypeDisplay = archetype.getArchetypePolicy().getDisplay();
				}
			} catch (Exception ex){
				LOGGER.error("Couldn't load archetype object, " + ex.getLocalizedMessage());
			}
		}
		if (objectTypeDisplay == null){
			objectTypeDisplay = new DisplayType();
		}
		if (objectTypeDisplay.getIcon() == null){
			objectTypeDisplay.setIcon(new IconType());
		}
		QName objectType = org.apache.commons.collections.CollectionUtils.isNotEmpty(relationSpec.getObjectTypes()) ? relationSpec.getObjectTypes().get(0) : null;
		if (StringUtils.isEmpty(WebComponentUtil.getIconCssClass(objectTypeDisplay)) && objectType != null){
			objectTypeDisplay.getIcon().setCssClass(WebComponentUtil.createDefaultBlackIcon(objectType));
		}
		if (StringUtils.isNotEmpty(WebComponentUtil.getIconCssClass(objectTypeDisplay))) {
			builder.setBasicIcon(objectTypeDisplay.getIcon(), IconCssStyle.IN_ROW_STYLE)
					.appendColorHtmlValue(WebComponentUtil.getIconColor(objectTypeDisplay))
					.appendLayerIcon(actionButtonIcon, IconCssStyle.BOTTOM_RIGHT_STYLE)
					.appendLayerIcon(relationIcon, IconCssStyle.TOP_RIGHT_STYLE);
		} else {
			builder.setBasicIcon(actionButtonIcon, IconCssStyle.IN_ROW_STYLE)
					.appendColorHtmlValue(actionButtonIcon.getColor());
		}
		return builder;
	}


	public static <O extends ObjectType> DisplayType getArchetypePolicyDisplayType(O object, PageBase pageBase) {
		if (object != null) {
			ArchetypePolicyType archetypePolicy = WebComponentUtil.getArchetypeSpecification(object.asPrismObject(), pageBase);
			if (archetypePolicy != null) {
				return archetypePolicy.getDisplay();
			}
		}
		return null;
	}

	public static IModel<String> getIconUrlModel(IconType icon){
		if (icon == null || StringUtils.isEmpty(icon.getImageUrl())){
			return Model.of();
		}
		String sUrl = icon.getImageUrl();
		if (URI.create(sUrl).isAbsolute()) {
			return Model.of(sUrl);
		}

		List<String> segments = RequestCycle.get().getUrlRenderer().getBaseUrl().getSegments();
		if (segments == null || segments.size() < 2) {
			return Model.of(sUrl);
		}

		String prefix = StringUtils.repeat("../", segments.size() - 1);
		if (!sUrl.startsWith("/")) {
			return Model.of(prefix + sUrl);
		}

		return Model.of(StringUtils.left(prefix, prefix.length() - 1) + sUrl);
	}

	public static void deleteSyncTokenPerformed(AjaxRequestTarget target, ResourceType resourceType, PageBase pageBase){
		String resourceOid = resourceType.getOid();
		String handlerUri = "http://midpoint.evolveum.com/xml/ns/public/model/synchronization/task/live-sync/handler-3";
		ObjectReferenceType resourceRef = new ObjectReferenceType();
		resourceRef.setOid(resourceOid);
		PrismObject<TaskType> oldTask;

		OperationResult result = new OperationResult(pageBase.getClass().getName() + "." + "deleteSyncToken");
		ObjectQuery query = pageBase.getPrismContext().queryFor(TaskType.class)
				.item(TaskType.F_OBJECT_REF).ref(resourceOid)
				.and().item(TaskType.F_HANDLER_URI).eq(handlerUri)
				.build();

		List<PrismObject<TaskType>> taskList = WebModelServiceUtils.searchObjects(TaskType.class, query, result, pageBase);

		if (taskList.size() != 1) {
			pageBase.error(pageBase.createStringResource("pageResource.message.invalidTaskSearch"));
		} else {
			oldTask = taskList.get(0);
			saveTask(oldTask, result, pageBase);
		}

		result.recomputeStatus();
		pageBase.showResult(result);
		target.add(pageBase.getFeedbackPanel());
	}

	/**
	 * The idea is to divide the list of AssignmentObjectRelation objects in such way that each AssignmentObjectRelation
	 * in the list will contain not more than 1 relation. This will simplify creating of a new_assignment_button
	 * on some panels
	 *
	  * @param initialRelationsList
	 * @return
	 */
	public static List<AssignmentObjectRelation> divideAssignmentRelationsByRelationValue(List<AssignmentObjectRelation> initialRelationsList){
		if (org.apache.commons.collections.CollectionUtils.isEmpty(initialRelationsList)){
			return initialRelationsList;
		}
		List<AssignmentObjectRelation> combinedRelationList =  new ArrayList<>();
		initialRelationsList.forEach(assignmentTargetRelation -> {
			if (org.apache.commons.collections.CollectionUtils.isEmpty(assignmentTargetRelation.getObjectTypes()) &&
					org.apache.commons.collections.CollectionUtils.isEmpty(assignmentTargetRelation.getRelations())){
				return;
			}
			if (org.apache.commons.collections.CollectionUtils.isEmpty(assignmentTargetRelation.getRelations())){
				combinedRelationList.add(assignmentTargetRelation);
			} else {
				assignmentTargetRelation.getRelations().forEach(relation -> {
					AssignmentObjectRelation relationObj = new AssignmentObjectRelation();
					relationObj.setObjectTypes(assignmentTargetRelation.getObjectTypes());
					relationObj.setRelations(Arrays.asList(relation));
					relationObj.setArchetypeRefs(assignmentTargetRelation.getArchetypeRefs());
					relationObj.setDescription(assignmentTargetRelation.getDescription());
					combinedRelationList.add(relationObj);
				});
			}
		});
		return combinedRelationList;
	}

	/**
	 * The idea is to divide the list of AssignmentObjectRelation objects in such way that each AssignmentObjectRelation
	 * in the list will contain not more than 1 relation, not more than 1 object type and not more than one archetype reference.
	 * This will simplify creating of a new_assignment_button
	 *
	 * @param initialAssignmentRelationsList
	 * @return
	 */
	public static List<AssignmentObjectRelation> divideAssignmentRelationsByAllValues(List<AssignmentObjectRelation> initialAssignmentRelationsList){
		if (initialAssignmentRelationsList == null){
			return null;
		}
		List<AssignmentObjectRelation> dividedByRelationList = divideAssignmentRelationsByRelationValue(initialAssignmentRelationsList);
		List<AssignmentObjectRelation> resultList = new ArrayList<>();
		dividedByRelationList.forEach(assignmentObjectRelation -> {
			if (CollectionUtils.isNotEmpty(assignmentObjectRelation.getObjectTypes())){
				assignmentObjectRelation.getObjectTypes().forEach(objectType -> {
					if (CollectionUtils.isNotEmpty(assignmentObjectRelation.getArchetypeRefs())){
						assignmentObjectRelation.getArchetypeRefs().forEach(archetypeRef -> {
							AssignmentObjectRelation newRelation = new AssignmentObjectRelation();
							newRelation.setObjectTypes(Arrays.asList(objectType));
							newRelation.setRelations(assignmentObjectRelation.getRelations());
							newRelation.setArchetypeRefs(Arrays.asList(archetypeRef));
							newRelation.setDescription(assignmentObjectRelation.getDescription());
							resultList.add(newRelation);
						});
					} else {
						AssignmentObjectRelation newRelation = new AssignmentObjectRelation();
						newRelation.setObjectTypes(Arrays.asList(objectType));
						newRelation.setRelations(assignmentObjectRelation.getRelations());
						newRelation.setArchetypeRefs(assignmentObjectRelation.getArchetypeRefs());
						newRelation.setDescription(assignmentObjectRelation.getDescription());
						resultList.add(newRelation);
					}
				});
			} else {
				if (CollectionUtils.isNotEmpty(assignmentObjectRelation.getArchetypeRefs())){
					assignmentObjectRelation.getArchetypeRefs().forEach(archetypeRef -> {
						AssignmentObjectRelation newRelation = new AssignmentObjectRelation();
						newRelation.setObjectTypes(assignmentObjectRelation.getObjectTypes());
						newRelation.setRelations(assignmentObjectRelation.getRelations());
						newRelation.setArchetypeRefs(Arrays.asList(archetypeRef));
						newRelation.setDescription(assignmentObjectRelation.getDescription());
						resultList.add(newRelation);
					});
				} else {
					AssignmentObjectRelation newRelation = new AssignmentObjectRelation();
					newRelation.setObjectTypes(assignmentObjectRelation.getObjectTypes());
					newRelation.setRelations(assignmentObjectRelation.getRelations());
					newRelation.setArchetypeRefs(assignmentObjectRelation.getArchetypeRefs());
					newRelation.setDescription(assignmentObjectRelation.getDescription());
					resultList.add(newRelation);
				}
			}
		});
		return resultList;
	}

	public static DisplayType getAssignmentObjectRelationDisplayType(PageBase pageBase, AssignmentObjectRelation assignmentTargetRelation,
																	 String defaultTitleKey){
		if (assignmentTargetRelation == null){
			return createDisplayType("", "", pageBase.createStringResource(defaultTitleKey, "", "").getString());
		}

		String typeTitle = "";
		if (CollectionUtils.isNotEmpty(assignmentTargetRelation.getArchetypeRefs())){
			OperationResult result = new OperationResult(pageBase.getClass().getSimpleName() + "." + "loadArchetypeObject");
			try {
				ArchetypeType archetype = pageBase.getModelObjectResolver().resolve(assignmentTargetRelation.getArchetypeRefs().get(0), ArchetypeType.class,
						null, null, pageBase.createSimpleTask(result.getOperation()), result);
				if (archetype != null) {
					DisplayType archetypeDisplayType = archetype.getArchetypePolicy() != null ? archetype.getArchetypePolicy().getDisplay() : null;
					String archetypeTooltip = archetypeDisplayType != null && archetypeDisplayType.getLabel() != null &&
							StringUtils.isNotEmpty(archetypeDisplayType.getLabel().getOrig()) ?
							archetypeDisplayType.getLabel().getOrig() :
							(archetype.getName() != null && StringUtils.isNotEmpty(archetype.getName().getOrig()) ?
							archetype.getName().getOrig() : null);
					typeTitle = StringUtils.isNotEmpty(archetypeTooltip) ?
							pageBase.createStringResource("abstractRoleMemberPanel.withType", archetypeTooltip).getString() : "";
				}
			} catch (Exception ex){
				LOGGER.error("Couldn't load archetype object. " + ex.getLocalizedMessage());
			}
		} else if (CollectionUtils.isNotEmpty(assignmentTargetRelation.getObjectTypes())){
			QName type = !CollectionUtils.isEmpty(assignmentTargetRelation.getObjectTypes()) ?
					assignmentTargetRelation.getObjectTypes().get(0) : null;
			String typeName = type != null ? pageBase.createStringResource("ObjectTypeLowercase." + type.getLocalPart()).getString() : null;
			typeTitle = StringUtils.isNotEmpty(typeName) ?
					pageBase.createStringResource("abstractRoleMemberPanel.withType", typeName).getString() : "";
		}


		QName relation = !CollectionUtils.isEmpty(assignmentTargetRelation.getRelations()) ?
				assignmentTargetRelation.getRelations().get(0) : null;

		String relationValue = "";
		String relationTitle = "";
		if (relation != null){
			RelationDefinitionType def = WebComponentUtil.getRelationDefinition(relation);
			if (def != null){
				DisplayType displayType = null;
				if (def.getDisplay() == null){
					displayType = new DisplayType();
				} else {
					displayType = createDisplayType(def.getDisplay().getCssClass());
					if (def.getDisplay().getIcon() != null){
						displayType.setIcon(new IconType());
						displayType.getIcon().setCssClass(def.getDisplay().getIcon().getCssClass());
						displayType.getIcon().setColor(def.getDisplay().getIcon().getColor());
					}
				}
				if (displayType.getLabel() != null && StringUtils.isNotEmpty(displayType.getLabel().getOrig())){
					relationValue = pageBase.createStringResource(displayType.getLabel().getOrig()).getString();
				} else {
					String relationKey = "RelationTypes." + RelationTypes.getRelationTypeByRelationValue(relation);
					relationValue = pageBase.createStringResource(relationValue).getString();
					if (StringUtils.isEmpty(relationValue) || relationKey.equals(relationValue)){
						relationValue = relation.getLocalPart();
					}
				}

				relationTitle = pageBase.createStringResource("abstractRoleMemberPanel.withRelation", relationValue).getString();


				if (displayType.getIcon() == null || StringUtils.isEmpty(displayType.getIcon().getCssClass())){
					displayType.setIcon(createIconType(""));
				}
				displayType.setTooltip(createPolyFromOrigString(pageBase.createStringResource(defaultTitleKey, typeTitle, relationTitle).getString()));
				return displayType;
			}
		}
		return createDisplayType(GuiStyleConstants.EVO_ASSIGNMENT_ICON, "green", pageBase.createStringResource(defaultTitleKey, typeTitle, relationTitle).getString());
	}

	public static void saveTask(PrismObject<TaskType> oldTask, OperationResult result, PageBase pageBase){
		Task task = pageBase.createSimpleTask(pageBase.getClass().getName() + "." + "saveSyncTask");

		PrismProperty property = oldTask.findProperty(ItemPath.create(TaskType.F_EXTENSION, SchemaConstants.SYNC_TOKEN));

		if(property == null){
			return;
		}
		Object value = property.getRealValue();

		ObjectDelta<TaskType> delta = pageBase.getPrismContext().deltaFactory().object().createModifyDelta(oldTask.getOid(),
				pageBase.getPrismContext().deltaFactory().property()
						.createModificationDeleteProperty(ItemPath.create(TaskType.F_EXTENSION, SchemaConstants.SYNC_TOKEN), property.getDefinition(), value),
				TaskType.class);

		if(LOGGER.isTraceEnabled()){
			LOGGER.trace(delta.debugDump());
		}

		try {
			pageBase.getModelService().executeChanges(WebComponentUtil.createDeltaCollection(delta), null, task, result);
		} catch (Exception e){
			LoggingUtils.logUnexpectedException(LOGGER, "Couldn't save task.", e);
			result.recordFatalError("Couldn't save task.", e);
		}
		result.recomputeStatus();
	}

	public static String getLocalizedOrOriginPolyStringValue(PolyString polyString){
		String value = getLocalizedPolyStringValue(polyString);
		if(value == null) {
			return getOrigStringFromPoly(polyString);
		}
		return value;
	}

	public static String getLocalizedPolyStringValue(PolyString polyString){
		if (polyString == null){
			return null;
		}
		if ((polyString.getTranslation() == null || StringUtils.isEmpty(polyString.getTranslation().getKey())) &&
				(polyString.getLang() == null || polyString.getLang().isEmpty())){
			return null;
		}
		if (polyString.getLang() != null && !polyString.getLang().isEmpty()){
			//check if it's really selected by user or configured through sysconfig locale
			String currentLocale = getCurrentLocale().getLanguage();
			for (String language : polyString.getLang().keySet()){
				if (currentLocale.equals(language)){
					return polyString.getLang().get(language);
				}
			}
		}

		if (polyString.getTranslation() != null && StringUtils.isNotEmpty(polyString.getTranslation().getKey())){
			List<String> argumentValues = new ArrayList<>();
			polyString.getTranslation().getArgument().forEach(argument -> {
				String argumentValue = "";
				String translationValue = "";
				if (argument.getTranslation() != null){
					String argumentKey = argument.getTranslation().getKey();
					String valueByKey = StringUtils.isNotEmpty(argumentKey) ? new StringResourceModel(argumentKey).getString() : null;
					translationValue = StringUtils.isNotEmpty(valueByKey) ? valueByKey : argument.getTranslation().getFallback();
				}
				argumentValue = StringUtils.isNotEmpty(translationValue) ? translationValue : argument.getValue();
				argumentValues.add(argumentValue);
			});
			return new StringResourceModel(polyString.getTranslation().getKey())
					.setDefaultValue(polyString.getTranslation().getKey())
					.setModel(new Model<String>())
					.setParameters(argumentValues.toArray())
					.getString();
		}
		return null;
	}

    public static <T> List<T> sortDropDownChoices(IModel<? extends List<? extends T>> choicesModel, IChoiceRenderer<T> renderer){
        List<T> sortedList = choicesModel.getObject().stream().sorted((choice1, choice2) -> {
            if (choice1 == null || choice2 == null){
            	return 0;
			}
            return String.CASE_INSENSITIVE_ORDER.compare(renderer.getDisplayValue(choice1).toString(), renderer.getDisplayValue(choice2).toString());


        }).collect(Collectors.toList());
        return sortedList;
    }

    public static SceneDto createSceneDto(CaseWorkItemType caseWorkItem, PageBase pageBase, String operation){
    	if (caseWorkItem == null){
    		return null;
		}
		return createSceneDto(CaseTypeUtil.getCase(caseWorkItem), pageBase, operation);
	}

	public static List<EvaluatedTriggerGroupDto> computeTriggers(ApprovalContextType wfc, Integer stage) {
		List<EvaluatedTriggerGroupDto> triggers = new ArrayList<>();
		if (wfc == null) {
			return triggers;
		}
		EvaluatedTriggerGroupDto.UniquenessFilter uniquenessFilter = new EvaluatedTriggerGroupDto.UniquenessFilter();
		List<List<EvaluatedPolicyRuleType>> rulesPerStageList = ApprovalContextUtil.getRulesPerStage(wfc);
		for (int i = 0; i < rulesPerStageList.size(); i++) {
			Integer stageNumber = i + 1;
			boolean highlighted = stageNumber.equals(stage);
			EvaluatedTriggerGroupDto group = EvaluatedTriggerGroupDto.initializeFromRules(rulesPerStageList.get(i), highlighted, uniquenessFilter);
			triggers.add(group);
		}
		return triggers;
	}

	public static SceneDto createSceneDto(CaseType caseObject, PageBase pageBase, String operation){
		if (caseObject == null || caseObject.getApprovalContext() == null) {
			return null;
		}
		ObjectReferenceType objectRef = caseObject.getObjectRef();

		OperationResult result = new OperationResult(operation);
		Task task = pageBase.createSimpleTask(operation);
		try {
			Scene deltasScene = SceneUtil.visualizeObjectTreeDeltas(caseObject.getApprovalContext().getDeltasToApprove(), "pageWorkItem.delta",
					pageBase.getPrismContext(), pageBase.getModelInteractionService(), objectRef, task, result);
			return new SceneDto(deltasScene);
		} catch (SchemaException | ExpressionEvaluationException ex){
			LOGGER.error("Unable to create delta visualization for case  " + caseObject.getName(), ex.getLocalizedMessage());
		}
		return null;
	}

	@NotNull
	public static List<SceneDto> computeChangesCategorizationList(ChangesByState changesByState, ObjectReferenceType objectRef,
																  ModelInteractionService modelInteractionService, PrismContext prismContext, Task opTask,
																  OperationResult thisOpResult) throws SchemaException, ExpressionEvaluationException {
		List<SceneDto> changes = new ArrayList<>();
		if (!changesByState.getApplied().isEmpty()) {
			changes.add(createTaskChangesDto("TaskDto.changesApplied", "box-solid box-success", changesByState.getApplied(),
					modelInteractionService, prismContext, objectRef, opTask, thisOpResult));
		}
		if (!changesByState.getBeingApplied().isEmpty()) {
			changes.add(createTaskChangesDto("TaskDto.changesBeingApplied", "box-solid box-info", changesByState.getBeingApplied(),
					modelInteractionService, prismContext, objectRef, opTask, thisOpResult));
		}
		if (!changesByState.getWaitingToBeApplied().isEmpty()) {
			changes.add(createTaskChangesDto("TaskDto.changesWaitingToBeApplied", "box-solid box-warning",
					changesByState.getWaitingToBeApplied(), modelInteractionService, prismContext, objectRef, opTask, thisOpResult));
		}
		if (!changesByState.getWaitingToBeApproved().isEmpty()) {
			changes.add(createTaskChangesDto("TaskDto.changesWaitingToBeApproved", "box-solid box-primary",
					changesByState.getWaitingToBeApproved(), modelInteractionService, prismContext, objectRef, opTask, thisOpResult));
		}
		if (!changesByState.getRejected().isEmpty()) {
			changes.add(createTaskChangesDto("TaskDto.changesRejected", "box-solid box-danger", changesByState.getRejected(),
					modelInteractionService, prismContext, objectRef, opTask, thisOpResult));
		}
		if (!changesByState.getCanceled().isEmpty()) {
			changes.add(createTaskChangesDto("TaskDto.changesCanceled", "box-solid box-danger", changesByState.getCanceled(),
					modelInteractionService, prismContext, objectRef, opTask, thisOpResult));
		}
		return changes;
	}

	private static SceneDto createTaskChangesDto(String titleKey, String boxClassOverride, ObjectTreeDeltas deltas, ModelInteractionService modelInteractionService,
													   PrismContext prismContext, ObjectReferenceType objectRef, Task opTask, OperationResult result) throws SchemaException, ExpressionEvaluationException {
		ObjectTreeDeltasType deltasType = ObjectTreeDeltas.toObjectTreeDeltasType(deltas);
		Scene scene = SceneUtil.visualizeObjectTreeDeltas(deltasType, titleKey, prismContext, modelInteractionService, objectRef, opTask, result);
		SceneDto sceneDto = new SceneDto(scene);
		sceneDto.setBoxClassOverride(boxClassOverride);
		return sceneDto;
	}

	public static String getMidpointCustomSystemName(PageBase pageBase, String defaultSystemNameKey){
		DeploymentInformationType deploymentInfo = MidPointApplication.get().getDeploymentInfo();
		String subscriptionId = deploymentInfo != null ? deploymentInfo.getSubscriptionIdentifier() : null;
		if (!isSubscriptionIdCorrect(subscriptionId) ||
				SubscriptionType.DEMO_SUBSRIPTION.getSubscriptionType().equals(subscriptionId.substring(0, 2))) {
			return pageBase.createStringResource(defaultSystemNameKey).getString();
		}
		return deploymentInfo != null && StringUtils.isNotEmpty(deploymentInfo.getSystemName()) ?
				deploymentInfo.getSystemName() : pageBase.createStringResource(defaultSystemNameKey).getString();
	}

    public static IModel<String> getResourceLabelModel(ShadowType shadow, PageBase pageBase){
    	return pageBase.createStringResource("DisplayNamePanel.resource",
				WebComponentUtil.getReferencedObjectDisplayNamesAndNames(shadow.getResourceRef(), false));
    }

    public static IModel<String> getResourceAttributesLabelModel(ShadowType shadow, PageBase pageBase){
    	StringBuilder sb = new StringBuilder();
		if(shadow != null) {
			if(shadow.getObjectClass() != null && !StringUtils.isBlank(shadow.getObjectClass().getLocalPart())) {
				sb.append(pageBase.createStringResource("DisplayNamePanel.objectClass", shadow.getObjectClass().getLocalPart()).getString());
			}
			if(shadow.getKind() != null && !StringUtils.isBlank(shadow.getKind().name())) {
				sb.append(", ");
				sb.append(pageBase.createStringResource("DisplayNamePanel.kind", shadow.getKind().name()).getString());
			}

			if(!StringUtils.isBlank(shadow.getIntent())) {
				sb.append(", ");
				sb.append(pageBase.createStringResource("DisplayNamePanel.intent", shadow.getIntent()).getString());
			}

			if(!StringUtils.isBlank(shadow.getTag())) {
				sb.append(", ");
				sb.append(pageBase.createStringResource("DisplayNamePanel.tag", shadow.getTag()).getString());
			}
			return Model.of(sb.toString());
		}
		return Model.of("");
    }


    public static String getObjectListPageStorageKey(String additionalKeyValue){
        if (StringUtils.isEmpty(additionalKeyValue)){
            return SessionStorage.KEY_OBJECT_LIST;
        }
        return SessionStorage.KEY_OBJECT_LIST + "." + additionalKeyValue;
    }

}
