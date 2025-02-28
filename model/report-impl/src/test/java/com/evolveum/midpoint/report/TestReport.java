/**
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
package com.evolveum.midpoint.report;

import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertNotNull;
import static org.testng.AssertJUnit.assertNull;
import static org.testng.AssertJUnit.assertTrue;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.context.ContextConfiguration;
import org.testng.annotations.Test;

import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.schema.SearchResultList;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.test.util.MidPointTestConstants;
import com.evolveum.midpoint.test.util.TestUtil;
import com.evolveum.midpoint.util.exception.CommunicationException;
import com.evolveum.midpoint.util.exception.ConfigurationException;
import com.evolveum.midpoint.util.exception.ExpressionEvaluationException;
import com.evolveum.midpoint.util.exception.ObjectNotFoundException;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.util.exception.SecurityViolationException;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ReportType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.TaskType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.UserType;

/**
 * Basic report tests.
 */
@ContextConfiguration(locations = { "classpath:ctx-report-test-main.xml" })
@DirtiesContext(classMode = ClassMode.AFTER_CLASS)
public class TestReport extends AbstractReportIntegrationTest {

	@Override
	public void initSystem(Task initTask, OperationResult initResult) throws Exception {
		super.initSystem(initTask, initResult);
		
		repoAddObjectFromFile(REPORT_USER_LIST_FILE, ReportType.class, initResult);
		repoAddObjectFromFile(REPORT_USER_LIST_EXPRESSIONS_CSV_FILE, ReportType.class, initResult);
		repoAddObjectFromFile(REPORT_USER_LIST_EXPRESSIONS_POISONOUS_QUERY_CSV_FILE, ReportType.class, initResult);
		repoAddObjectFromFile(REPORT_USER_LIST_EXPRESSIONS_POISONOUS_FIELD_CSV_FILE, ReportType.class, initResult);
		repoAddObjectFromFile(REPORT_USER_LIST_SCRIPT_FILE, ReportType.class, initResult);
		
		// Let's make this more interesting by adding a couple of users
		importObjectsFromFileNotRaw(USERS_MONKEY_ISLAND_FILE, initTask, initResult);
	}


  @Test
  public void test100ReportUserList() throws Exception {
	  final String TEST_NAME = "test100ReportUserList";
      displayTestTitle(TEST_NAME);

      Task task = createTask(TEST_NAME);
      OperationResult result = task.getResult();
      
      PrismObject<ReportType> report = getObject(ReportType.class, REPORT_USER_LIST_OID);
      
      // WHEN
      displayWhen(TEST_NAME);
      reportManager.runReport(report, null, task, result);
      
      assertInProgress(result);
      
      display("Background task", task);
      
      waitForTaskFinish(task.getOid(), true);

      // THEN
      displayThen(TEST_NAME);
      PrismObject<TaskType> finishedTask = getTask(task.getOid());
      display("Background task", finishedTask);
      
      assertSuccess("Report task result", finishedTask.asObjectable().getResult());
  }
  
  /**
   * Ordinary user list report. Should work well under all circumstances.
   * Even with safe expression profile.
   */
  @Test
  public void test110ReportUserListExpressionsCsv() throws Exception {
	  final String TEST_NAME = "test110ReportUserListExpressionsCsv";
	  testReportListUsersCsv(TEST_NAME, REPORT_USER_LIST_EXPRESSIONS_CSV_OID);
  }
  
  /**
   * Reports with poisonous operations in the query. This should work with null profile.
   * But it should fail with safe profile.
   * Field operations are safe in this report, just the query is poisonous.
   */
  @Test
  public void test112ReportUserListExpressionsPoisonousQueryCsv() throws Exception {
	  final String TEST_NAME = "test112ReportUserListExpressionsPoisonousQueryCsv";
	  testReportListUsersCsv(TEST_NAME, REPORT_USER_LIST_EXPRESSIONS_POISONOUS_QUERY_CSV_OID);
  }

  /**
   * Reports with poisonous operations in the field expression. This should work with null profile.
   * But it should fail with safe profile.
   * Query expression is safe in this report, just fields are poisonous.
   */
  @Test
  public void test114ReportUserListExpressionsPoisonousFieldCsv() throws Exception {
	  final String TEST_NAME = "test114ReportUserListExpressionsPoisonousFieldCsv";
	  testReportListUsersCsv(TEST_NAME, REPORT_USER_LIST_EXPRESSIONS_POISONOUS_FIELD_CSV_OID);
  }
  
  protected void testReportListUsersCsv(final String TEST_NAME, String reportOid) throws Exception {
	  PrismObject<ReportType> report = getObject(ReportType.class, reportOid);
	  
      PrismObject<TaskType> finishedTask = runReportTaskListUsersCsv(TEST_NAME, report);
      
      assertSuccess("Finished report task result", finishedTask.asObjectable().getResult());
      
      checkCsvUserReport(report);
  }
  
  protected void testReportListUsersCsvFailure(final String TEST_NAME, String reportOid) throws Exception {
	  PrismObject<ReportType> report = getObject(ReportType.class, reportOid);
	  
      PrismObject<TaskType> finishedTask = runReportTaskListUsersCsv(TEST_NAME, report);
      
      assertFailure("Finished report task result", finishedTask.asObjectable().getResult());
      
      assertNoCsvReport(report);
  }

  protected PrismObject<TaskType> runReportTaskListUsersCsv(final String TEST_NAME, PrismObject<ReportType> report) throws Exception {
	  displayTestTitle(TEST_NAME);
	  
      Task task = createTask(TEST_NAME);
      OperationResult result = task.getResult();
      
      // WHEN
      displayWhen(TEST_NAME);
      reportManager.runReport(report, null, task, result);
      
      assertInProgress(result);
      
      display("Background task (running)", task);
      
      waitForTaskFinish(task.getOid(), true);

      // THEN
      displayThen(TEST_NAME);
      PrismObject<TaskType> finishedTask = getTask(task.getOid());
      display("Background task (finished)", finishedTask);
      
      return finishedTask;
  }
  
  
  protected void checkCsvUserReport(PrismObject<ReportType> report) throws IOException, SchemaException, ObjectNotFoundException, SecurityViolationException, CommunicationException, ConfigurationException, ExpressionEvaluationException {
	  File outputFile = findOutputFile(report);
	  display("Found report file", outputFile);
	  assertNotNull("No output file for "+report, outputFile);
	  List<String> lines = Files.readAllLines(Paths.get(outputFile.getPath()));
	  display("Report content ("+lines.size()+" lines)", String.join("\n", lines));
	  outputFile.renameTo(new File(outputFile.getParentFile(), "processed-"+outputFile.getName()));
	  
	  Task task = createTask("checkCsvUserReport");
      OperationResult result = task.getResult();
	  SearchResultList<PrismObject<UserType>> currentUsers = modelService.searchObjects(UserType.class, null, null, task, result);
	  display("Current users in midPoint ("+currentUsers.size()+" users)", currentUsers.toString());
	  
	  assertEquals("Unexpected number of report lines", currentUsers.size() + 1, lines.size());
  }
  
  protected void assertNoCsvReport(PrismObject<ReportType> report) throws IOException, SchemaException, ObjectNotFoundException, SecurityViolationException, CommunicationException, ConfigurationException, ExpressionEvaluationException {
	  File outputFile = findOutputFile(report);
	  display("Found report file (expected null)", outputFile);
	  assertNull("Unexpected output file for "+report+": "+outputFile, outputFile);
  }
  
  protected File findOutputFile(PrismObject<ReportType> report) {
	  String filePrefix = report.getName().getOrig();
	  File[] matchingFiles = EXPORT_DIR.listFiles(new FilenameFilter() {
	      public boolean accept(File dir, String name) {
	          return name.startsWith(filePrefix);
	      }
	  });
	  if (matchingFiles.length == 0) {
		  return null;
	  }
	  if (matchingFiles.length > 1) {
		  throw new IllegalStateException("Found more than one output files for "+report+": "+Arrays.toString(matchingFiles));
	  }
	  return matchingFiles[0];
  }
  
  
  @Test
  public void test200ReportUserListScript() throws Exception {
	  final String TEST_NAME = "test200ReportUserListScript";
      displayTestTitle(TEST_NAME);
      
      if (!isOsUnix()) {
			displaySkip(TEST_NAME);
			return;
		}

      Task task = createTask(TEST_NAME);
      OperationResult result = task.getResult();
      
      PrismObject<ReportType> report = getObject(ReportType.class, REPORT_USER_LIST_SCRIPT_OID);
      
      // WHEN
      displayWhen(TEST_NAME);
      reportManager.runReport(report, null, task, result);
      
      assertInProgress(result);
      
      display("Background task", task);
      
      waitForTaskFinish(task.getOid(), true);

      // THEN
      displayThen(TEST_NAME);
      PrismObject<TaskType> finishedTask = getTask(task.getOid());
      display("Background task", finishedTask);
      
      TestUtil.assertSuccess("Report task result", finishedTask.asObjectable().getResult());
      
      File targetFile = new File(MidPointTestConstants.TARGET_DIR_PATH, "report-users.pdf");
      assertTrue("Target file is not there", targetFile.exists());
  }
}
