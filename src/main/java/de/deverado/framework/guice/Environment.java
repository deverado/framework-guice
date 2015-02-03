package de.deverado.framework.guice;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

@Singleton
public class Environment {

    public static enum Code {
        PROD, TEST, DEV, STAGING, LOC_STAGING, DEV_SUPPORT;
    }

    public interface EnvVisitor {
        void dev();

        void devSupport();

        void prod();

        void test();

        void staging();

        void locStaging();
    }

    private final Code envType;

    private final String envTypeString;

    /**
     * @param envCode
     *            via Named with 'app.env' - if <code>null</code> uses app.env
     *            system property - defaults to TEST.
     */
    @Inject
    public Environment(@Named("app.env") @Nullable String envCode) {
        if (envCode != null) {
            this.envType = Code.valueOf(envCode);
        } else {
            this.envType = Code.valueOf(System.getProperty("app.env", "TEST"));
        }
        envTypeString = envType.toString();
        log.debug("Environment set to {}", this.envType);
    }

    public void accept(EnvVisitor v) {
        switch (envType) {
        case PROD:
            v.prod();
            break;
        case TEST:
            v.test();
            break;
        case DEV:
            v.dev();
            break;
        case DEV_SUPPORT:
            v.devSupport();
            break;
        case STAGING:
            v.staging();
            break;
        case LOC_STAGING:
            v.locStaging();
            break;
        default:
            throw new RuntimeException("Missing an env type!");
        }
    }

    public Code getCurrentEnvironment() {
        return envType;
    }

    public String getCurrentEnvironmentStr() {
        return envTypeString;
    }

    @Override
    public String toString() {
        return getCurrentEnvironment().toString();
    }

    private static final Logger log = LoggerFactory
            .getLogger(Environment.class);
}
