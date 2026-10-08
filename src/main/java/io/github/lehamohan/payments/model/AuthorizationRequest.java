package io.github.lehamohan.payments.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Payload for {@code POST /v1/authorizations}.
 *
 * <p>Uses a payment token, never a raw card number: the API under test rejects PAN-like values.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuthorizationRequest(
        String merchantId,
        Money amount,
        String paymentToken,
        String reference,
        Boolean capture) {
}
