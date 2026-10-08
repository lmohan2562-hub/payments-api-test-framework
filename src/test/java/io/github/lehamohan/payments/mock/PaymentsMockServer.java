package io.github.lehamohan.payments.mock;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

/**
 * Embedded WireMock server exposing the synthetic payments API on a random local port.
 *
 * <p>Static behaviour (health) is a plain stub; stateful behaviour is delegated to
 * {@link PaymentApiSimulator} through {@link SimulatorTransformer}.
 */
public final class PaymentsMockServer {

    private final PaymentApiSimulator simulator;
    private final WireMockServer server;

    public PaymentsMockServer(String bearerToken, int slowGatewayDelayMs) {
        this.simulator = new PaymentApiSimulator(bearerToken, slowGatewayDelayMs);
        this.server = new WireMockServer(options()
                .bindAddress("127.0.0.1")
                .dynamicPort()
                .containerThreads(32)
                .extensions(new SimulatorTransformer(simulator)));
    }

    public PaymentsMockServer start() {
        server.start();
        server.stubFor(get(urlEqualTo("/v1/health"))
                .willReturn(okJson("{\"status\":\"UP\",\"service\":\"synthetic-payments-api\"}")));
        server.stubFor(any(urlPathMatching("/v1/authorizations(/.*)?"))
                .willReturn(aResponse().withTransformers(SimulatorTransformer.NAME)));
        return this;
    }

    public void stop() {
        server.stop();
    }

    /** Uses the literal loopback address so the client never resolves "localhost" to ::1. */
    public String baseUrl() {
        return "http://127.0.0.1:" + server.port();
    }

    public PaymentApiSimulator simulator() {
        return simulator;
    }

    /** Number of requests the server received that match the pattern (from WireMock's request journal). */
    public int countRequests(RequestPatternBuilder pattern) {
        return server.countRequestsMatching(pattern.build()).getCount();
    }
}
