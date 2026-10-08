package io.github.lehamohan.payments.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RefundResponse(
        String refundId,
        String authorizationId,
        String status,
        Money amount,
        Money remainingRefundable,
        String reason,
        String createdAt) {
}
