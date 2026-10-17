package com.clinic.visit;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * {@code doctorId} is honoured only for an admin, and only to move the visit to another active
 * doctor. Null means "leave the doctor as it is", so there is no way to blank it.
 *
 * <p>An edit never moves the visit's place in the chain: the booking it answered is a fact about
 * what happened, not something a later correction to the notes should rewrite.
 */
public record UpdateVisitRequest(
        @NotNull @PastOrPresent LocalDate visitDate,
        @NotBlank @Size(max = 4000) String notes,
        LocalDate nextVisitDate,
        Long doctorId) {
}
