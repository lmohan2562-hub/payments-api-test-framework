package io.github.lehamohan.payments.tests;

import io.github.lehamohan.payments.data.IdempotencyKeys;
import io.github.lehamohan.payments.data.SyntheticToken;
import io.github.lehamohan.payments.model.ApiError;
import io.github.lehamohan.payments.model.AuthorizationResponse;
import io.github.lehamohan.payments.model.Money;
import io.github.lehamohan.payments.model.RefundRequest;
import io.github.lehamohan.payments.model.RefundResponse;
import io.github.lehamohan.payments.support.BaseApiTest;
import io.restassured.response.Response;
import org.testng.annotations.Test;

import static io.github.lehamohan.payments.data.AuthorizationRequestBuilder.anAuthorization;
import static org.assertj.core.api.Assertions.assertThat;

public class RefundTest extends BaseApiTest {

    @Test(groups = {"smoke", "regression"}, description = "Refund without amount refunds the full authorized amount")
    public void fullRefundWhenAmountOmitted() {
        AuthorizationResponse auth = givenApprovedAuthorization(5_000);

        Response response = api.refund(auth.authorizationId(), new RefundRequest(null, null), IdempotencyKeys.next());

        assertThat(response.getStatusCode()).isEqualTo(201);
        RefundResponse refund = response.as(RefundResponse.class);
        assertThat(refund.refundId()).startsWith("rf_");
        assertThat(refund.status()).isEqualTo("SUCCEEDED");
        assertThat(refund.amount()).isEqualTo(Money.usd(5_000));
        assertThat(refund.remainingRefundable()).isEqualTo(Money.usd(0));
    }

    @Test(groups = "regression", description = "Partial refunds decrement the refundable balance")
    public void partialRefundsDecrementBalance() {
        AuthorizationResponse auth = givenApprovedAuthorization(10_000);

        RefundResponse first = api.refund(auth.authorizationId(), new RefundRequest(Money.usd(2_500), "item returned"),
                IdempotencyKeys.next()).as(RefundResponse.class);
        RefundResponse second = api.refund(auth.authorizationId(), new RefundRequest(Money.usd(3_000), "price adjustment"),
                IdempotencyKeys.next()).as(RefundResponse.class);

        assertThat(first.remainingRefundable()).isEqualTo(Money.usd(7_500));
        assertThat(second.remainingRefundable()).isEqualTo(Money.usd(4_500));
        assertThat(api.getAuthorization(auth.authorizationId()).as(AuthorizationResponse.class).refundableAmount())
                .isEqualTo(Money.usd(4_500));
    }

    @Test(groups = "regression", description = "Refund above remaining balance is rejected with 422")
    public void overRefundReturns422() {
        AuthorizationResponse auth = givenApprovedAuthorization(2_000);

        Response response = api.refund(auth.authorizationId(), new RefundRequest(Money.usd(2_001), null),
                IdempotencyKeys.next());

        assertThat(response.getStatusCode()).isEqualTo(422);
        assertThat(response.as(ApiError.class).code()).isEqualTo("REFUND_EXCEEDS_REMAINING");
    }

    @Test(groups = "regression", description = "Second full refund after balance is exhausted is rejected")
    public void refundAfterFullRefundReturns422() {
        AuthorizationResponse auth = givenApprovedAuthorization(1_200);
        api.refund(auth.authorizationId(), new RefundRequest(null, null), IdempotencyKeys.next());

        Response response = api.refund(auth.authorizationId(), new RefundRequest(null, null), IdempotencyKeys.next());

        assertThat(response.getStatusCode()).isEqualTo(422);
    }

    @Test(groups = "regression", description = "Declined authorizations cannot be refunded (409)")
    public void refundOfDeclinedAuthorizationReturns409() {
        String declinedId = api.authorize(anAuthorization().withToken(SyntheticToken.INSUFFICIENT_FUNDS).build(),
                IdempotencyKeys.next()).jsonPath().getString("authorizationId");

        Response response = api.refund(declinedId, new RefundRequest(null, null), IdempotencyKeys.next());

        assertThat(response.getStatusCode()).isEqualTo(409);
        assertThat(response.as(ApiError.class).code()).isEqualTo("AUTHORIZATION_NOT_REFUNDABLE");
    }

    @Test(groups = "regression", description = "Refund on unknown authorization returns 404")
    public void refundOfUnknownAuthorizationReturns404() {
        Response response = api.refund("auth_unknown0000000000000", new RefundRequest(null, null), IdempotencyKeys.next());

        assertThat(response.getStatusCode()).isEqualTo(404);
    }

    @Test(groups = "regression", description = "Refund currency must match the authorization currency")
    public void refundCurrencyMismatchReturns400() {
        AuthorizationResponse auth = givenApprovedAuthorization(3_000);

        Response response = api.refund(auth.authorizationId(), new RefundRequest(new Money(1_000L, "EUR"), null),
                IdempotencyKeys.next());

        assertThat(response.getStatusCode()).isEqualTo(400);
        assertThat(response.as(ApiError.class).hasFieldError("amount.currency")).isTrue();
    }

    @Test(groups = "regression", description = "Retried refund with same key does not refund twice")
    public void refundReplayDoesNotDoubleRefund() {
        AuthorizationResponse auth = givenApprovedAuthorization(6_000);
        RefundRequest request = new RefundRequest(Money.usd(1_000), "duplicate click");
        String key = IdempotencyKeys.next();

        RefundResponse first = api.refund(auth.authorizationId(), request, key).as(RefundResponse.class);
        Response replay = api.refund(auth.authorizationId(), request, key);

        assertThat(replay.getHeader("Idempotent-Replayed")).isEqualTo("true");
        assertThat(replay.as(RefundResponse.class).refundId()).isEqualTo(first.refundId());
        assertThat(api.getAuthorization(auth.authorizationId()).as(AuthorizationResponse.class).refundableAmount())
                .isEqualTo(Money.usd(5_000));
    }
}
