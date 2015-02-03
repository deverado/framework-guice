package de.deverado.framework.guice.coreext.problemreporting;

import de.deverado.framework.core.problemreporting.LoggingProblemReporter;
import de.deverado.framework.core.problemreporting.ProblemReporter;

public class LoggingProblemReporterModule extends
        AbstractFilteringProblemReporterModule {

    @Override
    protected void configure() {
        super.configure();
        bind(ProblemReporter.class).to(LoggingProblemReporter.class);
    }
}
