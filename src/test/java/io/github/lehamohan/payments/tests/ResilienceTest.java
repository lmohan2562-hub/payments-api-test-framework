package io.github.lehamohan.payments.tests;

import io.github.lehamohan.payments.client.ApiTransportException;
import io.github.lehamohan.payments.client.PaymentsApiClient;
import io.github.lehamohan.payments.data.IdempotencyKeys;
import io.github.lehamohan.payments.data.SyntheticToken;
import io.github.lehamohan.payments.mock.PaymentsMockServer;
import io.github.lehamohan.payments.model.AuthorizationResponse;
import io.github.lehamohan.payments.model.AuthorizationRequest;
import io.github.lehamohan.payments.support.BaseApiTest;
import io.github.lehamohan.payments.support.TestEnvironment;
import io.restassured.response.Response;
import org.testng.annotations.Test;

import java.net.SocketTimeoutException;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static io.github.lehamohan.payments.data.AuthorizationRequestBuilder.anAuthorization;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Retry and timeout behaviour, verified against WireMock's request journal so we assert
 * how many times the client actually hit the wire, not just the final outcome.
 */
public class ResilienceTest extends BaseApiTest {

    /** Well below the simulator's SLOW_GATEWAY delay. */
    private static final int SHORT_READ_TIMEOUT_MS = 500;

    @Test(groups = {"resilience", "regression"}, description = "503 is retried transparently and succeeds on the next attempt")
    public void serviceUnavailableIsRetried() {
        PaymentsMockServer mock = TestEnvironment.requireMockServer();
        String key = IdempotencyKeys.next();

        Response response = api.authorize(anAuthorization().withToken(SyntheticToken.FLAKY_GATEWAY).build(), key);

        assertThat(response.getStatusCode()).isEqualTo(201);
        assertThat(mock.countRequests(postRequestedFor(urlEqualTo(PaymentsApiClient.AUTHORIZATIONS))
                .withHeader(PaymentsApiClient.IDEMPOTENCY_KEY, equalTo(key)))).isEqualTo(2);
    }

    @Test(groups = {"resilience", "regression"}, description = "Without retries, a 503 is surfaced to the caller")
    public void serviceUnavailableSurfacedWhenRetriesDisabled() {
        PaymentsApiClient noRetry = new PaymentsApiClient(TestEnvironment.config().withMaxAttempts(1));

        Response response = noRetry.authorize(anAuthorization().withToken(SyntheticToken.FLAKY_GATEWAY).build(),
                IdempotencyKeys.next());

        assertThat(response.getStatusCode()).isEqualTo(503);
        assertThat(response.getHeader("Retry-After")).isEqualTo("0");
    }

    @Test(groups = {"resilience", "regression"}, description = "Read timeout raises ApiTransportException when retries are disabled")
    public void readTimeoutWithoutRetryFails() {
        PaymentsApiClient impatient = new PaymentsApiClient(TestEnvironment.config()
                .withReadTimeoutMs(SHORT_READ_TIMEOUT_MS)
                .withMaxAttempts(1));

        assertThatThrownBy(() -> impatient.authorize(
                anAuthorization().withToken(SyntheticToken.SLOW_GATEWAY).build(), IdempotencyKeys.next()))
                .isInstanceOf(ApiTransportException.class)
                .hasRootCauseInstanceOf(SocketTimeoutException.class);
    }

    @Test(groups = {"resilience", "regression"},
            description = "Timeout then retry with the same idempotency key replays the result without a duplicate charge")
    public void retryAfterTimeoutIsSafeBecauseOfIdempotency() {
        PaymentsMockServer mock = TestEnvironment.requireMockServer();
        PaymentsApiClient impatient = new PaymentsApiClient(TestEnvironment.config()
                .withReadTimeoutMs(SHORT_READ_TIMEOUT_MS)
                .withMaxAttempts(2));
        AuthorizationRequest request = anAuthorization().withToken(SyntheticToken.SLOW_GATEWAY).build();
        String key = IdempotencyKeys.next();

        Response response = impatient.authorize(request, key);

        assertThat(response.getStatusCode()).isEqualTo(201);
        assertThat(response.getHeader("Idempotent-Replayed")).isEqualTo("true");
        assertThat(mock.countRequests(postRequestedFor(urlEqualTo(PaymentsApiClient.AUTHORIZATIONS))
                .withHeader(PaymentsApiClient.IDEMPOTENCY_KEY, equalTo(key)))).isEqualTo(2);
        String id = response.as(AuthorizationResponse.class).authorizationId();
        assertThat(api.getAuthorization(id).getStatusCode()).isEqualTo(200);
    }
}
