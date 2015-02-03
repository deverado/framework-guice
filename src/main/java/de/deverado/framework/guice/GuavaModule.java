package de.deverado.framework.guice;

import com.google.inject.AbstractModule;

/**
 * Provides some framework functionality for Guava.
 * <ul>
 * </ul>
 */
public class GuavaModule extends AbstractModule {

    @Override
    protected void configure() {
//     move to jackson lib   Multibinder.newSetBinder(binder(), TransformerPlugin.class)
//                .addBinding().to(ImmutableMapTransformer.class);
    }
}
