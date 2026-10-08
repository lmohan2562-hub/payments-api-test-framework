package io.github.lehamohan.payments.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Payload for {@code POST /v1/authorizations/{id}/refunds}. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RefundRequest(Money amount, String reason) {
}
