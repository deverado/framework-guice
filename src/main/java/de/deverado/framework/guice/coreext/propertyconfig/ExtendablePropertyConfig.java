/*
 * Copyright (c) Georg Koester 2012-15. All rights reserved.
 */
package de.deverado.framework.guice.coreext.propertyconfig;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Binding this allows changing the configuration during runtime/startup.
 */
public class ExtendablePropertyConfig {

    private Properties currentTopConfig = new Properties();

    public synchronized ExtendablePropertyConfig addConfig(Map<String, String> props) {
        Properties newProperties = new Properties(currentTopConfig);
        newProperties.putAll(props);
        currentTopConfig = newProperties;
        return this;
    }

    public ExtendablePropertyConfig addConfig(Properties props) {
        Map<String, String> converted = new HashMap<>();
        for (String propertyName : props.stringPropertyNames()) {
            converted.put(propertyName, props.getProperty(propertyName));
        }
        addConfig(converted);
        return this;
    }

    public synchronized Properties getConfig() {
        return currentTopConfig;
    }
}
