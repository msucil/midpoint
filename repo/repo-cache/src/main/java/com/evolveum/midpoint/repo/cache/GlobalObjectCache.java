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

package com.evolveum.midpoint.repo.cache;

import com.evolveum.midpoint.schema.cache.CacheType;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ObjectType;
import org.cache2k.Cache2kBuilder;
import org.cache2k.expiry.ExpiryPolicy;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;

/**
 *
 */
@Component
public class GlobalObjectCache extends AbstractGlobalCache {

	private static final Trace LOGGER = TraceManager.getTrace(GlobalObjectCache.class);

	private static final String CACHE_NAME = "objectCache";

	private org.cache2k.Cache<String, GlobalCacheObjectValue> cache;

	public void initialize() {
		if (cache != null) {
			LOGGER.warn("Global object cache was already initialized -- ignoring this request.");
			return;
		}
		long capacity = getCapacity();
		if (capacity == 0) {
			LOGGER.warn("Capacity for " + getCacheType() + " is set to 0; this cache will be disabled (until system restart)");
			cache = null;
		} else {
			cache = new Cache2kBuilder<String, GlobalCacheObjectValue>() {}
					.name(CACHE_NAME)
					.entryCapacity(capacity)
					.expiryPolicy(getExpirePolicy())
					.storeByReference(true) // this is default in the current version of cache2k; we need this because we update TTL value for cached objects
					.build();
			LOGGER.info("Created global repository object cache with a capacity of {} objects", capacity);
		}
	}

	private ExpiryPolicy<String, GlobalCacheObjectValue> getExpirePolicy() {
		return (key, value, loadTime, oldEntry) -> getExpiryTime(value.getObjectType());
	}

	@PreDestroy
	public void destroy() {
		if (cache != null) {
			cache.close();
			cache = null;
		}
	}

	public boolean isAvailable() {
		return cache != null;
	}

	public <T extends ObjectType> GlobalCacheObjectValue<T> get(String oid) {
		//noinspection unchecked
		return cache != null ? cache.peek(oid) : null;
	}

	public void remove(String oid) {
		if (cache != null) {
			cache.remove(oid);
		}
	}

	public <T extends ObjectType> void put(GlobalCacheObjectValue<T> cacheObject) {
		if (cache != null) {
			cache.put(cacheObject.getObjectOid(), cacheObject);
		}
	}

	@Override
	protected CacheType getCacheType() {
		return CacheType.GLOBAL_REPO_OBJECT_CACHE;
	}
}
