package io.github.lehamohan.payments.mock;

import java.util.Locale;
import java.util.Map;

/** Transport-agnostic HTTP request handed to {@link PaymentApiSimulator}. Header names are lower-case. */
public record SimulatedRequest(String method, String path, Map<String, String> headers, String body) {

    public String header(String name) {
        return headers.get(name.toLowerCase(Locale.ROOT));
    }
}
