package de.deverado.framework.guice;/*
 * Copyright Georg Koester 2012-15. All rights reserved.
 */

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.google.common.collect.Multimap;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Provider;
import de.deverado.framework.concurrent.RunlevelShutdownListenerRunner;
import de.deverado.framework.concurrent.RunlevelStartupListenerRunner;
import de.deverado.framework.concurrent.ShutdownListener;
import de.deverado.framework.concurrent.StartupListener;
import de.deverado.framework.core.problemreporting.Problem;
import de.deverado.framework.guice.coreext.propertyconfig.ExtendablePropertyConfig;
import org.apache.commons.lang3.tuple.Pair;

import javax.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Stream;

public class StartupShutdownListenerMgr {

    @Inject
    Provider<Set<StartupListener>> startupListeners;

    @Inject
    Provider<Set<ConfigReceiver>> configListeners;

    @Inject
    Provider<Set<ShutdownListener>> shutdownListeners;

    @Inject
    Multimap<String, StartupListener> startupListenersByRunlevel;

    @Inject
    Multimap<String, ShutdownListener> shutdownListenersByRunlevel;

    @Inject
    Injector injector;

    @Inject(optional = true)
    ExtendablePropertyConfig extendablePropertyConfig;

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
        runner.addPreRunListener(new Function<Pair<String, Collection<StartupListener>>, Void>() {
            @Nullable
            @Override
            public Void apply(Pair<String, Collection<StartupListener>> input) {
                Collection<?> configReceiversForRunlevel = Collections2.<StartupListener>filter(
                        input.getRight(),
                        new Predicate<StartupListener>() {
                            @Override
                            public boolean apply(@Nullable StartupListener input) {
                                return input instanceof ConfigReceiver;
                            }
                        });

                List<Problem> problems = applyConfig((Collection<ConfigReceiver>) configReceiversForRunlevel);

                if (!problems.isEmpty()) {
                    Collections.sort(problems, new Comparator<Problem>() {
                        @Override
                        public int compare(Problem o1, Problem o2) {
                            return o1.getRef().compareTo(o2.getMessage());
                        }
                    });
                    String message = Joiner.on("/n").join(
                            Collections2.transform(problems, new Function<Problem, String>() {
                                @Nullable
                                @Override
                                public String apply(@Nullable Problem p) {
                                    if (p == null) {
                                        return "";
                                    }
                                    return "Config problem: " + p.getRef() + ": " + p.getMessage();
                                }
                            }));
                    throw new RuntimeException(message);
                }
                return null;
            }
        });

        runner.call();
    }

    private List<Problem> applyConfig(Collection<ConfigReceiver> configReceiversForRunlevel) {
        List<Problem> problems = new ArrayList<>();
        if (extendablePropertyConfig != null) {
            Properties config = extendablePropertyConfig.getConfig();
            for (ConfigReceiver cr : configReceiversForRunlevel) {
                List<Class> configurationHolderClasses = cr.getConfigurationHolderClasses();
                if (configurationHolderClasses == null) {
                    continue;
                }

                Map<Class, Object> instances = new HashMap<>();
                boolean hasLocalProblems = false;
                for (Class<?> c : configurationHolderClasses) {
                    Object instance = injector.getInstance(c);
                    List<Problem> localProblems = GuiceUtil.bindConfig(config, instance, true, true);
                    if (!localProblems.isEmpty()) {
                        hasLocalProblems = true;
                        problems.addAll(localProblems);
                    } else {
                        instances.put(c, instance);
                    }
                }
                if (!hasLocalProblems) {
                    cr.setConfigurationHolders(instances);
                }
            }
        }
        return problems;
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
