package com.clinic.patient;

import com.clinic.audit.AuditService;
import com.clinic.common.ConflictException;
import com.clinic.common.NotFoundException;
import com.clinic.common.PageResponse;
import com.clinic.user.AppUser;
import com.clinic.user.AppUserRepository;
import com.clinic.user.Role;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PatientService {

    private static final int MAX_PAGE_SIZE = 100;

    private final PatientRepository patients;
    private final AppUserRepository users;
    private final AuditService audit;

    public PatientService(PatientRepository patients, AppUserRepository users, AuditService audit) {
        this.patients = patients;
        this.users = users;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public PageResponse<PatientResponse> list(String search, boolean includeInactive, int page, int size,
                                              String actorEmail) {
        AppUser viewer = currentUser(actorEmail);
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "id"));
        PatientFilter filter = new PatientFilter(search, includeInactive, scopeFor(viewer));
        return PageResponse.of(patients.findAll(filter.toSpecification(), pageable).map(PatientResponse::from));
    }

    @Transactional
    public PatientResponse get(Long id, String actorEmail, String ip) {
        Patient patient = findVisible(id, currentUser(actorEmail));
        audit.record(AuditService.PATIENT_VIEWED, actorEmail, describe(patient), ip);
        return PatientResponse.from(patient);
    }

    @Transactional
    public PatientResponse create(CreatePatientRequest request, String actorEmail, String ip) {
        AppUser doctor = currentUser(actorEmail);
        Patient saved = patients.save(new Patient(request.fullName().trim(), request.phone().trim(), request.sex(),
                request.dateOfBirth(), trimmed(request.address()), trimmed(request.notes()), doctor, actorEmail));
        audit.record(AuditService.PATIENT_CREATED, actorEmail, describe(saved), ip);
        return PatientResponse.from(saved);
    }

    @Transactional
    public PatientResponse update(Long id, UpdatePatientRequest request, String actorEmail, String ip) {
        AppUser actor = currentUser(actorEmail);
        Patient patient = findVisible(id, actor);
        patient.update(request.fullName().trim(), request.phone().trim(), request.sex(), request.dateOfBirth(),
                trimmed(request.address()), trimmed(request.notes()));

        // Only an admin may move a patient, and only to another active doctor. A null id leaves it
        // alone, so no request can clear the field.
        if (actor.getRole() == Role.ADMIN && request.doctorId() != null
                && !request.doctorId().equals(patient.getDoctor().getId())) {
            patient.reassignTo(activeDoctor(request.doctorId()));
        }

        audit.record(AuditService.PATIENT_UPDATED, actorEmail, describe(patient), ip);
        return PatientResponse.from(patient);
    }

    /** Marks the patient inactive. The row is never removed. */
    @Transactional
    public void delete(Long id, String actorEmail, String ip) {
        Patient patient = findVisible(id, currentUser(actorEmail));
        patient.softDelete();
        audit.record(AuditService.PATIENT_DELETED, actorEmail, describe(patient), ip);
    }

    @Transactional
    public PatientResponse restore(Long id, String actorEmail, String ip) {
        Patient patient = findVisible(id, currentUser(actorEmail));
        patient.restore();
        audit.record(AuditService.PATIENT_RESTORED, actorEmail, describe(patient), ip);
        return PatientResponse.from(patient);
    }

    /**
     * In-process only, never exposed by a controller: returns the entity so the visit feature can
     * reuse this exact access rule instead of reimplementing it.
     */
    @Transactional(readOnly = true)
    public Patient requireVisible(Long patientId, String actorEmail) {
        return findVisible(patientId, currentUser(actorEmail));
    }

    /** True when the signed-in user is a doctor, who may only ever act on their own patients. */
    @Transactional(readOnly = true)
    public Long doctorScopeFor(String actorEmail) {
        return scopeFor(currentUser(actorEmail));
    }

    private AppUser currentUser(String email) {
        return users.findByEmail(email).filter(AppUser::isActive)
                .orElseThrow(() -> new NotFoundException("Signed-in user not found"));
    }

    /** Doctors are limited to their own patients; admins see every patient. */
    private Long scopeFor(AppUser viewer) {
        return viewer.getRole() == Role.DOCTOR ? viewer.getId() : null;
    }

    /**
     * A doctor asking for someone else's patient gets "not found" rather than "forbidden":
     * a 403 would confirm the record exists.
     */
    private Patient findVisible(Long id, AppUser viewer) {
        Patient patient = patients.findById(id).orElseThrow(() -> new NotFoundException("Patient not found"));
        if (viewer.getRole() == Role.DOCTOR && !patient.getDoctor().getId().equals(viewer.getId())) {
            throw new NotFoundException("Patient not found");
        }
        return patient;
    }

    private AppUser activeDoctor(Long doctorId) {
        return users.findById(doctorId)
                .filter(u -> u.getRole() == Role.DOCTOR && u.isActive())
                .orElseThrow(() -> new ConflictException("Choose an active doctor", "doctorId"));
    }

    private static String trimmed(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** Only the id: patient names, phones and notes must never reach a log (rules.md 5.8). */
    private String describe(Patient patient) {
        return "id=" + patient.getId();
    }
}
