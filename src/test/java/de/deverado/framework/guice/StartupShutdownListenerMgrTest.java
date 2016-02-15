package de.deverado.framework.guice;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.util.Providers;
import de.deverado.framework.concurrent.RunlevelProvider;
import de.deverado.framework.concurrent.StartupListener;
import de.deverado.framework.core.Multimaps2;
import de.deverado.framework.guice.coreext.propertyconfig.ExtendablePropertyConfig;
import org.junit.Test;

import javax.inject.Named;

import java.util.List;
import java.util.Map;
import java.util.Set;

/*
 * Copyright (c) Georg Koester 2012-15. All rights reserved.
 */
public class StartupShutdownListenerMgrTest {

    @Test
    public void testRunListenersForStartup() throws Exception {
        StartupShutdownListenerMgr cut = new StartupShutdownListenerMgr();
        ExtendablePropertyConfig config = new ExtendablePropertyConfig();
        List<TestStartupListener> listeners = ImmutableList.of(//
                new TestStartupListener("rl0", "0", config),
                new TestStartupListener("rl1", "1", config),
                new TestStartupListener("rl2", "2", config),
                new BrokenTestStartupListener("rl3", "3", config));
        Set<? extends StartupListener> listenersAsSet = Sets.newHashSet(listeners);
        cut.startupListeners = Providers.of((Set<StartupListener>) listenersAsSet);
        cut.injector = Guice.createInjector(new AbstractModule() {
            @Override
            protected void configure() {
            }
        });

        cut.extendablePropertyConfig = config;
        cut.startupListenersByRunlevel = Multimaps2.newHashSetMultimap();

        try {
            cut.runListenersForStartup();
            fail();
        } catch (RuntimeException re) {
            assertTrue(re.getMessage(), re.getMessage().contains("Config key 'testkeyBroken' not present in config"));
        }
        assertEquals("default", listeners.get(0).getConfigValSet());
        // now the configuration should have been changed for these:
        assertEquals("0", listeners.get(1).getConfigValSet());
        assertEquals("1", listeners.get(2).getConfigValSet());
        // the following failed, so should be empty:
        assertNull(listeners.get(3).getConfigValSet());

        // fourth listener should not have run, so this still remains:
        assertEquals("2", config.getConfig().getProperty("testkey"));
    }

    private static class BrokenTestStartupListener extends TestStartupListener {

        public BrokenTestStartupListener(String rl, String configValToSet,
                                         ExtendablePropertyConfig config) {
            super(rl, configValToSet, config);
        }

        @Override
        public List<Class> getConfigurationHolderClasses() {
            return ImmutableList.of((Class) BrokenTestConfigReceiver.class);
        }

    }

    private static class TestStartupListener implements StartupListener, RunlevelProvider, ConfigReceiver {

        private final String configVal;
        private final String rl;
        private final ExtendablePropertyConfig config;

        private String configValSet;

        public TestStartupListener(String rl, String configValToSet, ExtendablePropertyConfig config) {
            this.rl = rl;
            this.configVal = configValToSet;
            this.config = config;
        }

        @Override
        public ListenableFuture<?> startup() throws Exception {
            config.addConfig(ImmutableMap.of("testkey", configVal));
            return Futures.immediateFuture(null);
        }

        @Override
        public List<Class> getConfigurationHolderClasses() {
            return ImmutableList.of((Class) TestConfigReceiver.class);
        }

        @Override
        public void setConfigurationHolders(Map<Class, Object> configurationHolders) {
            configValSet = ((TestConfigReceiver) configurationHolders.get(TestConfigReceiver.class)).testKeyVal;
        }

        @Override
        public String getRunlevel() {
            return rl;
        }

        public String getConfigValSet() {
            return configValSet;
        }
    }

    private static class TestConfigReceiver {
        @Named("testkey")
        @Inject(optional = true)
        String testKeyVal = "default";
    }

    private static class BrokenTestConfigReceiver {
        @Named("testkeyBroken")
        String testKeyVal = "default";
    }

}