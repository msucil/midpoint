/*
 * Copyright (c) 2010-2013 Evolveum
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

package com.evolveum.midpoint.repo.sql;

import com.evolveum.midpoint.prism.*;
import com.evolveum.midpoint.prism.delta.*;
import com.evolveum.midpoint.prism.path.ItemPath;
import com.evolveum.midpoint.prism.polystring.PolyString;
import com.evolveum.midpoint.prism.util.PrismTestUtil;
import com.evolveum.midpoint.prism.xml.XmlTypeConverter;
import com.evolveum.midpoint.repo.sql.testing.SqlRepoTestUtil;
import com.evolveum.midpoint.schema.SearchResultList;
import com.evolveum.midpoint.schema.constants.ObjectTypes;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.schema.util.ObjectTypeUtil;
import com.evolveum.midpoint.util.logging.LoggingUtils;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;
import com.evolveum.prism.xml.ns._public.types_3.PolyStringType;

import org.hibernate.Session;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.testng.annotations.Test;

import javax.xml.namespace.QName;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.testng.AssertJUnit.*;

/**
 * @author Pavol Mederly
 */

@ContextConfiguration(locations = {"../../../../../ctx-test.xml"})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class ConcurrencyTest extends BaseSQLRepoTest {

    private static final Trace LOGGER = TraceManager.getTrace(ConcurrencyTest.class);

    private static final long WAIT_FOR_THREAD_NATURAL_STOP_TIME = 300000;
    //private static final long WAIT_STEP = 500;

    @Test
    public void test001TwoWriters_OneAttributeEach__NoReader() throws Exception {
        PropertyModifierThread[] mts = new PropertyModifierThread[] {
                new PropertyModifierThread(1, UserType.F_GIVEN_NAME, true, null, true),
                new PropertyModifierThread(2, UserType.F_FAMILY_NAME, true, null, true),
//                new ModifierThread(3, oid, UserType.F_DESCRIPTION, false),
//                new ModifierThread(4, oid, UserType.F_EMAIL_ADDRESS, false),
//                new ModifierThread(5, oid, UserType.F_TELEPHONE_NUMBER, false),
//                new ModifierThread(6, oid, UserType.F_EMPLOYEE_NUMBER, false),
//                new ModifierThread(8, oid, UserType.F_EMAIL_ADDRESS),
//                new ModifierThread(9, oid, UserType.F_EMPLOYEE_NUMBER)
        };
        concurrencyUniversal("Test1", 30000L, 500L, mts, null);
    }

    @Test
    public void test002FourWriters_OneAttributeEach__NoReader() throws Exception {
        PropertyModifierThread[] mts = new PropertyModifierThread[] {
                new PropertyModifierThread(1, UserType.F_GIVEN_NAME, true, null, true),
                new PropertyModifierThread(2, UserType.F_FAMILY_NAME, true, null, true),
                new PropertyModifierThread(3, UserType.F_DESCRIPTION, false, null, true),
                new PropertyModifierThread(4, UserType.F_EMAIL_ADDRESS, false, null, true)
        };
        concurrencyUniversal("Test2", 60000L, 500L, mts, null);
    }

    @Test
    public void test003OneWriter_TwoAttributes__OneReader() throws Exception {
        PropertyModifierThread[] mts = new PropertyModifierThread[] {
                new PropertyModifierThread(1, UserType.F_GIVEN_NAME, true,
                        ItemPath.create(UserType.F_ASSIGNMENT, 1L, AssignmentType.F_DESCRIPTION),
                        true)
        };
        Checker checker = (iteration, oid) -> {
            PrismObject<UserType> userRetrieved = repositoryService.getObject(UserType.class, oid, null, new OperationResult("dummy"));
            String givenName = userRetrieved.asObjectable().getGivenName().getOrig();
            String assignmentDescription = userRetrieved.asObjectable().getAssignment().get(0).getDescription();
            LOGGER.info("[" + iteration + "] givenName = " + givenName + ", assignment description = " + assignmentDescription);
            if (!givenName.equals(assignmentDescription)) {
                String msg = "Inconsistent object state: GivenName = " + givenName + ", assignment description = " + assignmentDescription;
                LOGGER.error(msg);
                throw new AssertionError(msg);
            }
        };
        concurrencyUniversal("Test3", 60000L, 0L, mts, checker);
    }

    @Test
    public void test004TwoWriters_TwoAttributesEach__OneReader() throws Exception {
        PropertyModifierThread[] mts = new PropertyModifierThread[] {
                new PropertyModifierThread(1, UserType.F_GIVEN_NAME, true,
                        ItemPath.create(UserType.F_ASSIGNMENT, 1L, AssignmentType.F_DESCRIPTION),
                        true),
                new PropertyModifierThread(2, UserType.F_FAMILY_NAME, true,
                        ItemPath.create(UserType.F_ASSIGNMENT, 1L, AssignmentType.F_CONSTRUCTION),
                        true),
        };
        Checker checker = (iteration, oid) -> {
            PrismObject<UserType> userRetrieved = repositoryService.getObject(UserType.class, oid, null, new OperationResult("dummy"));
            String givenName = userRetrieved.asObjectable().getGivenName().getOrig();
            String familyName = userRetrieved.asObjectable().getFamilyName().getOrig();
            String assignmentDescription = userRetrieved.asObjectable().getAssignment().get(0).getDescription();
            String referenceDescription = userRetrieved.asObjectable().getAssignment().get(0).getConstruction().getDescription();
            LOGGER.info("[" + iteration + "] givenName = " + givenName + ", assignment description = " + assignmentDescription + ", familyName = " + familyName + ", referenceDescription = " + referenceDescription);
            if (!givenName.equals(assignmentDescription)) {
                String msg = "Inconsistent object state: GivenName = " + givenName + ", assignment description = " + assignmentDescription;
                LOGGER.error(msg);
                throw new AssertionError(msg);
            }
            if (!familyName.equals(referenceDescription)) {
                String msg = "Inconsistent object state: FamilyName = " + familyName + ", account construction description = " + referenceDescription;
                LOGGER.error(msg);
                throw new AssertionError(msg);
            }
        };
        concurrencyUniversal("Test4", 60000L, 0L, mts, checker);
    }

    @FunctionalInterface
    private interface Checker {
        void check(int iteration, String oid) throws Exception;
    }

    private void concurrencyUniversal(String name, long duration, long waitStep, PropertyModifierThread[] modifierThreads, Checker checker) throws Exception {

        Session session = getFactory().openSession();
        session.doWork(connection -> System.out.println(">>>>" + connection.getTransactionIsolation()));
        session.close();

        final File file = new File("src/test/resources/concurrency/user.xml");
        PrismObject<UserType> user = prismContext.parseObject(file);
        user.asObjectable().setName(new PolyStringType(name));

        OperationResult result = new OperationResult("Concurrency Test");
        String oid = repositoryService.addObject(user, null, result);

        LOGGER.info("*** Object added: {} ***", oid);
        LOGGER.info("*** Starting modifier threads ***");

        for (PropertyModifierThread mt : modifierThreads) {
            mt.setObject(UserType.class, oid);
            mt.start();
        }

        LOGGER.info("*** Waiting {} ms ***", duration);
        long startTime = System.currentTimeMillis();
        int readIteration = 1;
        main:
        while (System.currentTimeMillis() - startTime < duration) {
            if (checker != null) {
                checker.check(readIteration, oid);
            }
            if (waitStep > 0L) {
                Thread.sleep(waitStep);
            }
            for (PropertyModifierThread mt : modifierThreads) {
                if (!mt.isAlive()) {
                    LOGGER.error("At least one of threads died prematurely, finishing waiting.");
                    break main;
                }
            }
            readIteration++;
        }

        for (PropertyModifierThread mt : modifierThreads) {
            mt.stop = true;             // stop the threads
            System.out.println("Thread " + mt.id + " has done " + mt.counter.get() + " iterations");
            LOGGER.info("Thread " + mt.id + " has done " + mt.counter.get() + " iterations");
        }

        // we do not have to wait for the threads to be stopped, just examine their results

        Thread.sleep(1000);         // give the threads a chance to finish (before repo will be shut down)

        for (PropertyModifierThread mt : modifierThreads) {
            LOGGER.info("Modifier thread {} finished with an exception", mt.id, mt.threadResult);
        }

        for (PropertyModifierThread mt : modifierThreads) {
            if (mt.threadResult != null) {
                throw new AssertionError("Modifier thread " + mt.id + " finished with an exception: " + mt.threadResult, mt.threadResult);
            }
        }
    }

    abstract class WorkerThread extends Thread {

        int id;
        Class<? extends ObjectType> objectClass;                // object to modify
        String oid;                                             // object to modify
        String lastVersion = null;
        volatile Throwable threadResult;
        AtomicInteger counter = new AtomicInteger(0);
        Integer limit;

        WorkerThread(int id) {
            this.id = id;
        }

        WorkerThread(int id, int limit) {
            this.id = id;
            this.limit = limit;
        }

        public volatile boolean stop = false;

        @Override
        public void run() {
            try {
                while (!stop && (limit == null || counter.intValue() < limit)) {
                    OperationResult result = new OperationResult("run");
                    LOGGER.info(" --- Iteration number {} for {} ---", counter.incrementAndGet(), description());
                    runOnce(result);
                }
            } catch (Throwable t) {
                LoggingUtils.logException(LOGGER, "Got exception: " + t, t);
                threadResult = t;
            }
        }

        abstract void runOnce(OperationResult result) throws Exception;
        abstract String description();
        public void setObject(Class<? extends ObjectType> objectClass, String oid) {
            this.objectClass = objectClass;
            this.oid = oid;
        }

        @Override
        public String toString() {
            return description() + " @" + counter;
        }
    }

    class PropertyModifierThread extends WorkerThread {

        final ItemPath attribute1;           // attribute to modify
        final ItemPath attribute2;           // attribute to modify
        boolean poly;
        boolean checkValue;

        PropertyModifierThread(int id, ItemPath attribute1, boolean poly, ItemPath attribute2, boolean checkValue) {
            super(id);
            this.attribute1 = attribute1;
            this.attribute2 = attribute2;
            this.poly = poly;
            this.setName("Modifier for " + attributeNames());
            this.checkValue = checkValue;
        }

        private String attributeNames() {
            return lastName(attribute1) + (attribute2 != null ? "/" + lastName(attribute2) : "");
        }

        @Override
        String description() {
            return attributeNames();
        }

        private String lastName(ItemPath path) {
            QName lastName = path.lastName();
            return lastName != null ? lastName.getLocalPart() : "?";
        }

        void runOnce(OperationResult result) {

            PrismObjectDefinition<?> userPrismDefinition = prismContext.getSchemaRegistry().findObjectDefinitionByCompileTimeClass(UserType.class);

            String prefix = lastName(attribute1);
            String dataWritten = "[" + prefix + ":" + counter.get() + "]";

            PrismPropertyDefinition<?> propertyDefinition1 = userPrismDefinition.findPropertyDefinition(attribute1);
            if (propertyDefinition1 == null) {
                throw new IllegalArgumentException("No definition for " + attribute1 + " in " + userPrismDefinition);
            }
            PropertyDelta delta1 = prismContext.deltaFactory().property().create(attribute1, propertyDefinition1);
            //noinspection unchecked
            delta1.setRealValuesToReplace(poly ? new PolyString(dataWritten) : dataWritten);
            List<ItemDelta> deltas = new ArrayList<>();
            deltas.add(delta1);

            ItemDefinition propertyDefinition2;
            if (attribute2 != null) {
                propertyDefinition2 = userPrismDefinition.findItemDefinition(attribute2);
                if (propertyDefinition2 == null) {
                    throw new IllegalArgumentException("No definition for " + attribute2 + " in " + userPrismDefinition);
                }
                
                ItemDelta delta2;
                if (propertyDefinition2 instanceof PrismContainerDefinition) {
                    //noinspection unchecked
                    delta2 = prismContext.deltaFactory().container().create(attribute2, (PrismContainerDefinition) propertyDefinition2);
                } else {
                    //noinspection unchecked
                    delta2 = prismContext.deltaFactory().property().create(attribute2, (PrismPropertyDefinition) propertyDefinition2);
                }
                if (ConstructionType.COMPLEX_TYPE.equals(propertyDefinition2.getTypeName())) {
                    ConstructionType act = new ConstructionType();
                    act.setDescription(dataWritten);
                    //noinspection unchecked
                    delta2.setValueToReplace(act.asPrismContainerValue());
                } else {
                    //noinspection unchecked
                    delta2.setValueToReplace(prismContext.itemFactory().createPropertyValue(dataWritten));
                }
                deltas.add(delta2);
            } else {
                propertyDefinition2 = null;
            }

            try {
                repositoryService.modifyObject(UserType.class, oid, deltas, result);
				result.computeStatus();
				if (result.isError()) {
					LOGGER.error("Error found in operation result:\n{}", result.debugDump());
					throw new IllegalStateException("Error found in operation result");
				}
            } catch (Exception e) {
                String msg = "modifyObject failed while modifying attribute(s) " + attributeNames() + " to value " + dataWritten;
                throw new RuntimeException(msg, e);
            }

            if (checkValue) {

                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    // ignore
                }

                PrismObject<UserType> user;
                try {
                    user = repositoryService.getObject(UserType.class, oid, null, result);
                } catch (Exception e) {
                    String msg = "getObject failed while getting attribute(s) " + attributeNames();
                    throw new RuntimeException(msg, e);
                }

                // check the attribute

                String dataRead;
                if (poly) {
                    dataRead = user.findProperty(attribute1).getRealValue(PolyString.class).getOrig();
                } else {
                    dataRead = user.findProperty(attribute1).getRealValue(String.class);
                }

                if (!dataWritten.equals(dataRead)) {
                    threadResult = new RuntimeException("Data read back (" + dataRead + ") does not match the data written (" + dataWritten + ") on attribute " + attribute1);
                    LOGGER.error("compare failed", threadResult);
                    stop = true;
                    return;
                }

                if (attribute2 != null) {
                    if (ConstructionType.COMPLEX_TYPE.equals(propertyDefinition2.getTypeName())) {
                        dataRead = ((ConstructionType)user.findContainer(attribute2).getValue().getValue()).getDescription();
                    } else {
                        dataRead = user.findProperty(attribute2).getRealValue(String.class);
                    }

                    if (!dataWritten.equals(dataRead)) {
                        threadResult = new RuntimeException("Data read back (" + dataRead + ") does not match the data written (" + dataWritten + ") on attribute " + attribute2);
                        LOGGER.error("compare failed", threadResult);
                        stop = true;
                        return;
                    }
                }
                
                String currentVersion = user.getVersion();
                String versionError = SqlRepoTestUtil.checkVersionProgress(lastVersion, currentVersion);
                if (versionError != null) {
                    threadResult = new RuntimeException(versionError);
                    LOGGER.error(versionError);
                    stop = true;
                    return;
                }
                lastVersion = currentVersion;
            }
        }
    }

    abstract class AddObjectsThread<T extends ObjectType> extends WorkerThread {

        String description;

        AddObjectsThread(int id, String description, int limit) {
            super(id, limit);
            this.description = description;
            this.setName("Executor: " + description);
        }

        @Override
        String description() {
            return description;
        }

        void runOnce(OperationResult result) throws Exception {
            repositoryService.addObject(getObjectToAdd(), null, result);
        }

        protected abstract PrismObject<T> getObjectToAdd();
    }

    abstract class DeleteObjectsThread<T extends ObjectType> extends WorkerThread {

        private Class<T> objectClass;
        String description;

        DeleteObjectsThread(int id, Class<T> objectClass, String description) {
            super(id);
            this.objectClass = objectClass;
            this.description = description;
            this.setName("Executor: " + description);
        }

        @Override
        String description() {
            return description;
        }

        void runOnce(OperationResult result) throws Exception {
            String oidToDelete = getOidToDelete();
            if (oidToDelete != null) {
                repositoryService.deleteObject(objectClass, oidToDelete, result);
            } else {
                stop = true;
            }
        }

        protected abstract String getOidToDelete();
    }


    abstract class DeltaExecutionThread extends WorkerThread {

        String description;

        DeltaExecutionThread(int id, Class<? extends ObjectType> objectClass, String oid, String description) {
            super(id);
            setObject(objectClass, oid);
            this.description = description;
            this.setName("Executor: " + description);
        }

        @Override
        String description() {
            return description;
        }

        abstract Collection<ItemDelta<?, ?>> getItemDeltas() throws Exception;

        void runOnce(OperationResult result) throws Exception {
            repositoryService.modifyObject(objectClass, oid, getItemDeltas(), result);
        }
    }

    @Test
    public void test010SearchIterative() throws Exception {

        String name = "Test10";
        final String newFullName = "new-full-name";

        final File file = new File("src/test/resources/concurrency/user.xml");
        PrismObject<UserType> user = prismContext.parseObject(file);
        user.asObjectable().setName(new PolyStringType(name));

        final OperationResult result = new OperationResult("Concurrency Test10");
        String oid = repositoryService.addObject(user, null, result);

        repositoryService.searchObjectsIterative(UserType.class,
                prismContext.queryFor(UserType.class)
                    .item(UserType.F_NAME).eqPoly(name).matchingOrig().build(),
                (object, parentResult) -> {
                    LOGGER.info("Handling " + object + "...");
                    ObjectDelta delta = prismContext.deltaFactory().object()
		                    .createModificationReplaceProperty(UserType.class, object.getOid(),
		                    UserType.F_FULL_NAME, new PolyString(newFullName));
                    try {
                        //noinspection unchecked
                        repositoryService.modifyObject(UserType.class,
                            object.getOid(),
                            delta.getModifications(),
                            parentResult);
                    } catch (Exception e) {
                        throw new RuntimeException("Exception in handle method", e);
                    }
                    return true;
                },
                null, true, result);

        PrismObject<UserType> reloaded = repositoryService.getObject(UserType.class, oid, null, result);
        assertEquals("Full name was not changed", newFullName, reloaded.asObjectable().getFullName().getOrig());
    }

    @Test
    public void test100AddOperationExecution() throws Exception {

        if (getConfiguration().isUsingH2()) {
            return;         // TODO
        }

        int THREADS = 8;
        long DURATION = 30000L;

        Session session = getFactory().openSession();
        session.doWork(connection -> System.out.println(">>>>" + connection.getTransactionIsolation()));
        session.close();

        UserType user = new UserType(prismContext).name("jack");

        OperationResult result = new OperationResult("test100AddOperationExecution");
        String oid = repositoryService.addObject(user.asPrismObject(), null, result);

        PrismTestUtil.display("object added", oid);

        LOGGER.info("Starting worker threads");

        List<DeltaExecutionThread> threads = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            final int threadIndex = i;

            DeltaExecutionThread thread = new DeltaExecutionThread(i, UserType.class, oid, "operationExecution adder #" + i) {
                @Override
                Collection<ItemDelta<?, ?>> getItemDeltas() throws Exception {
                    return prismContext.deltaFor(UserType.class)
                            .item(UserType.F_OPERATION_EXECUTION).add(
                                    new OperationExecutionType(prismContext)
                                            .channel(threadIndex + ":" + counter)
                                            .timestamp(XmlTypeConverter.createXMLGregorianCalendar(new Date())))
                            .asItemDeltas();
                }
            };
            thread.start();
            threads.add(thread);
        }

        waitForThreads(threads, DURATION);
    }

    @Test
    public void test110AddAssignments() throws Exception {

        if (getConfiguration().isUsingH2()) {
            // Because of:
            // Caused by: javax.persistence.EntityExistsException: A different object with the same identifier value was already associated with the session : [com.evolveum.midpoint.repo.sql.data.common.container.RAssignment#RContainerId{44c3e25d-e790-4142-958d-ff7ff3ff3a9f, 62}]
            //	at org.hibernate.internal.ExceptionConverterImpl.convert(ExceptionConverterImpl.java:118)
            //	at org.hibernate.internal.ExceptionConverterImpl.convert(ExceptionConverterImpl.java:157)
            //	at org.hibernate.internal.ExceptionConverterImpl.convert(ExceptionConverterImpl.java:164)
            //	at org.hibernate.internal.SessionImpl.doFlush(SessionImpl.java:1443)
            //	at org.hibernate.internal.SessionImpl.managedFlush(SessionImpl.java:493)
            //	at org.hibernate.internal.SessionImpl.flushBeforeTransactionCompletion(SessionImpl.java:3207)
            //	at org.hibernate.internal.SessionImpl.beforeTransactionCompletion(SessionImpl.java:2413)
            //	at org.hibernate.engine.jdbc.internal.JdbcCoordinatorImpl.beforeTransactionCompletion(JdbcCoordinatorImpl.java:473)
            //	at org.hibernate.resource.transaction.backend.jdbc.internal.JdbcResourceLocalTransactionCoordinatorImpl.beforeCompletionCallback(JdbcResourceLocalTransactionCoordinatorImpl.java:156)
            //	at org.hibernate.resource.transaction.backend.jdbc.internal.JdbcResourceLocalTransactionCoordinatorImpl.access$100(JdbcResourceLocalTransactionCoordinatorImpl.java:38)
            //	at org.hibernate.resource.transaction.backend.jdbc.internal.JdbcResourceLocalTransactionCoordinatorImpl$TransactionDriverControlImpl.commit(JdbcResourceLocalTransactionCoordinatorImpl.java:231)
            //	at org.hibernate.engine.transaction.internal.TransactionImpl.commit(TransactionImpl.java:68)
            return;         // TODO
        }

        int THREADS = 8;
        long DURATION = 30000L;

        UserType user = new UserType(prismContext).name("alice");

        OperationResult result = new OperationResult("test110AddAssignments");
        String oid = repositoryService.addObject(user.asPrismObject(), null, result);

        PrismTestUtil.display("object added", oid);

        LOGGER.info("Starting worker threads");

        List<DeltaExecutionThread> threads = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            final int threadIndex = i;

            DeltaExecutionThread thread = new DeltaExecutionThread(i, UserType.class, oid, "assignment adder #" + i) {
                @Override
                Collection<ItemDelta<?, ?>> getItemDeltas() throws Exception {
                    return prismContext.deltaFor(UserType.class)
                            .item(UserType.F_ASSIGNMENT).add(
                                    new AssignmentType(prismContext)
                                            .targetRef("0000-" + threadIndex + "-" + counter, OrgType.COMPLEX_TYPE))
                            .asItemDeltas();
                }
            };
            thread.start();
            threads.add(thread);
        }

        waitForThreads(threads, DURATION);
        PrismObject<UserType> userAfter = repositoryService.getObject(UserType.class, oid, null, result);
        display("user after", userAfter);
    }

    @Test
    public void test120AddApproverRef() throws Exception {

        int THREADS = 4;
        long DURATION = 30000L;
        final String APPROVER_REF_FORMAT = "oid-%d-%s";

        RoleType role = new RoleType(prismContext).name("judge");

        OperationResult result = new OperationResult("test120AddApproverRef");
        String oid = repositoryService.addObject(role.asPrismObject(), null, result);

        PrismTestUtil.display("object added", oid);

        LOGGER.info("Starting worker threads");

        List<DeltaExecutionThread> threads = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            final int threadIndex = i;

            DeltaExecutionThread thread = new DeltaExecutionThread(i, RoleType.class, oid, "approverRef adder #" + i) {
                @Override
                Collection<ItemDelta<?, ?>> getItemDeltas() throws Exception {
                    return prismContext.deltaFor(RoleType.class)
                            .item(RoleType.F_APPROVER_REF).add(
                                    ObjectTypeUtil.createObjectRef(String.format(APPROVER_REF_FORMAT, threadIndex, counter), ObjectTypes.USER))
                            .asItemDeltas();
                }
            };
            thread.start();
            threads.add(thread);
        }

        waitForThreads(threads, DURATION);
        PrismObject<RoleType> roleAfter = repositoryService.getObject(RoleType.class, oid, null, result);
        display("role after", roleAfter);

        int totalExecutions = threads.stream().mapToInt(t -> t.counter.get()).sum();
        int totalApprovers = roleAfter.asObjectable().getApproverRef().size();
        System.out.println("Total executions: " + totalExecutions);
        System.out.println("Approvers: " + totalApprovers);

        List<String> failures = new ArrayList<>();
        for (DeltaExecutionThread thread : threads) {
            for (int i = 1; i <= thread.counter.get(); i++) {
                String expected = String.format(APPROVER_REF_FORMAT, thread.id, i);
                List<String> matchingOids = roleAfter.asObjectable().getApproverRef().stream()
                        .map(ObjectReferenceType::getOid)
                        .filter(refOid -> refOid.equals(expected))
                        .collect(Collectors.toList());
                if (matchingOids.size() != 1) {
                    failures.add("Wrong # of occurrences of " + expected + ": " + matchingOids);
                }
            }
        }

        System.out.println("Failures:");
        failures.forEach(line -> System.out.println(" - " + line));

        assertEquals("Wrong # of approvers", totalExecutions, totalApprovers);
        assertEquals("Failures are there", 0, failures.size());
    }

    @Test
    public void test130AddDeleteObjects() throws Exception {

        int ADD_THREADS = 4;
        int DELETE_THREADS = 4;
        int OBJECTS_PER_THREAD = 100;
        long TIMEOUT = 30000L;

        OperationResult result = new OperationResult("test130DeleteObjects");

        SearchResultList<PrismObject<UserType>> users = repositoryService
                .searchObjects(UserType.class, null, null, result);
        for (PrismObject<UserType> user : users) {
            repositoryService.deleteObject(UserType.class, user.getOid(), result);
        }
        assertEquals("Wrong # of users at the beginning", 0,
                repositoryService.countObjects(UserType.class, null, null, result));

        LOGGER.info("Starting ADD worker threads");

        repositoryService.getPerformanceMonitor().clearGlobalPerformanceInformation();
        List<AddObjectsThread<UserType>> addThreads = new ArrayList<>();
        for (int i = 0; i < ADD_THREADS; i++) {
            int threadIndex = i;
            AddObjectsThread<UserType> thread = new AddObjectsThread<UserType>(i, "adder #" + i, OBJECTS_PER_THREAD) {
                @Override
                protected PrismObject<UserType> getObjectToAdd() {
                    return new UserType(prismContext).name(String.format("user-%d-%06d", threadIndex, counter.intValue())).asPrismObject();
                }
            };
            thread.start();
            addThreads.add(thread);
        }
        waitForThreadsFinish(addThreads, TIMEOUT);
        System.out.println("Add performance information:\n" + repositoryService.getPerformanceMonitor().getGlobalPerformanceInformation().debugDump());

        SearchResultList<PrismObject<UserType>> objectsCreated = repositoryService
                .searchObjects(UserType.class, null, null, result);
        assertEquals("Wrong # of users after creation", ADD_THREADS * OBJECTS_PER_THREAD, objectsCreated.size());

        LOGGER.info("Starting DELETE worker threads");

        repositoryService.getPerformanceMonitor().clearGlobalPerformanceInformation();
        AtomicInteger objectsPointer = new AtomicInteger(0);
        List<DeleteObjectsThread<UserType>> deleteThreads = new ArrayList<>();
        for (int i = 0; i < DELETE_THREADS; i++) {
            DeleteObjectsThread<UserType> thread = new DeleteObjectsThread<UserType>(i, UserType.class, "deleter #" + i) {
                @Override
                protected String getOidToDelete() {
                    int pointer = objectsPointer.getAndIncrement();
                    if (pointer < objectsCreated.size()) {
                        return objectsCreated.get(pointer).getOid();
                    } else {
                        return null;
                    }
                }
            };
            thread.start();
            deleteThreads.add(thread);
        }
        waitForThreadsFinish(deleteThreads, TIMEOUT);
        System.out.println("Delete performance information:\n" + repositoryService.getPerformanceMonitor().getGlobalPerformanceInformation().debugDump());

        assertEquals("Wrong # of users after deletion", 0,
                repositoryService.countObjects(UserType.class, null, null, result));
    }

    private void waitForThreadsFinish(List<? extends WorkerThread> threads, long timeout) throws InterruptedException {
        LOGGER.info("*** Waiting until finish, at most {} ms ***", timeout);
        long startTime = System.currentTimeMillis();
        main:
        while (System.currentTimeMillis() - startTime < timeout) {
            for (WorkerThread thread : threads) {
                if (thread.isAlive()) {
                    Thread.sleep(100);
                    continue main;
                } else if (thread.threadResult != null) {
                    throw new AssertionError("Thread " + thread + " failed with " + thread.threadResult, thread.threadResult);
                }
            }
            return;
        }

        List<WorkerThread> alive = threads.stream().filter(Thread::isAlive).collect(Collectors.toList());
        assertTrue("These threads did not finish in " + timeout + " millis: " + alive, alive.isEmpty());
    }

    private void waitForThreads(List<? extends WorkerThread> threads, long duration) throws InterruptedException {
        LOGGER.info("*** Waiting {} ms ***", duration);
        long startTime = System.currentTimeMillis();
        main:
        while (System.currentTimeMillis() - startTime < duration) {

            for (WorkerThread thread : threads) {
                if (!thread.isAlive()) {
                    LOGGER.error("At least one of threads died prematurely, finishing waiting.");
                    break main;
                }
            }

            Thread.sleep(100);
        }

        for (WorkerThread thread : threads) {
            thread.stop = true;             // stop the threads
            System.out.println("Thread " + thread.id + " has done " + thread.counter.get() + " iterations");
            LOGGER.info("Thread " + thread.id + " has done " + thread.counter.get() + " iterations");
        }

        long start = System.currentTimeMillis();
        boolean anyAlive = true;
        while (anyAlive && System.currentTimeMillis() - start < WAIT_FOR_THREAD_NATURAL_STOP_TIME) {
            anyAlive = threads.stream().anyMatch(Thread::isAlive);
            Thread.sleep(100);
        }
        List<String> alive = threads.stream().filter(Thread::isAlive).map(Thread::getName).collect(Collectors.toList());
        assertTrue("Some threads had not stopped in given time: " + alive, alive.isEmpty());

        for (WorkerThread thread : threads) {
            LOGGER.info("Modifier thread " + thread.id + " finished with an exception: ", thread.threadResult);
        }

        for (WorkerThread thread : threads) {
            if (thread.threadResult != null) {
                throw new AssertionError("Modifier thread " + thread.id + " finished with an exception: " + thread.threadResult, thread.threadResult);
            }
        }
    }

    private SqlRepositoryConfiguration getConfiguration() {
        return ((SqlRepositoryServiceImpl) repositoryService).getConfiguration();
    }

}
