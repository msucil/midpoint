/**
 * Copyright (c) 2011 Evolveum
 *
 * The contents of this file are subject to the terms
 * of the Common Development and Distribution License
 * (the License). You may not use this file except in
 * compliance with the License.
 *
 * You can obtain a copy of the License at
 * http://www.opensource.org/licenses/cddl1 or
 * CDDLv1.0.txt file in the source code distribution.
 * See the License for the specific language governing
 * permission and limitations under the License.
 *
 * If applicable, add the following below the CDDL Header,
 * with the fields enclosed by brackets [] replaced by
 * your own identifying information:
 * Portions Copyrighted 2011 [name of copyright owner]
 */
package com.evolveum.midpoint.common.valueconstruction;

import java.util.Map;

import javax.xml.bind.JAXBElement;
import javax.xml.namespace.QName;

import com.evolveum.midpoint.prism.Item;
import com.evolveum.midpoint.prism.ItemDefinition;
import com.evolveum.midpoint.prism.PrismConstants;
import com.evolveum.midpoint.prism.PrismContext;
import com.evolveum.midpoint.prism.PrismProperty;
import com.evolveum.midpoint.prism.PrismPropertyDefinition;
import com.evolveum.midpoint.prism.PrismPropertyValue;
import com.evolveum.midpoint.prism.PrismValue;
import com.evolveum.midpoint.prism.PropertyPath;
import com.evolveum.midpoint.prism.Visitable;
import com.evolveum.midpoint.prism.Visitor;
import com.evolveum.midpoint.prism.delta.PrismValueDeltaSetTriple;
import com.evolveum.midpoint.prism.delta.ItemDelta;
import com.evolveum.midpoint.prism.polystring.PolyString;
import com.evolveum.midpoint.prism.xml.XmlTypeConverter;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.util.DOMUtil;
import com.evolveum.midpoint.util.exception.ExpressionEvaluationException;
import com.evolveum.midpoint.util.exception.ObjectNotFoundException;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.xml.ns._public.common.common_1.AsIsValueConstructorType;

/**
 * @author Radovan Semancik
 */
public class AsIsValueConstructor implements ValueConstructor {
	
	private PrismContext prismContext;

    public AsIsValueConstructor(PrismContext prismContext) {
		super();
		this.prismContext = prismContext;
	}

	/* (non-Javadoc)
      * @see com.evolveum.midpoint.common.valueconstruction.ValueConstructor#construct(com.evolveum.midpoint.schema.processor.PropertyDefinition, com.evolveum.midpoint.schema.processor.Property)
      */
    @Override
    public <V extends PrismValue> PrismValueDeltaSetTriple<V> construct(JAXBElement<?> constructorElement, ItemDefinition outputDefinition,
			Item<V> input, ItemDelta<V> inputDelta, Map<QName, Object> variables, 
			boolean conditionResultOld, boolean conditionResultNew,
			String contextDescription, OperationResult result) throws SchemaException,
			ExpressionEvaluationException, ObjectNotFoundException {

        Object constructorTypeObject = null;
        if (constructorElement != null) {
        	constructorTypeObject = constructorElement.getValue();
        }
        if (constructorTypeObject != null && !(constructorTypeObject instanceof AsIsValueConstructorType)) {
            throw new IllegalArgumentException("AsIs value constructor cannot handle elements of type " + constructorTypeObject.getClass().getName());
        }
        //AsIsValueConstructorType constructorType = (AsIsValueConstructorType)constructorTypeObject;
        
        PrismValueDeltaSetTriple<V> outputTriple = ItemDelta.toDeltaSetTriple(input, inputDelta, conditionResultOld, conditionResultNew);
        
        if (outputTriple == null) {
        	return null;
        }
        return ValueConstructorUtil.toOutputTriple(outputTriple, outputDefinition, null, prismContext);
    }
    

}
