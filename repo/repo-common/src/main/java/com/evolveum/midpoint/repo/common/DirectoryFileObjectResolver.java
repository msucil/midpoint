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
package com.evolveum.midpoint.repo.common;

import java.io.File;
import java.io.IOException;
import java.util.Collection;

import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.query.ObjectQuery;
import com.evolveum.midpoint.prism.util.PrismTestUtil;
import com.evolveum.midpoint.schema.GetOperationOptions;
import com.evolveum.midpoint.schema.ResultHandler;
import com.evolveum.midpoint.schema.SearchResultList;
import com.evolveum.midpoint.schema.SelectorOptions;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.util.exception.CommunicationException;
import com.evolveum.midpoint.util.exception.ConfigurationException;
import com.evolveum.midpoint.util.exception.ExpressionEvaluationException;
import com.evolveum.midpoint.util.exception.ObjectNotFoundException;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.util.exception.SecurityViolationException;
import com.evolveum.midpoint.util.exception.SystemException;
import com.evolveum.midpoint.xml.ns._public.common.common_3.FunctionLibraryType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ObjectReferenceType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ObjectType;

/**
 * Object resolver that works on files in a directory.
 * This is only used in tests. But due to complicated dependencies this is
 * part of main code. That does not hurt much.
 * 
 * @author Radovan Semancik
 *
 */
public class DirectoryFileObjectResolver implements ObjectResolver {

	private File directory;

	public DirectoryFileObjectResolver(File directory) {
		super();
		this.directory = directory;
	}

	@Override
	public <T extends ObjectType> T resolve(ObjectReferenceType ref, Class<T> expectedType,
											Collection<SelectorOptions<GetOperationOptions>> options, String contextDescription,
											Task task, OperationResult result) throws ObjectNotFoundException, SchemaException, CommunicationException, ConfigurationException, SecurityViolationException, ExpressionEvaluationException {
		return getObject(expectedType, ref.getOid(), options, task, result);
	}

	private String oidToFilename(String oid) {
		return oid+".xml";
	}

	@Override
	public <O extends ObjectType> void searchIterative(Class<O> type, ObjectQuery query,
													   Collection<SelectorOptions<GetOperationOptions>> options, ResultHandler<O> handler,
													   Task task, OperationResult parentResult) throws SchemaException, ObjectNotFoundException,
			CommunicationException, ConfigurationException, SecurityViolationException {
		//TODO: do we want to test custom libraries in the "unit" tests
		if (type.equals(FunctionLibraryType.class)) {
			return;
		}
		throw new UnsupportedOperationException();
	}

	@Override
	public <O extends ObjectType> SearchResultList<PrismObject<O>> searchObjects(Class<O> type, ObjectQuery query,
			Collection<SelectorOptions<GetOperationOptions>> options, Task task, OperationResult parentResult) {
		throw new UnsupportedOperationException();
	}

	@Override
	public <T extends ObjectType> T getObject(Class<T> clazz, String oid,
			Collection<SelectorOptions<GetOperationOptions>> options, Task task, OperationResult result)
			throws ObjectNotFoundException, CommunicationException, SchemaException, ConfigurationException,
			SecurityViolationException, ExpressionEvaluationException {
		File file = new File( directory, oidToFilename(oid));
		if (file.exists()) {
			try {
				return (T)PrismTestUtil.parseObject(file).asObjectable();
			} catch (IOException e) {
				throw new SystemException(e.getMessage(), e);
			}
		} else {
			throw new ObjectNotFoundException("Object "+oid+" does not exists");
		}
	}


}
