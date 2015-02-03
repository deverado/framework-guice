package de.deverado.framework.guice;/*
 * Copyright Georg Koester 2012-15. All rights reserved.
 */

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.google.inject.AbstractModule;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.Multibinder;
import de.deverado.framework.concurrent.ShutdownListener;
import de.deverado.framework.concurrent.StartupListener;

public class StartupShutdownListenerModule extends AbstractModule {

    @Override
    protected void configure() {

        Multibinder.newSetBinder(binder(), StartupListener.class);
        Multibinder.newSetBinder(binder(), ShutdownListener.class);

        bind(new TypeLiteral<Multimap<String, ShutdownListener>>(){}).toInstance(Multimaps.synchronizedMultimap(
                ArrayListMultimap.<String, ShutdownListener>create()));

        bind(new TypeLiteral<Multimap<String, StartupListener>>(){}).toInstance(Multimaps.synchronizedMultimap(
                ArrayListMultimap.<String, StartupListener>create()));
    }
}
