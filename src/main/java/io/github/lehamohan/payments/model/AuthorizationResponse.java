package io.github.lehamohan.payments.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AuthorizationResponse(
        String authorizationId,
        AuthorizationStatus status,
        String merchantId,
        String reference,
        Money amount,
        Money refundableAmount,
        String declineCode,
        String declineReason,
        String createdAt) {

    public boolean isApproved() {
        return status == AuthorizationStatus.APPROVED;
    }
}
