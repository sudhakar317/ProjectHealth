package com.clinic.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app")
public record AppProperties(@NotBlank String adminEmail, @NotBlank String frontendUrl) {

    public String normalizedAdminEmail() {
        return adminEmail.trim().toLowerCase();
    }
}
