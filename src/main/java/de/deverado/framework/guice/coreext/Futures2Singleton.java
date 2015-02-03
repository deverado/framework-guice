package de.deverado.framework.guice.coreext;/*
 * Copyright Georg Koester 2012-15. All rights reserved.
 */

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import de.deverado.framework.core.Futures2;

import javax.inject.Inject;
import javax.inject.Named;

import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;

@javax.inject.Singleton
public class Futures2Singleton {

    private ConcurrentHashMap<Object, ListenableFuture<?>> transformContext = new ConcurrentHashMap<>();

    @Inject
    @Named("low_processing_load")
    private ListeningExecutorService lowProcessingLoadExec;

    /**
     * Use this for example with a blocking cache or other blocking resources to convert them to async calls - and
     * waste fewer threads by grouping similar calls (for example to the same cache key).
     * @param callSpecificTag e.g. Pair.of(cacheObjectMarker, cacheKey)
     * @param tagSpecificBlockingCall e.g. call() { return cacheObject.get(cacheKey); }
     * @param <V> type
     * @return a future that if canceled will lead to cancelling of all parallel requests.
     */
    public <V> ListenableFuture<V> transformWithGroupingByTag(Object callSpecificTag,
                                                              Callable<V> tagSpecificBlockingCall) {
        return Futures2.transformWithGroupingByTag(lowProcessingLoadExec, transformContext, callSpecificTag,
                tagSpecificBlockingCall);
    }
}
