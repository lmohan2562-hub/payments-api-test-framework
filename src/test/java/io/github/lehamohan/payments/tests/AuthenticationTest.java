package io.github.lehamohan.payments.tests;

import io.github.lehamohan.payments.data.IdempotencyKeys;
import io.github.lehamohan.payments.model.ApiError;
import io.github.lehamohan.payments.support.BaseApiTest;
import io.restassured.response.Response;
import org.testng.annotations.Test;

import static io.github.lehamohan.payments.data.AuthorizationRequestBuilder.anAuthorization;
import static org.assertj.core.api.Assertions.assertThat;

public class AuthenticationTest extends BaseApiTest {

    @Test(groups = {"smoke", "regression"}, description = "Request without bearer token is rejected with 401")
    public void missingTokenReturns401() {
        Response response = api.withToken(null).authorize(anAuthorization().build(), IdempotencyKeys.next());

        assertThat(response.getStatusCode()).isEqualTo(401);
        assertThat(response.getHeader("WWW-Authenticate")).startsWith("Bearer");
        assertThat(response.as(ApiError.class).code()).isEqualTo("UNAUTHORIZED");
    }

    @Test(groups = "regression", description = "Request with an invalid bearer token is rejected with 401")
    public void invalidTokenReturns401() {
        Response response = api.withToken("not-a-valid-token").authorize(anAuthorization().build(), IdempotencyKeys.next());

        assertThat(response.getStatusCode()).isEqualTo(401);
        assertThat(response.getHeader("WWW-Authenticate")).contains("invalid_token");
    }

    @Test(groups = "regression", description = "Read endpoints are protected too")
    public void getWithoutTokenReturns401() {
        String id = givenApprovedAuthorization(1_000).authorizationId();

        Response response = api.withToken(null).getAuthorization(id);

        assertThat(response.getStatusCode()).isEqualTo(401);
        assertThat(response.asString()).doesNotContain(id);
    }

    @Test(groups = "regression", description = "Authentication is checked before validation (no info leak on bad payloads)")
    public void authenticationCheckedBeforeValidation() {
        Response response = api.withToken(null).authorizeRawJson("{}", IdempotencyKeys.next());

        assertThat(response.getStatusCode()).isEqualTo(401);
    }
}
