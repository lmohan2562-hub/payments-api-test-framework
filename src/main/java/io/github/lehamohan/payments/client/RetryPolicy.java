package io.github.lehamohan.payments.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.function.ToIntFunction;

/**
 * Bounded retry with linear backoff for transient failures.
 *
 * <p>Retries on gateway-style statuses (502/503/504) and on transport {@link IOException}s
 * (including read timeouts). Callers must send an {@code Idempotency-Key} so a retried
 * {@code POST} can never double-charge; the client enforces that for all mutating calls.
 */
public final class RetryPolicy {

    private static final Logger LOG = LoggerFactory.getLogger(RetryPolicy.class);

    public static final Set<Integer> RETRYABLE_STATUSES = Set.of(502, 503, 504);

    /** Abstraction over Thread.sleep so unit tests run instantly. */
    @FunctionalInterface
    public interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }

    private final int maxAttempts;
    private final long backoffMs;
    private final Sleeper sleeper;

    public RetryPolicy(int maxAttempts, long backoffMs) {
        this(maxAttempts, backoffMs, Thread::sleep);
    }

    public RetryPolicy(int maxAttempts, long backoffMs, Sleeper sleeper) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be >= 1");
        }
        this.maxAttempts = maxAttempts;
        this.backoffMs = backoffMs;
        this.sleeper = sleeper;
    }

    public int maxAttempts() {
        return maxAttempts;
    }

    public <T> T execute(String operation, Callable<T> call, ToIntFunction<T> statusOf) {
        Throwable lastFailure = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                T result = call.call();
                int status = statusOf.applyAsInt(result);
                if (!RETRYABLE_STATUSES.contains(status) || attempt == maxAttempts) {
                    return result;
                }
                LOG.warn("event=retry operation={} attempt={} maxAttempts={} reason=status_{}",
                        operation, attempt, maxAttempts, status);
            } catch (Exception e) {
                if (!isTransient(e)) {
                    throw e instanceof RuntimeException re ? re : new IllegalStateException(e);
                }
                lastFailure = e;
                if (attempt == maxAttempts) {
                    break;
                }
                LOG.warn("event=retry operation={} attempt={} maxAttempts={} reason={}",
                        operation, attempt, maxAttempts, e.getClass().getSimpleName());
            }
            pause(attempt);
        }
        throw new ApiTransportException(operation, maxAttempts, lastFailure);
    }

    private void pause(int attempt) {
        try {
            sleeper.sleep(backoffMs * attempt);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted during retry backoff", ie);
        }
    }

    static boolean isTransient(Throwable t) {
        for (Throwable cur = t; cur != null; cur = cur.getCause()) {
            if (cur instanceof IOException) {
                return true;
            }
        }
        return false;
    }
}
