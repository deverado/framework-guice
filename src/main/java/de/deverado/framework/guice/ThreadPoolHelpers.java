package de.deverado.framework.guice;

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.ListenableFutureTask;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.inject.Binder;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Provider;
import com.google.inject.Scopes;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.name.Names;
import com.google.inject.util.Providers;
import de.deverado.framework.concurrent.ShutdownListener;
import de.deverado.framework.core.IntervalCounter;
import de.deverado.framework.core.problemreporting.ProblemReporter;
import de.deverado.framework.guice.coreext.problemreporting.ProblemReportingLinkedBlockingQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import java.lang.reflect.Constructor;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * <p>
 * This class helps implement a thread pool setup with one (default) thread pool for work that is not waiting for
 * other work to finish and one that is for work that does mostly wait and does only require little processing.
 * High processing load task can efficiently be delegated to the default pool, which has as many threads as there
 * are processors in the system - limiting concurrency.
 * </p>
 * <p>
 * Everything that requires waiting on e.g. a future's result needs to be delegated to the low processing load pool.
 * </p>
 * <p>
 * The design is based on the important issue of avoiding deadlocks. When if jobs are interdependent and a limited
 * number of threads wait for jobs to finish which are in their executor's queue, that is an example for deadlock.
 * </p>
 * <p>
 * So if there is a pool for efficient handling of high-processing load tasks that pool is not useful for interdependent
 * task processing. While it is possible to avoid these issues in fully future-listening environments, it is quite
 * normal that tasks are interdependent: Especially if you are reliant on interfaces that use blocking getters. But
 * also if tasks create new tasks: If all an executor's worker threads try to queue a task on their own executor and
 * that executor has a full queue then the executor is already deadlocked.
 * </p>
 * <p>
 * This system avoids by providing potentially unlimited resources to its pools: The default pool has a queue with
 * unlimited space and the low processing pool can create unlimited threads. So system resources are the limit. To
 * monitor these the implementations issue warnings when thread count or queue size thresholds are reached.
 * </p>
 * <p>
 *     If you want to avoid overflowing system resources you need to throttle inflow: Measure workloads on the system
 *     and slow uptake of new tasks if limits are reached - for example by counting ongoing parallel requests. This way
 *     clients of your system can also switch to a different system on timeout and you could also filter and drop (some)
 *     incoming requests depending on load. In a future version the pools are to issue load warnings to listeners so
 *     that this can be done dynamically.
 * </p>
 */
public class ThreadPoolHelpers {

    private static final Logger log = LoggerFactory
            .getLogger(ThreadPoolHelpers.class);
    private static final int DEFAULT_WORKER_COUNT_WARNING_INTERVAL = 30000;

    /**
     * Allows injection of {@link ThreadPoolExecutor}, {@link BlockingQueue} and
     * {@link ListeningExecutorService} annotated with given name. This allows
     * controlling the number of threads in the pool etc.
     * 
     * {@link #bindThreadPoolShutdownListener(com.google.inject.Binder, java.util.concurrent.ExecutorService, String)}
     * for the service is executed, too.
     *
     * Non-core workers are kept alive for 20 seconds
     *
     * @param coreExecutors count of workers that are always kept alive.
     * @param maxQueueSize
     *            -1: unlimited. Careful with those, can deadlock if thread add
     *            to the queue they are servicing: 0 synchronous, >0: limit.
     * @param workerCountWarnLimit if this is > then on thread creation checks are performed to check the amount of
     *                             worker threads and if this limit is crossed a warning is logged once every 30 seconds
     * @throws java.lang.IllegalArgumentException if coreExecutors != maxExecutors and tasks are to be
     * queued (maxQueueSize != 0): ThreadPoolExecutor doesn't support this - it will never grow
     * the size of the pool. See its javadoc, it states that pool will only grow above core threads if
     * the task cannot be queued. Problem is that pool doesn't have a count of busy threads.
     */
    public static void bindControllableExecutor(final String name,
            Binder binder, final int coreExecutors, final int maxExecutors,
            final int maxQueueSize,
            final int queueSizeWarnLimit,
            final int workerCountWarnLimit) {

        if (coreExecutors != maxExecutors) {
            Preconditions.checkArgument(maxQueueSize == 0,
                    "ThreadPoolExecutor doesn't support queued tasks when pool should grow with load. " +
                            "So either fix pool size or use 0 maxQueueSize.");
        }

        binder.bind(new TypeLiteral<BlockingQueue<Runnable>>() {
        }).annotatedWith(Names.named(name))
                .toProvider(new Provider<BlockingQueue<Runnable>>() {

                    @Inject
                    Provider<ProblemReporter> prProv;

                    @Override
                    public BlockingQueue<Runnable> get() {

                        BlockingQueue<Runnable> queue;
                        if (maxQueueSize == 0) {
                            queue = new SynchronousQueue<Runnable>();
                        } else if (maxQueueSize < 0 || maxQueueSize > 1000) {
                            if (prProv.get() != null && queueSizeWarnLimit > 0) {
                                queue = new ProblemReportingLinkedBlockingQueue<Runnable>(
                                        name + "Queue", prProv.get(),
                                        maxQueueSize, queueSizeWarnLimit);
                            } else {
                                queue = new LinkedBlockingQueue<Runnable>();
                            }
                        } else // between 0 and 1000
                        {
                            if (prProv.get() == null || queueSizeWarnLimit <= 0) {
                                queue = new ArrayBlockingQueue<Runnable>(
                                        maxQueueSize);
                            } else {
                                queue = new ProblemReportingLinkedBlockingQueue<Runnable>(
                                        name + "Queue", prProv.get(),
                                        maxQueueSize, queueSizeWarnLimit);
                            }
                        }

                        return queue;
                    }
                }).in(Scopes.SINGLETON);

        binder.bind(ThreadPoolExecutor.class).annotatedWith(Names.named(name))
                .toProvider(new Provider<ThreadPoolExecutor>() {
                    @Inject
                    Provider<Injector> injProv;

                    @Inject
                    Provider<ProblemReporter> prProv;

                    @Override
                    public ThreadPoolExecutor get() {
                        final AtomicReference<ThreadPoolExecutor> threadPoolRef = new
                                AtomicReference<ThreadPoolExecutor>();
                        ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(
                                coreExecutors,
                                maxExecutors,
                                20000L,
                                TimeUnit.MILLISECONDS,
                                injProv.get()
                                        .getInstance(
                                                Key.get(new TypeLiteral<BlockingQueue<Runnable>>() {
                                                }, Names.named(name))),
                                new WorkerCountCheckingNamedThreadFactory(name, threadPoolRef,
                                        workerCountWarnLimit, prProv, DEFAULT_WORKER_COUNT_WARNING_INTERVAL),
                                // wait up to thirty seconds to squeeze in a new
                                // job
                                // this should avoid filling up the memory, but
                                // will
                                // make processing halt/abort if the executor is
                                // really blocked
                                new TimedBlockingPolicy(30000));
                        threadPoolRef.set(threadPoolExecutor);
                        return threadPoolExecutor;
                    }
                }).in(Scopes.SINGLETON);

        binder.bind(ListeningExecutorService.class)
                .annotatedWith(Names.named(name))
                .toProvider(new Provider<ListeningExecutorService>() {

                    @Inject
                    Provider<Injector> injProv;

                    @Override
                    public ListeningExecutorService get() {
                        return MoreExecutors.listeningDecorator(injProv.get()
                                .getInstance(
                                        Key.get(ThreadPoolExecutor.class,
                                                Names.named(name))));
                    }
                }).in(Scopes.SINGLETON);

        bindThreadPoolShutdownListener(binder, new Provider<ExecutorService>() {
            @Inject
            Provider<Injector> injProv;

            @Override
            public ExecutorService get() {
                return injProv.get().getInstance(
                        Key.get(ListeningExecutorService.class,
                                Names.named(name)));
            }
        }, name);
    }

    public static void bindThreadPoolShutdownListener(Binder binder,
            final ExecutorService threadPool, String label) {

        bindThreadPoolShutdownListener(binder, Providers.of(threadPool), label);
    }

    /**
     * Binds an unnamed ScheduledExecutorService (listenable and normal) with shutdown listener.
     */
    public static void bindDefaultScheduledThreadPool(Binder binder) {
        String name = "defaultScheduled";
        ScheduledExecutorService defaultScheduledPool = Executors.newScheduledThreadPool(1,
                new DefaultNamedThreadFactory(name));
        binder.bind(ScheduledExecutorService.class)
                .toInstance(defaultScheduledPool);
        binder.bind(ListeningScheduledExecutorService.class).
                toInstance(MoreExecutors.listeningDecorator(defaultScheduledPool));

        bindThreadPoolShutdownListener(binder, Providers.<ExecutorService>of(defaultScheduledPool), name);
    }

    /**
     *
     * @param threadPoolProv
     *            injection for this is handled internally just before being
     *            used during shutdown.
     */
    public static void bindThreadPoolShutdownListener(Binder binder,
            final Provider<ExecutorService> threadPoolProv, final String name) {

        Multibinder<ShutdownListener> sls = Multibinder.newSetBinder(binder,
                ShutdownListener.class);
        sls.addBinding().toInstance(new ShutdownListener() {

            @Inject
            Provider<Injector> injProv;

            private volatile ExecutorService threadPool;

            @Override
            public ListenableFutureTask<?> shutdown() {

                try {
                    injProv.get().injectMembers(threadPoolProv);
                    threadPool = threadPoolProv.get();
                } catch (Exception e) {
                    log.error("Cannot get threadPool to shut down: {}", name, e);
                }

                final ListenableFutureTask<?> retval = ListenableFutureTask
                        .create(new Callable<Boolean>() {
                            @Override
                            public Boolean call() throws Exception {

                                threadPool.shutdown();
                                try {
                                    threadPool.awaitTermination(170,
                                            TimeUnit.MILLISECONDS);
                                } catch (InterruptedException e) {
                                    log.warn("Could not wait 170ms for " + name
                                            + " thread pool termination: {}", e);
                                }
                                List<Runnable> leftovers = threadPool
                                        .shutdownNow();
                                if (leftovers != null && leftovers.size() > 0) {
                                    log.warn(
                                            "Could not shutdown {}, {} Executor threads: {}",
                                            name, threadPool, leftovers);
                                    return false;
                                }
                                return true;
                            }
                        });
                new Thread(retval, "Shutdown for " + threadPool + " name "
                        + name).start();
                return retval;
            }

            @Override
            public String toString() {
                return MoreObjects.toStringHelper("ShutdownListener")
                        .add("name", name).add("pool", threadPool).toString();
            }
        });
    }

    /**
     * Creates a pool named "default" with fixed size of how many processors are
     * in this machine and an array task queue with unlimited size.
     * <p>
     *     This default thread pool doesn't warn.
     * </p>
     */
    public static ListeningExecutorService createDefaultThreadPool(
            String poolName) {
        int executors = Math.max(1,
                Runtime.getRuntime().availableProcessors());
        log.info("Starting default thread pool with {} workers", executors);
        return MoreExecutors.listeningDecorator(new ThreadPoolExecutor(
                executors, executors, 0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<Runnable>(),
                new DefaultNamedThreadFactory(poolName)));
    }

    /**
     * Creates a pool with as many worker threads as there are processors and an unlimited linked queue.
     * <p>
     *     I am unhappy that it is too difficult to use an queue implementation that doesn't require allocation
     *     of a new object for every entry (because default ArrayBlocking queue is a limited-size implementation). On
     *     the other hand: Futures and callbacks all require allocations - so usually queued tasks already do
     *     allocation. Alternatives here are: Creating an array/block of tasks and submitting them in one or many groups,
     *     using {@link com.google.common.util.concurrent.MoreExecutors#directExecutor()} where jobs are simple, and
     *     creating your own small array-backed queue and submitting #processor-count queue-emptying jobs to the
     *     default executor.
     * </p>
     * <p>
     *     Reminder: If queue has limited size there is always the risk that the queue fills and workers block when
     *     they want to enqueue a job on their own queue - adding to own queue is quite a normal case and its pure guess
     *     work to try to set an upper limit. Also: 10.000 entries require 8Mb for the array already - and 10.000 jobs
     *     are also not unheard of.
     * </p>
     *
     */
    public static void bindDefaultThreadPool(String name, Binder binder, int queueSizeWarnThreshold) {
        int executors = Math.max(1,
                Runtime.getRuntime().availableProcessors());
        bindControllableExecutor(name, binder, executors, executors, -1, queueSizeWarnThreshold, -1);
    }

    /**
     * Binds a low processing load pool with no thread limit and warning logging when pool size is over processors-1*500
     * threads. The queue is synchronous, threads quit after 20 seconds of inactivity.
     *
     */
    public static void bindDefaultLowProcessingLoadThreadPool(String name,
            Binder binder) {
        int workerWarningLimit = Math.max(1,
                Runtime.getRuntime().availableProcessors() - 1);
        workerWarningLimit = workerWarningLimit * 500;

        bindLowProcessingLoadThreadPool(name, binder, workerWarningLimit);
    }

    /**
     * Binds a low processing load pool with no worker count limit and warning logging when pool size is over a given
     * limit of worker threads. The queue is synchronous, threads quit after 20 seconds of inactivity.
     *
     */
    public static void bindLowProcessingLoadThreadPool(String name,
                                                              Binder binder, int workerWarningLimit) {

        log.info("Binding low processing load thread pool "
                + "with unlimited workers (warnings logged when more than {} workers)", workerWarningLimit);
        bindControllableExecutor(name, binder, 1, Integer.MAX_VALUE, 0,
                -1, workerWarningLimit);
    }

    /**
     * @param pr
     *            which receives logs if too many threads are created.
     */
    public static ListeningExecutorService createLowProcessingLoadThreadPool(
            String poolName, ProblemReporter pr) {
        int workerWarnLimit = Math.max(1,
                Runtime.getRuntime().availableProcessors() - 1);
        workerWarnLimit = workerWarnLimit * 500;
        log.info("Starting low processing load thread pool "
                + "with warning limit of {} workers", workerWarnLimit);

        AtomicReference<ThreadPoolExecutor> executorRef = new AtomicReference<>();
        ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(0,
                Integer.MAX_VALUE, 60L, TimeUnit.SECONDS,
                // this is a bit overdone since there are so
                // many threads but just to avoid stupid deadlocks in
                // extreme situations. Mem should suffice for thousands of
                // entries usually.
                new SynchronousQueue<Runnable>(),
                new WorkerCountCheckingNamedThreadFactory(poolName, executorRef, workerWarnLimit, Providers.of(pr),
                        DEFAULT_WORKER_COUNT_WARNING_INTERVAL));
        executorRef.set(threadPoolExecutor);
        return MoreExecutors.listeningDecorator(threadPoolExecutor);
    }

    public static ListeningScheduledExecutorService createSingleThreadScheduledThreadPool(
            String poolName) {

        log.info("Starting single thread scheduled thread pool ");
        return MoreExecutors
                .listeningDecorator(Executors
                        .newSingleThreadScheduledExecutor(new DefaultNamedThreadFactory(
                                poolName)));
    }

    /**
     * Useful to have a better control over the flow of tasks in a system.
     * 
     * Minimum timeout of a
     * {@link TimedSubmitExecutor#execute(Runnable, long, TimeUnit)} attempt is
     * 1 second.
     *
     * @param workQueue
     *            timed submission only sensible if queue has bound capacity as
     *            for example {@link SynchronousQueue} or
     *            {@link ArrayBlockingQueue}.
     *
     */
    public static TimedSubmitExecutor createTimedSubmitExecutor(
            int corePoolSize, int maximumPoolSize, long keepAliveTime,
            TimeUnit unit, BlockingQueue<Runnable> workQueue,
            ThreadFactory threadFactory) {

        return new TimedSubmitExecutorImpl(corePoolSize, maximumPoolSize,
                keepAliveTime, unit, workQueue, threadFactory);
    }

    /**
     * By example of {@link java.util.concurrent.Executors}, changed thread
     * number to long and added name.
     */
    public static class DefaultNamedThreadFactory implements ThreadFactory {
        final ThreadGroup group;
        final AtomicLong threadNumber = new AtomicLong(1);
        final String namePrefix;
        // private final Class<? extends Thread> markerClass;
        private final Constructor<? extends Thread> markerClassConstructor;

        public DefaultNamedThreadFactory(String poolName) {
            this(poolName, null);
        }

        /**
         *
         * @param markerClass
         *            new classes are a subclass of the given class
         */
        public DefaultNamedThreadFactory(String poolName,
                @Nullable Class<? extends Thread> markerClass) {
            SecurityManager s = System.getSecurityManager();
            group = (s != null) ? s.getThreadGroup() : Thread.currentThread()
                    .getThreadGroup();
            namePrefix = "" + poolName + "-thread-";
            // this.markerClass = markerClass;
            if (markerClass != null) {
                try {
                    this.markerClassConstructor = markerClass.getConstructor(
                            ThreadGroup.class, Runnable.class, String.class,
                            long.class);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            } else {
                markerClassConstructor = null;
            }
        }

        @Override
        public Thread newThread(@Nonnull Runnable r) {
            String name = namePrefix + threadNumber.getAndIncrement();
            Thread t;
            if (markerClassConstructor != null) {
                try {
                    t = markerClassConstructor
                            .newInstance(group, r, name, 0/* ign */);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            } else {
                t = new Thread(group, r, name, 0/* ign */);
            }
            if (t.isDaemon())
                t.setDaemon(false);
            if (t.getPriority() != Thread.NORM_PRIORITY)
                t.setPriority(Thread.NORM_PRIORITY);
            return t;
        }

        @Override
        public String toString() {
            return "DefaultNamedThreadFactory{" +
                    "namePrefix='" + namePrefix + '\'' +
                    ", threadNumber=" + threadNumber +
                    ", group=" + group +
                    '}';
        }
    }

    public static class TimedBlockingPolicy implements RejectedExecutionHandler {

        private final long waitTime;

        public TimedBlockingPolicy(long waitTime) {
            this.waitTime = waitTime;
        }

        @Override
        public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
            try {
                boolean successful = executor.getQueue().offer(r, waitTime,
                        TimeUnit.MILLISECONDS);
                if (!successful) {
                    throw new RejectedExecutionException(
                            "Could not execute even after wait of " + waitTime
                                    + " ms");
                }
            } catch (InterruptedException e) {
                throw new RejectedExecutionException(e);
            }
        }
    }

    public static RejectedExecutionHandler timedSubmitPolicy(
            long timeoutToAlwaysBlock) {
        return new TimedBlockingPolicy(timeoutToAlwaysBlock);
    }

    protected static class TimedSubmitExecutorImpl extends ThreadPoolExecutor
            implements TimedSubmitExecutor {

        public TimedSubmitExecutorImpl(int corePoolSize, int maximumPoolSize,
                long keepAliveTime, TimeUnit unit,
                BlockingQueue<Runnable> workQueue, ThreadFactory threadFactory) {
            super(corePoolSize, maximumPoolSize, keepAliveTime, unit,
                    workQueue, threadFactory, timedSubmitPolicy(1000));

            Preconditions
                    .checkArgument(
                            (corePoolSize >= maximumPoolSize)
                                    || unit.toMillis(keepAliveTime) > 1000,
                            "keepAliveTime should be > 1000 to exceed "
                                    + "min wait of blocking submit, otherwise useful threads "
                                    + "might die before submitting a timed task");
        }

        public boolean execute(Runnable task, long time, TimeUnit unit)
                throws InterruptedException {
            long start = System.currentTimeMillis();

            try {
                execute(task);
                return true;
            } catch (RejectedExecutionException ree) {
                long timeRest = unit.toMillis(time)
                        - System.currentTimeMillis() - start;
                return getQueue().offer(task, timeRest, TimeUnit.MILLISECONDS);
            }
        }
    }

    public interface TimedSubmitExecutor extends Executor {
        boolean execute(Runnable task, long time, TimeUnit unit)
                throws InterruptedException;
    }

    public static class WorkerCountCheckingNamedThreadFactory extends DefaultNamedThreadFactory {

        private final String name;
        private final AtomicReference<ThreadPoolExecutor> threadPoolRef;
        private final int workerCountWarnLimit;
        private final Provider<ProblemReporter> pr;
        private final IntervalCounter intervalCounter;

        public WorkerCountCheckingNamedThreadFactory(String name, AtomicReference<ThreadPoolExecutor> threadPoolRef,
                                                     int workerCountWarnLimit,
                                                     Provider<ProblemReporter> pr, long warningIntervalMs) {
            super(name);
            this.name = name;
            this.threadPoolRef = threadPoolRef;
            this.workerCountWarnLimit = workerCountWarnLimit;
            this.pr = pr;
            this.intervalCounter = new IntervalCounter(warningIntervalMs, TimeUnit.MILLISECONDS);
        }

        @Override
        public Thread newThread(@Nonnull Runnable r) {
            ThreadPoolExecutor pool = threadPoolRef.get();
            if (pool != null && workerCountWarnLimit > 0) {
                issueWarningIfNecessary(pool);
            }
            return super.newThread(r);
        }

        protected void issueWarningIfNecessary(ThreadPoolExecutor pool) {
            int poolSize = pool.getPoolSize();
            if (poolSize >= workerCountWarnLimit) {
                if (intervalCounter.incrementAndGet() == 1) {
                    warn(poolSize);
                }
            }
        }

        protected void warn(int poolSize) {
            pr.get().warn(log, "Thread pool '{}' is over limit of {}: poolSize={}!", name, workerCountWarnLimit,
                    poolSize);
        }
    }
}
