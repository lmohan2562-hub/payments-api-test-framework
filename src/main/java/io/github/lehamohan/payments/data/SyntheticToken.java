package io.github.lehamohan.payments.data;

/**
 * Synthetic payment tokens understood by the simulator.
 *
 * <p>These are opaque test tokens (prefix {@code tok_test_}), not card numbers. Each token
 * deterministically drives one behaviour of the synthetic API so tests are repeatable.
 */
public enum SyntheticToken {

    APPROVED("tok_test_visa_approved", null, null),
    APPROVED_ALT_BRAND("tok_test_mc_approved", null, null),
    INSUFFICIENT_FUNDS("tok_test_decline_insufficient_funds", "51", "INSUFFICIENT_FUNDS"),
    DO_NOT_HONOR("tok_test_decline_do_not_honor", "05", "DO_NOT_HONOR"),
    EXPIRED_CARD("tok_test_decline_expired_card", "54", "EXPIRED_CARD"),
    SUSPECTED_FRAUD("tok_test_decline_suspected_fraud", "59", "SUSPECTED_FRAUD"),
    /** Gateway returns 503 on the first attempt for a given idempotency key, then approves. */
    FLAKY_GATEWAY("tok_test_flaky_gateway", null, null),
    /** Gateway responds after a configurable delay (see simulator) to exercise read timeouts. */
    SLOW_GATEWAY("tok_test_slow_gateway", null, null);

    /** Amounts above this (minor units) are declined with code 61 regardless of token. */
    public static final long SINGLE_TRANSACTION_LIMIT = 1_000_000L;
    public static final String LIMIT_DECLINE_CODE = "61";
    public static final String LIMIT_DECLINE_REASON = "EXCEEDS_LIMIT";

    private final String token;
    private final String declineCode;
    private final String declineReason;

    SyntheticToken(String token, String declineCode, String declineReason) {
        this.token = token;
        this.declineCode = declineCode;
        this.declineReason = declineReason;
    }

    public String token() {
        return token;
    }

    public String declineCode() {
        return declineCode;
    }

    public String declineReason() {
        return declineReason;
    }

    public boolean isDecline() {
        return declineCode != null;
    }

    public static SyntheticToken fromToken(String token) {
        for (SyntheticToken t : values()) {
            if (t.token.equals(token)) {
                return t;
            }
        }
        return null;
    }
}
