package io.github.lehamohan.payments.logging;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Masks sensitive values before they reach any log sink.
 *
 * <p>Defence in depth: even though this framework only ever uses synthetic tokens, the masker
 * treats every payload as if it could contain real cardholder data. It masks:
 * <ul>
 *   <li>JSON string fields whose name is in {@link #SENSITIVE_JSON_FIELDS}</li>
 *   <li>Any 13-19 digit sequence that could be a primary account number (PAN)</li>
 *   <li>Sensitive HTTP headers (Authorization, API keys, cookies)</li>
 * </ul>
 */
public final class SensitiveDataMasker {

    public static final Set<String> SENSITIVE_JSON_FIELDS = Set.of(
            "paymentToken", "cardNumber", "pan", "cvv", "cvc", "securityCode",
            "accountNumber", "routingNumber", "password", "apiKey", "ssn");

    public static final Set<String> SENSITIVE_HEADERS = Set.of(
            "authorization", "x-api-key", "cookie", "set-cookie", "proxy-authorization");

    private static final String MASK = "****";
    private static final int VISIBLE_SUFFIX = 4;

    /** "fieldName" : "value" (value without escaped quotes, which is all our payloads use). */
    private static final Pattern JSON_STRING_FIELD = Pattern.compile(
            "\"(" + String.join("|", List.copyOf(SENSITIVE_JSON_FIELDS)) + ")\"(\\s*:\\s*)\"([^\"]*)\"");

    /** 13-19 digits, optionally separated by single spaces or dashes, not part of a longer number. */
    private static final Pattern PAN_LIKE = Pattern.compile("(?<!\\d)(?:\\d[ -]?){12,18}\\d(?!\\d)");

    private SensitiveDataMasker() {
    }

    /** Masks sensitive JSON fields and PAN-like digit runs in an arbitrary text body. */
    public static String maskBody(String body) {
        if (body == null || body.isEmpty()) {
            return body;
        }
        Matcher m = JSON_STRING_FIELD.matcher(body);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String replacement = "\"" + m.group(1) + "\"" + m.group(2) + "\"" + maskValue(m.group(3)) + "\"";
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);
        return maskPanLikeSequences(sb.toString());
    }

    /** Masks a header value if the header name is sensitive. */
    public static String maskHeader(String name, String value) {
        if (name != null && SENSITIVE_HEADERS.contains(name.toLowerCase(Locale.ROOT))) {
            if (value != null && value.regionMatches(true, 0, "Bearer ", 0, 7)) {
                return "Bearer " + MASK;
            }
            return MASK;
        }
        return value;
    }

    /** Keeps only the last four characters for correlation, e.g. {@code ****oved}. */
    public static String maskValue(String value) {
        if (value == null) {
            return null;
        }
        if (value.length() <= VISIBLE_SUFFIX * 2) {
            return MASK;
        }
        return MASK + value.substring(value.length() - VISIBLE_SUFFIX);
    }

    static String maskPanLikeSequences(String text) {
        Matcher m = PAN_LIKE.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String digits = m.group().replaceAll("[ -]", "");
            m.appendReplacement(sb, Matcher.quoteReplacement(MASK + digits.substring(digits.length() - VISIBLE_SUFFIX)));
        }
        m.appendTail(sb);
        return sb.toString();
    }
}
