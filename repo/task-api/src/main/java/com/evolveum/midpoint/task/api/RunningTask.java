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

package com.evolveum.midpoint.task.api;

import com.evolveum.midpoint.schema.result.OperationResult;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 *  A task that is directly used to execute the handler code.
 *
 *  It is a very sensitive structure. First of all, it must be thread-safe because it is used for the handler code execution
 *  and at the same time accessed by clients that need to check its state. There are two such situations:
 *  (1) Lightweight Asynchronous Tasks because they have no persistent representation. The only instance that exists
 *      is the one that is being executed by a handler.
 *  (2) When a client asks the task manager for a current state of the task (typically being interested in operational stats).
 *      The information could be fetched from the repository but it would be a bit outdated. This situation can be avoided
 *      by retrieving information always from the repository, sacrificing information timeliness a bit. But the (1) cannot.
 *
 *  Some information related to task execution (e.g. list of lightweight asynchronous tasks, information on task thread, etc)
 *  is relevant only for running tasks. Therefore they are moved here.
 */
public interface RunningTask extends Task {

	/**
	 * Returns true if the task can run (was not interrupted).
	 *
	 * Will return false e.g. if shutdown was signaled.
	 *
	 * BEWARE: this flag is present only on the instance of the task that is being "executed", i.e. passed to
	 * task execution routine and task handler(s).
	 *
	 * @return true if the task can run
	 */
	boolean canRun();

	/**
	 * Creates a transient subtask, ready to execute a given LightweightTaskHandler.
	 *
	 * Owner is inherited from parent task to subtask.
	 *
	 * @return
	 */
	RunningTask createSubtask(LightweightTaskHandler handler);


	/**
	 * Returns the in-memory version of the parent task. Applicable only to lightweight subtasks.
	 * EXPERIMENTAL (use with care)
	 */
	RunningTask getParentForLightweightAsynchronousTask();

	LightweightTaskHandler getLightweightTaskHandler();

	boolean isLightweightAsynchronousTask();

	Collection<? extends RunningTask> getLightweightAsynchronousSubtasks();

	Collection<? extends RunningTask> getRunningLightweightAsynchronousSubtasks();

	boolean lightweightHandlerStartRequested();

	/**
	 * Starts execution of a transient task carrying a LightweightTaskHandler.
	 * (just a shortcut to analogous call in TaskManager)
	 */
	void startLightweightHandler();

	void startCollectingOperationStats(@NotNull StatisticsCollectionStrategy strategy, boolean initialExecution);

	void storeOperationStatsDeferred();

	/**
	 * Call from the thread that executes the task ONLY! Otherwise wrong data might be recorded.
	 */
	void refreshLowLevelStatistics();

	void storeOperationStats();

	// stores operation statistics if the time has come
	void storeOperationStatsIfNeeded();

	Long getLastOperationStatsUpdateTimestamp();

	void setOperationStatsUpdateInterval(long interval);

	long getOperationStatsUpdateInterval();

	void incrementProgressAndStoreStatsIfNeeded();

	void deleteLightweightAsynchronousSubtasks();

	// EXPERIMENTAL; consider moving to AbstractSearchIterativeResultHandler
	int getAndIncrementObjectsSeen();

	/**
	 * Must be called from the thread that executes the task.
	 * EXPERIMENTAL; consider moving to AbstractSearchIterativeResultHandler
	 */
	void startDynamicProfilingIfNeeded(RunningTask coordinatorTask, int objectsSeen);

	/**
	 * Must be called from the thread that executes the task.
	 */
	void stopDynamicProfiling();

	/**
	 * EXPERIMENTAL
	 */
	void requestTracingIfNeeded(OperationResult result, int objectsSeen);
}
