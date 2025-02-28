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

//
// This file was generated by the JavaTM Architecture for XML Binding(JAXB) Reference Implementation, v2.2.4
// See <a href="http://java.sun.com/xml/jaxb">http://java.sun.com/xml/jaxb</a>
// Any modifications to this file will be lost upon recompilation of the source schema.
// Generated on: 2012.05.20 at 05:41:15 PM CEST
//


package com.evolveum.prism.xml.ns._public.types_3;

import java.io.File;
import java.io.Serializable;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.*;
import javax.activation.MimeType;
import javax.activation.MimeTypeParseException;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAnyElement;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;
import javax.xml.datatype.Duration;
import javax.xml.datatype.XMLGregorianCalendar;
import javax.xml.namespace.QName;

import com.evolveum.midpoint.prism.JaxbVisitable;
import com.evolveum.midpoint.prism.JaxbVisitor;
import com.evolveum.midpoint.prism.polystring.PolyString;
import com.evolveum.midpoint.util.DebugDumpable;
import com.evolveum.midpoint.util.DebugUtil;
import org.w3c.dom.Element;


/**
 *
 * 				Polymorphic string. String that may have more than one representation at
 * 				the same time. The primary representation is the original version that is
 * 				composed of the full Unicode character set. The other versions may be
 * 				normalized to trim it, normalize character case, normalize spaces,
 * 				remove national characters or even transliterate the string.
 *
 * WARNING: THIS IS NOT GENERATED CODE
 * Although it was originally generated, it has local modifications.
 *
 * <p>Java class for PolyStringType complex type.
 *
 * <p>The following schema fragment specifies the expected content contained within this class.
 *
 * <pre>
 * &lt;complexType name="PolyStringType"&gt;
 *   &lt;complexContent&gt;
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="orig" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *         &lt;element name="norm" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/&gt;
 *         &lt;any namespace='##other' maxOccurs="unbounded" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/restriction&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "PolyStringType", propOrder = {
    "orig",
    "norm",
    "translation",
    "lang",
    "any"
})
public class PolyStringType implements DebugDumpable, Serializable, Cloneable, JaxbVisitable {
	private static final long serialVersionUID = 1L;

	public static final QName COMPLEX_TYPE = new QName("http://prism.evolveum.com/xml/ns/public/types-3", "PolyStringType");

    @XmlElement(required = true)
    protected String orig;
    
    protected String norm;
    
    protected PolyStringTranslationType translation;
    
    protected PolyStringLangType lang;

    @XmlAnyElement(lax = true)
    protected List<Object> any;

    public PolyStringType() {
    	this.orig = null;
    	this.norm = null;
    }

    public PolyStringType(String orig) {
    	this.orig = orig;
    	this.norm = null;
    }

    public PolyStringType(PolyString polyString) {
    	this.orig = polyString.getOrig();
    	this.norm = polyString.getNorm();
    	this.translation = polyString.getTranslation();
    	Map<String, String> polyStringLang = polyString.getLang();
    	if (polyStringLang != null && !polyStringLang.isEmpty()) {
    		this.lang = new PolyStringLangType();
    		this.lang.setLang(new HashMap<>(polyStringLang));
    	}
    }

    /**
     * Gets the value of the orig property.
     *
     * @return
     *     possible object is
     *     {@link String }
     *
     */
    public String getOrig() {
        return orig;
    }

    /**
     * Sets the value of the orig property.
     *
     * @param value
     *     allowed object is
     *     {@link String }
     *
     */
    public void setOrig(String value) {
        this.orig = value;
    }

    /**
     * Gets the value of the norm property.
     *
     * @return
     *     possible object is
     *     {@link String }
     *
     */
    public String getNorm() {
        return norm;
    }

    /**
     * Sets the value of the norm property.
     *
     * @param value
     *     allowed object is
     *     {@link String }
     *
     */
    public void setNorm(String value) {
        this.norm = value;
    }
    
    public PolyStringTranslationType getTranslation() {
		return translation;
	}

	public void setTranslation(PolyStringTranslationType translation) {
		this.translation = translation;
	}
	
	public PolyStringLangType getLang() {
		return lang;
	}

	public void setLang(PolyStringLangType lang) {
		this.lang = lang;
	}

	/**
     * Gets the value of the any property.
     *
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the any property.
     *
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getAny().add(newItem);
     * </pre>
     *
     *
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link Object }
     *
     *
     */
    public List<Object> getAny() {
        if (any == null) {
            any = new ArrayList<>();
        }
        return this.any;
    }

    public boolean isEmpty() {
		if (orig == null) {
			return true;
		}
		return orig.isEmpty();
	}
    
	/**
	 * Returns true if the PolyString form contains only simple string.
	 * I.e. returns true if the polystring can be serialized in a simplified form of a single string.
	 * Returns true in case that there are language mutations, translation, etc.
	 */
	public boolean isSimple() {
		return translation == null && lang == null;
	}
    
    /**
     * Plus method for ease of use of PolyStrings in groovy (mapped from + operator).
     */
    public PolyStringType plus(String operand) {
    	if (operand == null) {
    		return this;
    	}
    	return new PolyStringType(getOrig() + operand);
    }

    public PolyStringType plus(PolyStringType operand) {
    	if (operand == null) {
    		return this;
    	}
    	return new PolyStringType(getOrig() + operand.getOrig());
    }

    public PolyString toPolyString() {
    	return new PolyString(orig, norm, translation, lang == null ? null : lang.getLang());
    }

    /**
     * toString is tweaked to provide convenience and compatibility with normal strings.
     * If PolyStringType is used in expressions that target the entire PolyString, the result
     * will be the "orig" value of PolyString.
     *
     * WARNING: This method was NOT generated. If the code is re-generated then it must be
     * manually re-introduced to the code.
     */
	@Override
	public String toString() {
		return orig;
	}

	@Override
	public String debugDump(int indent) {
		StringBuilder sb = new StringBuilder();
		DebugUtil.indentDebugDump(sb, indent);
		sb.append("PolyStringType(");
		sb.append(orig);
		if (norm != null) {
			sb.append(",");
			sb.append(norm);
		}
		if (translation != null) {
			sb.append(";translation=");
			sb.append(translation.getKey());
		}
		if (lang != null) {
			sb.append(";lang=");
			sb.append(lang.getLang());
		}
		sb.append(")");
		return sb.toString();

	}

    @Override
    public PolyStringType clone() {
        PolyStringType poly = new PolyStringType();
        poly.setNorm(getNorm());
        poly.setOrig(getOrig());
        if (translation != null) {
        	poly.setTranslation(translation.clone());
        }
        if (lang != null) {
        	poly.setLang(lang.clone());
        }
        copyContent(getAny(), poly.getAny());

        return poly;
    }

    /**
     * Copies all values of property {@code Content} deeply.
     *
     * @param source
     *     The source to copy from.
     * @param target
     *     The target to copy {@code source} to.
     * @throws NullPointerException
     *     if {@code target} is {@code null}.
     */
    @SuppressWarnings("unchecked")
    private static void copyContent(final List<Object> source, final List<Object> target) {
        // CC-XJC Version 2.0 Build 2011-09-16T18:27:24+0000
        if ((source!= null)&&(!source.isEmpty())) {
            for (final Iterator<?> it = source.iterator(); it.hasNext(); ) {
                final Object next = it.next();
                if (next instanceof JAXBElement) {
                    // Referenced elements without classes.
                    if (((JAXBElement) next).getValue() instanceof String) {
                        // CElementInfo: javax.xml.bind.JAXBElement<java.lang.String>
                        target.add(copyOfStringElement(((JAXBElement) next)));
                        continue;
                    }
                }
                if (next instanceof String) {
                    // CBuiltinLeafInfo: java.lang.String
                    target.add(((String) next));
                    continue;
                }
                if (next instanceof Object) {
                    // CBuiltinLeafInfo: java.lang.Object
                    target.add(copyOf(((Object) next)));
                    continue;
                }
                // Please report this at https://apps.sourceforge.net/mantisbt/ccxjc/
                throw new AssertionError((("Unexpected instance '"+ next)+"' for property 'Content' of class 'com.evolveum.prism.xml.ns._public.types_3.PolyStringType'."));
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static Object copyOf(final Object o) {
        // CC-XJC Version 2.0 Build 2011-09-16T18:27:24+0000
        try {
            if (o!= null) {
                if (o.getClass().isPrimitive()) {
                    return o;
                }
                if (o.getClass().isArray()) {
                    return copyOfArray(o);
                }
                // Immutable types.
                if (o instanceof Boolean) {
                    return o;
                }
                if (o instanceof Byte) {
                    return o;
                }
                if (o instanceof Character) {
                    return o;
                }
                if (o instanceof Double) {
                    return o;
                }
                if (o instanceof Enum) {
                    return o;
                }
                if (o instanceof Float) {
                    return o;
                }
                if (o instanceof Integer) {
                    return o;
                }
                if (o instanceof Long) {
                    return o;
                }
                if (o instanceof Short) {
                    return o;
                }
                if (o instanceof String) {
                    return o;
                }
                if (o instanceof BigDecimal) {
                    return o;
                }
                if (o instanceof BigInteger) {
                    return o;
                }
                if (o instanceof UUID) {
                    return o;
                }
                if (o instanceof QName) {
                    return o;
                }
                if (o instanceof Duration) {
                    return o;
                }
                if (o instanceof Currency) {
                    return o;
                }
                // String based types.
                if (o instanceof File) {
                    return new File(o.toString());
                }
                if (o instanceof URI) {
                    return new URI(o.toString());
                }
                if (o instanceof URL) {
                    return new URL(o.toString());
                }
                if (o instanceof MimeType) {
                    return new MimeType(o.toString());
                }
                // Cloneable types.
                if (o instanceof XMLGregorianCalendar) {
                    return ((XMLGregorianCalendar) o).clone();
                }
                if (o instanceof Date) {
                    return ((Date) o).clone();
                }
                if (o instanceof Calendar) {
                    return ((Calendar) o).clone();
                }
                if (o instanceof TimeZone) {
                    return ((TimeZone) o).clone();
                }
                if (o instanceof Locale) {
                    return ((Locale) o).clone();
                }
                if (o instanceof Element) {
                    return ((Element)((Element) o).cloneNode(true));
                }
                if (o instanceof JAXBElement) {
                    return copyOf(((JAXBElement) o));
                }
                try {
                    return o.getClass().getMethod("clone", ((Class[]) null)).invoke(o, ((Object[]) null));
                } catch (NoSuchMethodException e) {
                    if (o instanceof Serializable) {
                        return copyOf(((Serializable) o));
                    }
                    // Please report this at https://apps.sourceforge.net/mantisbt/ccxjc/
                    throw((AssertionError) new AssertionError((("Unexpected instance during copying object '"+ o)+"'.")).initCause(e));
                } catch (IllegalAccessException e) {
                    // Please report this at https://apps.sourceforge.net/mantisbt/ccxjc/
                    throw((AssertionError) new AssertionError((("Unexpected instance during copying object '"+ o)+"'.")).initCause(e));
                } catch (InvocationTargetException e) {
                    // Please report this at https://apps.sourceforge.net/mantisbt/ccxjc/
                    throw((AssertionError) new AssertionError((("Unexpected instance during copying object '"+ o)+"'.")).initCause(e));
                } catch (SecurityException e) {
                    // Please report this at https://apps.sourceforge.net/mantisbt/ccxjc/
                    throw((AssertionError) new AssertionError((("Unexpected instance during copying object '"+ o)+"'.")).initCause(e));
                } catch (IllegalArgumentException e) {
                    // Please report this at https://apps.sourceforge.net/mantisbt/ccxjc/
                    throw((AssertionError) new AssertionError((("Unexpected instance during copying object '"+ o)+"'.")).initCause(e));
                } catch (ExceptionInInitializerError e) {
                    // Please report this at https://apps.sourceforge.net/mantisbt/ccxjc/
                    throw((AssertionError) new AssertionError((("Unexpected instance during copying object '"+ o)+"'.")).initCause(e));
                }
            }
            return null;
        } catch (MalformedURLException e) {
            throw((AssertionError) new AssertionError((("Unexpected instance during copying object '"+ o)+"'.")).initCause(e));
        } catch (URISyntaxException e) {
            throw((AssertionError) new AssertionError((("Unexpected instance during copying object '"+ o)+"'.")).initCause(e));
        } catch (MimeTypeParseException e) {
            throw((AssertionError) new AssertionError((("Unexpected instance during copying object '"+ o)+"'.")).initCause(e));
        }
    }

    /**
     * Creates and returns a deep copy of a given array.
     *
     * @param array
     *     The array to copy or {@code null}.
     * @return
     *     A deep copy of {@code array} or {@code null} if {@code array} is {@code null}.
     */
    private static Object copyOfArray(final Object array) {
        // CC-XJC Version 2.0 Build 2011-09-16T18:27:24+0000
        if (array!= null) {
            if (array.getClass() == boolean[].class) {
                return copyOf(((boolean[]) array));
            }
            if (array.getClass() == byte[].class) {
                return copyOf(((byte[]) array));
            }
            if (array.getClass() == char[].class) {
                return copyOf(((char[]) array));
            }
            if (array.getClass() == double[].class) {
                return copyOf(((double[]) array));
            }
            if (array.getClass() == float[].class) {
                return copyOf(((float[]) array));
            }
            if (array.getClass() == int[].class) {
                return copyOf(((int[]) array));
            }
            if (array.getClass() == long[].class) {
                return copyOf(((long[]) array));
            }
            if (array.getClass() == short[].class) {
                return copyOf(((short[]) array));
            }
            final int len = Array.getLength(array);
            final Object copy = Array.newInstance(array.getClass().getComponentType(), len);
            for (int i = (len- 1); (i >= 0); i--) {
                Array.set(copy, i, copyOf(Array.get(array, i)));
            }
            return copy;
        }
        return null;
    }

    /**
     * Creates and returns a deep copy of a given {@code javax.xml.bind.JAXBElement<java.lang.String>} instance.
     *
     * @param e
     *     The instance to copy or {@code null}.
     * @return
     *     A deep copy of {@code e} or {@code null} if {@code e} is {@code null}.
     */
    @SuppressWarnings("unchecked")
    private static JAXBElement<String> copyOfStringElement(final JAXBElement<String> e) {
        // CC-XJC Version 2.0 Build 2011-09-16T18:27:24+0000
        if (e!= null) {
            final JAXBElement<String> copy = new JAXBElement<>(e.getName(), e.getDeclaredType(), e.getScope(), e.getValue());
            copy.setNil(e.isNil());
            // CBuiltinLeafInfo: java.lang.String
            copy.setValue(((String) copy.getValue()));
            return copy;
        }
        return null;
    }

	// !!! Do NOT autogenerate this method without preserving custom changes !!!
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((any == null || any.isEmpty()) ? 0 : any.hashCode());
		result = prime * result + ((lang == null) ? 0 : lang.hashCode());
		result = prime * result + ((norm == null) ? 0 : norm.hashCode());
		result = prime * result + ((orig == null) ? 0 : orig.hashCode());
		result = prime * result + ((translation == null) ? 0 : translation.hashCode());
		return result;
	}

	// !!! Do NOT autogenerate this method without preserving custom changes !!!
	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		PolyStringType other = (PolyStringType) obj;
		if (any == null || any.isEmpty()) {     // because any is instantiated on get (so null and empty should be considered equivalent)
			if (other.any != null && !other.any.isEmpty()) {
				return false;
			}
		} else if (!any.equals(other.any)) {
			return false;
		}
		if (lang == null || lang.isEmpty()) {
			if (other.lang != null && !other.lang.isEmpty()) {
				return false;
			}
		} else if (!lang.equals(other.lang)) {
			return false;
		}
		if (norm == null) {
			if (other.norm != null) {
				return false;
			}
		} else if (!norm.equals(other.norm)) {
			return false;
		}
		if (orig == null) {
			if (other.orig != null) {
				return false;
			}
		} else if (!orig.equals(other.orig)) {
			return false;
		}
		if (translation == null) {
			if (other.translation != null) {
				return false;
			}
		} else if (!translation.equals(other.translation)) {
			return false;
		}
		return true;
	}

	public static PolyStringType fromOrig(String name) {
		return name != null ? new PolyStringType(name) : null;
	}

	@Override
	public void accept(JaxbVisitor visitor) {
		visitor.visit(this);
	}
}
