package de.deverado.framework.guice.coreext.propertyconfig;

import com.google.common.base.Preconditions;
import com.google.inject.AbstractModule;
import com.google.inject.name.Names;
import de.deverado.framework.core.propertyconfig.PropsConfigurator;

import java.util.Properties;

/**
 * 
 */
public abstract class AbstractPropsConfigLoadingModule extends AbstractModule {

    protected AbstractPropsConfigLoadingModule() {
    }

    protected AbstractPropsConfigLoadingModule(PropsConfigurator pc) {
        this.pc = pc;
    }

    public void setPc(PropsConfigurator pc) {
        this.pc = pc;
    }

    private PropsConfigurator pc;

    private Properties loadedProps;

    public Properties load(String envProp) {
        Preconditions.checkState(pc != null, "PC not set!");
        loadedProps = pc.loadPropsCombination(envProp);
        return getLoadedProps();
    }

    public Properties load() {
        return load(null);
    }

    public Properties getLoadedProps() {
        if (loadedProps == null) {
            load();
        }
        return loadedProps;
    }

    @Override
    protected void configure() {
        Names.bindProperties(binder(), getLoadedProps());
    }

}
