package io.github.lehamohan.payments.mock;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.lehamohan.payments.data.SyntheticToken;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * In-memory behaviour of the synthetic "payment authorization / refund" API.
 *
 * <p>Pure Java (no HTTP), so the contract can be unit-tested directly and plugged into any
 * transport. {@link SimulatorTransformer} adapts it to WireMock.
 *
 * <p>Endpoints:
 * <pre>
 *   POST /v1/authorizations                 -> 201 approved | 402 declined | 400 | 401 | 409 | 503
 *   GET  /v1/authorizations/{id}            -> 200 | 401 | 404
 *   POST /v1/authorizations/{id}/refunds    -> 201 | 400 | 401 | 404 | 409 | 422
 * </pre>
 *
 * <p>All state is thread-safe because TestNG runs test classes in parallel against one instance.
 */
public final class PaymentApiSimulator {

    public static final Set<String> SUPPORTED_CURRENCIES = Set.of("USD", "EUR", "GBP", "CAD");
    public static final String REPLAY_HEADER = "Idempotent-Replayed";

    private static final Pattern MERCHANT_ID = Pattern.compile("^MRC-[A-Z0-9-]{4,32}$");
    private static final Pattern RAW_PAN = Pattern.compile("^[0-9 -]{12,23}$");
    private static final Pattern AUTH_PATH = Pattern.compile("^/v1/authorizations/([A-Za-z0-9_]+)$");
    private static final Pattern REFUND_PATH = Pattern.compile("^/v1/authorizations/([A-Za-z0-9_]+)/refunds$");
    private static final int MAX_REFERENCE_LENGTH = 64;
    private static final int MAX_REASON_LENGTH = 140;

    private final ObjectMapper mapper = new ObjectMapper();
    private final String expectedBearerToken;
    private final int slowGatewayDelayMs;

    private final Map<String, StoredAuthorization> authorizations = new ConcurrentHashMap<>();
    private final Map<String, IdempotentEntry> idempotencyStore = new ConcurrentHashMap<>();
    private final Map<String, Object> idempotencyLocks = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> flakyAttempts = new ConcurrentHashMap<>();

    public PaymentApiSimulator(String expectedBearerToken, int slowGatewayDelayMs) {
        this.expectedBearerToken = expectedBearerToken;
        this.slowGatewayDelayMs = slowGatewayDelayMs;
    }

    // ------------------------------------------------------------------ routing

    public SimulatedResponse handle(SimulatedRequest request) {
        SimulatedResponse unauthorized = checkAuthentication(request);
        if (unauthorized != null) {
            return unauthorized;
        }
        String path = request.path();
        String method = request.method();

        if ("/v1/authorizations".equals(path)) {
            return "POST".equals(method)
                    ? idempotent(request, () -> createAuthorization(request))
                    : methodNotAllowed();
        }
        Matcher refund = REFUND_PATH.matcher(path);
        if (refund.matches()) {
            return "POST".equals(method)
                    ? idempotent(request, () -> createRefund(refund.group(1), request.body()))
                    : methodNotAllowed();
        }
        Matcher auth = AUTH_PATH.matcher(path);
        if (auth.matches()) {
            return "GET".equals(method) ? getAuthorization(auth.group(1)) : methodNotAllowed();
        }
        return error(404, "NOT_FOUND", "No route for " + method + " " + path, List.of());
    }

    public int authorizationCount() {
        return authorizations.size();
    }

    public void reset() {
        authorizations.clear();
        idempotencyStore.clear();
        idempotencyLocks.clear();
        flakyAttempts.clear();
    }

    private SimulatedResponse checkAuthentication(SimulatedRequest request) {
        String header = request.header("Authorization");
        if (header == null || header.isBlank()) {
            return error(401, "UNAUTHORIZED", "Missing bearer token", List.of())
                    .withHeader("WWW-Authenticate", "Bearer realm=\"payments\"");
        }
        if (!header.equals("Bearer " + expectedBearerToken)) {
            return error(401, "UNAUTHORIZED", "Invalid bearer token", List.of())
                    .withHeader("WWW-Authenticate", "Bearer realm=\"payments\", error=\"invalid_token\"");
        }
        return null;
    }

    // ------------------------------------------------------------------ idempotency

    private SimulatedResponse idempotent(SimulatedRequest request, java.util.function.Supplier<SimulatedResponse> action) {
        String key = request.header("Idempotency-Key");
        if (key == null || key.isBlank()) {
            return error(400, "VALIDATION_ERROR", "Request validation failed",
                    List.of(fieldError("Idempotency-Key", "header is required for POST requests")));
        }
        String scopedKey = request.path() + "|" + key;
        String bodyHash = sha256(request.body() == null ? "" : request.body());

        Object lock = idempotencyLocks.computeIfAbsent(scopedKey, k -> new Object());
        synchronized (lock) {
            IdempotentEntry existing = idempotencyStore.get(scopedKey);
            if (existing != null) {
                if (!existing.bodyHash().equals(bodyHash)) {
                    return error(409, "IDEMPOTENCY_KEY_REUSED",
                            "Idempotency-Key was already used with a different request body", List.of());
                }
                // Replays are served immediately, even if the original was slow.
                return existing.response().withDelay(0).withHeader(REPLAY_HEADER, "true");
            }

            if (isFlakyFirstAttempt(request, scopedKey)) {
                return error(503, "SERVICE_UNAVAILABLE", "Upstream processor temporarily unavailable", List.of())
                        .withHeader("Retry-After", "0");
            }

            SimulatedResponse response = action.get();
            // Only cache final outcomes; 5xx must remain retryable.
            if (response.status() < 500) {
                idempotencyStore.put(scopedKey, new IdempotentEntry(bodyHash, response));
            }
            return response;
        }
    }

    private boolean isFlakyFirstAttempt(SimulatedRequest request, String scopedKey) {
        String body = request.body();
        if (body == null || !body.contains(SyntheticToken.FLAKY_GATEWAY.token())) {
            return false;
        }
        return flakyAttempts.computeIfAbsent(scopedKey, k -> new AtomicInteger()).incrementAndGet() == 1;
    }

    // ------------------------------------------------------------------ authorizations

    private SimulatedResponse createAuthorization(SimulatedRequest request) {
        JsonNode body;
        try {
            body = parseObject(request.body());
        } catch (JsonProcessingException | IllegalArgumentException e) {
            return error(400, "MALFORMED_JSON", "Request body is not a valid JSON object", List.of());
        }

        List<ObjectNode> errors = new ArrayList<>();
        String merchantId = text(body, "merchantId");
        if (merchantId == null) {
            errors.add(fieldError("merchantId", "is required"));
        } else if (!MERCHANT_ID.matcher(merchantId).matches()) {
            errors.add(fieldError("merchantId", "must match MRC-XXXX format"));
        }
        validateMoney(body.get("amount"), "amount", true, errors);

        String token = text(body, "paymentToken");
        if (token == null) {
            errors.add(fieldError("paymentToken", "is required"));
        } else if (RAW_PAN.matcher(token).matches()) {
            errors.add(fieldError("paymentToken", "raw card numbers are not accepted; tokenize first"));
        } else if (!token.startsWith("tok_")) {
            errors.add(fieldError("paymentToken", "must be a payment token (tok_...)"));
        }

        String reference = text(body, "reference");
        if (reference != null && reference.length() > MAX_REFERENCE_LENGTH) {
            errors.add(fieldError("reference", "must be at most " + MAX_REFERENCE_LENGTH + " characters"));
        }
        if (body.has("capture") && !body.get("capture").isBoolean() && !body.get("capture").isNull()) {
            errors.add(fieldError("capture", "must be a boolean"));
        }
        if (!errors.isEmpty()) {
            return error(400, "VALIDATION_ERROR", "Request validation failed", errors);
        }

        long amount = body.get("amount").get("value").asLong();
        String currency = body.get("amount").get("currency").asText();
        SyntheticToken known = SyntheticToken.fromToken(token);

        String declineCode = null;
        String declineReason = null;
        if (amount > SyntheticToken.SINGLE_TRANSACTION_LIMIT) {
            declineCode = SyntheticToken.LIMIT_DECLINE_CODE;
            declineReason = SyntheticToken.LIMIT_DECLINE_REASON;
        } else if (known != null && known.isDecline()) {
            declineCode = known.declineCode();
            declineReason = known.declineReason();
        }

        StoredAuthorization auth = new StoredAuthorization(
                "auth_" + compactUuid(), declineCode == null ? "APPROVED" : "DECLINED",
                merchantId, reference, amount, currency, declineCode == null ? amount : 0L,
                declineCode, declineReason, Instant.now().truncatedTo(ChronoUnit.SECONDS).toString());
        authorizations.put(auth.id, auth);

        SimulatedResponse response = SimulatedResponse.json(declineCode == null ? 201 : 402, write(auth.toJson(mapper)))
                .withHeader("Location", "/v1/authorizations/" + auth.id);
        if (known == SyntheticToken.SLOW_GATEWAY) {
            response = response.withDelay(slowGatewayDelayMs);
        }
        return response;
    }

    private SimulatedResponse getAuthorization(String id) {
        StoredAuthorization auth = authorizations.get(id);
        if (auth == null) {
            return error(404, "AUTHORIZATION_NOT_FOUND", "No authorization with id " + id, List.of());
        }
        synchronized (auth) {
            return SimulatedResponse.json(200, write(auth.toJson(mapper)));
        }
    }

    // ------------------------------------------------------------------ refunds

    private SimulatedResponse createRefund(String authorizationId, String rawBody) {
        StoredAuthorization auth = authorizations.get(authorizationId);
        if (auth == null) {
            return error(404, "AUTHORIZATION_NOT_FOUND", "No authorization with id " + authorizationId, List.of());
        }
        JsonNode body;
        try {
            body = parseObject(rawBody == null || rawBody.isBlank() ? "{}" : rawBody);
        } catch (JsonProcessingException | IllegalArgumentException e) {
            return error(400, "MALFORMED_JSON", "Request body is not a valid JSON object", List.of());
        }

        List<ObjectNode> errors = new ArrayList<>();
        JsonNode amountNode = body.get("amount");
        validateMoney(amountNode, "amount", false, errors);
        String reason = text(body, "reason");
        if (reason != null && reason.length() > MAX_REASON_LENGTH) {
            errors.add(fieldError("reason", "must be at most " + MAX_REASON_LENGTH + " characters"));
        }
        if (errors.isEmpty() && amountNode != null && !amountNode.isNull()
                && !auth.currency.equals(amountNode.get("currency").asText())) {
            errors.add(fieldError("amount.currency", "must match authorization currency " + auth.currency));
        }
        if (!errors.isEmpty()) {
            return error(400, "VALIDATION_ERROR", "Request validation failed", errors);
        }

        synchronized (auth) {
            if (!"APPROVED".equals(auth.status)) {
                return error(409, "AUTHORIZATION_NOT_REFUNDABLE",
                        "Only approved authorizations can be refunded (status=" + auth.status + ")", List.of());
            }
            long requested = amountNode == null || amountNode.isNull()
                    ? auth.refundable
                    : amountNode.get("value").asLong();
            if (auth.refundable == 0 || requested > auth.refundable) {
                return error(422, "REFUND_EXCEEDS_REMAINING",
                        "Requested " + requested + " exceeds refundable " + auth.refundable, List.of());
            }
            auth.refundable -= requested;

            ObjectNode refund = mapper.createObjectNode();
            refund.put("refundId", "rf_" + compactUuid());
            refund.put("authorizationId", auth.id);
            refund.put("status", "SUCCEEDED");
            refund.set("amount", money(requested, auth.currency));
            refund.set("remainingRefundable", money(auth.refundable, auth.currency));
            if (reason != null) {
                refund.put("reason", reason);
            }
            refund.put("createdAt", Instant.now().truncatedTo(ChronoUnit.SECONDS).toString());
            return SimulatedResponse.json(201, write(refund));
        }
    }

    // ------------------------------------------------------------------ helpers

    private void validateMoney(JsonNode node, String field, boolean required, List<ObjectNode> errors) {
        if (node == null || node.isNull()) {
            if (required) {
                errors.add(fieldError(field, "is required"));
            }
            return;
        }
        if (!node.isObject()) {
            errors.add(fieldError(field, "must be an object"));
            return;
        }
        JsonNode value = node.get("value");
        if (value == null || value.isNull()) {
            errors.add(fieldError(field + ".value", "is required"));
        } else if (!value.isIntegralNumber()) {
            errors.add(fieldError(field + ".value", "must be an integer amount in minor units"));
        } else if (value.asLong() <= 0) {
            errors.add(fieldError(field + ".value", "must be greater than 0"));
        }
        JsonNode currency = node.get("currency");
        if (currency == null || currency.isNull()) {
            errors.add(fieldError(field + ".currency", "is required"));
        } else if (!SUPPORTED_CURRENCIES.contains(currency.asText())) {
            errors.add(fieldError(field + ".currency", "is not supported"));
        }
    }

    private JsonNode parseObject(String raw) throws JsonProcessingException {
        JsonNode node = mapper.readTree(raw == null ? "" : raw);
        if (node == null || !node.isObject()) {
            throw new IllegalArgumentException("not a JSON object");
        }
        return node;
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }

    private ObjectNode money(long value, String currency) {
        ObjectNode m = mapper.createObjectNode();
        m.put("value", value);
        m.put("currency", currency);
        return m;
    }

    private ObjectNode fieldError(String field, String issue) {
        ObjectNode e = mapper.createObjectNode();
        e.put("field", field);
        e.put("issue", issue);
        return e;
    }

    private SimulatedResponse error(int status, String code, String message, List<ObjectNode> details) {
        ObjectNode root = mapper.createObjectNode();
        root.put("code", code);
        root.put("message", message);
        ArrayNode arr = root.putArray("details");
        details.forEach(arr::add);
        root.put("traceId", "trc_" + compactUuid());
        return SimulatedResponse.json(status, write(root));
    }

    private SimulatedResponse methodNotAllowed() {
        return error(405, "METHOD_NOT_ALLOWED", "Method not allowed", List.of());
    }

    private String write(JsonNode node) {
        try {
            return mapper.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String compactUuid() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 24);
    }

    private static String sha256(String s) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private record IdempotentEntry(String bodyHash, SimulatedResponse response) {
    }

    /** Mutable only under its own monitor (refund balance). */
    private static final class StoredAuthorization {
        final String id;
        final String status;
        final String merchantId;
        final String reference;
        final long amount;
        final String currency;
        long refundable;
        final String declineCode;
        final String declineReason;
        final String createdAt;

        StoredAuthorization(String id, String status, String merchantId, String reference, long amount,
                            String currency, long refundable, String declineCode, String declineReason,
                            String createdAt) {
            this.id = id;
            this.status = status;
            this.merchantId = merchantId;
            this.reference = reference;
            this.amount = amount;
            this.currency = currency;
            this.refundable = refundable;
            this.declineCode = declineCode;
            this.declineReason = declineReason;
            this.createdAt = createdAt;
        }

        ObjectNode toJson(ObjectMapper mapper) {
            ObjectNode n = mapper.createObjectNode();
            n.put("authorizationId", id);
            n.put("status", status);
            n.put("merchantId", merchantId);
            if (reference != null) {
                n.put("reference", reference);
            }
            ObjectNode amt = n.putObject("amount");
            amt.put("value", amount);
            amt.put("currency", currency);
            ObjectNode ref = n.putObject("refundableAmount");
            ref.put("value", refundable);
            ref.put("currency", currency);
            if (declineCode != null) {
                n.put("declineCode", declineCode);
                n.put("declineReason", declineReason);
            }
            n.put("createdAt", createdAt);
            return n;
        }
    }
}
