package com.clinic.common;

import com.clinic.user.ProtectedAdminException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.hibernate.exception.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Turns every failure into an {@link ApiError}, so the client has exactly one shape to read.
 *
 * <p>Messages are written for the person using the app. Nothing internal leaks: an unexpected
 * failure says only that something went wrong and gives the request id, while the detail goes to
 * the log under that same id.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    /**
     * Database constraints that back a business rule. A race between two saves can slip past the
     * service-level check, and the constraint then catches it: it should read like the rule, not
     * like SQL.
     */
    private static final Map<String, Violation> CONSTRAINTS = Map.of(
            "uq_app_user_email", new Violation("email", "A user with this email already exists"),
            "uq_specialization_title", new Violation("title", "A specialization with this title already exists"),
            "uq_visit_answers_one_follow_up", new Violation(null, "That follow-up was already answered by another visit"),
            "uq_visit_one_per_patient_per_day", new Violation("visitDate", "This patient already has a visit on that date"));

    private record Violation(String field, String message) {
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> validation(MethodArgumentNotValidException ex) {
        Map<String, String> fields = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(e -> fields.putIfAbsent(e.getField(), e.getDefaultMessage()));
        return ResponseEntity.badRequest().body(ApiError.of("Some fields need attention", fields));
    }

    @ExceptionHandler(ConflictException.class)
    ResponseEntity<ApiError> conflict(ConflictException ex) {
        Map<String, String> fields = ex.getField() == null ? Map.of() : Map.of(ex.getField(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError.of(ex.getMessage(), fields));
    }

    @ExceptionHandler(ProtectedAdminException.class)
    ResponseEntity<ApiError> protectedAdmin(ProtectedAdminException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError.of(ex.getMessage()));
    }

    @ExceptionHandler(NotFoundException.class)
    ResponseEntity<ApiError> notFound(NotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError.of(ex.getMessage()));
    }

    /** Only reached from method security; the filter chain answers its own 403s in the same shape. */
    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiError> forbidden(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiError.of("You don't have permission to do that"));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> unreadable(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest().body(ApiError.of("The request could not be read. Check the values and try again"));
    }

    /** A path or query value of the wrong type, such as {@code /api/visits/abc}. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ApiError> typeMismatch(MethodArgumentTypeMismatchException ex) {
        return ResponseEntity.badRequest().body(ApiError.of("'" + ex.getName() + "' has an invalid value"));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiError> integrity(DataIntegrityViolationException ex) {
        Violation known = CONSTRAINTS.get(constraintName(ex));
        if (known == null) {
            // Not a rule we recognise, so it may be a real bug: keep the detail, show nothing of it.
            log.warn("Unmapped data integrity violation", ex);
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiError.of("This change conflicts with existing data"));
        }
        Map<String, String> fields = known.field() == null ? Map.of() : Map.of(known.field(), known.message());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError.of(known.message(), fields));
    }

    /**
     * Everything else. Spring's own web exceptions (unknown route, wrong method, missing parameter,
     * {@code ResponseStatusException}) carry their status and are answered with it; anything that
     * does not is a genuine fault and becomes a 500 with no detail beyond the request id.
     */
    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> unexpected(Exception ex) {
        if (ex instanceof ErrorResponse framework) {
            HttpStatusCode status = framework.getStatusCode();
            return ResponseEntity.status(status).body(ApiError.of(messageFor(status)));
        }
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiError.of("Something went wrong on our side. Please try again"));
    }

    private static String messageFor(HttpStatusCode status) {
        return switch (status.value()) {
            case 400 -> "The request was not valid";
            case 401 -> "Your session has ended. Please sign in again";
            case 403 -> "You don't have permission to do that";
            case 404 -> "Not found";
            case 405 -> "That action is not supported here";
            case 415 -> "That content type is not supported";
            default -> status.is5xxServerError()
                    ? "Something went wrong on our side. Please try again"
                    : "The request could not be completed";
        };
    }

    private static String constraintName(Throwable ex) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t instanceof ConstraintViolationException c && c.getConstraintName() != null) {
                return c.getConstraintName().toLowerCase();
            }
        }
        return null;
    }
}
