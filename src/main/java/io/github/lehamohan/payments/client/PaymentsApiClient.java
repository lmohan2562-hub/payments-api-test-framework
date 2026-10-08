package io.github.lehamohan.payments.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.lehamohan.payments.config.FrameworkConfig;
import io.github.lehamohan.payments.logging.MaskingLoggingFilter;
import io.github.lehamohan.payments.model.AuthorizationRequest;
import io.github.lehamohan.payments.model.RefundRequest;
import io.restassured.RestAssured;
import io.restassured.config.HttpClientConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

import java.util.Objects;
import java.util.UUID;

/**
 * Thin, typed client for the payments API. Owns transport concerns only (base URL, auth,
 * timeouts, retries, logging); assertions live in tests.
 *
 * <p>Instances are immutable and thread-safe, so a single client can be shared by parallel tests.
 */
public final class PaymentsApiClient {

    public static final String AUTHORIZATIONS = "/v1/authorizations";
    public static final String IDEMPOTENCY_KEY = "Idempotency-Key";
    public static final String CORRELATION_ID = "X-Correlation-Id";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final FrameworkConfig config;
    private final String bearerToken;
    private final RetryPolicy retryPolicy;
    private final RestAssuredConfig restAssuredConfig;

    public PaymentsApiClient(FrameworkConfig config) {
        this(config, config.apiToken());
    }

    private PaymentsApiClient(FrameworkConfig config, String bearerToken) {
        this.config = Objects.requireNonNull(config);
        this.bearerToken = bearerToken;
        this.retryPolicy = new RetryPolicy(config.maxAttempts(), config.retryBackoffMs());
        this.restAssuredConfig = RestAssuredConfig.config().httpClient(
                HttpClientConfig.httpClientConfig()
                        .setParam("http.connection.timeout", config.connectTimeoutMs())
                        .setParam("http.socket.timeout", config.readTimeoutMs()));
    }

    /** Same client with a different bearer token; {@code null} sends no Authorization header. */
    public PaymentsApiClient withToken(String token) {
        return new PaymentsApiClient(config, token);
    }

    public FrameworkConfig config() {
        return config;
    }

    public Response authorize(AuthorizationRequest request, String idempotencyKey) {
        return authorizeRawJson(toJson(request), idempotencyKey);
    }

    /** Sends an arbitrary JSON string; used by negative tests to submit malformed payloads. */
    public Response authorizeRawJson(String json, String idempotencyKey) {
        return retryPolicy.execute("POST " + AUTHORIZATIONS,
                () -> withIdempotencyKey(spec(), idempotencyKey).body(json).post(AUTHORIZATIONS),
                Response::getStatusCode);
    }

    public Response getAuthorization(String authorizationId) {
        String path = AUTHORIZATIONS + "/" + authorizationId;
        return retryPolicy.execute("GET " + path, () -> spec().get(path), Response::getStatusCode);
    }

    public Response refund(String authorizationId, RefundRequest request, String idempotencyKey) {
        String path = AUTHORIZATIONS + "/" + authorizationId + "/refunds";
        String json = toJson(request);
        return retryPolicy.execute("POST " + path,
                () -> withIdempotencyKey(spec(), idempotencyKey).body(json).post(path),
                Response::getStatusCode);
    }

    public Response health() {
        return spec().get("/v1/health");
    }

    private RequestSpecification spec() {
        RequestSpecification spec = RestAssured.given()
                .config(restAssuredConfig)
                .baseUri(config.baseUrl())
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .header(CORRELATION_ID, UUID.randomUUID().toString())
                .filter(new MaskingLoggingFilter(config.logBodies()));
        if (bearerToken != null) {
            spec.header("Authorization", "Bearer " + bearerToken);
        }
        return spec;
    }

    private static RequestSpecification withIdempotencyKey(RequestSpecification spec, String key) {
        return key == null ? spec : spec.header(IDEMPOTENCY_KEY, key);
    }

    private static String toJson(Object body) {
        try {
            return MAPPER.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Unable to serialise request body", e);
        }
    }
}
