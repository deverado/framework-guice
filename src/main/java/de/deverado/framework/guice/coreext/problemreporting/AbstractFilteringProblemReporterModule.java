package de.deverado.framework.guice.coreext.problemreporting;

import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.multibindings.Multibinder;
import de.deverado.framework.core.problemreporting.ProblemReporterFilter;
import de.deverado.framework.core.problemreporting.ProblemReporterFilterTool;

import java.util.Set;

/**
 * If using this module see to it that filters are added to the {@link com.google.inject.multibindings.Multibinder}
 * for {@link de.deverado.framework.core.problemreporting.ProblemReporterFilter}s.
 */
public class AbstractFilteringProblemReporterModule extends AbstractModule
        implements Module {

    @Override
    protected void configure() {

        Multibinder.newSetBinder(binder(), ProblemReporterFilter.class);
    }

    /**
     * Calls init() on instance.
     *
     */
    @Provides
    public ProblemReporterFilterTool provideProblemReporterFilterTool(
            Injector inj, Set<ProblemReporterFilter> filters) {
        ProblemReporterFilterTool retval = new ProblemReporterFilterTool(filters);
        retval.init();
        return retval;
    }
}
