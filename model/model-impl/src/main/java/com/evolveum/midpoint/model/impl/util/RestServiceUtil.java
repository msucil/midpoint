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

package com.evolveum.midpoint.model.impl.util;

import java.net.URI;
import java.util.List;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.UriInfo;

import com.evolveum.midpoint.util.logging.LoggingUtils;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import org.apache.commons.lang.StringUtils;
import org.apache.cxf.common.util.Base64Utility;
import org.apache.cxf.jaxrs.ext.MessageContext;

import com.evolveum.midpoint.model.api.ModelExecuteOptions;
import com.evolveum.midpoint.security.api.RestAuthenticationMethod;
import com.evolveum.midpoint.model.impl.security.SecurityHelper;
import com.evolveum.midpoint.schema.constants.SchemaConstants;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.security.api.ConnectionEnvironment;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.util.exception.AuthorizationException;
import com.evolveum.midpoint.util.exception.CommunicationException;
import com.evolveum.midpoint.util.exception.ConcurrencyException;
import com.evolveum.midpoint.util.exception.ConfigurationException;
import com.evolveum.midpoint.util.exception.ExpressionEvaluationException;
import com.evolveum.midpoint.util.exception.NoFocusNameSchemaException;
import com.evolveum.midpoint.util.exception.ObjectAlreadyExistsException;
import com.evolveum.midpoint.util.exception.ObjectNotFoundException;
import com.evolveum.midpoint.util.exception.PolicyViolationException;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.util.exception.SecurityViolationException;
import com.evolveum.midpoint.util.exception.TunnelException;
import com.evolveum.midpoint.xml.ns._public.common.common_3.OperationResultType;

/**
 * @author mederly (only copied existing code)
 */
public class RestServiceUtil {

	private static final Trace LOGGER = TraceManager.getTrace(RestServiceUtil.class);

	public static final String MESSAGE_PROPERTY_TASK_NAME = "task";
	private static final String QUERY_PARAMETER_OPTIONS = "options";
	public static final String OPERATION_RESULT_STATUS = "OperationResultStatus";
	public static final String OPERATION_RESULT_MESSAGE = "OperationResultMessage";

	public static final String APPLICATION_YAML = "application/yaml";

	public static Response handleException(OperationResult result, Throwable t) {
		LoggingUtils.logUnexpectedException(LOGGER, "Got exception while servicing REST request: {}", t,
				result != null ? result.getOperation() : "(null)");
		return handleExceptionNoLog(result, t);
	}

	public static Response handleExceptionNoLog(OperationResult result, Throwable t) {
		return createErrorResponseBuilder(result, t).build();
	}

	public static <T> Response createResponse(Response.Status statusCode, OperationResult result) {

		return createResponse(statusCode, null, result, false);

	}

	public static <T> Response createResponse(Response.Status statusCode, T body, OperationResult result) {

		return createResponse(statusCode, body, result, false);

	}

	public static <T> Response createResponse(Response.Status statusCode, T body, OperationResult result, boolean sendOriginObjectIfNotSuccess) {
		result.computeStatusIfUnknown();

		if (result.isPartialError()) {
			return createBody(Response.status(250), sendOriginObjectIfNotSuccess, body, result).build();
		} else if (result.isHandledError()) {
			return createBody(Response.status(240), sendOriginObjectIfNotSuccess, body, result).build();
		}

		return body == null ? Response.status(statusCode).build() : Response.status(statusCode).entity(body).build();
	}

	private static <T> ResponseBuilder createBody(ResponseBuilder builder, boolean sendOriginObjectIfNotSuccess, T body, OperationResult result) {
		if (sendOriginObjectIfNotSuccess) {
			return builder.entity(body);
		}
		return builder.entity(result);

	}

	public static <T> Response createResponse(Response.Status statusCode, URI location, OperationResult result) {
		result.computeStatusIfUnknown();

		if (result.isPartialError()) {
			return createBody(Response.status(250), false, null, result).location(location).build();
		} else if (result.isHandledError()) {
			return createBody(Response.status(240), false, null, result).location(location).build();
		}


		return location == null ? Response.status(statusCode).build() : Response.status(statusCode).location(location).build();
	}



	public static Response.ResponseBuilder createErrorResponseBuilder(OperationResult result, Throwable t) {
		if (t instanceof ObjectNotFoundException) {
			return createErrorResponseBuilder(Response.Status.NOT_FOUND, result);
		}

		if (t instanceof CommunicationException || t instanceof TunnelException) {
			return createErrorResponseBuilder(Response.Status.GATEWAY_TIMEOUT, result);
		}

		if (t instanceof SecurityViolationException) {
			return createErrorResponseBuilder(Response.Status.FORBIDDEN, result);
		}

		if (t instanceof ConfigurationException) {
			return createErrorResponseBuilder(Response.Status.BAD_GATEWAY, result);
		}

		if (t instanceof SchemaException || t instanceof ExpressionEvaluationException) {
			return createErrorResponseBuilder(Response.Status.BAD_REQUEST, result);
		}

		if (t instanceof PolicyViolationException
				|| t instanceof ObjectAlreadyExistsException
				|| t instanceof ConcurrencyException) {
			return createErrorResponseBuilder(Response.Status.CONFLICT, result);
		}

		return createErrorResponseBuilder(Response.Status.INTERNAL_SERVER_ERROR, result);
	}

	public static Response.ResponseBuilder createErrorResponseBuilder(Response.Status status, OperationResult result) {
		OperationResultType resultBean;
		if (result != null) {
			result.computeStatusIfUnknown();
			resultBean = result.createOperationResultType();
		} else {
			resultBean = null;
		}
		return createErrorResponseBuilder(status, resultBean);
	}

	public static Response.ResponseBuilder createErrorResponseBuilder(Response.Status status, OperationResultType message) {
		return Response.status(status).entity(message);
	}

	public static ModelExecuteOptions getOptions(UriInfo uriInfo){
    	List<String> options = uriInfo.getQueryParameters().get(QUERY_PARAMETER_OPTIONS);
		return ModelExecuteOptions.fromRestOptions(options);
    }

	public static Task initRequest(MessageContext mc) {
		// No need to audit login. it was already audited during authentication
		return (Task) mc.get(MESSAGE_PROPERTY_TASK_NAME);
	}

	public static void finishRequest(Task task, SecurityHelper securityHelper) {
		task.getResult().computeStatus();
		ConnectionEnvironment connEnv = ConnectionEnvironment.create(SchemaConstants.CHANNEL_REST_URI);
		connEnv.setSessionIdOverride(task.getTaskIdentifier());
		securityHelper.auditLogout(connEnv, task);
	}

	// slightly experimental
	public static Response.ResponseBuilder createResultHeaders(Response.ResponseBuilder builder, OperationResult result) {
		return builder.entity(result);
//				.header(OPERATION_RESULT_STATUS, OperationResultStatus.createStatusType(result.getStatus()).value())
//				.header(OPERATION_RESULT_MESSAGE, result.getMessage());
	}

	public static void createAbortMessage(ContainerRequestContext requestCtx){
		requestCtx.abortWith(Response.status(Status.UNAUTHORIZED)
				.header("WWW-Authenticate", RestAuthenticationMethod.BASIC.getMethod() + " realm=\"midpoint\", " + RestAuthenticationMethod.SECURITY_QUESTIONS.getMethod()).build());
	}

	public static void createSecurityQuestionAbortMessage(ContainerRequestContext requestCtx, String secQChallenge){
		String challenge = "";
		if (StringUtils.isNotBlank(secQChallenge)) {
			challenge = " " + Base64Utility.encode(secQChallenge.getBytes());
		}

		requestCtx.abortWith(Response.status(Status.UNAUTHORIZED)
				.header("WWW-Authenticate",
						RestAuthenticationMethod.SECURITY_QUESTIONS.getMethod() + challenge)
				.build());
	}
}
