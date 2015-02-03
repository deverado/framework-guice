package de.deverado.framework.guice;/*
 * Copyright Georg Koester 2012-15. All rights reserved.
 */

import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.name.Names;

import java.util.concurrent.ScheduledExecutorService;

/**
 * Binds a low processing load pool with name low_processing_load, a default pool with name default and a scheduled
 * executor without a name.
 * Needs: ProblemReporter binding and optionally Shutdown listener .
 */
public class DefaultThreadingModule extends AbstractModule {

    @Override
    protected void configure() {
        ThreadPoolHelpers.bindDefaultLowProcessingLoadThreadPool("low_processing_load", binder());
        ThreadPoolHelpers.bindDefaultThreadPool("default", binder(), 10000);
        ThreadPoolHelpers.bindDefaultScheduledThreadPool(binder());

    }

    public static void shutdown(Injector injector) {
        injector.getInstance(Key.get(ListeningExecutorService.class, Names.named("low_processing_load"))).shutdown();
        injector.getInstance(Key.get(ListeningExecutorService.class, Names.named("default"))).shutdown();
        injector.getInstance(ScheduledExecutorService.class).shutdown();
    }
}
