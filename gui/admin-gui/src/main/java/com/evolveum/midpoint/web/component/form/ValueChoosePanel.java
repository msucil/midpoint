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
package com.evolveum.midpoint.web.component.form;

import java.util.ArrayList; 
import java.util.List;

import javax.xml.namespace.QName;

import com.evolveum.midpoint.prism.polystring.PolyString;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.form.AjaxFormComponentUpdatingBehavior;
import org.apache.wicket.ajax.markup.html.AjaxLink;
import org.apache.wicket.behavior.AttributeAppender;
import org.apache.wicket.feedback.ComponentFeedbackMessageFilter;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.markup.html.panel.FeedbackPanel;
import org.apache.wicket.model.IModel;

import com.evolveum.midpoint.gui.api.component.BasePanel;
import com.evolveum.midpoint.gui.api.component.ObjectBrowserPanel;
import com.evolveum.midpoint.gui.api.util.WebComponentUtil;
import com.evolveum.midpoint.prism.Referencable;
import com.evolveum.midpoint.prism.query.ObjectFilter;
import com.evolveum.midpoint.prism.query.ObjectQuery;
import com.evolveum.midpoint.schema.util.ObjectTypeUtil;
import com.evolveum.midpoint.util.MiscUtil;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.web.component.util.VisibleEnableBehaviour;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ObjectReferenceType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ObjectType;

/**
 *
 * TODO: rename to ValueObjectChoicePanel, PrismValueObjectSelectorPanel or
 * something better
 *
 * @param <T>
 * @param <O>
 *            common superclass for all the options of objects that this panel
 *            should choose
 */
public class ValueChoosePanel<R extends Referencable> extends BasePanel<R> {

	private static final long serialVersionUID = 1L;

	private static final Trace LOGGER = TraceManager.getTrace(ValueChoosePanel.class);
	private static final String DOT_CLASS = ValueChoosePanel.class.getName() + ".";
	protected static final String OPERATION_LOAD_REFERENCE_OBJECT_DISPLAY_NAME = DOT_CLASS + "loadReferenceObjectDisplayName";

	private static final String ID_TEXT_WRAPPER = "textWrapper";
	private static final String ID_TEXT = "text";
	private static final String ID_FEEDBACK = "feedback";
	private static final String ID_EDIT = "edit";
	
	public ValueChoosePanel(String id, IModel<R> value) {
		super(id, value);
		setOutputMarkupId(true);		
	}
	
	@Override
	protected void onInitialize() {
		super.onInitialize();
		
		WebMarkupContainer textWrapper = new WebMarkupContainer(ID_TEXT_WRAPPER);

		textWrapper.setOutputMarkupId(true);

		IModel<String> textModel = createTextModel();
		TextField<String> text = new TextField<>(ID_TEXT, textModel);
		text.add(new AjaxFormComponentUpdatingBehavior("blur") {
			private static final long serialVersionUID = 1L;

			@Override
			protected void onUpdate(AjaxRequestTarget ajaxRequestTarget) {
			}
		});
		text.add(AttributeAppender.append("title", textModel));
		text.setRequired(isRequired());
		text.setEnabled(false);
		textWrapper.add(text);

		FeedbackPanel feedback = new FeedbackPanel(ID_FEEDBACK, new ComponentFeedbackMessageFilter(text));
		textWrapper.add(feedback);

		AjaxLink<String> edit = new AjaxLink<String>(ID_EDIT) {
			private static final long serialVersionUID = 1L;

			@Override
			public void onClick(AjaxRequestTarget target) {
				editValuePerformed(target);
			}
		};
		
		edit.add(new VisibleEnableBehaviour() {
			
			private static final long serialVersionUID = 1L;
			@Override
			public boolean isEnabled() {
				return isEditButtonEnabled();
			}
		});
		textWrapper.add(edit);
		add(textWrapper);

        initButtons();
    }
    
    protected boolean isEditButtonEnabled() {
    	return true;
    }

	protected void replaceIfEmpty(ObjectType object) {
		ObjectReferenceType ort = ObjectTypeUtil.createObjectRef(object, getPageBase().getPrismContext());
		getModel().setObject((R) ort);

	}

	protected ObjectQuery createChooseQuery() {
		ArrayList<String> oidList = new ArrayList<>();
		ObjectQuery query = getPrismContext().queryFactory().createQuery();
		// TODO we should add to filter currently displayed value
		// not to be displayed on ObjectSelectionPanel instead of saved value
		
		if (oidList.isEmpty()) {
			ObjectFilter customFilter = createCustomFilter();
			if (customFilter != null) {
				query.addFilter(customFilter);
				return query;
			}
			
			return null;
			
		}

		ObjectFilter oidFilter = getPrismContext().queryFactory().createInOid(oidList);
		query.setFilter(getPrismContext().queryFactory().createNot(oidFilter));

		ObjectFilter customFilter = createCustomFilter();
		if (customFilter != null) {
			query.addFilter(customFilter);
		}
		
		return query;
	}
	
	protected boolean isRequired() {
		return false;
	}
	
	protected ObjectFilter createCustomFilter() {
		return null;
	}

	/**
	 * @return css class for off-setting other values (not first, left to the
	 *         first there is a label)
	 */
	protected String getOffsetClass() {
		return "col-md-offset-4";
	}

	protected IModel<String> createTextModel() {
		final IModel<R> model = getModel();
		return new IModel<String>() {

			private static final long serialVersionUID = 1L;

			@Override
			public String getObject() {
				R prv = model.getObject();

//				if (ort instanceof PrismReferenceValue) {
//					PrismReferenceValue prv = (PrismReferenceValue) ort;
//					return prv == null ? null
//							: (prv.getTargetName() != null
//									? (prv.getTargetName().getOrig() + (prv.getTargetType() != null
//											? ": " + prv.getTargetType().getLocalPart() : ""))
//									: prv.getOid());
//				} else if (ort instanceof ObjectReferenceType) {
//					Referencable prv = (Referencable) ort;
					return prv == null ? null
							: (prv.getTargetName() != null ? (prv.getTargetName().getOrig()
									+ (prv.getType() != null ? ": " + prv.getType().getLocalPart() : ""))
									: prv.getOid());
//				} else if (ort instanceof ObjectViewDto) {
//					return ((ObjectViewDto) ort).getName();
//				}
//				return ort != null ? ort.toString() : null;
//=======
//				T ort = (T) model.getObject();
//				if (ort == null){
//					return createStringResource("ValueChoosePanel.undefined").getString();
//				}
//
//				if (ort instanceof PrismReferenceValue) {
//					PrismReferenceValue prv = (PrismReferenceValue) ort;
//					if (StringUtils.isEmpty(prv.getOid())){
//						return createStringResource("ValueChoosePanel.undefined").getString();
//					}
//					ObjectReferenceType objectReferenceType = new ObjectReferenceType();
//					objectReferenceType.setupReferenceValue((PrismReferenceValue) ort);
//					String targetObjectName = WebComponentUtil.getName(objectReferenceType,
//							ValueChoosePanel.this.getPageBase(), OPERATION_LOAD_REFERENCE_OBJECT_DISPLAY_NAME);
//					return StringUtils.isNotEmpty(targetObjectName)
//									? (targetObjectName + (prv.getTargetType() != null ? ": " + prv.getTargetType().getLocalPart() : ""))
//									: prv.getOid();
//				} else if (ort instanceof ObjectReferenceType) {
//					ObjectReferenceType prv = (ObjectReferenceType) ort;
//					if (StringUtils.isEmpty(prv.getOid())){
//						return createStringResource("ValueChoosePanel.undefined").getString();
//					}
//					String targetObjectName = WebComponentUtil.getName(prv,
//							ValueChoosePanel.this.getPageBase(), OPERATION_LOAD_REFERENCE_OBJECT_DISPLAY_NAME);
//
//					return StringUtils.isNotEmpty(targetObjectName) ?
//							(targetObjectName + (prv.getType() != null ? ": " + prv.getType().getLocalPart() : ""))
//							: prv.getOid();
//				} else if (ort instanceof ObjectViewDto) {
//					return ((ObjectViewDto) ort).getName();
//				}
//				return ort != null ? ort.toString() : null;
//>>>>>>> origin/master

			}
		};
	}

	protected <O extends ObjectType> void editValuePerformed(AjaxRequestTarget target) {
		List<QName> supportedTypes = getSupportedTypes();
		ObjectFilter filter = createChooseQuery() == null ? null
				: createChooseQuery().getFilter();
		if (CollectionUtils.isEmpty(supportedTypes)){
			supportedTypes = WebComponentUtil.createObjectTypeList();
		}
		Class<O> defaultType = getDefaultType(supportedTypes);
		ObjectBrowserPanel<O> objectBrowserPanel = new ObjectBrowserPanel<O>(
				getPageBase().getMainPopupBodyId(), defaultType, supportedTypes, false, getPageBase(),
				filter) {
			private static final long serialVersionUID = 1L;

			@Override
			protected void onSelectPerformed(AjaxRequestTarget target, O object) {
				getPageBase().hideMainPopup(target);
				ValueChoosePanel.this.choosePerformed(target, object);
			}

		};

		getPageBase().showMainPopup(objectBrowserPanel, target);

	}
	
	public List<QName> getSupportedTypes() {
		return WebComponentUtil.createObjectTypeList();
	}

	protected <O extends ObjectType> Class<O> getDefaultType(List<QName> supportedTypes){
		return (Class<O>) WebComponentUtil.qnameToClass(getPageBase().getPrismContext(), supportedTypes.iterator().next());
	}

	
	/*
	 * TODO - this method contains check, if chosen object already is not in
	 * selected values array This is a temporary solution until we well be able
	 * to create "already-chosen" query
	 */
	protected <O extends ObjectType> void choosePerformed(AjaxRequestTarget target, O object) {
		choosePerformedHook(target, object);

		if (isObjectUnique(object)) {
			replaceIfEmpty(object);
		}

		if (LOGGER.isTraceEnabled()) {
			LOGGER.trace("New object instance has been added to the model.");
		}
		target.add(getTextWrapperComponent());
	}

    public WebMarkupContainer getTextWrapperComponent(){
        return (WebMarkupContainer)get(ID_TEXT_WRAPPER);
    }

    protected void initButtons() {
    }

    protected <O extends ObjectType> boolean isObjectUnique(O object) {

		Referencable old = getModelObject();
//		if (modelObject instanceof PrismReferenceValue) {
//
//			PrismReferenceValue old = (PrismReferenceValue) modelObject;
//			if (old == null || old.isEmpty()) {
//				return true;
//			}
//
//			return !old.getOid().equals(object.getOid());
//		} else if (modelObject instanceof ObjectReferenceType) {
//			ObjectReferenceType old = (ObjectReferenceType) modelObject;
			if (old == null) {
				return true;
			}
			return !MiscUtil.equals(old.getOid(),object.getOid());
//		}
//
//		return true;

	}

	/**
	 * A custom code in form of hook that can be run on event of choosing new
	 * object with this chooser component
	 */
	protected <O extends ObjectType> void choosePerformedHook(AjaxRequestTarget target, O object) {
	}

}
