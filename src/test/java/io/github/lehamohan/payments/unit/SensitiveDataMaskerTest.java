package io.github.lehamohan.payments.unit;

import io.github.lehamohan.payments.logging.SensitiveDataMasker;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SensitiveDataMaskerTest {

    @Test(groups = {"unit", "smoke", "regression"})
    public void masksPaymentTokenKeepingLastFour() {
        String masked = SensitiveDataMasker.maskBody("{\"paymentToken\":\"tok_test_visa_approved\",\"merchantId\":\"MRC-TEST-0001\"}");

        assertThat(masked)
                .contains("\"paymentToken\":\"****oved\"")
                .contains("\"merchantId\":\"MRC-TEST-0001\"")
                .doesNotContain("tok_test_visa_approved");
    }

    @Test(groups = {"unit", "regression"})
    public void masksPanLikeDigitRunsAnywhereInText() {
        String masked = SensitiveDataMasker.maskBody("note=customer typed 9999 8888 7777 6666 in a free-text field");

        assertThat(masked).doesNotContain("9999 8888 7777").contains("****6666");
    }

    @Test(groups = {"unit", "regression"})
    public void leavesShortNumbersAndAmountsUntouched() {
        String body = "{\"amount\":{\"value\":1999,\"currency\":\"USD\"},\"declineCode\":\"51\"}";

        assertThat(SensitiveDataMasker.maskBody(body)).isEqualTo(body);
    }

    @Test(groups = {"unit", "regression"})
    public void masksBearerTokenHeaderCaseInsensitively() {
        assertThat(SensitiveDataMasker.maskHeader("authorization", "Bearer abc.def.ghi")).isEqualTo("Bearer ****");
        assertThat(SensitiveDataMasker.maskHeader("X-API-KEY", "k-123")).isEqualTo("****");
        assertThat(SensitiveDataMasker.maskHeader("Idempotency-Key", "idem-1")).isEqualTo("idem-1");
    }

    @Test(groups = {"unit", "regression"})
    public void shortValuesAreFullyMasked() {
        assertThat(SensitiveDataMasker.maskValue("123")).isEqualTo("****");
        assertThat(SensitiveDataMasker.maskValue(null)).isNull();
    }

    @Test(groups = {"unit", "regression"})
    public void handlesNullAndEmptyBodies() {
        assertThat(SensitiveDataMasker.maskBody(null)).isNull();
        assertThat(SensitiveDataMasker.maskBody("")).isEmpty();
    }
}
