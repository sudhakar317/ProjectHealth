package com.clinic.admin;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateDoctorRequest(
        @NotBlank @Size(max = 120) String fullName,
        @NotBlank @Email @Size(max = 320) String email,
        @NotBlank @Pattern(regexp = "^\\+?[0-9 ()-]{7,20}$", message = "must be a valid phone number") String phone,
        @NotNull Long specializationId,
        @NotBlank @Size(max = 50) String registrationNumber) {
}
