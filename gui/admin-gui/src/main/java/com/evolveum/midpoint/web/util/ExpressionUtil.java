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

package com.evolveum.midpoint.web.util;

import com.evolveum.midpoint.prism.PrismContext;
import com.evolveum.midpoint.prism.path.ItemPath;
import com.evolveum.midpoint.prism.query.EqualFilter;
import com.evolveum.midpoint.prism.xnode.*;
import com.evolveum.midpoint.schema.SchemaConstantsGenerated;
import com.evolveum.midpoint.schema.constants.SchemaConstants;
import com.evolveum.midpoint.util.DebugUtil;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.util.logging.LoggingUtils;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;
import com.evolveum.prism.xml.ns._public.query_3.SearchFilterType;
import com.evolveum.prism.xml.ns._public.types_3.ItemPathType;
import com.evolveum.prism.xml.ns._public.types_3.RawType;
import org.apache.commons.lang.StringUtils;

import javax.xml.bind.JAXBElement;
import javax.xml.namespace.QName;
import java.util.*;

/**
 *  @author shood
 * */
public class ExpressionUtil {

	private static final Trace LOGGER = TraceManager.getTrace(ExpressionUtil.class);

	public static enum ExpressionEvaluatorType{
        LITERAL,
        AS_IS,
        PATH,
        SCRIPT,
        GENERATE
    }

    public static enum Language{
        GROOVY("http://midpoint.evolveum.com/xml/ns/public/expression/language#Groovy"),
        XPATH("http://www.w3.org/TR/xpath/"),
        JAVASCRIPT("http://midpoint.evolveum.com/xml/ns/public/expression/language#ECMAScript");

        protected String language;

        Language(String language){
            this.language = language;
        }

        public String getLanguage() {
            return language;
        }
    }

    public final static QName SHADOW_REF_KEY = new QName("shadowRef");
    private final static QName SHADOW_OID_KEY = new QName("oid");
    private final static QName SHADOW_TYPE_KEY = new QName("type");

    public static final String SCRIPT_START_NS = "<c:script xmlns:c=\"http://midpoint.evolveum.com/xml/ns/public/common/common-3\">";
    public static final String SCRIPT_END_NS = "</c:script>";
    public static final String CODE_START_NS = "<c:code>";
    public static final String CODE_END_NS = "</c:code>";
    public static final String VALUE_START_NS = "<c:value xmlns:c=\"http://midpoint.evolveum.com/xml/ns/public/common/common-3\">";
    public static final String VALUE_END_NS = "</c:value>";
    public static final String PATH_START_NS = "<c:path xmlns:c=\"http://midpoint.evolveum.com/xml/ns/public/common/common-3\">";
    public static final String PATH_END_NS = "</c:path>";

    public static final String EXPRESSION_SCRIPT =
                    "<script>\n" +
                    "    <code>\n" +
                    "        Insert your script here\n" +
                    "    </code>\n" +
                    "</script>";

    public static final String EXPRESSION_LITERAL = "<value>Insert value(s) here</value>";
    public static final String EXPRESSION_AS_IS = "<asIs/>";
    public static final String EXPRESSION_PATH = "<path>Insert path here</path>";
    public static final String EXPRESSION_GENERATE =
                    "<generate>\n" +
                    //"    <valuePolicyRef oid=\"Insert value policy oid\"/>\n" +
                    "</generate>";

    public static final String ELEMENT_SCRIPT = "</script>";
    public static final String ELEMENT_GENERATE = "</generate>";
    public static final String ELEMENT_GENERATE_WITH_NS = "<generate";
    public static final String ELEMENT_PATH = "</path>";
    public static final String ELEMENT_VALUE = "</value>";
    public static final String ELEMENT_AS_IS = "<asIs/>";
    public static final String ELEMENT_AS_IS_WITH_NS = "<asIs";

    public static String getExpressionString(ExpressionEvaluatorType type, ObjectReferenceType policy){
        if(ExpressionEvaluatorType.GENERATE.equals(type) && policy != null){
            StringBuilder sb = new StringBuilder();
            sb.append("<generate>\n" +
                    "    <valuePolicyRef oid=\"").append(policy.getOid()).append("\"/>\n" +
                    "</generate>");

            return sb.toString();
        }

        return EXPRESSION_GENERATE;
    }

    public static String getExpressionString(ExpressionEvaluatorType type, Language lang){
        if(ExpressionEvaluatorType.SCRIPT.equals(type) && !Language.GROOVY.equals(lang)){
            StringBuilder sb = new StringBuilder();
            sb.append("<script>\n");
            sb.append("    <language>").append(lang.getLanguage()).append("</language>\n");
            sb.append("    <code>\n" +
                    "        Insert your script here\n" +
                    "    </code>\n" +
                    "<script>");

            return sb.toString();
        }

        return EXPRESSION_SCRIPT;
    }

    public static String getExpressionString(ExpressionEvaluatorType type){
        if(type == null){
            return "";
        }

        switch(type){
            case AS_IS:
                return EXPRESSION_AS_IS;

            case GENERATE:
                return EXPRESSION_GENERATE;

            case LITERAL:
                return EXPRESSION_LITERAL;

            case PATH:
                return EXPRESSION_PATH;

            case SCRIPT:
                return EXPRESSION_SCRIPT;

            default:
                return "";
        }
    }

    public static ExpressionEvaluatorType getExpressionType(String expression){
        if(expression.contains(ELEMENT_AS_IS) || expression.contains(ELEMENT_AS_IS_WITH_NS)){
            return ExpressionEvaluatorType.AS_IS;
        } else if(expression.contains(ELEMENT_GENERATE) || expression.contains(ELEMENT_GENERATE_WITH_NS)){
            return ExpressionEvaluatorType.GENERATE;
        } else if(expression.contains(ELEMENT_PATH)){
            return ExpressionEvaluatorType.PATH;
        } else if(expression.contains(ELEMENT_SCRIPT)){
            return ExpressionEvaluatorType.SCRIPT;
        } else if(expression.contains(ELEMENT_VALUE)){
            return ExpressionEvaluatorType.LITERAL;
        }

        return null;
    }

    public static Language getExpressionLanguage(String expression){
        if(expression.contains("<language>")){
            if(expression.contains(Language.XPATH.getLanguage())){
                return Language.XPATH;
            } else if(expression.contains(Language.JAVASCRIPT.getLanguage())) {
                return Language.JAVASCRIPT;
            } else {
                return Language.GROOVY;
            }
        } else {
            return Language.GROOVY;
        }
    }

    public static String addNamespaces(String expression, ExpressionEvaluatorType type){
        String newExpression = expression;

        if(ExpressionEvaluatorType.PATH.equals(type)){
            newExpression = newExpression.replaceAll("<path>", PATH_START_NS);
            newExpression = newExpression.replaceAll("</path>", PATH_END_NS);
        } else if(ExpressionEvaluatorType.LITERAL.equals(type)){
            newExpression = newExpression.replaceAll("<value>", VALUE_START_NS);
            newExpression = newExpression.replaceAll("</value>", VALUE_END_NS);
        } else if(ExpressionEvaluatorType.SCRIPT.equals(type)){
            newExpression = newExpression.replaceAll("<code>", CODE_START_NS);
            newExpression = newExpression.replaceAll("</code>", CODE_END_NS);
            newExpression = newExpression.replaceAll("<script>", SCRIPT_START_NS);
            newExpression = newExpression.replaceAll("</script>", SCRIPT_END_NS);
        }

        return newExpression;
    }

    public static String loadExpression(ExpressionType expression, PrismContext prismContext, Trace LOGGER) {
		if (expression == null || expression.getExpressionEvaluator().isEmpty()) {
			return "";
		}
		List<JAXBElement<?>> evaluators = expression.getExpressionEvaluator();
		try {
			return serializeEvaluators(evaluators, prismContext);
		} catch (SchemaException e) {
			//TODO - how can we show this error to user?
			LoggingUtils.logUnexpectedException(LOGGER, "Could not load expressions from mapping.", e, e.getStackTrace());
			return e.getMessage();
		}
	}

	private static String serializeEvaluators(List<JAXBElement<?>> evaluators, PrismContext prismContext) throws SchemaException {
		if (evaluators.size() == 1) {
			return serialize(evaluators.get(0), prismContext);
		} else {
			StringBuilder sb = new StringBuilder();
			for (JAXBElement<?> element : evaluators) {
				String subElement = serialize(element, prismContext);
				sb.append(subElement).append("\n");
			}
			return sb.toString();
		}
	}

	private static String serialize(JAXBElement<?> element, PrismContext prismContext) throws SchemaException {
		String xml;
		if (element.getValue() instanceof RawType) {
			RawType raw = (RawType) element.getValue();
			RootXNode rootNode = prismContext.xnodeFactory().root(element.getName(), raw.serializeToXNode());
			xml = prismContext.xmlSerializer().serialize(rootNode);
		} else {
			xml = prismContext.xmlSerializer().serialize(element);
		}
		return WebXmlUtil.stripNamespaceDeclarations(xml);
	}

	public static boolean isEmpty(ExpressionType expression) {
		return expression == null || expression.getExpressionEvaluator().isEmpty();
	}

	public static boolean isShadowRefNotEmpty(ExpressionType expression, PrismContext prismContext) {
        List<ObjectReferenceType> shadowRefValueList = getShadowRefValue(expression, prismContext);
		return !isEmpty(expression) && shadowRefValueList != null && shadowRefValueList.size() > 0;
	}

	public static boolean isAssociationTargetSearchNotEmpty(ExpressionType expression) {
        String path = getTargetSearchExpPathValue(expression);
        String value = getTargetSearchExpValue(expression);
		return StringUtils.isNotEmpty(path) && StringUtils.isNotEmpty(value);
	}

	public static boolean isLiteralExpressionValueNotEmpty(ExpressionType expression) throws SchemaException{
        List<String> values = getLiteralExpressionValues(expression);
		return values != null && values.size() > 0;
	}

	public static boolean areAllExpressionValuesEmpty(ExpressionType expression, PrismContext prismContext) throws SchemaException {
        return !isShadowRefNotEmpty(expression, prismContext) && !isLiteralExpressionValueNotEmpty(expression) && !isAssociationTargetSearchNotEmpty(expression);
    }

	public static void parseExpressionEvaluators(String xml, ExpressionType expressionObject, PrismContext context) throws SchemaException {
		expressionObject.getExpressionEvaluator().clear();
		if (StringUtils.isNotBlank(xml)) {
			xml = WebXmlUtil.wrapInElement("expression", xml, true);
			LOGGER.info("Expression to serialize: {}", xml);
			JAXBElement<?> newElement = context.parserFor(xml).xml().parseRealValueToJaxbElement();
			expressionObject.getExpressionEvaluator().addAll(((ExpressionType) (newElement.getValue())).getExpressionEvaluator());
		}
	}

	// TODO move somewhere else? generalize a bit?
	public static RootXNode parseSearchFilter(String data, PrismContext context) throws SchemaException {
		String xml = WebXmlUtil.wrapInElement("root", data, false);
		RootXNode rootXNode = context.parserFor(xml).xml().parseToXNode();
		if (rootXNode.getSubnode() instanceof MapXNode) {
			MapXNode mapXNode = (MapXNode) rootXNode.getSubnode();
			if (mapXNode.size() != 1) {
				throw new SchemaException("Content cannot be parsed as a search filter: " + mapXNode.debugDump());
			}
			return mapXNode.getEntryAsRoot(mapXNode.keySet().iterator().next());
		} else {
			throw new SchemaException("Content cannot be parsed as a search filter: " + DebugUtil.debugDump(rootXNode.getSubnode()));
		}
	}

	public static JAXBElement findFirstEvaluatorByName(ExpressionType expression, QName elementName){
        if (isEmpty(expression) || elementName == null){
            return null;
        }
        for (JAXBElement<?> element : expression.getExpressionEvaluator()){
            if (element != null && element.getName().equals(elementName)){
                return element;
            }
        }
        return null;
    }

    public static List<JAXBElement> findAllEvaluatorsByName(ExpressionType expression, QName elementName){
	    List<JAXBElement> elements = new ArrayList<>();
        if (isEmpty(expression) || elementName == null){
            return elements;
        }
        for (JAXBElement<?> element : expression.getExpressionEvaluator()){
            if (element != null && element.getName().equals(elementName)){
                elements.add(element);
            }
        }
        return elements;
    }

    public static void removeEvaluatorByName(ExpressionType expression, QName elementName){
        if (isEmpty(expression) || elementName == null){
            return;
        }
        Iterator<JAXBElement<?>> it = expression.getExpressionEvaluator().iterator();
        while (it.hasNext()){
            JAXBElement<?> element = it.next();
            if (element != null && element.getName().equals(elementName)){
                it.remove();
            }
        }
    }

    public static void removeShadowRefEvaluatorValue(ExpressionType expression, String shadowRefOid, PrismContext prismContext){
        if (expression == null){
            return;
        }
        JAXBElement<RawType> element = findFirstEvaluatorByName(expression, SchemaConstants.C_VALUE);
        if (element == null){
            element = new JAXBElement(SchemaConstants.C_VALUE, RawType.class,
                    new RawType(prismContext));
        }
        if (element != null && element.getValue() instanceof RawType) {
            RawType raw = element.getValue();
            XNode node = raw.getXnode();
            if (node instanceof MapXNode && ((MapXNode) node).containsKey(SHADOW_REF_KEY)) {
                XNode shadowRefNodes = ((MapXNode) node).get(SHADOW_REF_KEY);
               if (shadowRefNodes instanceof MapXNode && shadowRefOid.equals(getShadowRefNodeOid((MapXNode) shadowRefNodes))) {
                   prismContext.xnodeMutator().putToMapXNode((MapXNode) node, SHADOW_REF_KEY, null);
                   //todo don't get why while using removeEvaluatorByName no changes are saved
//                   removeEvaluatorByName(expression, SchemaConstantsGenerated.C_VALUE);
               } else if (shadowRefNodes instanceof ListXNode) {
                   Iterator<? extends XNode> it = ((ListXNode) shadowRefNodes).asList().iterator();
                   while (it.hasNext()) {
                       XNode shadowRefNode = it.next();
                       if (shadowRefNode instanceof MapXNode && shadowRefOid.equals(getShadowRefNodeOid((MapXNode) shadowRefNode))) {
                           it.remove();
                           break;
                       }
                   }
               }
            }
        }
        expression.getExpressionEvaluator().add(element);
    }

    public static void clearExpressionEvaluator(ExpressionType expression){
        if (expression == null){
            return;
        }
        expression.getExpressionEvaluator().clear();
    }

    private static String getShadowRefNodeOid(MapXNode shadowRefNode){
        if (shadowRefNode != null && ((MapXNode) shadowRefNode).containsKey(SHADOW_OID_KEY)) {
            PrimitiveXNode shadowOidNode = (PrimitiveXNode) ((MapXNode) shadowRefNode).get(SHADOW_OID_KEY);
            return shadowOidNode != null && shadowOidNode.getValueParser() != null ? shadowOidNode.getValueParser().getStringValue() :
                    (shadowOidNode != null && shadowOidNode.getValue() != null ? (String) shadowOidNode.getValue() : null);

        }
        return "";
    }

    public static JAXBElement createAssociationTargetSearchElement(PrismContext prismContext) {
        JAXBElement evaluator = new JAXBElement(SchemaConstantsGenerated.C_ASSOCIATION_TARGET_SEARCH, JAXBElement.GlobalScope.class,
                new JAXBElement.GlobalScope());
        SearchObjectExpressionEvaluatorType searchObjectExpressionEvaluatorType = new SearchObjectExpressionEvaluatorType();

        XNodeFactory factory = prismContext.xnodeFactory();
        Map<QName, XNode> valuesMap = new HashMap<>();
        valuesMap.put(new QName("path"), factory.primitive());
        valuesMap.put(new QName("value"), factory.primitive());
        MapXNode values = factory.map(valuesMap);
        MapXNode filterClauseNode = factory.map(new QName("equal"), values);

        SearchFilterType filterType = new SearchFilterType();
        filterType.setFilterClauseXNode(filterClauseNode);
        searchObjectExpressionEvaluatorType.setFilter(filterType);

        evaluator.setValue(searchObjectExpressionEvaluatorType);
        return evaluator;
    }

    public static MapXNode getOrCreateAssociationTargetSearchValues(ExpressionType expression,
            PrismContext prismContext) {
        JAXBElement element = findFirstEvaluatorByName(expression, SchemaConstantsGenerated.C_ASSOCIATION_TARGET_SEARCH);
        if (element == null){
            element = createAssociationTargetSearchElement(prismContext);
        }
        SearchObjectExpressionEvaluatorType evaluator = (SearchObjectExpressionEvaluatorType) element.getValue();
        if (evaluator == null){
            evaluator = new SearchObjectExpressionEvaluatorType();
        }
        SearchFilterType filterType = evaluator.getFilter();
        if (filterType == null){
            filterType = new SearchFilterType();
        }
        MapXNode filterClauseNode = filterType.getFilterClauseXNode();
        if (filterClauseNode == null){
            filterClauseNode = prismContext.xnodeFactory().map();
        }
        if (!filterClauseNode.containsKey(new QName("equal"))) {
            prismContext.xnodeMutator().putToMapXNode(filterClauseNode, new QName("equal"), null);
        }
        MapXNode values = (MapXNode)filterClauseNode.get(new QName("equal"));
        if (values == null) {
            values = prismContext.xnodeFactory().map();        // todo [med] this has no effect on the map node!
        }
        return values;
    }

    public static void updateAssociationTargetSearchPath(ExpressionType expression, ItemPathType path, PrismContext prismContext) {
        MapXNode values = getOrCreateAssociationTargetSearchValues(expression, prismContext);
        PrimitiveXNode<ItemPathType> pathValue = (PrimitiveXNode<ItemPathType>)values.get(new QName("path"));
        if (pathValue != null) {
            prismContext.xnodeMutator().setPrimitiveXNodeValue(pathValue, path, null);
        }
    }

    public static void updateAssociationTargetSearchValue(ExpressionType expression, String newPath, String newValue,
                                                          PrismContext prismContext) throws SchemaException{
        SearchObjectExpressionEvaluatorType associationTargetSearchType = new SearchObjectExpressionEvaluatorType();
        EqualFilter pathFilter = prismContext.queryFactory().createEqual(ItemPath.create(newPath), null, null, prismContext, newValue);

        SearchFilterType filterType = prismContext.getQueryConverter().createSearchFilterType(pathFilter);
        associationTargetSearchType.setFilter(filterType);
        JAXBElement<SearchObjectExpressionEvaluatorType> evaluator = new ObjectFactory().createAssociationTargetSearch(associationTargetSearchType);

        removeEvaluatorByName(expression, SchemaConstantsGenerated.C_ASSOCIATION_TARGET_SEARCH);
        expression.getExpressionEvaluator().add(evaluator);
    }

    public static List<ObjectReferenceType> getShadowRefValue(ExpressionType expressionType, PrismContext prismContext) {
        if (expressionType == null) {
            return null;
        }
        List<ObjectReferenceType> shadowRefList = null;
        ListXNode shadowRefNodes = getShadowRefNodesList(expressionType, false, prismContext);

        if (shadowRefNodes != null) {
            for (XNode shadowRefNode : shadowRefNodes.asList()) {
                if (shadowRefNode instanceof MapXNode) {
                    if (shadowRefNode != null && ((MapXNode) shadowRefNode).containsKey(SHADOW_OID_KEY)) {
                        ObjectReferenceType shadowRef = new ObjectReferenceType();
                        PrimitiveXNode shadowOidNode = (PrimitiveXNode) ((MapXNode) shadowRefNode).get(SHADOW_OID_KEY);
                        String oid = shadowOidNode != null && shadowOidNode.getValueParser() != null ? shadowOidNode.getValueParser().getStringValue() :
                                (shadowOidNode != null && shadowOidNode.getValue() != null ? (String) shadowOidNode.getValue() : null);
                        shadowRef.setOid(oid);
                        shadowRef.setType(ShadowType.COMPLEX_TYPE);
                        if (shadowRefList == null){
                            shadowRefList =  new ArrayList<>();
                        }
                        shadowRefList.add(shadowRef);
                    }
                }
            }
        }
        return shadowRefList;
    }

    public static boolean isShadowRefNodeExists(ExpressionType expression) {
        if (expression == null) {
            return false;
        }
        JAXBElement element = ExpressionUtil.findFirstEvaluatorByName(expression, SchemaConstantsGenerated.C_VALUE);
        if (element == null) {
            return false;
        }
        if (element.getValue() instanceof RawType) {
            RawType raw = (RawType) element.getValue();
            XNode node = raw.getXnode();
            if (node == null) {
                return false;
            }
            if (node instanceof MapXNode && ((MapXNode) node).containsKey(SHADOW_REF_KEY)) {
                return true;
            }
        }
        return false;
    }

    public static ListXNode getShadowRefNodesList(ExpressionType expression, boolean createIfNotExist, PrismContext prismContext){
        if (expression == null) {
            return null;
        }
        if (createIfNotExist && prismContext == null) {
            throw new IllegalArgumentException("createIfNotExist is true but prismContext is null");
        }
        JAXBElement element = ExpressionUtil.findFirstEvaluatorByName(expression, SchemaConstantsGenerated.C_VALUE);
        ListXNode shadowRefNodes = null;
        if (element == null && createIfNotExist){
            element =  new JAXBElement(SchemaConstantsGenerated.C_VALUE, RawType.class, new RawType(prismContext));
            expression.getExpressionEvaluator().add(element);
        }
        if (element != null && element.getValue() instanceof RawType) {
            RawType raw = (RawType) element.getValue();
            XNode node = raw.getXnode();
            if (node == null && createIfNotExist) {
                raw = new RawType(prismContext.xnodeFactory().map(), prismContext);
                node = raw.getXnode();
                element.setValue(raw);
            }
            if (node instanceof MapXNode) {
                if (((MapXNode) node).containsKey(SHADOW_REF_KEY)) {
                    if (((MapXNode) node).get(SHADOW_REF_KEY) instanceof ListXNode) {
                        shadowRefNodes = (ListXNode) ((MapXNode) node).get(SHADOW_REF_KEY);
                    } else if (((MapXNode) node).get(SHADOW_REF_KEY) instanceof MapXNode) {
                        MapXNode shadowRef = (MapXNode) ((MapXNode) node).get(SHADOW_REF_KEY);
                        shadowRefNodes = prismContext.xnodeFactory().list(shadowRef);
                        prismContext.xnodeMutator().putToMapXNode((MapXNode) node, SHADOW_REF_KEY, shadowRefNodes);
                    }
                } else if (createIfNotExist) {
                    shadowRefNodes = prismContext.xnodeFactory().list();
                    prismContext.xnodeMutator().putToMapXNode((MapXNode) node, SHADOW_REF_KEY, shadowRefNodes);
                }
            } else if (createIfNotExist) {
                shadowRefNodes = prismContext.xnodeFactory().list();
                node = prismContext.xnodeFactory().map();
                prismContext.xnodeMutator().putToMapXNode((MapXNode) node, SHADOW_REF_KEY, shadowRefNodes);
                raw = new RawType(node, prismContext);
                element.setValue(raw);
            }
        }
        return shadowRefNodes;
    }

    public static void addShadowRefEvaluatorValue(ExpressionType expression, String oid, PrismContext prismContext) {
        XNodeFactory factory = prismContext.xnodeFactory();

        if (expression == null) {
            expression = new ExpressionType();      // TODO ??? this value is thrown away
        }
        JAXBElement valueElement = findFirstEvaluatorByName(expression, SchemaConstantsGenerated.C_VALUE);
        if (valueElement == null) {
           valueElement = new JAXBElement<>(SchemaConstants.C_VALUE, RawType.class, new RawType(prismContext));
           expression.getExpressionEvaluator().add(valueElement);
        }
        ListXNode shadowRefNodes = getShadowRefNodesList(expression, true, prismContext);
        if (StringUtils.isNotEmpty(oid)) {
            Map<QName, XNode> shadowRefNodeSource = new HashMap<>();
            shadowRefNodeSource.put(SHADOW_OID_KEY, factory.primitive(oid));
            shadowRefNodeSource.put(SHADOW_TYPE_KEY, factory.primitive(ShadowType.COMPLEX_TYPE.getLocalPart()));
            prismContext.xnodeMutator().addToListXNode(shadowRefNodes, factory.map(shadowRefNodeSource));
        }
    }

    public static List<String> getLiteralExpressionValues(ExpressionType expression) throws SchemaException{
        List<String> values = new ArrayList<>();
        List<JAXBElement> elements = ExpressionUtil.findAllEvaluatorsByName(expression, SchemaConstantsGenerated.C_VALUE);
        if (elements != null) {
            for (JAXBElement element : elements){
                 if (element.getValue() instanceof RawType){
                     RawType raw = (RawType) element.getValue();
                     if (raw != null) {
                         if (raw.getXnode() != null && raw.getXnode() instanceof PrimitiveXNode) {
                             PrimitiveXNode valueNode = (PrimitiveXNode) raw.getXnode();
                             if (valueNode != null && valueNode.getValue() != null) {
                                 values.add(valueNode.getValue().toString());
                             } else if (valueNode.getValueParser() != null) {
                                 values.add(valueNode.getValueParser().getStringValue());
                             }
                         } else if (raw.getParsedRealValue(String.class) != null) {
                             values.add(raw.getParsedRealValue(String.class));
                         }
                     }
                 }
            }
        }
        return values;
    }

    public static void updateLiteralExpressionValue(ExpressionType expression, List<String> values, PrismContext prismContext){
        if (expression == null) {
            expression = new ExpressionType();      // TODO ??? this is thrown away
        }
        removeEvaluatorByName(expression, SchemaConstantsGenerated.C_VALUE);
        for (String value : values) {
            PrimitiveXNode<String> newValueNode = prismContext.xnodeFactory().primitive(value);
            RawType raw = new RawType(newValueNode, prismContext);
            JAXBElement element =  new JAXBElement<>(SchemaConstantsGenerated.C_VALUE, RawType.class, raw);
            expression.expressionEvaluator(element);
        }
    }

    public static MapXNode getAssociationTargetSearchFilterValuesMap(ExpressionType expression){
        if (expression == null){
            return null;
        }
        JAXBElement element = ExpressionUtil.findFirstEvaluatorByName(expression, SchemaConstantsGenerated.C_ASSOCIATION_TARGET_SEARCH);
        if (element != null && element.getValue() != null && element.getValue() instanceof SearchObjectExpressionEvaluatorType) {
            SearchFilterType filter = ((SearchObjectExpressionEvaluatorType) element.getValue()).getFilter();
            if (filter == null){
                return null;
            }
            MapXNode filterValue = filter.getFilterClauseXNode();
            return filterValue != null && filterValue.containsKey(new QName("equal")) ?
                    (MapXNode)filterValue.get(new QName("equal")) : null;

        }
        return null;
    }

    public static String getTargetSearchExpPathValue(ExpressionType expression) {
        if (expression == null){
            return null;
        }
        MapXNode filterNodeMap = getAssociationTargetSearchFilterValuesMap(expression);
        if (filterNodeMap == null || !filterNodeMap.containsKey(new QName("path"))) {
            return null;
        }
        PrimitiveXNode<ItemPathType> pathValue = (PrimitiveXNode<ItemPathType>)filterNodeMap.get(new QName("path"));
        return pathValue != null && pathValue.getValue() != null ? pathValue.getValue().toString() : null;
    }

    public static String getTargetSearchExpValue(ExpressionType expression) {
        if (expression == null){
            return null;
        }
        MapXNode filterNodeMap = getAssociationTargetSearchFilterValuesMap(expression);
        if (filterNodeMap == null || !filterNodeMap.containsKey(new QName("value"))) {
            return null;
        }
        XNode node = filterNodeMap.get(new QName("value"));
        if (node instanceof ListXNode) {
            if (((ListXNode) node).size() > 0) {
                node = ((ListXNode) node).get(0);
            }
        }
        PrimitiveXNode valueNode = (PrimitiveXNode) node;
        if (valueNode == null) {
            return null;
        }
        if (valueNode.getValueParser() != null) {
            return valueNode.getValueParser().getStringValue();
        } else {
            return valueNode.getValue() != null ? valueNode.getValue().toString() : null;
        }

    }
}
