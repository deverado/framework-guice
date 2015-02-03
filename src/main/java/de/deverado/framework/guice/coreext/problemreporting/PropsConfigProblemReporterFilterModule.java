package de.deverado.framework.guice.coreext.problemreporting;/*
 * Copyright Georg Koester 2012-15. All rights reserved.
 */

import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.name.Named;
import de.deverado.framework.core.problemreporting.ProblemReporterFilter;
import de.deverado.framework.core.problemreporting.TextConfigProblemReporterFilter;

/**
 * Configures a problem reporter filters via props config.
 */
public class PropsConfigProblemReporterFilterModule extends AbstractModule {

    @Override
    protected void configure() {

        Multibinder.newSetBinder(binder(),
                ProblemReporterFilter.class).addBinding().toProvider(new Provider<ProblemReporterFilter>() {

            @Named("problemReporter.filterConfig")
            @Inject(optional = true)
            String filterConfig;

            @Override
            public ProblemReporterFilter get() {

                TextConfigProblemReporterFilter filter = new TextConfigProblemReporterFilter();
                filter.init(filterConfig);
                return filter;
            }
        });
    }
}
