package io.github.lehamohan.payments.tests;

import io.github.lehamohan.payments.data.IdempotencyKeys;
import io.github.lehamohan.payments.data.SyntheticToken;
import io.github.lehamohan.payments.model.Money;
import io.github.lehamohan.payments.model.RefundRequest;
import io.github.lehamohan.payments.support.BaseApiTest;
import io.restassured.response.Response;
import org.testng.annotations.Test;

import static io.github.lehamohan.payments.data.AuthorizationRequestBuilder.anAuthorization;
import static io.restassured.module.jsv.JsonSchemaValidator.matchesJsonSchemaInClasspath;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Consumer-side contract checks: response bodies must conform to the JSON Schemas in
 * {@code src/test/resources/schemas}. Schemas use {@code additionalProperties: false} so
 * unannounced fields fail fast.
 */
public class SchemaContractTest extends BaseApiTest {

    private static final String AUTH_SCHEMA = "schemas/authorization-response.schema.json";
    private static final String REFUND_SCHEMA = "schemas/refund-response.schema.json";
    private static final String ERROR_SCHEMA = "schemas/error-response.schema.json";

    @Test(groups = {"contract", "smoke", "regression"}, description = "Approved authorization matches schema")
    public void approvedAuthorizationMatchesSchema() {
        Response response = api.authorize(anAuthorization().build(), IdempotencyKeys.next());

        assertThat(response.asString(), matchesJsonSchemaInClasspath(AUTH_SCHEMA));
    }

    @Test(groups = {"contract", "regression"}, description = "Declined authorization matches schema")
    public void declinedAuthorizationMatchesSchema() {
        Response response = api.authorize(anAuthorization().withToken(SyntheticToken.EXPIRED_CARD).build(),
                IdempotencyKeys.next());

        assertThat(response.asString(), matchesJsonSchemaInClasspath(AUTH_SCHEMA));
    }

    @Test(groups = {"contract", "regression"}, description = "Refund response matches schema")
    public void refundMatchesSchema() {
        String id = givenApprovedAuthorization(3_000).authorizationId();

        Response response = api.refund(id, new RefundRequest(Money.usd(1_000), "customer request"), IdempotencyKeys.next());

        assertThat(response.asString(), matchesJsonSchemaInClasspath(REFUND_SCHEMA));
    }

    @Test(groups = {"contract", "regression"}, description = "400 error envelope matches schema")
    public void validationErrorMatchesSchema() {
        Response response = api.authorizeRawJson("{}", IdempotencyKeys.next());

        assertThat(response.asString(), matchesJsonSchemaInClasspath(ERROR_SCHEMA));
    }

    @Test(groups = {"contract", "regression"}, description = "401 error envelope matches schema")
    public void unauthorizedErrorMatchesSchema() {
        Response response = api.withToken(null).getAuthorization("auth_any");

        assertThat(response.asString(), matchesJsonSchemaInClasspath(ERROR_SCHEMA));
    }
}
