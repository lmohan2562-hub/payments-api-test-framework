package io.github.lehamohan.payments.tests;

import io.github.lehamohan.payments.data.IdempotencyKeys;
import io.github.lehamohan.payments.data.SyntheticToken;
import io.github.lehamohan.payments.model.AuthorizationRequest;
import io.github.lehamohan.payments.model.AuthorizationResponse;
import io.github.lehamohan.payments.model.AuthorizationStatus;
import io.github.lehamohan.payments.model.Money;
import io.github.lehamohan.payments.support.BaseApiTest;
import io.restassured.response.Response;
import org.testng.annotations.Test;

import static io.github.lehamohan.payments.data.AuthorizationRequestBuilder.anAuthorization;
import static org.assertj.core.api.Assertions.assertThat;

public class AuthorizationTest extends BaseApiTest {

    @Test(groups = {"smoke", "regression"}, description = "Valid token and amount are approved with 201")
    public void approvedAuthorizationReturns201() {
        AuthorizationRequest request = anAuthorization().withAmountCents(2_599).withReference("ORDER-HAPPY-1").build();

        Response response = api.authorize(request, IdempotencyKeys.next());

        assertThat(response.getStatusCode()).isEqualTo(201);
        assertThat(response.getHeader("Location")).startsWith("/v1/authorizations/auth_");
        AuthorizationResponse body = response.as(AuthorizationResponse.class);
        assertThat(body.status()).isEqualTo(AuthorizationStatus.APPROVED);
        assertThat(body.authorizationId()).startsWith("auth_");
        assertThat(body.amount()).isEqualTo(Money.usd(2_599));
        assertThat(body.refundableAmount()).isEqualTo(Money.usd(2_599));
        assertThat(body.reference()).isEqualTo("ORDER-HAPPY-1");
        assertThat(body.declineCode()).isNull();
    }

    @Test(groups = {"smoke", "regression"}, description = "Created authorization can be read back by id")
    public void getAuthorizationReturnsPersistedState() {
        AuthorizationResponse created = givenApprovedAuthorization(4_200);

        Response response = api.getAuthorization(created.authorizationId());

        assertThat(response.getStatusCode()).isEqualTo(200);
        AuthorizationResponse fetched = response.as(AuthorizationResponse.class);
        assertThat(fetched.authorizationId()).isEqualTo(created.authorizationId());
        assertThat(fetched.status()).isEqualTo(AuthorizationStatus.APPROVED);
        assertThat(fetched.amount()).isEqualTo(created.amount());
    }

    @Test(groups = "regression", description = "Non-default currency and card brand are approved")
    public void alternateBrandAndCurrencyApproved() {
        AuthorizationRequest request = anAuthorization()
                .withToken(SyntheticToken.APPROVED_ALT_BRAND)
                .withAmount(new Money(10_000L, "EUR"))
                .build();

        Response response = api.authorize(request, IdempotencyKeys.next());

        assertThat(response.getStatusCode()).isEqualTo(201);
        assertThat(response.as(AuthorizationResponse.class).amount().currency()).isEqualTo("EUR");
    }

    @Test(groups = "regression", description = "Amount above the single-transaction limit is declined with code 61")
    public void amountAboveLimitIsDeclined() {
        AuthorizationRequest request = anAuthorization()
                .withAmountCents(SyntheticToken.SINGLE_TRANSACTION_LIMIT + 1)
                .build();

        Response response = api.authorize(request, IdempotencyKeys.next());

        assertThat(response.getStatusCode()).isEqualTo(402);
        AuthorizationResponse body = response.as(AuthorizationResponse.class);
        assertThat(body.declineCode()).isEqualTo(SyntheticToken.LIMIT_DECLINE_CODE);
        assertThat(body.declineReason()).isEqualTo(SyntheticToken.LIMIT_DECLINE_REASON);
    }

    @Test(groups = "regression", description = "Boundary: amount exactly at the limit is approved")
    public void amountAtLimitIsApproved() {
        AuthorizationRequest request = anAuthorization()
                .withAmountCents(SyntheticToken.SINGLE_TRANSACTION_LIMIT)
                .build();

        assertThat(api.authorize(request, IdempotencyKeys.next()).getStatusCode()).isEqualTo(201);
    }

    @Test(groups = "regression", description = "Unknown authorization id returns 404")
    public void unknownAuthorizationReturns404() {
        Response response = api.getAuthorization("auth_doesnotexist000000000");

        assertThat(response.getStatusCode()).isEqualTo(404);
        assertThat(response.jsonPath().getString("code")).isEqualTo("AUTHORIZATION_NOT_FOUND");
    }
}
