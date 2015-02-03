package de.deverado.framework.guice.cluster;/*
 * Copyright Georg Koester 2012-15. All rights reserved.
 */

import com.google.inject.AbstractModule;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Names;

import java.util.concurrent.atomic.AtomicReference;

public class ClusterNodeModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(new TypeLiteral<AtomicReference<String>>() {
        }).annotatedWith(Names.named("nodeId")).toInstance(new AtomicReference<String>());
    }
}
