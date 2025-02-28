/*
 * Copyright (c) 2010-2013 Evolveum
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

package com.evolveum.midpoint.web.component.input;

import com.evolveum.midpoint.web.component.prism.InputPanel;
import org.apache.wicket.markup.html.form.FormComponent;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.model.IModel;
import org.apache.wicket.util.string.Strings;

public class TextPanel<T> extends InputPanel {

    private static final String ID_INPUT = "input";

    public TextPanel(String id, IModel<T> model, boolean shouldTrim) {
        this(id, model, String.class, shouldTrim);
    }
    
    public TextPanel(String id, IModel<T> model) {
        this(id, model, String.class, true);
    }

    public TextPanel(String id, IModel<T> model, Class clazz) {
    	this(id, model, clazz, true);
    }    
    
    public TextPanel(String id, IModel<T> model, Class clazz, boolean shouldTrim) {
        super(id);

        final TextField<T> text = new TextField<T>(ID_INPUT, model) {
        	
        	@Override
        	protected boolean shouldTrimInput() {
        		return shouldTrim;
        	}

        	@Override
            public void convertInput() {
        	    T convertedValue = getConvertedInputValue();
        	    if (convertedValue != null){
        	        setConvertedInput(convertedValue);
                } else {
        	        super.convertInput();
                }
            }


        };
        text.setType(clazz);
        add(text);
    }

    protected T getConvertedInputValue(){
        return null;
    }

    @Override
    public FormComponent<T> getBaseFormComponent() {
        return (FormComponent) get(ID_INPUT);
    }
}
