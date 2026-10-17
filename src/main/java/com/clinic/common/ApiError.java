package com.clinic.common;

import com.clinic.config.RequestIdFilter;
import java.util.Map;
import org.slf4j.MDC;

/**
 * The one error shape every failing API call returns, whether it fails in a controller, in
 * validation, or in the security filter chain before a controller is reached.
 *
 * <p>{@code requestId} is the same id written to the logs and the {@code X-Request-Id} header, so a
 * user who reports "it said reference ab12cd34ef56" can be matched to the exact log line.
 */
public record ApiError(String message, Map<String, String> fieldErrors, String requestId) {

    public static ApiError of(String message) {
        return of(message, Map.of());
    }

    public static ApiError of(String message, Map<String, String> fieldErrors) {
        return new ApiError(message, fieldErrors, MDC.get(RequestIdFilter.MDC_KEY));
    }
}
