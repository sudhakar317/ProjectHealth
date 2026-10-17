package com.clinic.visit;

import com.clinic.user.AppUser;
import java.time.Instant;
import java.time.LocalDate;

public record VisitResponse(Long id, Long patientId, String patientName, Long doctorId, String doctorName,
                            LocalDate visitDate, String notes, LocalDate nextVisitDate, boolean active,
                            Instant createdAt) {

    static VisitResponse from(Visit v) {
        AppUser doctor = v.getDoctor();
        String doctorName = doctor.getFullName() == null || doctor.getFullName().isBlank()
                ? doctor.getEmail()
                : doctor.getFullName();
        return new VisitResponse(v.getId(), v.getPatient().getId(), v.getPatient().getFullName(), doctor.getId(),
                doctorName, v.getVisitDate(), v.getNotes(), v.getNextVisitDate(), v.isActive(), v.getCreatedAt());
    }
}
