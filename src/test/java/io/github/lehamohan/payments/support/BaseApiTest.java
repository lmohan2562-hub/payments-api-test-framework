package io.github.lehamohan.payments.support;

import io.github.lehamohan.payments.client.PaymentsApiClient;
import io.github.lehamohan.payments.data.IdempotencyKeys;
import io.github.lehamohan.payments.model.AuthorizationRequest;
import io.github.lehamohan.payments.model.AuthorizationResponse;
import io.restassured.response.Response;
import org.testng.annotations.BeforeClass;

import static io.github.lehamohan.payments.data.AuthorizationRequestBuilder.anAuthorization;
import static org.assertj.core.api.Assertions.assertThat;

/** Common setup and helpers for API tests. Stateless per class, so classes can run in parallel. */
public abstract class BaseApiTest {

    protected PaymentsApiClient api;

    @BeforeClass(alwaysRun = true)
    public void initClient() {
        api = TestEnvironment.client();
    }

    /** Creates an approved authorization for tests that need one as a precondition. */
    protected AuthorizationResponse givenApprovedAuthorization(long amountCents) {
        AuthorizationRequest request = anAuthorization().withAmountCents(amountCents).build();
        Response response = api.authorize(request, IdempotencyKeys.next());
        assertThat(response.getStatusCode()).as("precondition: authorization approved").isEqualTo(201);
        return response.as(AuthorizationResponse.class);
    }
}
