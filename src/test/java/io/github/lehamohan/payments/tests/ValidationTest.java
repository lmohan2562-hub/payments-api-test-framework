package io.github.lehamohan.payments.tests;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.lehamohan.payments.data.IdempotencyKeys;
import io.github.lehamohan.payments.model.ApiError;
import io.github.lehamohan.payments.model.AuthorizationRequest;
import io.github.lehamohan.payments.model.Money;
import io.github.lehamohan.payments.support.BaseApiTest;
import io.restassured.response.Response;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static io.github.lehamohan.payments.data.AuthorizationRequestBuilder.anAuthorization;
import static org.assertj.core.api.Assertions.assertThat;

/** Negative tests: every invalid input yields 400 with a field-level error pointing at the culprit. */
public class ValidationTest extends BaseApiTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @DataProvider(name = "invalidAuthorizations")
    public Object[][] invalidAuthorizations() {
        return new Object[][]{
                {"missing merchantId", json(anAuthorization().withMerchantId(null).build()), "merchantId"},
                {"malformed merchantId", json(anAuthorization().withMerchantId("merchant-1").build()), "merchantId"},
                {"missing amount", json(anAuthorization().withAmount(null).build()), "amount"},
                {"zero amount", json(anAuthorization().withAmountCents(0).build()), "amount.value"},
                {"negative amount", json(anAuthorization().withAmountCents(-500).build()), "amount.value"},
                {"missing currency", json(anAuthorization().withAmount(new Money(100L, null)).build()), "amount.currency"},
                {"unsupported currency", json(anAuthorization().withAmount(new Money(100L, "XYZ")).build()), "amount.currency"},
                {"missing payment token", json(anAuthorization().withRawPaymentToken(null).build()), "paymentToken"},
                {"raw card number instead of token", json(anAuthorization().withRawPaymentToken("9999 8888 7777 6666").build()), "paymentToken"},
                {"non-token string", json(anAuthorization().withRawPaymentToken("card-abc").build()), "paymentToken"},
                {"reference too long", json(anAuthorization().withReference("R".repeat(65)).build()), "reference"},
                {"decimal amount", "{\"merchantId\":\"MRC-TEST-0001\",\"amount\":{\"value\":19.99,\"currency\":\"USD\"},"
                        + "\"paymentToken\":\"tok_test_visa_approved\"}", "amount.value"},
                {"non-boolean capture", "{\"merchantId\":\"MRC-TEST-0001\",\"amount\":{\"value\":1999,\"currency\":\"USD\"},"
                        + "\"paymentToken\":\"tok_test_visa_approved\",\"capture\":\"yes\"}", "capture"},
        };
    }

    @Test(groups = "regression", dataProvider = "invalidAuthorizations",
            description = "Invalid authorization payload returns 400 with a field error")
    public void invalidPayloadReturns400(String scenario, String payload, String expectedField) {
        Response response = api.authorizeRawJson(payload, IdempotencyKeys.next());

        assertThat(response.getStatusCode()).as(scenario).isEqualTo(400);
        ApiError error = response.as(ApiError.class);
        assertThat(error.code()).as(scenario).isEqualTo("VALIDATION_ERROR");
        assertThat(error.hasFieldError(expectedField))
                .as("%s -> expected field error on '%s' but got %s", scenario, expectedField, error.details())
                .isTrue();
    }

    @Test(groups = "regression", description = "Unparseable JSON returns 400 MALFORMED_JSON")
    public void malformedJsonReturns400() {
        Response response = api.authorizeRawJson("{\"merchantId\": ", IdempotencyKeys.next());

        assertThat(response.getStatusCode()).isEqualTo(400);
        assertThat(response.as(ApiError.class).code()).isEqualTo("MALFORMED_JSON");
    }

    @Test(groups = "regression", description = "All violations are reported together, not one at a time")
    public void emptyObjectReportsAllRequiredFields() {
        Response response = api.authorizeRawJson("{}", IdempotencyKeys.next());

        assertThat(response.getStatusCode()).isEqualTo(400);
        ApiError error = response.as(ApiError.class);
        assertThat(error.details()).extracting(ApiError.FieldError::field)
                .contains("merchantId", "amount", "paymentToken");
    }

    @Test(groups = "regression", description = "Error responses never echo the submitted card-like value")
    public void validationErrorDoesNotEchoSensitiveInput() {
        String rawPan = "9999888877776666";
        Response response = api.authorizeRawJson(json(anAuthorization().withRawPaymentToken(rawPan).build()),
                IdempotencyKeys.next());

        assertThat(response.getStatusCode()).isEqualTo(400);
        assertThat(response.asString()).doesNotContain(rawPan);
    }

    private static String json(AuthorizationRequest request) {
        try {
            return MAPPER.writeValueAsString(request);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
