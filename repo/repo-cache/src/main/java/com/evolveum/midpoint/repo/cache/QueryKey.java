/*
 * Copyright (c) 2010-2014 Evolveum
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.evolveum.midpoint.repo.cache;

import com.evolveum.midpoint.prism.PrismContext;
import com.evolveum.midpoint.prism.query.ObjectQuery;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ObjectType;

/**
 * @author Pavol Mederly
 */
public class QueryKey {

    private Class<? extends ObjectType> type;
    private ObjectQuery query;

    public <T extends ObjectType> QueryKey(Class<T> type, ObjectQuery query) {
        this.type = type;
        this.query = query;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        QueryKey queryKey = (QueryKey) o;

        if (query != null ? !query.equals(queryKey.query) : queryKey.query != null) return false;
        if (type != null ? !type.equals(queryKey.type) : queryKey.type != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = type != null ? type.hashCode() : 0;
        result = 31 * result + (query != null ? query.hashCode() : 0);
        return result;
    }

    public Class<? extends ObjectType> getType() {
        return type;
    }

    public ObjectQuery getQuery() {
        return query;
    }

    @Override
    public String toString() {
        return "QueryKey{" +
                "type=" + type +
                ", query=" + query +
                '}';
    }
}
