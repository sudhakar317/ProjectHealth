package com.clinic.visit;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record UpdateVisitRequest(
        @NotNull @PastOrPresent LocalDate visitDate,
        @NotBlank @Size(max = 4000) String notes,
        LocalDate nextVisitDate) {
}
