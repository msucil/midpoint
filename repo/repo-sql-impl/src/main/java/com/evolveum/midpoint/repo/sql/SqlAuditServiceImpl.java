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

import com.evolveum.midpoint.audit.api.*;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.repo.sql.data.audit.*;
import com.evolveum.midpoint.repo.sql.data.common.enums.ROperationResultStatus;
import com.evolveum.midpoint.repo.sql.data.common.other.RObjectType;
import com.evolveum.midpoint.repo.sql.helpers.BaseHelper;
import com.evolveum.midpoint.repo.sql.perf.SqlPerformanceMonitorImpl;
import com.evolveum.midpoint.repo.sql.util.DtoTranslationException;
import com.evolveum.midpoint.repo.sql.util.GetObjectResult;
import com.evolveum.midpoint.repo.sql.util.RUtil;
import com.evolveum.midpoint.repo.sql.util.TemporaryTableDialect;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.util.DebugUtil;
import com.evolveum.midpoint.util.Holder;
import com.evolveum.midpoint.util.MiscUtil;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.util.exception.SystemException;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.xml.ns._public.common.common_3.CleanupPolicyType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ObjectType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.OperationResultStatusType;
import com.evolveum.prism.xml.ns._public.types_3.PolyStringType;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.Validate;
import org.hibernate.FlushMode;
import org.hibernate.ScrollableResults;
import org.hibernate.Session;
import org.hibernate.dialect.Dialect;
import org.hibernate.dialect.pagination.LimitHandler;
import org.hibernate.engine.spi.RowSelection;
import org.hibernate.query.NativeQuery;
import org.hibernate.query.Query;
import org.springframework.beans.factory.annotation.Autowired;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.xml.datatype.Duration;
import javax.xml.datatype.XMLGregorianCalendar;
import java.sql.*;
import java.util.*;
import java.util.Date;
import java.util.Map.Entry;
import java.util.function.BiFunction;

import static org.apache.commons.lang3.ObjectUtils.defaultIfNull;

/**
 * @author lazyman
 */
public class SqlAuditServiceImpl extends SqlBaseService implements AuditService {

    public static final String OP_CLEANUP_AUDIT_MAX_AGE = "cleanupAuditMaxAge";
    public static final String OP_CLEANUP_AUDIT_MAX_RECORDS = "cleanupAuditMaxRecords";
    @Autowired
    private BaseHelper baseHelper;

    private static final Trace LOGGER = TraceManager.getTrace(SqlAuditServiceImpl.class);
    private static final Integer CLEANUP_AUDIT_BATCH_SIZE = 500;

    private static final String QUERY_MAX_RESULT = "setMaxResults";
    private static final String QUERY_FIRST_RESULT = "setFirstResult";

    public SqlAuditServiceImpl(SqlRepositoryFactory repositoryFactory) {
        super(repositoryFactory);
    }

    @Override
    public void audit(AuditEventRecord record, Task task) {
        Validate.notNull(record, "Audit event record must not be null.");
        Validate.notNull(task, "Task must not be null.");

        final String operation = "audit";
        SqlPerformanceMonitorImpl pm = getPerformanceMonitor();
        long opHandle = pm.registerOperationStart(operation, AuditEventRecord.class);
        int attempt = 1;

        while (true) {
            try {
                auditAttempt(record);
                return;
            } catch (RuntimeException ex) {
                attempt = baseHelper.logOperationAttempt(null, operation, attempt, ex, null);
                pm.registerOperationNewAttempt(opHandle, attempt);
            } finally {
                pm.registerOperationFinish(opHandle, attempt);
            }
        }
    }

    @Override
    public List<AuditEventRecord> listRecords(String query, Map<String, Object> params) {
        final String operation = "listRecords";
        SqlPerformanceMonitorImpl pm = getPerformanceMonitor();
        long opHandle = pm.registerOperationStart(operation, AuditEventRecord.class);
        int attempt = 1;

        while (true) {
            try {
                final List<AuditEventRecord> auditEventRecords = new ArrayList<>();

                AuditResultHandler handler = new AuditResultHandler() {

                    @Override
                    public boolean handle(AuditEventRecord auditRecord) {
                        auditEventRecords.add(auditRecord);
                        return true;
                    }

                    @Override
                    public int getProgress() {
                        return 0;
                    }
                };
                listRecordsIterativeAttempt(query, params, handler);
                return auditEventRecords;
            } catch (RuntimeException ex) {
                attempt = baseHelper.logOperationAttempt(null, operation, attempt, ex, null);
                pm.registerOperationNewAttempt(opHandle, attempt);
            } finally {
                pm.registerOperationFinish(opHandle, attempt);
            }
        }
    }

    @Override
    public void listRecordsIterative(String query, Map<String, Object> params, AuditResultHandler handler) {
        // TODO operation recording ... but beware, this method is called from within listRecords
        //  (fortunately, currently it is not used from the outside, so it does not matter that it skips recording)
        final String operation = "listRecordsIterative";
        int attempt = 1;

        while (true) {
            try {
                listRecordsIterativeAttempt(query, params, handler);
                return;
            } catch (RuntimeException ex) {
                attempt = baseHelper.logOperationAttempt(null, operation, attempt, ex, null);
            }
        }
    }

    @Override
    public void reindexEntry(AuditEventRecord record) {
        final String operation = "reindexEntry";
        SqlPerformanceMonitorImpl pm = getPerformanceMonitor();
        long opHandle = pm.registerOperationStart(operation, AuditEventRecord.class);
        int attempt = 1;

        while (true) {
            try {
                reindexEntryAttempt(record);
                return;
            } catch (RuntimeException ex) {
                attempt = baseHelper.logOperationAttempt(null, operation, attempt, ex, null);
                pm.registerOperationNewAttempt(opHandle, attempt);
            } finally {
                pm.registerOperationFinish(opHandle, attempt);
            }
        }
    }

    private void reindexEntryAttempt(AuditEventRecord record) {
        Session session = null;
        try {
            session = baseHelper.beginTransaction();

            RAuditEventRecord reindexed = RAuditEventRecord.toRepo(record, getPrismContext(), null);
            //TODO FIXME temporary hack, merge will eventyually load the object to the session if there isn't one,
            // but in this case we force loading object because of "objectDeltaOperation". There is some problem probably
            // during serializing/deserializing which causes constraint violation on priamry key..
            Object o = session.load(RAuditEventRecord.class, record.getRepoId());

            if (o instanceof RAuditEventRecord) {
                RAuditEventRecord rRecord = (RAuditEventRecord) o;
                rRecord.getChangedItems().clear();
                rRecord.getChangedItems().addAll(reindexed.getChangedItems());

                session.merge(rRecord);
            }

            session.getTransaction().commit();

        } catch (DtoTranslationException ex) {
            baseHelper.handleGeneralCheckedException(ex, session, null);
        } catch (RuntimeException ex) {
            baseHelper.handleGeneralRuntimeException(ex, session, null);
        } finally {
            baseHelper.cleanupSessionAndResult(session, null);
        }

    }

    private void listRecordsIterativeAttempt(String query, Map<String, Object> params,
                                             AuditResultHandler handler) {
        Session session = null;
        int count = 0;

        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("List records attempt\n  query: {}\n params:\n{}", query,
                    DebugUtil.debugDump(params, 2));
        }

        try {
            session = baseHelper.beginReadOnlyTransaction();

            Query q;

            if (StringUtils.isBlank(query)) {
                query = "from RAuditEventRecord as aer where 1=1 order by aer.timestamp desc";
                q = session.createQuery(query);
                setParametersToQuery(q, params);
            } else {
                q = session.createQuery(query);
                setParametersToQuery(q, params);
            }
            // q.setResultTransformer(Transformers.aliasToBean(RAuditEventRecord.class));

            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("List records attempt\n  processed query: {}", q);
            }

            ScrollableResults resultList = q.scroll();

            while (resultList.next()) {
                Object o = resultList.get(0);
                if (!(o instanceof RAuditEventRecord)) {
                    throw new DtoTranslationException(
                            "Unexpected object in result set. Expected audit record, but got "
                                    + o.getClass().getSimpleName());
                }
                RAuditEventRecord raudit = (RAuditEventRecord) o;

                AuditEventRecord audit = RAuditEventRecord.fromRepo(raudit, getPrismContext(), getConfiguration().isUsingSQLServer());

                // TODO what if original name (in audit log) differs from the current one (in repo) ?
                audit.setInitiator(resolve(session, raudit.getInitiatorOid(), raudit.getInitiatorName(), defaultIfNull(raudit.getInitiatorType(), RObjectType.USER)));
                audit.setAttorney(resolve(session, raudit.getAttorneyOid(), raudit.getAttorneyName(), RObjectType.USER));
                audit.setTarget(resolve(session, raudit.getTargetOid(), raudit.getTargetName(), raudit.getTargetType()),
		                getPrismContext());
                audit.setTargetOwner(resolve(session, raudit.getTargetOwnerOid(), raudit.getTargetOwnerName(), raudit.getTargetOwnerType()));
                count++;
                if (!handler.handle(audit)) {
                    LOGGER.trace("Skipping handling of objects after {} was handled. ", audit);
                    break;
                }
            }

            session.getTransaction().commit();

        } catch (DtoTranslationException | SchemaException ex) {
            baseHelper.handleGeneralCheckedException(ex, session, null);
        } catch (RuntimeException ex) {
            baseHelper.handleGeneralRuntimeException(ex, session, null);
        } finally {
            baseHelper.cleanupSessionAndResult(session, null);
        }

        LOGGER.trace("List records iterative attempt processed {} records", count);
    }

    private void setParametersToQuery(Query q, Map<String, Object> params) {
        if (params == null) {
            return;
        }

        if (params.containsKey("setFirstResult")) {
            q.setFirstResult((int) params.get("setFirstResult"));
            params.remove("setFirstResult");
        }
        if (params.containsKey("setMaxResults")) {
            q.setMaxResults((int) params.get("setMaxResults"));
            params.remove("setMaxResults");
        }
        Set<Entry<String, Object>> paramSet = params.entrySet();
        for (Entry<String, Object> p : paramSet) {
            if (p.getValue() == null) {
                q.setParameter(p.getKey(), null);
                continue;
            }

            if (List.class.isAssignableFrom(p.getValue().getClass())) {
                q.setParameterList(p.getKey(), convertValues((List) p.getValue()));
            } else {
                q.setParameter(p.getKey(), toRepoType(p.getValue()));
            }
//			if (XMLGregorianCalendar.class.isAssignableFrom(p.getValue().getClass())) {
//				q.setParameter(p.getKey(), MiscUtil.asDate((XMLGregorianCalendar) p.getValue()));
//			} else if (p.getValue() instanceof AuditEventType) {
//				q.setParameter(p.getKey(), RAuditEventType.toRepo((AuditEventType) p.getValue()));
//			} else if (p.getValue() instanceof AuditEventStage) {
//				q.setParameter(p.getKey(), RAuditEventStage.toRepo((AuditEventStage) p.getValue()));
//			} else {
//				q.setParameter(p.getKey(), p.getValue());
//			}
        }
    }

    private List<?> convertValues(List<?> originValues) {
        List<Object> repoValues = new ArrayList<>();
        for (Object value : originValues) {
            repoValues.add(toRepoType(value));
        }
        return repoValues;
    }

    private Object toRepoType(Object value) {
        if (XMLGregorianCalendar.class.isAssignableFrom(value.getClass())) {
            return MiscUtil.asDate((XMLGregorianCalendar) value);
        } else if (value instanceof AuditEventType) {
            return RAuditEventType.toRepo((AuditEventType) value);
        } else if (value instanceof AuditEventStage) {
            return RAuditEventStage.toRepo((AuditEventStage) value);
        } else if (value instanceof OperationResultStatusType) {
            return ROperationResultStatus.toRepo((OperationResultStatusType) value);
        }

        return value;
    }

    // using generic parameter to avoid typing warnings
    private <X extends ObjectType> PrismObject<X> resolve(Session session, String oid, String defaultName, RObjectType defaultType) throws SchemaException {
        if (oid == null) {
            return null;
        }
        Query query = session.getNamedQuery("get.object");
        query.setParameter("oid", oid);
        query.setResultTransformer(GetObjectResult.RESULT_STYLE.getResultTransformer());
        GetObjectResult object = (GetObjectResult) query.uniqueResult();

        PrismObject result;
        if (object != null) {
            String xml = RUtil.getXmlFromByteArray(object.getFullObject(), getConfiguration().isUseZip());
            result = getPrismContext().parserFor(xml).language(SqlRepositoryServiceImpl.DATA_LANGUAGE).compat().parse();
        } else if (defaultType != null) {
            result = getPrismContext().createObject(defaultType.getJaxbClass());
            result.asObjectable().setName(PolyStringType.fromOrig(defaultName != null ? defaultName : oid));
            result.setOid(oid);
        } else {
            result = null;
        }
        //noinspection unchecked
        return result;
    }

    private void auditAttempt(AuditEventRecord record) {
        Session session = null;
        try {
            session = baseHelper.beginTransaction();

            RAuditEventRecord newRecord = RAuditEventRecord.toRepo(record, getPrismContext(), true);
            session.save(newRecord);

            session.getTransaction().commit();
        } catch (DtoTranslationException ex) {
            baseHelper.handleGeneralCheckedException(ex, session, null);
        } catch (RuntimeException ex) {
            baseHelper.handleGeneralRuntimeException(ex, session, null);
        } finally {
            baseHelper.cleanupSessionAndResult(session, null);
        }
    }

    @Override
    public void cleanupAudit(CleanupPolicyType policy, OperationResult parentResult) {
        Validate.notNull(policy, "Cleanup policy must not be null.");
        Validate.notNull(parentResult, "Operation result must not be null.");

        // TODO review monitoring performance of these cleanup operations
        //  It looks like the attempts (and wasted time) are not counted correctly
        cleanupAuditMaxRecords(policy, parentResult);
        cleanupAuditMaxAge(policy, parentResult);
    }

    private void cleanupAuditMaxAge(CleanupPolicyType policy, OperationResult parentResult) {

        if (policy.getMaxAge() == null) {
            return;
        }

        final String operation = "deletingMaxAge";

        SqlPerformanceMonitorImpl pm = getPerformanceMonitor();
        long opHandle = pm.registerOperationStart(OP_CLEANUP_AUDIT_MAX_AGE, AuditEventRecord.class);
        int attempt = 1;

        Duration duration = policy.getMaxAge();
        if (duration.getSign() > 0) {
            duration = duration.negate();
        }
        Date minValue = new Date();
        duration.addTo(minValue);

        // factored out because it produces INFO-level message
        Dialect dialect = Dialect.getDialect(baseHelper.getSessionFactoryBean().getHibernateProperties());
		checkTemporaryTablesSupport(dialect);

        long start = System.currentTimeMillis();
        boolean first = true;
        Holder<Integer> totalCountHolder = new Holder<>(0);
        try {
            while (true) {
                try {
                    LOGGER.info("{} audit cleanup, deleting up to {} (duration '{}'), batch size {}{}.",
                            first ? "Starting" : "Continuing with ", minValue, duration, CLEANUP_AUDIT_BATCH_SIZE,
                            first ? "" : ", up to now deleted " + totalCountHolder.getValue() + " entries");
                    first = false;
                    int count;
                    do {
                        // the following method may restart due to concurrency
                        // (or any other) problem - in any iteration
                        long batchStart = System.currentTimeMillis();
                        LOGGER.debug(
                                "Starting audit cleanup batch, deleting up to {} (duration '{}'), batch size {}, up to now deleted {} entries.",
                                minValue, duration, CLEANUP_AUDIT_BATCH_SIZE, totalCountHolder.getValue());
						count = batchDeletionAttempt((session, tempTable) -> selectRecordsByMaxAge(session, tempTable, minValue, dialect),
								totalCountHolder, batchStart, dialect, parentResult);
                    } while (count > 0);
                    return;
                } catch (RuntimeException ex) {
                    attempt = baseHelper.logOperationAttempt(null, operation, attempt, ex, parentResult);
                    pm.registerOperationNewAttempt(opHandle, attempt);
                }
            }
        } finally {
            pm.registerOperationFinish(opHandle, attempt);
            LOGGER.info("Audit cleanup based on age finished; deleted {} entries in {} seconds.",
                    totalCountHolder.getValue(), (System.currentTimeMillis() - start) / 1000L);
        }
    }

    private void cleanupAuditMaxRecords(CleanupPolicyType policy, OperationResult parentResult) {

        if (policy.getMaxRecords() == null) {
            return;
        }

        final String operation = "deletingMaxRecords";

        SqlPerformanceMonitorImpl pm = getPerformanceMonitor();
        long opHandle = pm.registerOperationStart(OP_CLEANUP_AUDIT_MAX_RECORDS, AuditEventRecord.class);
        int attempt = 1;

        Integer recordsToKeep = policy.getMaxRecords();

		// factored out because it produces INFO-level message
		Dialect dialect = Dialect.getDialect(baseHelper.getSessionFactoryBean().getHibernateProperties());
		checkTemporaryTablesSupport(dialect);

        long start = System.currentTimeMillis();
        boolean first = true;
        Holder<Integer> totalCountHolder = new Holder<>(0);
        try {
            while (true) {
                try {
                    LOGGER.info("{} audit cleanup, keeping at most {} records, batch size {}{}.",
                            first ? "Starting" : "Continuing with ", recordsToKeep, CLEANUP_AUDIT_BATCH_SIZE,
                            first ? "" : ", up to now deleted " + totalCountHolder.getValue() + " entries");
                    first = false;
                    int count;
                    do {
                        // the following method may restart due to concurrency
                        // (or any other) problem - in any iteration
                        long batchStart = System.currentTimeMillis();
                        LOGGER.debug(
                                "Starting audit cleanup batch, keeping at most {} records, batch size {}, up to now deleted {} entries.",
                                recordsToKeep, CLEANUP_AUDIT_BATCH_SIZE, totalCountHolder.getValue());
						count = batchDeletionAttempt((session, tempTable) -> selectRecordsByNumberToKeep(session, tempTable, recordsToKeep, dialect),
								totalCountHolder, batchStart, dialect, parentResult);
                    } while (count > 0);
                    return;
                } catch (RuntimeException ex) {
                    attempt = baseHelper.logOperationAttempt(null, operation, attempt, ex, parentResult);
                    pm.registerOperationNewAttempt(opHandle, attempt);
                }
            }
        } finally {
            pm.registerOperationFinish(opHandle, attempt);
            LOGGER.info("Audit cleanup based on record count finished; deleted {} entries in {} seconds.",
                    totalCountHolder.getValue(), (System.currentTimeMillis() - start) / 1000L);
        }
    }

	private void checkTemporaryTablesSupport(Dialect dialect) {
        TemporaryTableDialect ttDialect = TemporaryTableDialect.getTempTableDialect(dialect);

		if (!ttDialect.supportsTemporaryTables()) {
			LOGGER.error("Dialect {} doesn't support temporary tables, couldn't cleanup audit logs.",
					dialect);
			throw new SystemException(
					"Dialect " + dialect + " doesn't support temporary tables, couldn't cleanup audit logs.");
		}
	}

	// deletes one batch of records (using recordsSelector to select records according to particular cleanup policy)
	private int batchDeletionAttempt(BiFunction<Session, String, Integer> recordsSelector, Holder<Integer> totalCountHolder,
                                     long batchStart, Dialect dialect, OperationResult subResult) {

        Session session = null;
        try {
            session = baseHelper.beginTransaction();

            TemporaryTableDialect ttDialect = TemporaryTableDialect.getTempTableDialect(dialect);

			// create temporary table
			final String tempTable = ttDialect.generateTemporaryTableName(RAuditEventRecord.TABLE_NAME);
			createTemporaryTable(session, dialect, tempTable);
			LOGGER.trace("Created temporary table '{}'.", tempTable);

			int count = recordsSelector.apply(session, tempTable);
			LOGGER.trace("Inserted {} audit record ids ready for deleting.", count);

			// drop records from m_audit_item, m_audit_event, m_audit_delta, and others
			session.createNativeQuery(createDeleteQuery(RAuditItem.TABLE_NAME, tempTable,
					RAuditItem.COLUMN_RECORD_ID)).executeUpdate();
			session.createNativeQuery(createDeleteQuery(RObjectDeltaOperation.TABLE_NAME, tempTable,
					RObjectDeltaOperation.COLUMN_RECORD_ID)).executeUpdate();
			session.createNativeQuery(createDeleteQuery(RAuditPropertyValue.TABLE_NAME, tempTable,
					RAuditPropertyValue.COLUMN_RECORD_ID)).executeUpdate();
			session.createNativeQuery(createDeleteQuery(RAuditReferenceValue.TABLE_NAME, tempTable,
					RAuditReferenceValue.COLUMN_RECORD_ID)).executeUpdate();
			session.createNativeQuery(createDeleteQuery(RAuditEventRecord.TABLE_NAME, tempTable, "id"))
					.executeUpdate();

			// drop temporary table
			if (ttDialect.dropTemporaryTableAfterUse()) {
				LOGGER.debug("Dropping temporary table.");
				StringBuilder sb = new StringBuilder();
				sb.append(ttDialect.getDropTemporaryTableString());
				sb.append(' ').append(tempTable);

				session.createNativeQuery(sb.toString()).executeUpdate();
            }

            session.getTransaction().commit();
            int totalCount = totalCountHolder.getValue() + count;
            totalCountHolder.setValue(totalCount);
            LOGGER.debug("Audit cleanup batch finishing successfully in {} milliseconds; total count = {}",
                    System.currentTimeMillis() - batchStart, totalCount);
            return count;
        } catch (RuntimeException ex) {
            LOGGER.debug("Audit cleanup batch finishing with exception in {} milliseconds; exception = {}",
                    System.currentTimeMillis() - batchStart, ex.getMessage());
            baseHelper.handleGeneralRuntimeException(ex, session, subResult);
            throw new AssertionError("We shouldn't get here.");
        } finally {
            baseHelper.cleanupSessionAndResult(session, subResult);
        }
    }

	private int selectRecordsByMaxAge(Session session, String tempTable, Date minValue, Dialect dialect) {

		// fill temporary table, we don't need to join task on object on
		// container, oid and id is already in task table
		StringBuilder selectSB = new StringBuilder();
		selectSB.append("select a.id as id from ").append(RAuditEventRecord.TABLE_NAME).append(" a");
		selectSB.append(" where a.").append(RAuditEventRecord.COLUMN_TIMESTAMP).append(" < ###TIME###");
		String selectString = selectSB.toString();

		// batch size
		RowSelection rowSelection = new RowSelection();
		rowSelection.setMaxRows(CLEANUP_AUDIT_BATCH_SIZE);
		LimitHandler limitHandler = dialect.getLimitHandler();
		selectString = limitHandler.processSql(selectString, rowSelection);

		// replace ? -> batch size, $ -> ?
		// Sorry for that .... I just don't know how to write this query in HQL,
		// nor I'm not sure if limiting max size in
		// compound insert into ... select ... query via query.setMaxSize()
		// would work - TODO write more nicely if anybody knows how)
		selectString = selectString.replace("?", String.valueOf(CLEANUP_AUDIT_BATCH_SIZE));
		selectString = selectString.replace("###TIME###", "?");

		String queryString = "insert into " + tempTable + " " + selectString;
		LOGGER.trace("Query string = {}", queryString);
		NativeQuery query = session.createNativeQuery(queryString);
		query.setParameter(1, new Timestamp(minValue.getTime()));

		return query.executeUpdate();
    }

	private int selectRecordsByNumberToKeep(Session session, String tempTable, Integer recordsToKeep, Dialect dialect) {
        CriteriaBuilder cb = session.getCriteriaBuilder();
        CriteriaQuery cq = cb.createQuery(RAuditEventRecord.class);
        cq.select(cb.count(cq.from(RAuditEventRecord.class)));
        Number totalAuditRecords = (Number) session.createQuery(cq).uniqueResult();
        int recordsToDelete = totalAuditRecords.intValue() - recordsToKeep;
        if (recordsToDelete <= 0) {
            recordsToDelete = 0;
        } else if (recordsToDelete > CLEANUP_AUDIT_BATCH_SIZE) {
            recordsToDelete = CLEANUP_AUDIT_BATCH_SIZE;
        }
        LOGGER.debug("Total audit records: {}, records to keep: {} => records to delete in this batch: {}",
                totalAuditRecords, recordsToKeep, recordsToDelete);
        if (recordsToDelete == 0) {
			return 0;
        }

		StringBuilder selectSB = new StringBuilder();
		selectSB.append("select a.id as id from ").append(RAuditEventRecord.TABLE_NAME).append(" a");
		selectSB.append(" order by a.").append(RAuditEventRecord.COLUMN_TIMESTAMP).append(" asc");
		String selectString = selectSB.toString();

		// batch size
		RowSelection rowSelection = new RowSelection();
		rowSelection.setMaxRows(recordsToDelete);
		LimitHandler limitHandler = dialect.getLimitHandler();
		selectString = limitHandler.processSql(selectString, rowSelection);
		selectString = selectString.replace("?", String.valueOf(recordsToDelete));

		String queryString = "insert into " + tempTable + " " + selectString;
		LOGGER.trace("Query string = {}", queryString);
		NativeQuery query = session.createNativeQuery(queryString);
		return query.executeUpdate();
	}

	/**
	 * This method creates temporary table for cleanup audit method.
	 *
	 * @param session
	 * @param dialect
	 * @param tempTable
	 */
	private void createTemporaryTable(Session session, final Dialect dialect, final String tempTable) {
		session.doWork(connection -> {
			// check if table exists
            if (!getConfiguration().isUsingPostgreSQL()) {
                try {
                    Statement s = connection.createStatement();
                    s.execute("select id from " + tempTable + " where id = 1");

                    s.close();
                    // table already exists
                    return;
                } catch (Exception ex) {
                    // we expect this on the first time
                }
            }

            TemporaryTableDialect ttDialect = TemporaryTableDialect.getTempTableDialect(dialect);

			StringBuilder sb = new StringBuilder();
			sb.append(ttDialect.getCreateTemporaryTableString());
			sb.append(' ').append(tempTable).append(" (id ");
			sb.append(dialect.getTypeName(Types.BIGINT));
			sb.append(" not null)");
			sb.append(ttDialect.getCreateTemporaryTablePostfix());

            Statement s = connection.createStatement();
            s.execute(sb.toString());
            s.close();
		});
	}

	private String createDeleteQuery(String objectTable, String tempTable, String idColumnName) {
		if (getConfiguration().isUsingMySqlCompatible()) {
			return createDeleteQueryAsJoin(objectTable, tempTable, idColumnName);
		} else if (getConfiguration().isUsingPostgreSQL()) {
			return createDeleteQueryAsJoinPostgreSQL(objectTable, tempTable, idColumnName);
		} else {
			// todo consider using join for other databases as well
			return createDeleteQueryAsSubquery(objectTable, tempTable, idColumnName);
		}
	}

	private String createDeleteQueryAsJoin(String objectTable, String tempTable, String idColumnName) {
		return "DELETE FROM main, temp USING " + objectTable + " AS main INNER JOIN " + tempTable + " as temp "
				+ "WHERE main." + idColumnName + " = temp.id";
	}

	private String createDeleteQueryAsJoinPostgreSQL(String objectTable, String tempTable, String idColumnName) {
		return "delete from " + objectTable + " main using " + tempTable + " temp where main." + idColumnName + " = temp.id";
	}

	private String createDeleteQueryAsSubquery(String objectTable, String tempTable, String idColumnName) {
		StringBuilder sb = new StringBuilder();
		sb.append("delete from ").append(objectTable);
		sb.append(" where ").append(idColumnName).append(" in (select id from ").append(tempTable)
				.append(')');

		return sb.toString();
    }

    public long countObjects(String query, Map<String, Object> params) {
        Session session = null;
        long count = 0;
        try {
            session = baseHelper.beginTransaction();
            session.setFlushMode(FlushMode.MANUAL);
            if (StringUtils.isBlank(query)) {
                query = "select count (*) from RAuditEventRecord as aer where 1 = 1";
            }
            Query q = session.createQuery(query);

            setParametersToQuery(q, params);
            Number numberCount = (Number) q.uniqueResult();
            count = numberCount != null ? numberCount.intValue() : 0;
        } catch (RuntimeException ex) {
            baseHelper.handleGeneralRuntimeException(ex, session, null);
        } finally {
            baseHelper.cleanupSessionAndResult(session, null);
        }
        return count;
    }

    @Override
    public boolean supportsRetrieval() {
        return true;
    }

}
