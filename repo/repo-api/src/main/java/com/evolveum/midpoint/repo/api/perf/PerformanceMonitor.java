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

package com.evolveum.midpoint.repo.api.perf;

import com.evolveum.midpoint.xml.ns._public.common.common_3.RepositoryStatisticsReportingConfigurationType;

/**
 *  EXPERIMENTAL. Probably temporary.
 */
public interface PerformanceMonitor {

	void clearGlobalPerformanceInformation();

	PerformanceInformation getGlobalPerformanceInformation();

	/**
	 * Starts gathering thread-local performance information, clearing existing (if any).
	 */
	void startThreadLocalPerformanceInformationCollection();

	/**
	 * Stops gathering thread-local performance information, clearing existing (if any).
	 */
	void stopThreadLocalPerformanceInformationCollection();

	PerformanceInformation getThreadLocalPerformanceInformation();

	void setConfiguration(RepositoryStatisticsReportingConfigurationType statistics);
}
