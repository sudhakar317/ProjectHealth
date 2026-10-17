package com.clinic.admin;

import com.clinic.audit.AuditService;
import com.clinic.common.ConflictException;
import com.clinic.common.NotFoundException;
import com.clinic.patient.PatientRepository;
import com.clinic.user.AppUser;
import com.clinic.user.AppUserRepository;
import com.clinic.user.Role;
import com.clinic.visit.VisitRepository;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DoctorService {

    private final AppUserRepository users;
    private final SpecializationService specializations;
    private final PatientRepository patients;
    private final VisitRepository visits;
    private final AuditService audit;

    public DoctorService(AppUserRepository users, SpecializationService specializations, PatientRepository patients,
                         VisitRepository visits, AuditService audit) {
        this.users = users;
        this.specializations = specializations;
        this.patients = patients;
        this.visits = visits;
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
        AppUser saved = users.save(AppUser.doctor(email, request.fullName().trim(), trimmed(request.phone()),
                specializations.find(request.specializationId()), actor));
        audit.record(AuditService.DOCTOR_CREATED, actor, describe(saved), ip);
        return DoctorResponse.from(saved);
    }

    @Transactional
    public DoctorResponse update(Long id, UpdateDoctorRequest request, String actor, String ip) {
        AppUser doctor = findDoctor(id);
        doctor.updateDoctor(request.fullName().trim(), trimmed(request.phone()),
                specializations.find(request.specializationId()), request.active());
        audit.record(AuditService.DOCTOR_UPDATED, actor, describe(doctor), ip);
        return DoctorResponse.from(doctor);
    }

    /**
     * Only a doctor with no clinical footprint can be removed. Patients and visits keep a hard
     * reference to their doctor, and deleting one would orphan who wrote a note; for anyone who has
     * practised here, deactivating is the right tool, and it is what the message points to.
     */
    @Transactional
    public void delete(Long id, String actor, String ip) {
        AppUser doctor = findDoctor(id);
        if (patients.existsByDoctorId(id) || visits.existsByDoctorId(id)) {
            throw new ConflictException(
                    "This doctor has patients or visit records, so they can't be deleted. Deactivate them instead",
                    null);
        }
        users.delete(doctor);
        // Flush now: the audit row commits on its own, so any failure must surface before it is
        // written, or the log would record a deletion that never happened.
        users.flush();
        audit.record(AuditService.DOCTOR_DELETED, actor, describe(doctor), ip);
    }

    private AppUser findDoctor(Long id) {
        return users.findById(id).filter(u -> u.getRole() == Role.DOCTOR)
                .orElseThrow(() -> new NotFoundException("Doctor not found"));
    }

    /** Phone is optional, so it arrives null or blank when nobody has the number yet. */
    private static String trimmed(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String describe(AppUser doctor) {
        return "id=" + doctor.getId() + " email=" + doctor.getEmail();
    }
}
