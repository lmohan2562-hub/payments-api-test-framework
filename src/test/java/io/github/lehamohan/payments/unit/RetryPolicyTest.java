package io.github.lehamohan.payments.unit;

import io.github.lehamohan.payments.client.ApiTransportException;
import io.github.lehamohan.payments.client.RetryPolicy;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class RetryPolicyTest {

    private List<Long> sleeps;
    private RetryPolicy policy;

    @BeforeMethod(alwaysRun = true)
    public void setUp() {
        sleeps = new ArrayList<>();
        policy = new RetryPolicy(3, 100, sleeps::add);
    }

    @Test(groups = {"unit", "regression"})
    public void retriesRetryableStatusThenReturnsSuccess() {
        AtomicInteger calls = new AtomicInteger();

        int status = policy.execute("op", () -> calls.incrementAndGet() < 3 ? 503 : 200, s -> s);

        assertThat(status).isEqualTo(200);
        assertThat(calls).hasValue(3);
        assertThat(sleeps).containsExactly(100L, 200L);
    }

    @Test(groups = {"unit", "regression"})
    public void doesNotRetryClientErrors() {
        AtomicInteger calls = new AtomicInteger();

        int status = policy.execute("op", () -> {
            calls.incrementAndGet();
            return 400;
        }, s -> s);

        assertThat(status).isEqualTo(400);
        assertThat(calls).hasValue(1);
    }

    @Test(groups = {"unit", "regression"})
    public void returnsLastRetryableResponseWhenAttemptsExhausted() {
        int status = policy.execute("op", () -> 503, s -> s);

        assertThat(status).isEqualTo(503);
        assertThat(sleeps).hasSize(2);
    }

    @Test(groups = {"unit", "regression"})
    public void transportFailureIsWrappedAfterExhaustingAttempts() {
        AtomicInteger calls = new AtomicInteger();

        assertThatThrownBy(() -> policy.execute("op", () -> {
            calls.incrementAndGet();
            throw new SocketTimeoutException("Read timed out");
        }, (Integer s) -> s))
                .isInstanceOf(ApiTransportException.class)
                .hasCauseInstanceOf(SocketTimeoutException.class);
        assertThat(calls).hasValue(3);
    }

    @Test(groups = {"unit", "regression"})
    public void nonTransientExceptionsAreNotRetried() {
        AtomicInteger calls = new AtomicInteger();

        assertThatThrownBy(() -> policy.execute("op", () -> {
            calls.incrementAndGet();
            throw new IllegalArgumentException("bug");
        }, (Integer s) -> s)).isInstanceOf(IllegalArgumentException.class);
        assertThat(calls).hasValue(1);
    }
}
