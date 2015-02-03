package de.deverado.framework.guice.coreext;

import com.google.inject.AbstractModule;
import de.deverado.framework.core.Process2Builder;

public class Process2Module extends AbstractModule {

    @Override
    protected void configure() {
        bind(Process2Builder.class).to(Process2BuilderUsingLowProcessingLoadPool.class);
    }
}
