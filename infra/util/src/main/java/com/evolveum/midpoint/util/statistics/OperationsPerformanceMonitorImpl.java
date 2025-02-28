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

package com.evolveum.midpoint.util.statistics;

import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;

/**
 *
 */
public class OperationsPerformanceMonitorImpl implements OperationsPerformanceMonitor {

    public static final OperationsPerformanceMonitorImpl INSTANCE = new OperationsPerformanceMonitorImpl();
    private static final Trace LOGGER = TraceManager.getTrace(OperationsPerformanceMonitorImpl.class);

    /**
     * Aggregated performance information local to the thread.
     */
    private final ThreadLocal<OperationsPerformanceInformationImpl> threadLocalPerformanceInformation = new ThreadLocal<>();

    /**
     * Aggregated performance information common to all threads.
     */
    private final OperationsPerformanceInformationImpl globalPerformanceInformation = new OperationsPerformanceInformationImpl();

    @Override
    public void clearGlobalPerformanceInformation() {
        globalPerformanceInformation.clear();
    }

    @Override
    public OperationsPerformanceInformationImpl getGlobalPerformanceInformation() {
        return globalPerformanceInformation;
    }

    @Override
    public void startThreadLocalPerformanceInformationCollection() {
        threadLocalPerformanceInformation.set(new OperationsPerformanceInformationImpl());
    }

    @Override
    public OperationsPerformanceInformationImpl getThreadLocalPerformanceInformation() {
        return threadLocalPerformanceInformation.get();
    }

    @Override
    public void stopThreadLocalPerformanceInformationCollection() {
        threadLocalPerformanceInformation.remove();
    }

    public void initialize() {
        globalPerformanceInformation.clear();
        threadLocalPerformanceInformation.remove();         // at least for this thread; other threads have to do their own homework
    }

    public void shutdown() {
        LOGGER.info("Methods performance Monitor shutting down.");
        LOGGER.debug("Global performance information:\n{}", globalPerformanceInformation.debugDump());
    }

    void registerInvocationCompletion(OperationInvocationRecord invocation) {
        globalPerformanceInformation.register(invocation);
        OperationsPerformanceInformationImpl local = getThreadLocalPerformanceInformation();
        if (local != null) {
            local.register(invocation);
        }
    }
}
