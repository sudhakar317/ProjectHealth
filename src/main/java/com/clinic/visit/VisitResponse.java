package com.clinic.visit;

import com.clinic.user.AppUser;
import java.time.Instant;
import java.time.LocalDate;

/**
 * {@code previousVisitId} and {@code expectedDate} describe the follow-up this visit answered.
 * How early or late the patient was is deliberately not sent: it is {@code visitDate} minus
 * {@code expectedDate}, and a stored number would go stale the moment either date is corrected.
 * The client subtracts the two when it renders.
 */
public record VisitResponse(Long id, Long patientId, String patientName, Long doctorId, String doctorName,
                            LocalDate visitDate, String notes, LocalDate nextVisitDate, Long previousVisitId,
                            LocalDate expectedDate, boolean active, Instant createdAt) {

    static VisitResponse from(Visit v) {
        AppUser doctor = v.getDoctor();
        String doctorName = doctor.getFullName() == null || doctor.getFullName().isBlank()
                ? doctor.getEmail()
                : doctor.getFullName();
        Visit previous = v.getPreviousVisit();
        return new VisitResponse(v.getId(), v.getPatient().getId(), v.getPatient().getFullName(), doctor.getId(),
                doctorName, v.getVisitDate(), v.getNotes(), v.getNextVisitDate(),
                previous == null ? null : previous.getId(),
                previous == null ? null : previous.getNextVisitDate(),
                v.isActive(), v.getCreatedAt());
    }
}
