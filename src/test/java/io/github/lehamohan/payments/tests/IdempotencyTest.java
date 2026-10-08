package io.github.lehamohan.payments.tests;

import io.github.lehamohan.payments.data.IdempotencyKeys;
import io.github.lehamohan.payments.data.SyntheticToken;
import io.github.lehamohan.payments.model.AuthorizationRequest;
import io.github.lehamohan.payments.model.AuthorizationResponse;
import io.github.lehamohan.payments.model.ApiError;
import io.github.lehamohan.payments.support.BaseApiTest;
import io.restassured.response.Response;
import org.testng.annotations.Test;

import static io.github.lehamohan.payments.data.AuthorizationRequestBuilder.anAuthorization;
import static org.assertj.core.api.Assertions.assertThat;

public class IdempotencyTest extends BaseApiTest {

    @Test(groups = {"smoke", "regression"}, description = "Replaying the same key and body returns the original authorization")
    public void replaySameKeySameBodyReturnsOriginal() {
        AuthorizationRequest request = anAuthorization().withAmountCents(1_500).build();
        String key = IdempotencyKeys.next();

        Response first = api.authorize(request, key);
        Response replay = api.authorize(request, key);

        assertThat(first.getStatusCode()).isEqualTo(201);
        assertThat(first.getHeader("Idempotent-Replayed")).isNull();
        assertThat(replay.getStatusCode()).isEqualTo(201);
        assertThat(replay.getHeader("Idempotent-Replayed")).isEqualTo("true");
        assertThat(replay.as(AuthorizationResponse.class).authorizationId())
                .isEqualTo(first.as(AuthorizationResponse.class).authorizationId());
    }

    @Test(groups = "regression", description = "Reusing a key with a different body is rejected with 409")
    public void sameKeyDifferentBodyReturns409() {
        String key = IdempotencyKeys.next();
        api.authorize(anAuthorization().withAmountCents(1_000).build(), key);

        Response conflict = api.authorize(anAuthorization().withAmountCents(9_999).build(), key);

        assertThat(conflict.getStatusCode()).isEqualTo(409);
        assertThat(conflict.as(ApiError.class).code()).isEqualTo("IDEMPOTENCY_KEY_REUSED");
    }

    @Test(groups = "regression", description = "Different keys create distinct authorizations for identical bodies")
    public void differentKeysCreateDistinctAuthorizations() {
        AuthorizationRequest request = anAuthorization().withAmountCents(700).build();

        String first = api.authorize(request, IdempotencyKeys.next()).as(AuthorizationResponse.class).authorizationId();
        String second = api.authorize(request, IdempotencyKeys.next()).as(AuthorizationResponse.class).authorizationId();

        assertThat(first).isNotEqualTo(second);
    }

    @Test(groups = "regression", description = "POST without Idempotency-Key is rejected with 400")
    public void missingIdempotencyKeyReturns400() {
        Response response = api.authorize(anAuthorization().build(), null);

        assertThat(response.getStatusCode()).isEqualTo(400);
        assertThat(response.as(ApiError.class).hasFieldError("Idempotency-Key")).isTrue();
    }

    @Test(groups = "regression", description = "Declines are idempotent too: replay returns the same decline")
    public void declineIsReplayedNotReprocessed() {
        AuthorizationRequest request = anAuthorization()
                .withToken(SyntheticToken.DO_NOT_HONOR).build();
        String key = IdempotencyKeys.next();

        Response first = api.authorize(request, key);
        Response replay = api.authorize(request, key);

        assertThat(replay.getStatusCode()).isEqualTo(first.getStatusCode()).isEqualTo(402);
        assertThat(replay.getHeader("Idempotent-Replayed")).isEqualTo("true");
        assertThat(replay.jsonPath().getString("authorizationId"))
                .isEqualTo(first.jsonPath().getString("authorizationId"));
    }
}
