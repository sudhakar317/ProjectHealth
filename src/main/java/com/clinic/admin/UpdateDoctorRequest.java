package com.clinic.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** The email is the login identity and cannot be changed. */
public record UpdateDoctorRequest(
        @NotBlank @Size(max = 120) String fullName,
        @Pattern(regexp = "^$|^\\+?[0-9 ()-]{7,20}$", message = "must be a valid phone number") String phone,
        @NotNull Long specializationId,
        @NotNull Boolean active) {
}
