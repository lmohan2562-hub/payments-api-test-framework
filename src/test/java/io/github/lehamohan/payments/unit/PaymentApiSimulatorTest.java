package io.github.lehamohan.payments.unit;

import io.github.lehamohan.payments.mock.PaymentApiSimulator;
import io.github.lehamohan.payments.mock.SimulatedRequest;
import io.github.lehamohan.payments.mock.SimulatedResponse;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests the simulator itself (no HTTP). Guards against the test double drifting from the
 * documented contract, which would make the API tests meaningless.
 */
public class PaymentApiSimulatorTest {

    private static final String TOKEN = "unit-test-token";
    private static final String VALID_BODY =
            "{\"merchantId\":\"MRC-TEST-0001\",\"amount\":{\"value\":1000,\"currency\":\"USD\"},"
                    + "\"paymentToken\":\"tok_test_visa_approved\"}";

    private PaymentApiSimulator simulator;

    @BeforeMethod(alwaysRun = true)
    public void setUp() {
        simulator = new PaymentApiSimulator(TOKEN, 0);
    }

    @Test(groups = {"unit", "regression"})
    public void approvesValidAuthorization() {
        SimulatedResponse response = simulator.handle(post("/v1/authorizations", VALID_BODY, "k1"));

        assertThat(response.status()).isEqualTo(201);
        assertThat(response.body()).contains("\"status\":\"APPROVED\"");
        assertThat(simulator.authorizationCount()).isEqualTo(1);
    }

    @Test(groups = {"unit", "regression"})
    public void replayDoesNotCreateSecondAuthorization() {
        simulator.handle(post("/v1/authorizations", VALID_BODY, "k1"));
        SimulatedResponse replay = simulator.handle(post("/v1/authorizations", VALID_BODY, "k1"));

        assertThat(replay.headers()).containsEntry(PaymentApiSimulator.REPLAY_HEADER, "true");
        assertThat(simulator.authorizationCount()).isEqualTo(1);
    }

    @Test(groups = {"unit", "regression"})
    public void rejectsMissingBearerToken() {
        Map<String, String> headers = new HashMap<>();
        headers.put("idempotency-key", "k1");

        SimulatedResponse response = simulator.handle(
                new SimulatedRequest("POST", "/v1/authorizations", headers, VALID_BODY));

        assertThat(response.status()).isEqualTo(401);
    }

    @Test(groups = {"unit", "regression"})
    public void flakyTokenFailsOnceThenSucceeds() {
        String body = VALID_BODY.replace("tok_test_visa_approved", "tok_test_flaky_gateway");

        assertThat(simulator.handle(post("/v1/authorizations", body, "k-flaky")).status()).isEqualTo(503);
        assertThat(simulator.handle(post("/v1/authorizations", body, "k-flaky")).status()).isEqualTo(201);
    }

    @Test(groups = {"unit", "regression"})
    public void unknownRouteReturns404AndWrongMethodReturns405() {
        assertThat(simulator.handle(get("/v1/unknown")).status()).isEqualTo(404);
        assertThat(simulator.handle(new SimulatedRequest("DELETE", "/v1/authorizations", authHeaders(null), null))
                .status()).isEqualTo(405);
    }

    @Test(groups = {"unit", "regression"})
    public void refundReducesRefundableBalance() {
        SimulatedResponse auth = simulator.handle(post("/v1/authorizations", VALID_BODY, "k1"));
        String id = auth.body().replaceAll(".*\"authorizationId\":\"([^\"]+)\".*", "$1");

        SimulatedResponse refund = simulator.handle(post("/v1/authorizations/" + id + "/refunds",
                "{\"amount\":{\"value\":400,\"currency\":\"USD\"}}", "r1"));

        assertThat(refund.status()).isEqualTo(201);
        assertThat(simulator.handle(get("/v1/authorizations/" + id)).body())
                .contains("\"refundableAmount\":{\"value\":600,\"currency\":\"USD\"}");
    }

    private static SimulatedRequest post(String path, String body, String idempotencyKey) {
        return new SimulatedRequest("POST", path, authHeaders(idempotencyKey), body);
    }

    private static SimulatedRequest get(String path) {
        return new SimulatedRequest("GET", path, authHeaders(null), null);
    }

    private static Map<String, String> authHeaders(String idempotencyKey) {
        Map<String, String> headers = new HashMap<>();
        headers.put("authorization", "Bearer " + TOKEN);
        if (idempotencyKey != null) {
            headers.put("idempotency-key", idempotencyKey);
        }
        return headers;
    }
}
