package com.clinic.patient;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * {@code doctorId} is honoured only for an admin, and only to move the patient to another active
 * doctor. Null means "leave the doctor as it is", so there is no way to blank it.
 */
public record UpdatePatientRequest(
        @NotBlank @Size(max = 120) String fullName,
        @NotBlank @Pattern(regexp = "^\\+?[0-9 ()-]{7,20}$", message = "must be a valid phone number") String phone,
        @NotNull Sex sex,
        @NotNull @PastOrPresent LocalDate dateOfBirth,
        @Size(max = 300) String address,
        @Size(max = 1000) String notes,
        Long doctorId) {
}
