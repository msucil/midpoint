package com.evolveum.midpoint.ninja;

import com.evolveum.midpoint.repo.api.RepositoryService;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ObjectType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.OrgType;
import org.testng.AssertJUnit;
import org.testng.annotations.Test;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Method;
import java.util.List;

/**
 * Created by Viliam Repan (lazyman).
 */
public class ImportRepositoryTest extends BaseTest {

    @Override
    protected void beforeMethodInternal(Method method) throws Exception {
        setupMidpointHome();
    }

    @Test
    public void test100ImportByOid() {
        String[] input = new String[]{"-m", getMidpointHome(), "import", "-o", "00000000-8888-6666-0000-100000000001",
                "-i", RESOURCES_FOLDER + "/org-monkey-island-simple.xml.zip", "-z"};

        ByteArrayOutputStream bos = new ByteArrayOutputStream();

        executeTest(null,
                context -> {
                    RepositoryService repo = context.getRepository();

                    OperationResult result = new OperationResult("count objects");
                    int count = repo.countObjects(ObjectType.class, null, null, result);

                    AssertJUnit.assertEquals(0, count);
                },
                context -> {
                    RepositoryService repo = context.getRepository();

                    OperationResult result = new OperationResult("count");
                    int count = repo.countObjects(ObjectType.class, null, null, result);

                    AssertJUnit.assertEquals(1, count);

                    count = repo.countObjects(OrgType.class, null, null, result);

                    AssertJUnit.assertEquals(1, count);
                },
                true, true, input);

        List<String> out = getSystemOut();
        AssertJUnit.assertEquals(out.toString(), 5, out.size());
        AssertJUnit.assertTrue(getSystemErr().isEmpty());
    }

    @Test
    public void test110ImportByFilterAsOption() throws Exception {
        String[] input = new String[]{"-m", getMidpointHome(), "import", "-f", "<equal><path>name</path><value>F0002</value></equal>",
                "-i", RESOURCES_FOLDER + "/org-monkey-island-simple.xml.zip", "-z"};

        ByteArrayOutputStream bos = new ByteArrayOutputStream();

        executeTest(null,
                context -> {
                    RepositoryService repo = context.getRepository();

                    OperationResult result = new OperationResult("count objects");
                    int count = repo.countObjects(ObjectType.class, null, null, result);

                    AssertJUnit.assertEquals(0, count);
                },
                context -> {
                    RepositoryService repo = context.getRepository();

                    OperationResult result = new OperationResult("count objects");
                    int count = repo.countObjects(ObjectType.class, null, null, result);

                    AssertJUnit.assertEquals(1, count);
                },
                true, true, input);

        List<String> out = getSystemOut();
        AssertJUnit.assertEquals(out.toString(), 5, out.size());
        AssertJUnit.assertTrue(getSystemErr().isEmpty());
    }

    @Test
    public void test120ImportByFilterAsFile() throws Exception {
        String[] input = new String[]{"-m", getMidpointHome(), "import", "-f", "@src/test/resources/filter.xml",
                "-i", RESOURCES_FOLDER + "/org-monkey-island-simple.xml.zip", "-z"};

        ByteArrayOutputStream bos = new ByteArrayOutputStream();

        executeTest(null,
                context -> {
                    RepositoryService repo = context.getRepository();

                    OperationResult result = new OperationResult("count objects");
                    int count = repo.countObjects(ObjectType.class, null, null, result);

                    AssertJUnit.assertEquals(0, count);
                },
                context -> {
                    RepositoryService repo = context.getRepository();

                    OperationResult result = new OperationResult("count users");
                    int count = repo.countObjects(ObjectType.class, null, null, result);

                    AssertJUnit.assertEquals(1, count);
                },
                true, true, input);

        List<String> out = getSystemOut();
        AssertJUnit.assertEquals(out.toString(), 5, out.size());
        AssertJUnit.assertTrue(getSystemErr().isEmpty());
    }

    @Test
    public void test130ImportRaw() throws Exception {
        // todo implement
    }

    @Test
    public void test140ImportFromZipFileByFilterAllowOverwrite() throws Exception {
        // todo implement
    }
}
