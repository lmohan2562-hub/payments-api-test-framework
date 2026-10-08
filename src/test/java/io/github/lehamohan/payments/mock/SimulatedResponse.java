package io.github.lehamohan.payments.mock;

import java.util.LinkedHashMap;
import java.util.Map;

/** Transport-agnostic HTTP response produced by {@link PaymentApiSimulator}. */
public record SimulatedResponse(int status, Map<String, String> headers, String body, int delayMs) {

    public static SimulatedResponse json(int status, String body) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/json");
        return new SimulatedResponse(status, headers, body, 0);
    }

    public SimulatedResponse withHeader(String name, String value) {
        Map<String, String> copy = new LinkedHashMap<>(headers);
        copy.put(name, value);
        return new SimulatedResponse(status, copy, body, delayMs);
    }

    public SimulatedResponse withDelay(int millis) {
        return new SimulatedResponse(status, headers, body, millis);
    }
}
