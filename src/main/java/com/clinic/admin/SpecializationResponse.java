package com.clinic.admin;

public record SpecializationResponse(Long id, String title, String description) {

    static SpecializationResponse from(Specialization s) {
        return new SpecializationResponse(s.getId(), s.getTitle(), s.getDescription());
    }
}
