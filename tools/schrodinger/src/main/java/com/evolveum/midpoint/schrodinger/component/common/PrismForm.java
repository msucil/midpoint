/*
 * Copyright (c) 2010-2018 Evolveum
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

package com.evolveum.midpoint.schrodinger.component.common;

import com.codeborne.selenide.Condition;
import com.codeborne.selenide.ElementsCollection;
import com.codeborne.selenide.Selenide;
import com.codeborne.selenide.SelenideElement;
import com.evolveum.midpoint.schrodinger.MidPoint;
import com.evolveum.midpoint.schrodinger.component.Component;
import com.evolveum.midpoint.schrodinger.component.configuration.ObjectCollectionViewsPanel;
import com.evolveum.midpoint.schrodinger.util.Schrodinger;
import org.openqa.selenium.By;

import javax.xml.namespace.QName;

import java.io.File;

import static com.codeborne.selenide.Selenide.$;

/**
 * Created by Viliam Repan (lazyman).
 */
public class PrismForm<T> extends Component<T> {

    private static final String CARET_DOWN_ICON_STYLE = "fa-caret-down";

    public PrismForm(T parent, SelenideElement parentElement) {
        super(parent, parentElement);
    }

    public PrismForm<T> addAttributeValue(String name, String value) {
        SelenideElement property = findProperty(name);

        $(By.className("prism-properties")).waitUntil(Condition.appears,MidPoint.TIMEOUT_MEDIUM_6_S);

        ElementsCollection values = property.$$(By.className("prism-property-value"));
        if (values.size() == 1) {
            values.first().$(By.className("form-control")).setValue(value);
        }

        // todo implement
        return this;
    }

    public PrismForm<T> addProtectedAttributeValue(String protectedAttributeName, String value) {
        SelenideElement property = findProperty(protectedAttributeName);
        ElementsCollection values = property.$$(By.xpath(".//input[contains(@class,\"form-control\")]"));
        for (SelenideElement valueElemen : values) {
            valueElemen.setValue(value).waitUntil(Condition.visible, MidPoint.TIMEOUT_DEFAULT_2_S);
        }

        return this;
    }

    public PrismForm<T> removeAttributeValue(String name, String value) {
        // todo implement
        return this;
    }

    public PrismForm<T> changeAttributeValue(String name, String oldValue, String newValue) {
        SelenideElement property = findProperty(name);

        $(By.className("prism-properties")).waitUntil(Condition.appears,MidPoint.TIMEOUT_MEDIUM_6_S);

        ElementsCollection values = property.$$(By.className("prism-property-value"));
        if (values.size() == 1) {
            values.first().$(By.className("form-control")).waitUntil(Condition.appears,MidPoint.TIMEOUT_MEDIUM_6_S).setValue(newValue);
        }

        // todo implement
        return this;
    }


    public PrismForm<T> setFileForUploadAsAttributeValue(String name, File file) {
        SelenideElement property = findProperty(name);
        property.$(By.cssSelector("input.form-object-value-binary-file-input")).uploadFile(file);

        return this;
    }

    public PrismForm<T> removeFileAsAttributeValue(String name) {
        SelenideElement property = findProperty(name);
        property.$(Schrodinger.byElementAttributeValue("a", "title", "Remove file")).click();

        return this;
    }

    public PrismForm<T> showEmptyAttributes(String containerName) {
        $(Schrodinger.byAncestorPrecedingSiblingDescendantOrSelfElementEnclosedValue("div", "data-s-id", "showEmptyButton", "class", "prism-properties", containerName))
                .waitUntil(Condition.visible, MidPoint.TIMEOUT_DEFAULT_2_S).click();

        return this;
    }

    public Boolean compareInputAttributeValue(String name, String expectedValue) {
        SelenideElement property = findProperty(name);
        SelenideElement value = property.$(By.xpath(".//input[contains(@class,\"form-control\")]"));
        String valueElement = value.getValue();

        if (!valueElement.isEmpty()) {

            return valueElement.equals(expectedValue);

        } else if (!expectedValue.isEmpty()) {

            return false;

        } else {

            return true;

        }

    }

    public Boolean compareSelectAttributeValue(String name, String expectedValue) {
        SelenideElement property = findProperty(name);
        SelenideElement value = property.$(By.xpath(".//select[contains(@class,\"form-control\")]"));
        String selectedOptionText = value.getSelectedText();

        if (!selectedOptionText.isEmpty()) {

            return selectedOptionText.equals(expectedValue);

        } else if (!expectedValue.isEmpty()) {

            return false;

        } else {

            return true;

        }

    }

    public PrismForm<T> addAttributeValue(QName name, String value) {
        SelenideElement property = findProperty(name);

        ElementsCollection values = property.$$(By.className("prism-property-value"));
        if (values.size() == 1) {
            values.first().$(By.className("form-control")).setValue(value);
        }
        // todo implement
        return this;
    }

    public PrismForm<T> setPasswordFieldsValues(QName name, String value) {
        SelenideElement property = findProperty(name);

        ElementsCollection values = property.$$(By.className("prism-property-value"));
        if (values.size() > 0) {
            ElementsCollection passwordInputs = values.first().$$(By.tagName("input"));
            if (passwordInputs != null){
                passwordInputs.forEach(inputElement -> inputElement.setValue(value));
            }
        }
        return this;
    }

    public PrismForm<T> setDropDownAttributeValue(QName name, String value) {
        SelenideElement property = findProperty(name);

        ElementsCollection values = property.$$(By.className("prism-property-value"));
        if (values.size() > 0) {
            SelenideElement dropDown = values.first().$(By.tagName("select"));
            if (dropDown != null){
                dropDown.selectOptionContainingText(value);
            }
        }
        return this;
    }

    public PrismForm<T> setAttributeValue(QName name, String value) {
        // todo implement
        return this;
    }

    public PrismForm<T> removeAttributeValue(QName name, String value) {
        // todo implement
        return this;
    }

    public PrismForm<T> changeAttributeValue(QName name, String oldValue, String newValue) {
        // todo implement
        return this;
    }

    public PrismForm<T> showEmptyAttributes(QName containerName, String value) {
        // todo implement
        return this;
    }

    public PrismForm<T> setFileForUploadAsAttributeValue(QName containerName, File file) {
        // todo implement
        return this;
    }

    public PrismForm<T> removeFileAsAttributeValue(QName containerName) {
        // todo implement
        return this;
    }

    private SelenideElement findProperValueContainer() {
        return null;
    }

    public SelenideElement findProperty(String name) {

        Selenide.sleep(5000);

        SelenideElement element = null;

        boolean doesElementAttrValueExist = $(Schrodinger.byElementAttributeValue(null, "contains",
                Schrodinger.DATA_S_QNAME, "#" + name)).exists();

        if (doesElementAttrValueExist) {
            element = $(Schrodinger.byElementAttributeValue(null, "contains",
                    Schrodinger.DATA_S_QNAME, "#" + name)).waitUntil(Condition.visible, MidPoint.TIMEOUT_DEFAULT_2_S);

        } else {
            element = $(By.xpath("//span[@data-s-id=\"label\"][contains(.,\"" + name + "\")]/..")).waitUntil(Condition.visible, MidPoint.TIMEOUT_MEDIUM_6_S)
                    .parent().waitUntil(Condition.visible, MidPoint.TIMEOUT_MEDIUM_6_S);
        }

        return element;
    }

    private SelenideElement findProperty(QName qname) {
        String name = Schrodinger.qnameToString(qname);
        return $(Schrodinger.byDataQName(name));
    }

    public PrismForm<T> selectOption(String attributeName, String option) {

        SelenideElement property = findProperty(attributeName);

        property.$(By.xpath(".//select[contains(@class,\"form-control\")]"))
                .waitUntil(Condition.appears, MidPoint.TIMEOUT_DEFAULT_2_S).selectOption(option);

        return this;
    }

    public PrismForm<T> expandContainerPropertiesPanel(String containerHeaderKey){
        SelenideElement panelHeader = $(Schrodinger.byElementAttributeValue("div", "data-s-resource-key", containerHeaderKey));

        SelenideElement headerChevron = panelHeader.$(By.tagName("i"));
        if (headerChevron.getAttribute("class") != null && !headerChevron.getAttribute("class").contains(CARET_DOWN_ICON_STYLE)) {
            headerChevron.click();
            panelHeader
                    .$(Schrodinger.byElementAttributeValue("i", "class","fa fa-caret-down fa-lg"))
                    .waitUntil(Condition.visible, MidPoint.TIMEOUT_DEFAULT_2_S);
        }
        panelHeader
                .parent()
                .$(By.className("prism-properties"))
                .shouldBe(Condition.visible);
        return this;
    }

    public PrismForm<T> addNewContainerValue(String containerHeaderKey, String newContainerHeaderKey){
        SelenideElement panelHeader = $(Schrodinger.byDataResourceKey("div", containerHeaderKey));
        panelHeader.$(Schrodinger.byDataId("addButton")).click();

        panelHeader
                .parent()
                .$(Schrodinger.byDataResourceKey(newContainerHeaderKey))
                .shouldBe(Condition.visible)
                .waitUntil(Condition.visible, MidPoint.TIMEOUT_DEFAULT_2_S);

        return this;
    }

    public SelenideElement getPrismPropertiesPanel(String containerHeaderKey){
        expandContainerPropertiesPanel(containerHeaderKey);

        SelenideElement containerHeaderPanel = $(Schrodinger.byDataResourceKey("div", containerHeaderKey));
        return containerHeaderPanel
                .parent()
                .$(By.className("prism-properties"))
                .shouldBe(Condition.visible)
                .waitUntil(Condition.visible, MidPoint.TIMEOUT_DEFAULT_2_S);

    }
}
