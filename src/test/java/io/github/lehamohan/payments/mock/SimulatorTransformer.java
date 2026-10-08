package io.github.lehamohan.payments.mock;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.extension.ResponseDefinitionTransformerV2;
import com.github.tomakehurst.wiremock.http.HttpHeader;
import com.github.tomakehurst.wiremock.http.Request;
import com.github.tomakehurst.wiremock.http.ResponseDefinition;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** WireMock extension that delegates matched requests to {@link PaymentApiSimulator}. */
public final class SimulatorTransformer implements ResponseDefinitionTransformerV2 {

    public static final String NAME = "payment-simulator";

    private final PaymentApiSimulator simulator;

    public SimulatorTransformer(PaymentApiSimulator simulator) {
        this.simulator = simulator;
    }

    @Override
    public ResponseDefinition transform(ServeEvent serveEvent) {
        Request request = serveEvent.getRequest();
        Map<String, String> headers = new HashMap<>();
        for (HttpHeader header : request.getHeaders().all()) {
            headers.put(header.key().toLowerCase(Locale.ROOT), header.firstValue());
        }
        String url = request.getUrl();
        int query = url.indexOf('?');
        String path = query >= 0 ? url.substring(0, query) : url;

        SimulatedResponse result = simulator.handle(new SimulatedRequest(
                request.getMethod().toString(), path, headers, request.getBodyAsString()));

        ResponseDefinitionBuilder builder = ResponseDefinitionBuilder.responseDefinition()
                .withStatus(result.status())
                .withBody(result.body());
        result.headers().forEach(builder::withHeader);
        if (result.delayMs() > 0) {
            builder.withFixedDelay(result.delayMs());
        }
        return builder.build();
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public boolean applyGlobally() {
        return false;
    }
}
