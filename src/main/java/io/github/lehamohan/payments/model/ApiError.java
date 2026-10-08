package io.github.lehamohan.payments.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/** Uniform error envelope returned for every 4xx/5xx response. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ApiError(String code, String message, List<FieldError> details, String traceId) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FieldError(String field, String issue) {
    }

    public boolean hasFieldError(String field) {
        return details != null && details.stream().anyMatch(d -> field.equals(d.field()));
    }
}
