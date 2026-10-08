package io.github.lehamohan.payments.config;

import java.util.Objects;

/**
 * Immutable, typed view of the framework configuration.
 *
 * <p>Values are resolved by {@link ConfigLoader}; tests never read raw properties directly.
 *
 * @param baseUrl            base URL of the payments API, or {@code embedded} to start the local WireMock simulator
 * @param apiToken           bearer token sent on every request (synthetic in this repo; inject real values via env vars)
 * @param connectTimeoutMs   TCP connect timeout
 * @param readTimeoutMs      socket read timeout
 * @param maxAttempts        total attempts for retryable failures (1 = no retry)
 * @param retryBackoffMs     base backoff between attempts (linear: attempt * backoff)
 * @param logBodies          whether request/response bodies are logged (always masked)
 */
public record FrameworkConfig(
        String baseUrl,
        String apiToken,
        int connectTimeoutMs,
        int readTimeoutMs,
        int maxAttempts,
        long retryBackoffMs,
        boolean logBodies) {

    public static final String EMBEDDED = "embedded";

    public FrameworkConfig {
        Objects.requireNonNull(baseUrl, "baseUrl");
        if (connectTimeoutMs <= 0 || readTimeoutMs <= 0) {
            throw new IllegalArgumentException("timeouts must be positive");
        }
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be >= 1");
        }
        if (retryBackoffMs < 0) {
            throw new IllegalArgumentException("retryBackoffMs must be >= 0");
        }
    }

    public boolean useEmbeddedSimulator() {
        return EMBEDDED.equalsIgnoreCase(baseUrl);
    }

    /** Returns a copy pointing at a concrete base URL (used once the embedded simulator has a port). */
    public FrameworkConfig withBaseUrl(String newBaseUrl) {
        return new FrameworkConfig(newBaseUrl, apiToken, connectTimeoutMs, readTimeoutMs,
                maxAttempts, retryBackoffMs, logBodies);
    }

    public FrameworkConfig withReadTimeoutMs(int newReadTimeoutMs) {
        return new FrameworkConfig(baseUrl, apiToken, connectTimeoutMs, newReadTimeoutMs,
                maxAttempts, retryBackoffMs, logBodies);
    }

    public FrameworkConfig withMaxAttempts(int newMaxAttempts) {
        return new FrameworkConfig(baseUrl, apiToken, connectTimeoutMs, readTimeoutMs,
                newMaxAttempts, retryBackoffMs, logBodies);
    }

    /** Never print the token, even in debug output. */
    @Override
    public String toString() {
        return "FrameworkConfig[baseUrl=" + baseUrl
                + ", apiToken=" + (apiToken == null || apiToken.isBlank() ? "<unset>" : "****")
                + ", connectTimeoutMs=" + connectTimeoutMs
                + ", readTimeoutMs=" + readTimeoutMs
                + ", maxAttempts=" + maxAttempts
                + ", retryBackoffMs=" + retryBackoffMs
                + ", logBodies=" + logBodies + "]";
    }
}
