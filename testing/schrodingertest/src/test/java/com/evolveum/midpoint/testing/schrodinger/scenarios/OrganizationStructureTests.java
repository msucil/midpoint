package com.evolveum.midpoint.testing.schrodinger.scenarios;

import com.codeborne.selenide.Selenide;
import com.evolveum.midpoint.schrodinger.page.configuration.ImportObjectPage;
import com.evolveum.midpoint.schrodinger.page.resource.ListResourcesPage;
import com.evolveum.midpoint.schrodinger.page.user.ListUsersPage;
import com.evolveum.midpoint.schrodinger.page.user.UserPage;
import com.evolveum.midpoint.testing.schrodinger.TestBase;
import org.apache.commons.io.FileUtils;
import org.testng.Assert;
import org.testng.annotations.Test;

import javax.naming.ConfigurationException;
import java.io.File;
import java.io.IOException;

/**
 * Created by matus on 5/11/2018.
 */
public class OrganizationStructureTests extends TestBase {

    private static File CSV_TARGET_FILE;

    private static final File CSV_RESOURCE_ADVANCED_SYNC = new File("../../samples/resources/csv/resource-csv-groups.xml");

    private static final File CSV_INITIAL_SOURCE_FILE = new File("../../samples/resources/csv/midpoint-groups.csv");
    private static final File ORG_ACCOUNT_INDUCEMENT_FILE = new File("./src/test/resources/org-account-inducement.xml");
    private static final File ORG_MONKEY_ISLAND_SOURCE_FILE = new File("../../samples/org/org-monkey-island-simple.xml");

    private static final String TEST_USER_GUYBRUSH_NAME = "guybrush";

    private static final String NAME_ORG_UNIT_ASSIGN= "P0001";
    private static final String NAME_ORG_UNIT_UNASSIGN= "Save Elaine";
    private static final String NAME_ORG_UNIT_ASSIGN_AND_INDUCE= "testOrgUnit";
    private static final String NAME_CSV_RESOURCE_ADVANCED_SYNC ="CSV (target with groups)";
    private static final String TYPE_SELECTOR_ORG= "Org";

    private static final String IMPORT_ORG_STRUCT_DEPENDENCY = "importOrgStructure";
    private static final String ASSIGN_ORG_UNIT_DEPENDENCY = "assignOrgUnit";
    private static final String ORG_UNIT_ACCOUNT_INDUCEMENT_DEPENDENCY = "orgUnitAccountInducement";

    private static final String DIRECTORY_CURRENT_TEST = "organizationStructureTests";
    private static final String FILE_RESOUCE_NAME = "midpoint-advanced-sync.csv";

    @Test
    public void importOrgStructure() throws IOException, ConfigurationException {

        initTestDirectory(DIRECTORY_CURRENT_TEST);

        CSV_TARGET_FILE = new File(CSV_TARGET_DIR, FILE_RESOUCE_NAME);
        FileUtils.copyFile(CSV_INITIAL_SOURCE_FILE,CSV_TARGET_FILE);

        ImportObjectPage importPage = basicPage.importObject();
        Assert.assertTrue(
                importPage
                    .getObjectsFromFile()
                    .chooseFile(ORG_MONKEY_ISLAND_SOURCE_FILE)
                    .checkOverwriteExistingObject()
                    .clickImport()
                        .feedback()
                        .isSuccess()
        );
    }

    @Test (dependsOnMethods ={IMPORT_ORG_STRUCT_DEPENDENCY})
    public void assignOrgUnit(){
         ListUsersPage users = basicPage.listUsers();
         UserPage userPage = (UserPage) users
                .table()
                    .search()
                    .byName()
                    .inputValue(TEST_USER_GUYBRUSH_NAME)
                    .updateSearch()
                .and()
                .clickByName(TEST_USER_GUYBRUSH_NAME)
                    .selectTabAssignments()
                        .clickAddAssignemnt()
                            .selectType(TYPE_SELECTOR_ORG)
                            .table()
                                .search()
                                    .byName()
                                    .inputValue(NAME_ORG_UNIT_ASSIGN)
                                .updateSearch()
                            .and()
                            .selectCheckboxByName(NAME_ORG_UNIT_ASSIGN)
                        .and()
                    .clickAdd()
                .and();
        userPage.checkKeepDisplayingResults()
                .clickSave()
                    .feedback()
                    .isSuccess()
        ;
    }

    @Test (dependsOnMethods ={ORG_UNIT_ACCOUNT_INDUCEMENT_DEPENDENCY})
    public void unassignOrgUnit(){
        ListUsersPage users = basicPage.listUsers();
        UserPage userPage = (UserPage) users
                .table()
                    .search()
                    .byName()
                    .inputValue(TEST_USER_GUYBRUSH_NAME)
                    .updateSearch()
                .and()
                .clickByName(TEST_USER_GUYBRUSH_NAME)
                    .selectTabAssignments()
                        .table()
                        .unassignByName(NAME_ORG_UNIT_UNASSIGN)
                    .and()
                .and();
        userPage.checkKeepDisplayingResults()
                .clickSave()
                    .feedback()
                    .isSuccess();
    }

    @Test (dependsOnMethods ={ASSIGN_ORG_UNIT_DEPENDENCY})
    public void orgUnitAccountInducement(){
        importObject(CSV_RESOURCE_ADVANCED_SYNC,true);
        importObject(ORG_ACCOUNT_INDUCEMENT_FILE);
        importObject(ScenariosCommons.USER_TEST_RAPHAEL_FILE, true);

        changeResourceFilePath();

       refreshResourceSchema(NAME_CSV_RESOURCE_ADVANCED_SYNC);

         ListUsersPage users = basicPage.listUsers();
         UserPage userPage = (UserPage) users
                .table()
                    .search()
                    .byName()
                    .inputValue(ScenariosCommons.TEST_USER_RAPHAEL_NAME)
                    .updateSearch()
                .and()
                .clickByName(ScenariosCommons.TEST_USER_RAPHAEL_NAME)
                    .selectTabAssignments()
                        .clickAddAssignemnt()
                            .selectType(TYPE_SELECTOR_ORG)
                            .table()
                                .search()
                                    .byName()
                                    .inputValue(NAME_ORG_UNIT_ASSIGN_AND_INDUCE)
                                .updateSearch()
                            .and()
                            .selectCheckboxByName(NAME_ORG_UNIT_ASSIGN_AND_INDUCE)
                        .and()
                    .clickAdd()
                .and();
         userPage
                .checkKeepDisplayingResults()
                .clickSave()
                    .feedback()
                    .isSuccess();
   }

    public void changeResourceFilePath(){
        ListResourcesPage listResourcesPage = basicPage.listResources();

        Assert.assertTrue(listResourcesPage
                .table()
                .search()
                .byName()
                .inputValue(NAME_CSV_RESOURCE_ADVANCED_SYNC)
                .updateSearch()
                .and()
                .clickByName(NAME_CSV_RESOURCE_ADVANCED_SYNC)
                    .clickEditResourceConfiguration()
                        .form()
                        .changeAttributeValue("File path", "", CSV_TARGET_FILE.getAbsolutePath())
                        .changeAttributeValue(CSV_RESOURCE_ATTR_UNIQUE,"","login")
                    .and()
                .and()
                .clickSaveAndTestConnection()
                .isTestSuccess()
        );
    }
}
