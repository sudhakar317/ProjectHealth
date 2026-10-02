package com.clinic.admin;

import com.clinic.user.AppUser;
import java.time.Instant;

public record DoctorResponse(Long id, String fullName, String email, String phone, Long specializationId,
                             String specializationTitle, String registrationNumber, boolean active,
                             Instant createdAt) {

    static DoctorResponse from(AppUser u) {
        Specialization s = u.getSpecialization();
        return new DoctorResponse(u.getId(), u.getFullName(), u.getEmail(), u.getPhone(),
                s == null ? null : s.getId(), s == null ? null : s.getTitle(), u.getRegistrationNumber(),
                u.isActive(), u.getCreatedAt());
    }
}
