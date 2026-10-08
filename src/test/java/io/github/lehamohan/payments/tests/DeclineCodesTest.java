package io.github.lehamohan.payments.tests;

import io.github.lehamohan.payments.data.IdempotencyKeys;
import io.github.lehamohan.payments.data.SyntheticToken;
import io.github.lehamohan.payments.model.AuthorizationResponse;
import io.github.lehamohan.payments.model.AuthorizationStatus;
import io.github.lehamohan.payments.support.BaseApiTest;
import io.restassured.response.Response;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.Arrays;

import static io.github.lehamohan.payments.data.AuthorizationRequestBuilder.anAuthorization;
import static org.assertj.core.api.Assertions.assertThat;

/** Data-driven: every synthetic decline token maps to the expected issuer response code. */
public class DeclineCodesTest extends BaseApiTest {

    @DataProvider(name = "declineTokens", parallel = true)
    public Object[][] declineTokens() {
        return Arrays.stream(SyntheticToken.values())
                .filter(SyntheticToken::isDecline)
                .map(t -> new Object[]{t, t.declineCode(), t.declineReason()})
                .toArray(Object[][]::new);
    }

    @Test(groups = "regression", dataProvider = "declineTokens",
            description = "Decline token returns 402 with mapped decline code and no refundable balance")
    public void declineTokenReturnsExpectedCode(SyntheticToken token, String expectedCode, String expectedReason) {
        Response response = api.authorize(anAuthorization().withToken(token).build(), IdempotencyKeys.next());

        assertThat(response.getStatusCode()).isEqualTo(402);
        AuthorizationResponse body = response.as(AuthorizationResponse.class);
        assertThat(body.status()).isEqualTo(AuthorizationStatus.DECLINED);
        assertThat(body.declineCode()).isEqualTo(expectedCode);
        assertThat(body.declineReason()).isEqualTo(expectedReason);
        assertThat(body.refundableAmount().value()).isZero();
    }
}
