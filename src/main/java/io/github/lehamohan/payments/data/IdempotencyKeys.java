package io.github.lehamohan.payments.data;

import java.util.UUID;

/** Generates unique idempotency keys so parallel tests never collide. */
public final class IdempotencyKeys {

    private IdempotencyKeys() {
    }

    public static String next() {
        return "idem-" + UUID.randomUUID();
    }
}
