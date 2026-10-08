package io.github.lehamohan.payments.support;

import io.github.lehamohan.payments.client.PaymentsApiClient;
import io.github.lehamohan.payments.config.ConfigLoader;
import io.github.lehamohan.payments.config.FrameworkConfig;
import io.github.lehamohan.payments.mock.PaymentsMockServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.ISuite;
import org.testng.ISuiteListener;
import org.testng.SkipException;

/**
 * Suite-scoped lifecycle, registered as a TestNG listener in the suite XML.
 *
 * <p>Resolves configuration once and, when {@code payments.base.url=embedded} (the default),
 * starts the WireMock-backed simulator so the whole suite runs offline.
 */
public final class TestEnvironment implements ISuiteListener {

    private static final Logger LOG = LoggerFactory.getLogger(TestEnvironment.class);

    /** Simulator delay for the SLOW_GATEWAY token; must exceed the read timeout used in resilience tests. */
    public static final int SLOW_GATEWAY_DELAY_MS = 1500;

    private static volatile FrameworkConfig config;
    private static volatile PaymentsMockServer mockServer;

    @Override
    public synchronized void onStart(ISuite suite) {
        FrameworkConfig loaded = new ConfigLoader().load();
        if (loaded.useEmbeddedSimulator()) {
            mockServer = new PaymentsMockServer(loaded.apiToken(), SLOW_GATEWAY_DELAY_MS).start();
            loaded = loaded.withBaseUrl(mockServer.baseUrl());
            LOG.info("event=env_ready mode=embedded baseUrl={}", mockServer.baseUrl());
        } else {
            LOG.info("event=env_ready mode=remote baseUrl={}", loaded.baseUrl());
        }
        config = loaded;
        LOG.info("event=config_loaded config={}", config);
    }

    @Override
    public synchronized void onFinish(ISuite suite) {
        if (mockServer != null) {
            mockServer.stop();
            mockServer = null;
        }
    }

    public static FrameworkConfig config() {
        if (config == null) {
            throw new IllegalStateException("TestEnvironment listener is not registered in the suite XML");
        }
        return config;
    }

    public static PaymentsApiClient client() {
        return new PaymentsApiClient(config());
    }

    /** Tests that inspect the simulator's request journal call this; they are skipped against a remote API. */
    public static PaymentsMockServer requireMockServer() {
        if (mockServer == null) {
            throw new SkipException("Requires the embedded simulator (payments.base.url=embedded)");
        }
        return mockServer;
    }
}
