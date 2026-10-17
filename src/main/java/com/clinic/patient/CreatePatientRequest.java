package com.clinic.patient;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** The doctor is taken from the signed-in user, never from the request. */
public record CreatePatientRequest(
        @NotBlank @Size(max = 120) String fullName,
        @NotBlank @Pattern(regexp = "^\\+?[0-9 ()-]{7,20}$", message = "must be a valid phone number") String phone,
        @NotNull Sex sex,
        @NotNull @PastOrPresent LocalDate dateOfBirth,
        @Size(max = 300) String address,
        @Size(max = 1000) String notes) {
}
