package de.deverado.framework.guice.coreext.problemreporting;

import com.google.common.base.Preconditions;
import de.deverado.framework.core.IntervalCounter;
import de.deverado.framework.core.problemreporting.ProblemReporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class ProblemReportingLinkedBlockingQueue<E> extends
        LinkedBlockingQueue<E> {

    private static final long serialVersionUID = -1871384467836251839L;

    private static final Logger log = LoggerFactory
            .getLogger(ProblemReportingLinkedBlockingQueue.class);

    private final ProblemReporter pr;

    private final IntervalCounter intervalCounter = new IntervalCounter(1,
            TimeUnit.MINUTES);

    private final int warningLimitSize;

    private final String name;

    public ProblemReportingLinkedBlockingQueue(String name, ProblemReporter pr,
            int capacity, int warningLimitSize) {
        super(capacity < 0 ? Integer.MAX_VALUE : capacity);
        this.name = name;
        this.pr = pr;
        Preconditions.checkArgument(warningLimitSize <= capacity
                || capacity < 0, "warningLimitSize needs to be < capacity");
        this.warningLimitSize = warningLimitSize;
    }

    @Override
    public void put(@Nonnull E e) throws InterruptedException {
        checkSize();
        super.put(e);
    }

    @Override
    public boolean offer(@Nonnull E e) {
        checkSize();
        return super.offer(e);
    }

    @Override
    public boolean offer(@Nonnull E e, long timeout, java.util.concurrent.TimeUnit unit)
            throws InterruptedException {
        checkSize();
        return super.offer(e, timeout, unit);
    }

    private void checkSize() {
        int size = size();
        if (size >= warningLimitSize) {
            warningSizeReached(size);
        }
    }

    protected void warningSizeReached(int size) {
        if (intervalCounter.incrementAndGet() == 1) {
            pr.logError(log, null, "" + name + " reached warning limit of "
                    + warningLimitSize + ": " + size);
        }
    }
}
