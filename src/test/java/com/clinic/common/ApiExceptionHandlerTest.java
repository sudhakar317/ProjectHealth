package com.clinic.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.clinic.config.RequestIdFilter;
import java.sql.SQLException;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

/** The branches no endpoint can trigger on demand, tested directly. */
class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @BeforeEach
    void tagRequest() {
        MDC.put(RequestIdFilter.MDC_KEY, "abc123def456");
    }

    @AfterEach
    void clear() {
        MDC.clear();
    }

    @Test
    void anUnexpectedFailureIsAGeneric500ThatLeaksNothing() {
        ResponseEntity<ApiError> response = handler.unexpected(
                new IllegalStateException("connection to db-internal:5432 refused, password=hunter2"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().message()).isEqualTo("Something went wrong on our side. Please try again");
        assertThat(response.getBody().toString()).doesNotContain("hunter2", "db-internal");
        assertThat(response.getBody().requestId()).isEqualTo("abc123def456");
    }

    @Test
    void aResponseStatusExceptionKeepsItsStatusInsteadOfBecomingA500() {
        // MeController throws this for a user disabled mid-session; a naive catch-all would 500 it.
        ResponseEntity<ApiError> response = handler.unexpected(new ResponseStatusException(HttpStatus.UNAUTHORIZED));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().message()).isEqualTo("Your session has ended. Please sign in again");
    }

    @Test
    void aKnownConstraintReadsLikeTheRuleItEnforces() {
        DataIntegrityViolationException race = new DataIntegrityViolationException("duplicate",
                new ConstraintViolationException("duplicate key", new SQLException(), "uq_app_user_email"));

        ResponseEntity<ApiError> response = handler.integrity(race);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().fieldErrors()).containsEntry("email", "A user with this email already exists");
    }

    @Test
    void anUnknownConstraintIsAConflictWithoutSqlInTheMessage() {
        DataIntegrityViolationException unknown = new DataIntegrityViolationException("raw sql detail",
                new ConstraintViolationException("ERROR: insert violates fk_something", new SQLException(),
                        "fk_something"));

        ResponseEntity<ApiError> response = handler.integrity(unknown);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().message()).isEqualTo("This change conflicts with existing data");
        assertThat(response.getBody().toString()).doesNotContain("fk_something", "ERROR");
    }
}
