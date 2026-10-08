package io.github.lehamohan.payments.tests;

import io.github.lehamohan.payments.support.BaseApiTest;
import io.restassured.response.Response;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class HealthCheckTest extends BaseApiTest {

    @Test(groups = {"smoke", "regression"}, description = "Service health endpoint reports UP")
    public void healthEndpointReportsUp() {
        Response response = api.health();

        assertThat(response.getStatusCode()).isEqualTo(200);
        assertThat(response.jsonPath().getString("status")).isEqualTo("UP");
    }
}
