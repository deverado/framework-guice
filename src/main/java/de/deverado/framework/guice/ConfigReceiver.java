/*
 * Copyright (c) Georg Koester 2012-15. All rights reserved.
 */
package de.deverado.framework.guice;

import java.util.List;
import java.util.Map;

/**
 * Implement this on StartupListener to receive configuration before services are executed in a runlevel.
 *
 * Configuration with string properties was causing so many
 * weird errors, it warrants more careful attention during
 * startup and a grouped reporting per-startlevel. This is
 * providing the tools.
 *
 * Furthermore combined with {@link de.deverado.framework.guice.coreext.propertyconfig.ExtendablePropertyConfig} this
 * allows earlier runlevels to gather configuration information and update/extend the configuration that is used in
 * setting up the later runlevels.
 */
public interface ConfigReceiver {

    /**
     * @return the classes that should be created (with injection) and receive configuration just before executing
     * the startup listeners for the a runlevel.
     */
    List<Class> getConfigurationHolderClasses();

    /**
     * Instantiated objects for classes returned from {@link #getConfigurationHolderClasses()}.
     */
    void setConfigurationHolders(Map<Class, Object> configurationHolders);
}
