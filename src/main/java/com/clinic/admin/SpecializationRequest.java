package com.clinic.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SpecializationRequest(
        @NotBlank @Size(max = 100) String title,
        @NotBlank @Size(max = 500) String description) {
}
