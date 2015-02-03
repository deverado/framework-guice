package de.deverado.framework.guice;/*
 * Copyright Georg Koester 2012-15. All rights reserved.
 */

import com.google.common.collect.Multimap;
import com.google.inject.Inject;
import com.google.inject.Provider;
import de.deverado.framework.concurrent.RunlevelShutdownListenerRunner;
import de.deverado.framework.concurrent.RunlevelStartupListenerRunner;
import de.deverado.framework.concurrent.ShutdownListener;
import de.deverado.framework.concurrent.StartupListener;

import java.util.Set;

public class StartupShutdownListenerMgr {

    @Inject
    Provider<Set<StartupListener>> startupListeners;

    @Inject
    Provider<Set<ShutdownListener>> shutdownListeners;

    @Inject
    Multimap<String, StartupListener> startupListenersByRunlevel;

    @Inject
    Multimap<String, ShutdownListener> shutdownListenersByRunlevel;

    /**
     * Runs startup listeners registered via Guice, no timeout. Exceptions propagated, startup listener execution is aborted after the
     * exception.
     * Shutdown listeners are not run - also not after an exception was thrown.
     * @throws Exception propagated from StartupListener
     */
    public void runListenersForStartup() throws Exception {
        Multimap<String, StartupListener> runlevelMap =
                RunlevelShutdownListenerRunner.<StartupListener>createRunlevelMap(startupListeners.get(),
                        startupListenersByRunlevel, "0");

        RunlevelStartupListenerRunner runner = RunlevelStartupListenerRunner.create(runlevelMap);
        runner.call();
    }

    /**
     * Runs shutdown listeners registered via Guice. Exceptions swallowed, startup listener execution is continued
     * after single listener failed with exception.
     * Shutdown listeners are not run - also not after an exception was thrown.
     *
     * @param executionTimeoutMs Timeout after which the the runner will stop waiting for the (parallel) execution
     *                           of listeners to finish.
     */
    public void runListenersForShutdown(int executionTimeoutMs) {
        Multimap<String, ShutdownListener> runlevelMap =
                RunlevelShutdownListenerRunner.createRunlevelMap(shutdownListeners.get(),
                        shutdownListenersByRunlevel, "0");

        RunlevelShutdownListenerRunner runner = RunlevelShutdownListenerRunner.create(runlevelMap, executionTimeoutMs);
        try {
            runner.call();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
