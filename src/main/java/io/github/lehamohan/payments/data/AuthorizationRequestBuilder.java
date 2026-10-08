package io.github.lehamohan.payments.data;

import io.github.lehamohan.payments.model.AuthorizationRequest;
import io.github.lehamohan.payments.model.Money;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Test data builder with valid, synthetic defaults. Tests override only what they care about,
 * which keeps intent obvious: {@code anAuthorization().withToken(EXPIRED_CARD).build()}.
 */
public final class AuthorizationRequestBuilder {

    private String merchantId = "MRC-TEST-0001";
    private Money amount = Money.usd(ThreadLocalRandom.current().nextLong(100, 50_000));
    private String paymentToken = SyntheticToken.APPROVED.token();
    private String reference = "ORDER-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    private Boolean capture = Boolean.FALSE;

    private AuthorizationRequestBuilder() {
    }

    public static AuthorizationRequestBuilder anAuthorization() {
        return new AuthorizationRequestBuilder();
    }

    public AuthorizationRequestBuilder withMerchantId(String merchantId) {
        this.merchantId = merchantId;
        return this;
    }

    public AuthorizationRequestBuilder withAmount(Money amount) {
        this.amount = amount;
        return this;
    }

    public AuthorizationRequestBuilder withAmountCents(long cents) {
        this.amount = Money.usd(cents);
        return this;
    }

    public AuthorizationRequestBuilder withToken(SyntheticToken token) {
        this.paymentToken = token.token();
        return this;
    }

    public AuthorizationRequestBuilder withRawPaymentToken(String paymentToken) {
        this.paymentToken = paymentToken;
        return this;
    }

    public AuthorizationRequestBuilder withReference(String reference) {
        this.reference = reference;
        return this;
    }

    public AuthorizationRequestBuilder withCapture(Boolean capture) {
        this.capture = capture;
        return this;
    }

    public AuthorizationRequest build() {
        return new AuthorizationRequest(merchantId, amount, paymentToken, reference, capture);
    }
}
