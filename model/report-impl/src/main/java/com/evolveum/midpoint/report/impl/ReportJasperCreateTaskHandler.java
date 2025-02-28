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
package com.evolveum.midpoint.report.impl;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.PostConstruct;

import com.evolveum.midpoint.prism.delta.DeltaFactory;
import com.evolveum.midpoint.repo.common.ObjectResolver;
import com.evolveum.midpoint.repo.common.commandline.CommandLineScriptExecutor;
import com.evolveum.midpoint.repo.common.expression.ExpressionVariables;

import com.evolveum.midpoint.task.api.*;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JRTemplate;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.design.JRDesignExpression;
import net.sf.jasperreports.engine.design.JRDesignParameter;
import net.sf.jasperreports.engine.design.JRDesignReportTemplate;
import net.sf.jasperreports.engine.design.JasperDesign;
import net.sf.jasperreports.engine.export.JRCsvExporter;
import net.sf.jasperreports.engine.export.JRRtfExporter;
import net.sf.jasperreports.engine.export.JRXlsExporter;
import net.sf.jasperreports.engine.export.oasis.JROdsExporter;
import net.sf.jasperreports.engine.export.oasis.JROdtExporter;
import net.sf.jasperreports.engine.export.ooxml.JRDocxExporter;
import net.sf.jasperreports.engine.export.ooxml.JRPptxExporter;
import net.sf.jasperreports.engine.export.ooxml.JRXlsxExporter;
import net.sf.jasperreports.engine.xml.JRXmlTemplateLoader;
import net.sf.jasperreports.export.Exporter;
import net.sf.jasperreports.export.ExporterOutput;
import net.sf.jasperreports.export.SimpleExporterInput;
import net.sf.jasperreports.export.SimpleOutputStreamExporterOutput;
import net.sf.jasperreports.export.SimpleWriterExporterOutput;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import com.evolveum.midpoint.model.api.ModelService;
import com.evolveum.midpoint.prism.Item;
import com.evolveum.midpoint.prism.PrismContainer;
import com.evolveum.midpoint.prism.PrismContainerValue;
import com.evolveum.midpoint.prism.PrismContext;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.PrismProperty;
import com.evolveum.midpoint.prism.delta.ObjectDelta;
import com.evolveum.midpoint.report.api.ReportConstants;
import com.evolveum.midpoint.report.api.ReportService;
import com.evolveum.midpoint.schema.ObjectDeltaOperation;
import com.evolveum.midpoint.schema.SearchResultList;
import com.evolveum.midpoint.schema.constants.ExpressionConstants;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.schema.util.MiscSchemaUtil;
import com.evolveum.midpoint.schema.util.ObjectTypeUtil;
import com.evolveum.midpoint.schema.util.ReportTypeUtil;
import com.evolveum.midpoint.task.api.TaskRunResult.TaskRunResultStatus;
import com.evolveum.midpoint.util.DebugUtil;
import com.evolveum.midpoint.util.exception.CommunicationException;
import com.evolveum.midpoint.util.exception.ConfigurationException;
import com.evolveum.midpoint.util.exception.ExpressionEvaluationException;
import com.evolveum.midpoint.util.exception.ObjectNotFoundException;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.util.exception.SecurityViolationException;
import com.evolveum.midpoint.util.exception.SystemException;
import com.evolveum.midpoint.util.logging.LoggingUtils;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.xml.ns._public.common.common_3.CommandLineScriptType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ExportType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.NodeType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ObjectType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ReportOutputType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ReportParameterType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ReportType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.SubreportType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.TaskPartitionDefinitionType;
import com.evolveum.prism.xml.ns._public.types_3.PolyStringType;
import net.sf.jasperreports.engine.JRParameter;
import net.sf.jasperreports.engine.fill.JRAbstractLRUVirtualizer;
import net.sf.jasperreports.engine.fill.JRFileVirtualizer;
import net.sf.jasperreports.engine.fill.JRGzipVirtualizer;
import net.sf.jasperreports.engine.fill.JRSwapFileVirtualizer;
import net.sf.jasperreports.engine.util.JRSwapFile;
import net.sf.jasperreports.governors.MaxPagesGovernor;
import net.sf.jasperreports.governors.TimeoutGovernor;

@Component
public class ReportJasperCreateTaskHandler implements TaskHandler {

    public static final String REPORT_CREATE_TASK_URI = "http://midpoint.evolveum.com/xml/ns/public/report/jasper/create/handler-3";
    private static final Trace LOGGER = TraceManager.getTrace(ReportJasperCreateTaskHandler.class);

    // TODO: is this a good place for those constants?
//    public static final String PARAMETER_TEMPLATE_STYLES = "baseTemplateStyles";
//    public static final String PARAMETER_REPORT_OID = "midpointReportOid";
//    public static final String PARAMETER_REPORT_OBJECT = "midpointReportObject";
//    public static final String PARAMETER_TASK = "midpointTask";
//    public static final String PARAMETER_OPERATION_RESULT = "midpointOperationResult";

    private static final String MIDPOINT_HOME = System.getProperty("midpoint.home");
    private static final String EXPORT_DIR = MIDPOINT_HOME + "export/";
    private static final String TEMP_DIR = MIDPOINT_HOME + "tmp/";

    private static final String JASPER_VIRTUALIZER_PKG = "net.sf.jasperreports.engine.fill";

    @Autowired private TaskManager taskManager;
    @Autowired private ModelService modelService;
    @Autowired private PrismContext prismContext;
    @Autowired private ReportService reportService;
    @Autowired @Qualifier("modelObjectResolver") private ObjectResolver objectResolver;
    @Autowired private CommandLineScriptExecutor commandLineScriptExecutor;

    @PostConstruct
    protected void initialize() {
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("Registering with taskManager as a handler for " + REPORT_CREATE_TASK_URI);
        }
        taskManager.registerHandler(REPORT_CREATE_TASK_URI, this);
    }

    @Override
    public TaskRunResult run(RunningTask task, TaskPartitionDefinitionType partition) {
        OperationResult parentResult = task.getResult();
        OperationResult result = parentResult.createSubresult(ReportJasperCreateTaskHandler.class.getSimpleName() + ".run");

        TaskRunResult runResult = new TaskRunResult();
        runResult.setOperationResult(result);

        recordProgress(task, 0, result);
        JRSwapFile swapFile = null;
        JRAbstractLRUVirtualizer virtualizer = null; // http://community.jaspersoft.com/wiki/virtualizers-jasperreports

        try {
            ReportType parentReport = objectResolver.resolve(task.getObjectRef(), ReportType.class, null, "resolving report", task, result);
            Map<String, Object> parameters = completeReport(parentReport, task, result);

            JasperReport jasperReport = loadJasperReport(parentReport);
            LOGGER.trace("compile jasper design, create jasper report : {}", jasperReport);

            PrismContainer<ReportParameterType> reportParams = (PrismContainer) task.getExtensionItem(ReportConstants.REPORT_PARAMS_PROPERTY_NAME);
            if (reportParams != null) {
                PrismContainerValue<ReportParameterType> reportParamsValues = reportParams.getValue();
                Collection<Item<?, ?>> items = reportParamsValues.getItems();
                for (Item item : items) {
                    PrismProperty pp = (PrismProperty) item;
                    String paramName = pp.getPath().lastName().getLocalPart();
                    Object value;
                    if (isSingleValue(paramName, jasperReport.getParameters())) {
                        value = pp.getRealValues().iterator().next();
                    } else {
                        value = pp.getRealValues();
                    }

                    parameters.put(paramName, value);

                }
            }



            String virtualizerS = parentReport.getVirtualizer();
            Integer virtualizerKickOn = parentReport.getVirtualizerKickOn();
            Integer maxPages = parentReport.getMaxPages();
            Integer timeout = parentReport.getTimeout();

            if (maxPages != null && maxPages > 0) {
                LOGGER.trace("Setting hardlimit on number of report pages: " + maxPages);
                jasperReport.setProperty(MaxPagesGovernor.PROPERTY_MAX_PAGES_ENABLED, Boolean.TRUE.toString());
                jasperReport.setProperty(MaxPagesGovernor.PROPERTY_MAX_PAGES, String.valueOf(maxPages));
            }

            if (timeout != null && timeout > 0) {
                LOGGER.trace("Setting timeout on report execution [ms]: " + timeout);
                jasperReport.setProperty(TimeoutGovernor.PROPERTY_TIMEOUT_ENABLED, Boolean.TRUE.toString());
                jasperReport.setProperty(TimeoutGovernor.PROPERTY_TIMEOUT, String.valueOf(timeout));
            }

            if (virtualizerS != null && virtualizerKickOn != null && virtualizerKickOn > 0) {

                String virtualizerClassName = JASPER_VIRTUALIZER_PKG + "." + virtualizerS;
                try {
                    Class<?> clazz = Class.forName(virtualizerClassName);

                    if (clazz.equals(JRSwapFileVirtualizer.class)) {
                        swapFile = new JRSwapFile(TEMP_DIR, 4096, 200);
                        virtualizer = new JRSwapFileVirtualizer(virtualizerKickOn, swapFile);
                    } else if (clazz.equals(JRGzipVirtualizer.class)) {
                        virtualizer = new JRGzipVirtualizer(virtualizerKickOn);
                    } else if (clazz.equals(JRFileVirtualizer.class)) {
                        virtualizer = new JRFileVirtualizer(virtualizerKickOn, TEMP_DIR);
                    } else {
                        throw new ClassNotFoundException("No support for virtualizer class: " + clazz.getName());
                    }

                    LOGGER.trace("Setting explicit Jasper virtualizer: " + virtualizer);
                    virtualizer.setReadOnly(false);
                    parameters.put(JRParameter.REPORT_VIRTUALIZER, virtualizer);
                } catch (ClassNotFoundException e) {
                    LOGGER.error("Cannot find Jasper virtualizer: " + e.getMessage());
                }
            }
            
            if (LOGGER.isTraceEnabled()) {
            	LOGGER.trace("All Report parameters:\n{}", DebugUtil.debugDump(parameters, 1));
            }

            JasperPrint jasperPrint = JasperFillManager.fillReport(jasperReport, parameters);
            LOGGER.trace("fill report : {}", jasperPrint);

            String reportFilePath = generateReport(parentReport, jasperPrint);
            LOGGER.trace("generate report : {}", reportFilePath);

            saveReportOutputType(reportFilePath, parentReport, task, result);
            LOGGER.trace("create report output type : {}", reportFilePath);

            if (parentReport.getPostReportScript() != null) {
                processPostReportScript(parentReport, reportFilePath, task, result);
            }
            result.computeStatus();

        } catch (Exception ex) {
            LOGGER.error("CreateReport: {}", ex.getMessage(), ex);
            result.recordFatalError(ex.getMessage(), ex);
            runResult.setRunResultStatus(TaskRunResultStatus.PERMANENT_ERROR);
            return runResult;
        } finally {
            if (swapFile != null) {
                swapFile.dispose();
            }
            if (virtualizer != null) {
                virtualizer.cleanup();
            }
        }

        // This "run" is finished. But the task goes on ...
        runResult.setRunResultStatus(TaskRunResultStatus.FINISHED);
        LOGGER.trace("CreateReportTaskHandler.run stopping");
        return runResult;
    }

    private boolean isSingleValue(String paramName, JRParameter[] jrParams) {
    	JRParameter param = Arrays.stream(jrParams).filter(p -> p.getName().equals(paramName)).findAny().get();
    	return !List.class.isAssignableFrom(param.getValueClass());
    }

    private Map<String, Object> completeReport(ReportType parentReport, Task task, OperationResult result) throws SchemaException, ObjectNotFoundException, CommunicationException, ConfigurationException, SecurityViolationException, ExpressionEvaluationException {
        return completeReport(parentReport, null, null, task, result);
    }

    private Map<String, Object> completeReport(ReportType parentReport, JasperReport subReport, String subReportName, Task task, OperationResult result) throws SchemaException, ObjectNotFoundException, CommunicationException, ConfigurationException, SecurityViolationException, ExpressionEvaluationException {
        Map<String, Object> params = new HashMap<>();

        if (subReport != null && StringUtils.isNotBlank(subReportName)) {
            params.put(subReportName, subReport);
        }

        Map<String, Object> parameters = prepareReportParameters(parentReport, task, result);
        params.putAll(parameters);

        Map<String, Object> subreportParameters = processSubreportParameters(parentReport, task, result);
        params.putAll(subreportParameters);
        
        if (LOGGER.isTraceEnabled()) {
        	LOGGER.trace("create report params:\n{}", DebugUtil.debugDump(parameters, 1));
        }
        return params;
    }

    private Map<String, Object> prepareReportParameters(ReportType reportType, Task task, OperationResult parentResult) {
        Map<String, Object> params = new HashMap<>();
        if (reportType.getTemplateStyle() != null) {
            byte[] reportTemplateStyleBase64 = reportType.getTemplateStyle();
            byte[] reportTemplateStyle = Base64.decodeBase64(reportTemplateStyleBase64);
            try {
                LOGGER.trace("Style template string {}", new String(reportTemplateStyle));
                InputStream inputStreamJRTX = new ByteArrayInputStream(reportTemplateStyle);
                JRTemplate templateStyle = JRXmlTemplateLoader.load(inputStreamJRTX);
                params.put(ReportTypeUtil.PARAMETER_TEMPLATE_STYLES, templateStyle);
                LOGGER.trace("Style template parameter {}", templateStyle);

            } catch (Exception ex) {
                LOGGER.error("Error create style template parameter {}", ex.getMessage());
                throw new SystemException(ex);
            }

        }
        
        if (parentResult == null) {
        	throw new IllegalArgumentException("No result");
        }

        // for our special datasource
        params.put(ReportTypeUtil.PARAMETER_REPORT_OID, reportType.getOid());
        params.put(ReportTypeUtil.PARAMETER_REPORT_OBJECT, reportType.asPrismObject());
        params.put(ReportTypeUtil.PARAMETER_TASK, task);
        params.put(ReportTypeUtil.PARAMETER_OPERATION_RESULT, parentResult);
        params.put(ReportService.PARAMETER_REPORT_SERVICE, reportService);

        return params;
    }

    private Map<String, Object> processSubreportParameters(ReportType reportType, Task task, OperationResult subreportResult) throws SchemaException, ObjectNotFoundException, CommunicationException, ConfigurationException, SecurityViolationException, ExpressionEvaluationException {
        Map<String, Object> subreportParameters = new HashMap<>();
        for (SubreportType subreport : reportType.getSubreport()) {
            Map<String, Object> subreportParam = getSubreportParameters(subreport, task, subreportResult);
            LOGGER.trace("create subreport params : {}", subreportParam);
            subreportParameters.putAll(subreportParam);

        }
        return subreportParameters;
    }

    private Map<String, Object> getSubreportParameters(SubreportType subreportType, Task task, OperationResult subResult)
            throws SchemaException, ObjectNotFoundException, CommunicationException, ConfigurationException, SecurityViolationException, ExpressionEvaluationException {
        Map<String, Object> reportParams = new HashMap<>();
        ReportType reportType = objectResolver.resolve(subreportType.getReportRef(), ReportType.class, null,
                "resolve subreport", task, subResult);

        Map<String, Object> parameters = prepareReportParameters(reportType, task, subResult);
        reportParams.putAll(parameters);

        JasperReport jasperReport = loadJasperReport(reportType);
        reportParams.put(subreportType.getName(), jasperReport);

        Map<String, Object> subReportParams = processSubreportParameters(reportType, task, subResult);
        reportParams.putAll(subReportParams);

        return reportParams;
    }
    
	private JasperReport loadJasperReport(ReportType reportType) throws SchemaException {

		if (reportType.getTemplate() == null) {
			throw new IllegalStateException("Could not create report. No jasper template defined.");
		}
		
		LOGGER.trace("Loading Jasper report for {}", reportType);
		try	 {
	    	 	JasperDesign jasperDesign = ReportTypeUtil.loadJasperDesign(reportType.getTemplate());
//	    	 	LOGGER.trace("load jasper design : {}", jasperDesign);
	    	 	jasperDesign.setLanguage(ReportTypeUtil.REPORT_LANGUAGE);

			 if (reportType.getTemplateStyle() != null){
				JRDesignReportTemplate templateStyle = new JRDesignReportTemplate(new JRDesignExpression("$P{" + ReportTypeUtil.PARAMETER_TEMPLATE_STYLES + "}"));
				jasperDesign.addTemplate(templateStyle);
				
				jasperDesign.addParameter(createParameter(ReportTypeUtil.PARAMETER_TEMPLATE_STYLES, JRTemplate.class));
				
			 }

			 jasperDesign.addParameter(createParameter("finalQuery", Object.class));
			 jasperDesign.addParameter(createParameter(ReportTypeUtil.PARAMETER_REPORT_OID, String.class));
			 //TODO is this right place, we don't see e.g. task
//			 jasperDesign.addParameter(createParameter(PARAMETER_TASK, Object.class));
			 jasperDesign.addParameter(createParameter(ReportTypeUtil.PARAMETER_OPERATION_RESULT, OperationResult.class));
			 
			 //TODO maybe other paramteres? sunch as PARAMETER_REPORT_OBJECT PARAMETER_REPORT_SERVICE ???

			 JasperReport jasperReport = JasperCompileManager.compileReport(jasperDesign);
			 
			 LOGGER.trace("Loaded Jasper report for {}: {}", reportType, jasperReport);
			 
			 return jasperReport;
			 
		 } catch (JRException ex) {
			 LOGGER.error("Error loading Jasper report for {}: {}", reportType, ex.getMessage(), ex);
			 throw new SchemaException(ex.getMessage(), ex.getCause());
		 }
	}
	
	private JRDesignParameter createParameter(String paramName, Class<?> valueClass) {
		JRDesignParameter param = new JRDesignParameter();
		param.setName(paramName);
		param.setValueClass(valueClass);
		param.setForPrompting(false);
		param.setSystemDefined(true);
		return param;
		
	}


    protected void recordProgress(Task task, long progress, OperationResult opResult) {
        try {
            task.setProgressImmediate(progress, opResult);
        } catch (ObjectNotFoundException e) {             // these exceptions are of so little probability and harmless, so we just log them and do not report higher
            LoggingUtils.logException(LOGGER, "Couldn't record progress to task {}, probably because the task does not exist anymore", e, task);
        } catch (SchemaException e) {
            LoggingUtils.logException(LOGGER, "Couldn't record progress to task {}, due to unexpected schema exception", e, task);
        }
    }

    private String generateReport(ReportType reportType, JasperPrint jasperPrint) throws JRException {
        String destinationFileName = getDestinationFileName(reportType);
        switch (getExport(reportType)) {
            case PDF:
                JasperExportManager.exportReportToPdfFile(jasperPrint, destinationFileName);
                break;
            case XML:
                JasperExportManager.exportReportToXmlFile(jasperPrint, destinationFileName, true);
                break;
            case XML_EMBED:
                JasperExportManager.exportReportToXmlFile(jasperPrint, destinationFileName, true);
                break;
            case XHTML:
            case HTML:
                JasperExportManager.exportReportToHtmlFile(jasperPrint, destinationFileName);
                break;
            case CSV:
            case RTF:
            case XLS:
            case ODT:
            case ODS:
            case DOCX:
            case XLSX:
            case PPTX:
                Exporter exporter = createExporter(reportType.getExport(), jasperPrint, destinationFileName);
                if (exporter != null) {
                    exporter.exportReport();
                }
            default:
                break;
        }
        return destinationFileName;
    }

    private Exporter createExporter(ExportType type, JasperPrint jasperPrint, String destinationFileName) {
        Exporter exporter;
        boolean writerOutput;
        switch (type) {
            case CSV:
                writerOutput = true;
                exporter = new JRCsvExporter();
                break;
            case RTF:
                writerOutput = true;
                exporter = new JRRtfExporter();
                break;
            case XLS:
                writerOutput = false;
                exporter = new JRXlsExporter();
                break;
            case ODT:
                writerOutput = false;
                exporter = new JROdtExporter();
                break;
            case ODS:
                writerOutput = false;
                exporter = new JROdsExporter();
                break;
            case DOCX:
                writerOutput = false;
                exporter = new JRDocxExporter();
                break;
            case XLSX:
                writerOutput = false;
                exporter = new JRXlsxExporter();
                break;
            case PPTX:
                writerOutput = false;
                exporter = new JRPptxExporter();
                break;
            default:
                return null;
        }
        //noinspection unchecked
        exporter.setExporterInput(new SimpleExporterInput(jasperPrint));
        ExporterOutput output = writerOutput
                ? new SimpleWriterExporterOutput(destinationFileName)
                : new SimpleOutputStreamExporterOutput(destinationFileName);
        //noinspection unchecked
        exporter.setExporterOutput(output);
        return exporter;
    }

    public static String getDateTime() {
        Date createDate = new Date(System.currentTimeMillis());
        SimpleDateFormat formatDate = new SimpleDateFormat("dd-MM-yyyy hh-mm-ss.SSS");
        return formatDate.format(createDate);
    }

    protected String getDestinationFileName(ReportType reportType) {
        File exportFolder = new File(EXPORT_DIR);
        if (!exportFolder.exists() || !exportFolder.isDirectory()) {
            exportFolder.mkdir();
        }

        String output = EXPORT_DIR + reportType.getName().getOrig() + " " + getDateTime();
        
        if (getExport(reportType) == ExportType.XML_EMBED) {
            return output + "_embed.xml";
        } 

        return output + "." + getExport(reportType).value();
    }

    protected void saveReportOutputType(String filePath, ReportType reportType, Task task, OperationResult parentResult) throws Exception {

        String fileName = FilenameUtils.getBaseName(filePath);
        String reportOutputName = fileName + " - " + getExport(reportType).value();

        ReportOutputType reportOutputType = new ReportOutputType();
        prismContext.adopt(reportOutputType);

        reportOutputType.setFilePath(filePath);
        reportOutputType.setReportRef(MiscSchemaUtil.createObjectReference(reportType.getOid(), ReportType.COMPLEX_TYPE));
        reportOutputType.setName(new PolyStringType(reportOutputName));
        reportOutputType.setDescription(reportType.getDescription() + " - " + getExport(reportType).value());
        reportOutputType.setExportType(getExport(reportType));


        SearchResultList<PrismObject<NodeType>> nodes = modelService.searchObjects(NodeType.class, prismContext
		        .queryFor(NodeType.class).item(NodeType.F_NODE_IDENTIFIER).eq(task.getNode()).build(), null, task, parentResult);
        if (nodes == null || nodes.isEmpty()) {
        	LOGGER.error("Could not found node for storing the report.");
        	throw new ObjectNotFoundException("Could not find node where to save report");
        }

        if (nodes.size() > 1) {
        	LOGGER.error("Found more than one node with ID {}.", task.getNode());
        	throw new IllegalStateException("Found more than one node with ID " + task.getNode());
        }

        reportOutputType.setNodeRef(ObjectTypeUtil.createObjectRef(nodes.iterator().next(), prismContext));

        ObjectDelta<ReportOutputType> objectDelta = null;
        Collection<ObjectDelta<? extends ObjectType>> deltas = new ArrayList<>();
        OperationResult subResult = null;

        objectDelta = DeltaFactory.Object.createAddDelta((PrismObject<ReportOutputType>) reportOutputType.asPrismObject());
        deltas.add(objectDelta);
        subResult = parentResult.createSubresult(ReportJasperCreateTaskHandler.class.getName() + "createRepourtOutput");

        Collection<ObjectDeltaOperation<? extends ObjectType>> executedDeltas = modelService.executeChanges(deltas, null, task, subResult);
        String reportOutputOid = ObjectDeltaOperation.findAddDeltaOid(executedDeltas, reportOutputType.asPrismObject());

		LOGGER.debug("Created report output with OID {}", reportOutputOid);
		PrismProperty<String> outputOidProperty = prismContext.getSchemaRegistry().findPropertyDefinitionByElementName(ReportConstants.REPORT_OUTPUT_OID_PROPERTY_NAME).instantiate();
		outputOidProperty.setRealValue(reportOutputOid);
		task.setExtensionPropertyImmediate(outputOidProperty, subResult);

        subResult.computeStatus();
    }

    protected void processPostReportScript(ReportType parentReport, String reportOutputFilePath, Task task, OperationResult parentResult ) {
    	CommandLineScriptType scriptType = parentReport.getPostReportScript();
        if (scriptType == null) {
        	LOGGER.debug("No post report script found in {}, skipping", parentReport);
        	return;
        }
        
        ExpressionVariables variables = new ExpressionVariables();
        variables.put(ExpressionConstants.VAR_OBJECT, parentReport, parentReport.asPrismObject().getDefinition());
        variables.put(ExpressionConstants.VAR_TASK, task.getTaskPrismObject().asObjectable(), task.getTaskPrismObject().getDefinition());
        variables.put(ExpressionConstants.VAR_FILE, commandLineScriptExecutor.getOsSpecificFilePath(reportOutputFilePath), String.class);

        try {
        	
			commandLineScriptExecutor.executeScript(scriptType, variables, "post-report script in "+parentReport, task, parentResult);
			
        } catch (Exception e) {
            LOGGER.error("An exception has occurred during post report script execution {}",e.getLocalizedMessage());
            // LoggingUtils.logExceptionAsWarning(LOGGER,"And unexpected exception occurred during post report script execution",e, task);
        }
    }
    
    protected ExportType getExport(ReportType report) {
    	return report.getExport();
    }
    

    @Override
    public Long heartbeat(Task task) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void refreshStatus(Task task) {
        // TODO Auto-generated method stub

    }

    @Override
    public String getCategoryName(Task task) {
        return TaskCategory.REPORT;
    }
}
