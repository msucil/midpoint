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

package com.evolveum.midpoint.repo.sql.data.common.dictionary;

import com.evolveum.midpoint.prism.ItemDefinition;
import com.evolveum.midpoint.repo.api.RepositoryService;
import com.evolveum.midpoint.repo.sql.RestartOperationRequestedException;
import com.evolveum.midpoint.repo.sql.perf.SqlPerformanceMonitorImpl;
import com.evolveum.midpoint.repo.sql.SqlRepositoryServiceImpl;
import com.evolveum.midpoint.repo.sql.data.common.any.RExtItem;
import com.evolveum.midpoint.repo.sql.helpers.BaseHelper;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import org.hibernate.Session;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;

import javax.annotation.PostConstruct;
import javax.persistence.criteria.CriteriaQuery;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TODO clean this up!
 *
 * @author mederly
 */
public class ExtItemDictionary {

    private static final Trace LOGGER = TraceManager.getTrace(ExtItemDictionary.class);

    @Autowired private SqlRepositoryServiceImpl repositoryService;
    @Autowired private BaseHelper baseHelper;

    private Map<Integer, RExtItem> itemsById;
    private Map<RExtItem.Key, RExtItem> itemsByKey;

    private boolean fetchItemsIfNeeded() {
        if (itemsByKey != null) {
            return false;
        } else {
            fetchItems();
            return true;
        }
    }

    private void fetchItems() {
        executeAttempts(RepositoryService.OP_FETCH_EXT_ITEMS, RExtItem.class, "fetch ext items", () -> fetchItemsAttempt());
    }

    private void fetchItemsAttempt() {
        Session session = null;
        try {
            session = baseHelper.beginReadOnlyTransaction();

            CriteriaQuery<RExtItem> query = session.getCriteriaBuilder().createQuery(RExtItem.class);
            query.select(query.from(RExtItem.class));
            List<RExtItem> items = session.createQuery(query).getResultList();
            LOGGER.debug("Fetched {} item definitions", items.size());

            itemsById = new ConcurrentHashMap<>(items.size());
            itemsByKey = new ConcurrentHashMap<>(items.size());

            for (RExtItem item : items) {
                itemsById.put(item.getId(), item);
                itemsByKey.put(item.toKey(), item);
            }

            session.getTransaction().commit();
        } catch (RuntimeException ex) {
            LOGGER.debug("Exception fetch: {}", ex.getMessage());
            baseHelper.handleGeneralException(ex, session, null);
        } finally {
            baseHelper.cleanupSessionAndResult(session, null);
        }
    }

    // TODO add "synchronized" before 4.0 release
    @NotNull
    public RExtItem createOrFindItemDefinition(@NotNull ItemDefinition<?> definition, boolean throwExceptionAfterCreate) {
        return createOrFindItemByDefinitionInternal(definition, true, throwExceptionAfterCreate);
    }

    // TODO add "synchronized" before 4.0 release
    @NotNull
    public RExtItem createOrFindItemDefinition(@NotNull ItemDefinition<?> definition) {
        return createOrFindItemByDefinitionInternal(definition, true, true);
    }

    // TODO add "synchronized" before 4.0 release
    @Nullable
    public RExtItem findItemByDefinition(@NotNull ItemDefinition<?> definition) {
        return createOrFindItemByDefinitionInternal(definition, false, true);
    }

    @Contract("_, _, true -> !null")
    private RExtItem createOrFindItemByDefinitionInternal(
            @NotNull ItemDefinition<?> definition, boolean create, boolean throwExceptionAfterCreate) {

        boolean fetchedNow = fetchItemsIfNeeded();
        RExtItem.Key key = RExtItem.createKeyFromDefinition(definition);

        RExtItem item = itemsByKey.get(key);

        if (item == null && !fetchedNow) {
            LOGGER.debug("Ext item for {} not found, fetching all items.", key);
            fetchItems();
            item = itemsByKey.get(key);
        }
        if (item == null && create) {
            LOGGER.debug("Ext item for {} not found even in current items; creating it.", key);

            item = RExtItem.createFromDefinition(definition);

            addExtItemAttempt(item);

            if (throwExceptionAfterCreate) {
                throw new RestartOperationRequestedException("Restarting parent operation because an extension item was created");
            }
        }

        return item;
    }

    @PostConstruct
    public synchronized void initialize() {
        itemsByKey = null;
        itemsById = null;
    }

    private void addExtItemAttempt(RExtItem item) {
        Session session = null;
        try {
            session = baseHelper.beginTransaction();
            session.persist(item);
            session.getTransaction().commit();
        } catch (RuntimeException ex) {
            baseHelper.handleGeneralException(ex, session, null);
        } finally {
            baseHelper.cleanupSessionAndResult(session, null);
        }
    }

    private void executeAttempts(String operationName, Class<?> type, String operationVerb, Runnable runnable) {
        SqlPerformanceMonitorImpl pm = repositoryService.getPerformanceMonitor();
        long opHandle = pm.registerOperationStart(operationName, type);
        int attempt = 1;
        try {
            while (true) {
                try {
                    runnable.run();
                    break;
                } catch (RuntimeException ex) {
                    attempt = baseHelper.logOperationAttempt(null, operationVerb, attempt, ex, null);
                    pm.registerOperationNewAttempt(opHandle, attempt);
                }
            }
        } finally {
            pm.registerOperationFinish(opHandle, attempt);
        }
    }

    // TODO add "synchronized" before 4.0 release
    public RExtItem getItemById(Integer extItemId) {
        boolean fresh = fetchItemsIfNeeded();
        RExtItem extItem = itemsById.get(extItemId);
        if (extItem != null || fresh) {
            return extItem;
        }
        fetchItems();
        return itemsById.get(extItemId);
    }
}
