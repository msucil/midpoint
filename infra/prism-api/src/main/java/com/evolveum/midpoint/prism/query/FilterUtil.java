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

package com.evolveum.midpoint.prism.query;

import com.evolveum.prism.xml.ns._public.query_3.SearchFilterType;

/**
 *
 */
public class FilterUtil {

	// TODO move to SearchFilterType
	public static boolean isFilterEmpty(SearchFilterType filter) {
		return filter == null || (filter.getDescription() == null && !filter.containsFilterClause());
	}
}
