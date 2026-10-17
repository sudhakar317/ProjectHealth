package com.clinic.visit;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * The patient comes from the path. {@code doctorId} is honoured only for an admin, who is not a
 * doctor and so must say whose visit this is; left null it falls back to the patient's own doctor.
 * For a doctor it is ignored entirely: they always author their own visits.
 *
 * <p>A new visit is always aligned to the patient's open booking, if they have one. Nothing in the
 * body controls that: the server decides, so the chain cannot be built wrong from the client.
 */
public record CreateVisitRequest(
        @NotNull @PastOrPresent LocalDate visitDate,
        @NotBlank @Size(max = 4000) String notes,
        LocalDate nextVisitDate,
        Long doctorId) {
}
