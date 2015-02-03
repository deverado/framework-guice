package de.deverado.framework.guice;

import static org.junit.Assert.assertTrue;

import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import com.google.inject.Guice;
import com.google.inject.Injector;
import de.deverado.framework.guice.coreext.problemreporting.LoggingProblemReporterModule;
import org.junit.Test;

import javax.inject.Inject;
import javax.inject.Named;

public class DefaultThreadingModuleTest {

    @Test
    public void testCreationAndShutdown() {
        Injector injector = Guice.createInjector(new DefaultThreadingModule(), new LoggingProblemReporterModule());
        InjHelper helper = injector.getInstance(InjHelper.class);
        DefaultThreadingModule.shutdown(injector);
        assertTrue(helper.defaultExec.isShutdown());
    }

    public static class InjHelper {
        @Inject
        @Named("default")
        ListeningExecutorService defaultExec;

        @Inject
        @Named("low_processing_load")
        ListeningExecutorService lowProc;

        @Inject
        ListeningScheduledExecutorService scheduled;
    }

}