/*
 * Copyright (c) 2010-2018 Evolveum
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
package com.evolveum.midpoint.prism.impl.xjc;

import com.evolveum.midpoint.prism.polystring.PolyString;
import com.evolveum.prism.xml.ns._public.types_3.PolyStringLangType;
import com.evolveum.prism.xml.ns._public.types_3.PolyStringType;

import java.util.Map;

/**
 * @author semancik
 *
 */
public class JaxbTypeConverter {

	public static <T> T mapPropertyRealValueToJaxb(Object propertyRealValue) {
    	// TODO: check for type compatibility
    	if (propertyRealValue instanceof PolyString) {
    		return (T)toPolyStringType((PolyString)propertyRealValue);
    	}
    	return (T)propertyRealValue;
    }

	private static PolyStringType toPolyStringType(PolyString polyString) {
		if (polyString == null) {
			return null;
		}
		PolyStringType polyStringType = new PolyStringType();
		polyStringType.setOrig(polyString.getOrig());
		polyStringType.setNorm(polyString.getNorm());
		polyStringType.setTranslation(polyString.getTranslation());
		Map<String, String> sourceLang = polyString.getLang();
		if (sourceLang != null && !sourceLang.isEmpty()) {
			PolyStringLangType lang = new PolyStringLangType();
			lang.setLang(sourceLang);
			polyStringType.setLang(lang);
		}
		return polyStringType;
	}

	public static Object mapJaxbToPropertyRealValue(Object jaxbObject) {
		if (jaxbObject instanceof PolyStringType) {
			return fromPolyStringType((PolyStringType)jaxbObject);
		}
		return jaxbObject;
	}

	private static PolyString fromPolyStringType(PolyStringType polyStringType) {
		if (polyStringType == null || polyStringType.getOrig() == null) {
			return null;
		}
		PolyString polyString;
		PolyStringLangType lang = polyStringType.getLang();
		if (lang != null && !lang.getLang().isEmpty()) {
			polyString = new PolyString(polyStringType.getOrig(), polyStringType.getNorm(),
					polyStringType.getTranslation(), lang.getLang());
		} else {
			polyString = new PolyString(polyStringType.getOrig(), polyStringType.getNorm(), polyStringType.getTranslation());
		}
		return polyString;
	}

}
