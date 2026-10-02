package com.clinic.admin;

import com.clinic.audit.AuditService;
import com.clinic.common.ConflictException;
import com.clinic.common.NotFoundException;
import com.clinic.user.AppUser;
import com.clinic.user.AppUserRepository;
import com.clinic.user.Role;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DoctorService {

    private final AppUserRepository users;
    private final SpecializationService specializations;
    private final AuditService audit;

    public DoctorService(AppUserRepository users, SpecializationService specializations, AuditService audit) {
        this.users = users;
        this.specializations = specializations;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<DoctorResponse> list(DoctorFilter filter) {
        return users.findAll(filter.toSpecification(), Sort.by(Sort.Direction.DESC, "createdAt")).stream()
                .map(DoctorResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public DoctorResponse get(Long id) {
        return DoctorResponse.from(findDoctor(id));
    }

    @Transactional
    public DoctorResponse create(CreateDoctorRequest request, String actor, String ip) {
        String email = request.email().trim().toLowerCase();
        if (users.findByEmail(email).isPresent()) {
            throw new ConflictException("A user with this email already exists", "email");
        }
        AppUser saved = users.save(AppUser.doctor(email, request.fullName().trim(), request.phone().trim(),
                specializations.find(request.specializationId()), request.registrationNumber().trim(), actor));
        audit.record(AuditService.DOCTOR_CREATED, actor, describe(saved), ip);
        return DoctorResponse.from(saved);
    }

    @Transactional
    public DoctorResponse update(Long id, UpdateDoctorRequest request, String actor, String ip) {
        AppUser doctor = findDoctor(id);
        doctor.updateDoctor(request.fullName().trim(), request.phone().trim(),
                specializations.find(request.specializationId()), request.registrationNumber().trim(),
                request.active());
        audit.record(AuditService.DOCTOR_UPDATED, actor, describe(doctor), ip);
        return DoctorResponse.from(doctor);
    }

    @Transactional
    public void delete(Long id, String actor, String ip) {
        AppUser doctor = findDoctor(id);
        users.delete(doctor);
        audit.record(AuditService.DOCTOR_DELETED, actor, describe(doctor), ip);
    }

    private AppUser findDoctor(Long id) {
        return users.findById(id).filter(u -> u.getRole() == Role.DOCTOR)
                .orElseThrow(() -> new NotFoundException("Doctor not found"));
    }

    private String describe(AppUser doctor) {
        return "id=" + doctor.getId() + " email=" + doctor.getEmail();
    }
}
