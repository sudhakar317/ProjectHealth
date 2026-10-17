package com.clinic.patient;

import com.clinic.user.AppUser;
import java.time.Instant;
import java.time.LocalDate;

public record PatientResponse(Long id, String fullName, String phone, Sex sex, LocalDate dateOfBirth, String address,
                              String notes, Long doctorId, String doctorName, boolean active, Instant createdAt) {

    static PatientResponse from(Patient p) {
        AppUser doctor = p.getDoctor();
        String name = doctor.getFullName() == null || doctor.getFullName().isBlank()
                ? doctor.getEmail()
                : doctor.getFullName();
        return new PatientResponse(p.getId(), p.getFullName(), p.getPhone(), p.getSex(), p.getDateOfBirth(),
                p.getAddress(), p.getNotes(), doctor.getId(), name, p.isActive(), p.getCreatedAt());
    }
}
