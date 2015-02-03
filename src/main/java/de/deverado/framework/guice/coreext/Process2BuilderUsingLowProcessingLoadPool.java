package de.deverado.framework.guice.coreext;/*
 * Copyright Georg Koester 2012-15. All rights reserved.
 */

import com.google.common.util.concurrent.ListeningExecutorService;
import de.deverado.framework.core.Process2Builder;

import javax.inject.Inject;
import javax.inject.Named;

public class Process2BuilderUsingLowProcessingLoadPool extends Process2Builder {

    @Inject
    @Named("low_processing_load")
    ListeningExecutorService defaultExec;

    @Override
    protected ListeningExecutorService getDefaultExecutor() {
        return defaultExec;
    }
}
