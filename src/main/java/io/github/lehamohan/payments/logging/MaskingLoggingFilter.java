package io.github.lehamohan.payments.logging;

import io.restassured.filter.Filter;
import io.restassured.filter.FilterContext;
import io.restassured.http.Header;
import io.restassured.response.Response;
import io.restassured.specification.FilterableRequestSpecification;
import io.restassured.specification.FilterableResponseSpecification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.StringJoiner;
import java.util.concurrent.TimeUnit;

/**
 * REST Assured filter that writes one structured (logfmt-style) line per HTTP exchange,
 * with every header and body passed through {@link SensitiveDataMasker} first.
 *
 * <p>Used instead of REST Assured's built-in {@code log().all()}, which prints raw values.
 */
public final class MaskingLoggingFilter implements Filter {

    private static final Logger LOG = LoggerFactory.getLogger("payments.http");

    private final boolean logBodies;

    public MaskingLoggingFilter(boolean logBodies) {
        this.logBodies = logBodies;
    }

    @Override
    public Response filter(FilterableRequestSpecification request,
                           FilterableResponseSpecification responseSpec,
                           FilterContext ctx) {
        Response response = ctx.next(request, responseSpec);

        String path = request.getURI().replaceFirst("^https?://[^/]+", "");
        LOG.info("event=http_exchange method={} path={} status={} durationMs={} idempotencyKey={}",
                request.getMethod(), path, response.getStatusCode(),
                response.getTimeIn(TimeUnit.MILLISECONDS),
                headerOrDash(request, "Idempotency-Key"));

        if (LOG.isDebugEnabled()) {
            LOG.debug("event=http_request_headers headers={}", maskedHeaders(request));
            if (logBodies) {
                Object body = request.getBody();
                LOG.debug("event=http_request_body body={}",
                        body == null ? "-" : SensitiveDataMasker.maskBody(String.valueOf(body)));
                LOG.debug("event=http_response_body body={}",
                        SensitiveDataMasker.maskBody(response.getBody().asString()));
            }
        }
        return response;
    }

    private static String headerOrDash(FilterableRequestSpecification request, String name) {
        String value = request.getHeaders().getValue(name);
        return value == null ? "-" : value;
    }

    private static String maskedHeaders(FilterableRequestSpecification request) {
        StringJoiner joiner = new StringJoiner(", ", "{", "}");
        for (Header h : request.getHeaders()) {
            joiner.add(h.getName() + "=" + SensitiveDataMasker.maskHeader(h.getName(), h.getValue()));
        }
        return joiner.toString();
    }
}
