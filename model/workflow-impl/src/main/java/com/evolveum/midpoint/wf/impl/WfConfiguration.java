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
package com.evolveum.midpoint.wf.impl;

import com.evolveum.midpoint.common.configuration.api.MidpointConfiguration;
import com.evolveum.midpoint.util.exception.SystemException;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.wf.impl.processors.ChangeProcessor;
import org.apache.commons.configuration.Configuration;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.*;

/**
 *  Holds static configuration of workflows (from config.xml file).
 */
@Component
@DependsOn({ "midpointConfiguration" })
public class WfConfiguration {

    private static final transient Trace LOGGER = TraceManager.getTrace(WfConfiguration.class);

    private static final String KEY_ENABLED = "enabled";
    private static final List<String> KNOWN_KEYS = Arrays.asList("midpoint.home", KEY_ENABLED);
    private static final List<String> DEPRECATED_KEYS = Collections.emptyList();

    @Autowired
    private MidpointConfiguration midpointConfiguration;

    private boolean enabled;

    private List<ChangeProcessor> changeProcessors = new ArrayList<>();

    @PostConstruct
    void initialize() {
        Configuration c = midpointConfiguration.getConfiguration(MidpointConfiguration.WORKFLOW_CONFIGURATION);
        checkAllowedKeys(c);

        enabled = c.getBoolean(KEY_ENABLED, true);
        if (!enabled) {
            LOGGER.info("Workflows are disabled.");
        }
    }

    private void checkAllowedKeys(Configuration c) {
        Set<String> knownKeysSet = new HashSet<>(KNOWN_KEYS);
        Set<String> deprecatedKeysSet = new HashSet<>(DEPRECATED_KEYS);

        //noinspection unchecked
        Iterator<String> keyIterator = c.getKeys();
        while (keyIterator.hasNext())  {
            String keyName = keyIterator.next();
            String normalizedKeyName = StringUtils.substringBefore(keyName, ".");                       // because of subkeys
            if (deprecatedKeysSet.contains(keyName) || deprecatedKeysSet.contains(normalizedKeyName)) {
                throw new SystemException("Deprecated key " + keyName + " in workflow configuration. Please see https://wiki.evolveum.com/display/midPoint/Workflow+configuration.");
            }
            if (!knownKeysSet.contains(keyName) && !knownKeysSet.contains(normalizedKeyName)) {         // ...we need to test both because of keys like 'midpoint.home'
                throw new SystemException("Unknown key " + keyName + " in workflow configuration");
            }
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public ChangeProcessor findChangeProcessor(String processorClassName) {
        for (ChangeProcessor cp : changeProcessors) {
            if (cp.getClass().getName().equals(processorClassName)) {
                return cp;
            }
        }
        throw new IllegalStateException("Change processor " + processorClassName + " is not registered.");
    }

    public void registerProcessor(ChangeProcessor changeProcessor) {
        changeProcessors.add(changeProcessor);
    }

    public List<ChangeProcessor> getChangeProcessors() {
        return changeProcessors;
    }
}
