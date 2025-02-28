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
package com.evolveum.midpoint.web.component;

import com.evolveum.midpoint.gui.api.component.BasePanel;
import com.evolveum.midpoint.gui.api.util.WebComponentUtil;
import com.evolveum.midpoint.gui.impl.model.FlexibleLabelModel;
import com.evolveum.midpoint.prism.Containerable;
import com.evolveum.midpoint.prism.PrismContainer;
import com.evolveum.midpoint.prism.path.ItemName;
import com.evolveum.midpoint.repo.common.expression.ExpressionVariables;
import com.evolveum.midpoint.web.component.util.SummaryTag;
import com.evolveum.midpoint.web.component.util.VisibleBehaviour;
import com.evolveum.midpoint.web.component.util.VisibleEnableBehaviour;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.wicket.AttributeModifier;
import org.apache.wicket.Component;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.image.NonCachingImage;
import org.apache.wicket.markup.repeater.RepeatingView;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.request.resource.AbstractResource;

import javax.xml.namespace.QName;
import java.util.ArrayList;
import java.util.List;

/**
 * @author semancik
 * @author mederly
 */
public abstract class AbstractSummaryPanel<C extends Containerable> extends BasePanel<C> {
	private static final long serialVersionUID = 1L;

	protected static final String ID_BOX = "summaryBox";
    protected static final String ID_ICON_BOX = "summaryIconBox";
	protected static final String ID_TAG_BOX = "summaryTagBox";
	protected static final String ID_SUMMARY_TAG = "summaryTag";
    protected static final String ID_ICON = "summaryIcon";
    protected static final String ID_DISPLAY_NAME = "summaryDisplayName";
    protected static final String ID_IDENTIFIER = "summaryIdentifier";
    protected static final String ID_IDENTIFIER_PANEL = "summaryIdentifierPanel";
    protected static final String ID_TITLE = "summaryTitle";
    protected static final String ID_TITLE2 = "summaryTitle2";
    protected static final String ID_TITLE3 = "summaryTitle3";

    protected static final String ID_PHOTO = "summaryPhoto";                  // perhaps useful only for focal objects but it was simpler to include it here
    protected static final String ID_ORGANIZATION = "summaryOrganization";    // similar (requires ObjectWrapper to get parent organizations so hard to use in ObjectSummaryPanel)

    protected static final String BOX_CSS_CLASS = "col-xs-12 info-box";
    protected static final String ICON_BOX_CSS_CLASS = "info-box-icon";
    protected static final String ARCHETYPE_ICON_FONT_SIZE = "font-size: 45px !important;";

    protected SummaryPanelSpecificationType configuration;

    protected WebMarkupContainer box;
    protected RepeatingView tagBox;
    protected WebMarkupContainer iconBox;

    public AbstractSummaryPanel(String id, IModel<C> model, SummaryPanelSpecificationType configuration) {
        super(id, model);
        this.configuration = configuration;
        setOutputMarkupId(true);
    }

    @Override
    protected void onInitialize() {
    	super.onInitialize();

        box = new WebMarkupContainer(ID_BOX);
        add(box);

		String archetypePolicyAdditionalCssClass = getArchetypePolicyAdditionalCssClass();
		box.add(new AttributeModifier("class", BOX_CSS_CLASS + " " + getBoxAdditionalCssClass()));
		if (StringUtils.isNotEmpty(archetypePolicyAdditionalCssClass)){
			box.add(AttributeModifier.append("style", "border-color: " + archetypePolicyAdditionalCssClass + ";"));
		}

	    if (getDisplayNameModel() != null) {
		    box.add(new Label(ID_DISPLAY_NAME, getDisplayNameModel()));
	    } else if (getDisplayNamePropertyName() != null) {
		    box.add(new Label(ID_DISPLAY_NAME, createLabelModel(getDisplayNamePropertyName(), SummaryPanelSpecificationType.F_DISPLAY_NAME)));
	    } else {
		    box.add(new Label(ID_DISPLAY_NAME, " "));
	    }

        WebMarkupContainer identifierPanel = new WebMarkupContainer(ID_IDENTIFIER_PANEL);
        identifierPanel.add(new Label(ID_IDENTIFIER, createLabelModel(getIdentifierPropertyName(), SummaryPanelSpecificationType.F_IDENTIFIER)));
        identifierPanel.add(new VisibleEnableBehaviour() {
			private static final long serialVersionUID = 1L;

			@Override
            public boolean isVisible() {
                return isIdentifierVisible();
            }
        });
        box.add(identifierPanel);

	    if (getTitleModel() != null) {
		    box.add(new Label(ID_TITLE, getTitleModel()));
	    } else if (getTitlePropertyName() != null) {
        	box.add(new Label(ID_TITLE, createLabelModel(getTitlePropertyName(), SummaryPanelSpecificationType.F_TITLE_1)));
        } else {
            box.add(new Label(ID_TITLE, " "));
        }

	    if (getTitle2Model() != null) {
		    box.add(new Label(ID_TITLE2, getTitle2Model()));
	    } else if (getTitle2PropertyName() != null) {
        	box.add(new Label(ID_TITLE, createLabelModel(getTitle2PropertyName(), SummaryPanelSpecificationType.F_TITLE_2)));
        } else {
            Label label = new Label(ID_TITLE2, " ");
            label.setVisible(false);
            box.add(label);
        }

	    if (getTitle3Model() != null) {
		    box.add(new Label(ID_TITLE3, getTitle3Model()));
	    } else if (getTitle3PropertyName() != null) {
			box.add(new Label(ID_TITLE, createLabelModel(getTitle3PropertyName(), SummaryPanelSpecificationType.F_TITLE_3)));
		} else {
			Label label = new Label(ID_TITLE3, " ");
			label.setVisible(false);
			box.add(label);
		}

		final IModel<String> parentOrgModel = getParentOrgModel();
        Label parentOrgLabel = new Label(ID_ORGANIZATION, parentOrgModel);
        parentOrgLabel.add(new VisibleEnableBehaviour() {
        	private static final long serialVersionUID = 1L;
            @Override
            public boolean isVisible() {
                return parentOrgModel.getObject() != null;
            }
        });
        box.add(parentOrgLabel);

        iconBox = new WebMarkupContainer(ID_ICON_BOX);
        box.add(iconBox);

        String iconAdditionalCssClass = getIconBoxAdditionalCssClass();
		if (StringUtils.isNotEmpty(iconAdditionalCssClass)) {
			iconBox.add(new AttributeModifier("class", ICON_BOX_CSS_CLASS + " " + iconAdditionalCssClass));
		}
        if (StringUtils.isNotEmpty(archetypePolicyAdditionalCssClass)){
        	iconBox.add(AttributeModifier.append("style", "background-color: " + archetypePolicyAdditionalCssClass + ";"));
		}

        Label icon = new Label(ID_ICON, "");

        String archetypeIconCssClass = getArchetypeIconCssClass();
        if (StringUtils.isNotEmpty(archetypeIconCssClass)){
			icon.add(AttributeModifier.append("class", archetypeIconCssClass));
			icon.add(AttributeModifier.append("style", ARCHETYPE_ICON_FONT_SIZE));
		} else {
			icon.add(AttributeModifier.append("class", getIconCssClass()));
		}
        icon.add(new VisibleEnableBehaviour() {
            @Override
            public boolean isVisible(){
                return getPhotoModel().getObject() == null;
            }
        });
        iconBox.add(icon);
        NonCachingImage img = new NonCachingImage(ID_PHOTO, getPhotoModel());
        img.add(new VisibleEnableBehaviour(){
			private static final long serialVersionUID = 1L;
			@Override
            public boolean isVisible() {
                return getPhotoModel().getObject() != null;
            }
        });
        iconBox.add(img);

		tagBox = new RepeatingView(ID_TAG_BOX);
		List<SummaryTag<C>> summaryTags = getSummaryTagComponentList();

		if (getArchetypeSummaryTag() != null){
			summaryTags.add(getArchetypeSummaryTag());
		}
		summaryTags.forEach(summaryTag -> {
			WebMarkupContainer summaryTagPanel = new WebMarkupContainer(tagBox.newChildId());
			summaryTagPanel.setOutputMarkupId(true);

			summaryTagPanel.add(summaryTag);
			tagBox.add(summaryTagPanel);
		});
		if (getTagBoxCssClass() != null) {
			tagBox.add(new AttributeModifier("class", getTagBoxCssClass()));
		}
		tagBox.add(new VisibleBehaviour(() -> CollectionUtils.isNotEmpty(summaryTags)));
		box.add(tagBox);
    }

	private FlexibleLabelModel<C> createLabelModel(QName modelPropertyName, QName configurationPropertyName) {
		return createFlexibleLabelModel(modelPropertyName, getLabelConfiguration(configurationPropertyName));
	}

	private FlexibleLabelModel<C> createFlexibleLabelModel(QName modelPropertyName, GuiFlexibleLabelType configuration) {
		return new FlexibleLabelModel<C>(getModel(), ItemName.fromQName(modelPropertyName), getPageBase(), configuration) {
			private static final long serialVersionUID = 1L;
			@Override
			protected void addAdditionalExpressionVariables(ExpressionVariables variables) {
				super.addAdditionalExpressionVariables(variables);
				AbstractSummaryPanel.this.addAdditionalExpressionVariables(variables);
			}
		};
	}

	protected List<SummaryTag<C>> getSummaryTagComponentList(){
    	return new ArrayList<>();
	}

	private SummaryTag<C> getArchetypeSummaryTag(){
		String archetypeIconCssClass = getArchetypeIconCssClass();
		String archetypeIconColor = getArchetypePolicyAdditionalCssClass();
		String archetypeLabel = getArchetypeLabel();
		if (StringUtils.isNotEmpty(archetypeLabel)){
			SummaryTag<C> archetypeSummaryTag = new SummaryTag<C>(ID_SUMMARY_TAG, getModel()) {
				private static final long serialVersionUID = 1L;

				@Override
				protected void initialize(C object) {
						setIconCssClass(archetypeIconCssClass);
						setLabel(createStringResource(archetypeLabel).getString());
						setColor(archetypeIconColor);
				}
			};
			return archetypeSummaryTag;
		}
		return null;
	}

    protected void addAdditionalExpressionVariables(ExpressionVariables variables) {

    }

	private GuiFlexibleLabelType getLabelConfiguration(QName configurationPropertyName) {
		if (configuration == null) {
			return null;
		}
		@SuppressWarnings("unchecked")
		PrismContainer<GuiFlexibleLabelType> subContainer = configuration.asPrismContainerValue().findContainer(configurationPropertyName);
		if (subContainer == null) {
			return null;
		}
		return subContainer.getRealValue();
	}

	protected String getTagBoxCssClass() {
		return null;
	}

	public Component getTag(String id) {
		return tagBox.get(id);
	}

	private String getArchetypePolicyAdditionalCssClass(){
    	if (getModelObject() instanceof AssignmentHolderType){
			DisplayType displayType = WebComponentUtil.getArchetypePolicyDisplayType((AssignmentHolderType) getModelObject(), getPageBase());
			return WebComponentUtil.getIconColor(displayType);
		}
		return "";
	}

	private String getArchetypeLabel(){
		if (getModelObject() instanceof AssignmentHolderType){
			DisplayType displayType = WebComponentUtil.getArchetypePolicyDisplayType((AssignmentHolderType) getModelObject(), getPageBase());
			return displayType == null || displayType.getLabel() == null ? "" : displayType.getLabel().getOrig();
		}
		return "";
	}

	private String getArchetypeIconCssClass(){
		if (getModelObject() instanceof AssignmentHolderType){
			DisplayType displayType = WebComponentUtil.getArchetypePolicyDisplayType((AssignmentHolderType) getModelObject(), getPageBase());
			return WebComponentUtil.getIconCssClass(displayType);
		}
		return "";
	}

	protected abstract String getIconCssClass();

    protected abstract String getIconBoxAdditionalCssClass();

    protected abstract String getBoxAdditionalCssClass();

    protected QName getIdentifierPropertyName() {
        return FocusType.F_NAME;
    }

	protected QName getDisplayNamePropertyName() {
		return ObjectType.F_NAME;
	}

	protected IModel<String> getDisplayNameModel() {
		return null;
	}

	protected QName getTitlePropertyName() {
        return null;
    }

    protected IModel<String> getTitleModel() {
        return null;
    }

    protected QName getTitle2PropertyName() {
        return null;
    }

	protected IModel<String> getTitle2Model() {
		return null;
	}

	protected QName getTitle3PropertyName() {
		return null;
	}

	protected IModel<String> getTitle3Model() {
		return null;
	}

	protected boolean isIdentifierVisible() {
        return true;
    }

    protected IModel<String> getParentOrgModel() {
    	GuiFlexibleLabelType config = getLabelConfiguration(SummaryPanelSpecificationType.F_ORGANIZATION);
    	if (config != null) {
    		return createFlexibleLabelModel(ObjectType.F_PARENT_ORG_REF, config);
    	} else {
    		return getDefaltParentOrgModel();
    	}
    }

    protected IModel<String> getDefaltParentOrgModel() {
		return new Model<>(null);
	}

	protected IModel<AbstractResource> getPhotoModel() {
        return new Model<>(null);
    }

    protected WebMarkupContainer getSummaryBoxPanel(){
    	return (WebMarkupContainer) get(ID_BOX);
	}
}
