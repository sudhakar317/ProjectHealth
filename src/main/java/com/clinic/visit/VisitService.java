package com.clinic.visit;

import com.clinic.audit.AuditService;
import com.clinic.common.ConflictException;
import com.clinic.common.NotFoundException;
import com.clinic.common.PageResponse;
import com.clinic.patient.Patient;
import com.clinic.patient.PatientService;
import com.clinic.user.AppUser;
import com.clinic.user.AppUserRepository;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VisitService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_FOLLOW_UPS = 50;

    private final VisitRepository visits;
    private final PatientService patientService;
    private final AppUserRepository users;
    private final AuditService audit;

    public VisitService(VisitRepository visits, PatientService patientService, AppUserRepository users,
                        AuditService audit) {
        this.visits = visits;
        this.patientService = patientService;
        this.users = users;
        this.audit = audit;
    }

    /** Timeline for one patient. Access is decided by whether the caller may see that patient. */
    @Transactional(readOnly = true)
    public List<VisitResponse> listForPatient(Long patientId, boolean includeInactive, String actorEmail) {
        patientService.requireVisible(patientId, actorEmail);
        VisitFilter filter = new VisitFilter(null, null, null, includeInactive, null, patientId);
        return visits.findAll(filter.toSpecification(), Sort.by(Sort.Direction.DESC, "visitDate").and(
                        Sort.by(Sort.Direction.DESC, "id")))
                .stream().map(VisitResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<VisitResponse> list(String search, LocalDate from, LocalDate to, boolean includeInactive,
                                            int page, int size, String actorEmail) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "visitDate").and(Sort.by(Sort.Direction.DESC, "id")));
        VisitFilter filter = new VisitFilter(search, from, to, includeInactive,
                patientService.doctorScopeFor(actorEmail), null);
        return PageResponse.of(visits.findAll(filter.toSpecification(), pageable).map(VisitResponse::from));
    }

    /** Visits whose next appointment has come due, oldest first so overdue sorts to the top. */
    @Transactional(readOnly = true)
    public List<VisitResponse> followUps(String actorEmail) {
        Long doctorScope = patientService.doctorScopeFor(actorEmail);
        LocalDate today = LocalDate.now();
        return visits.findAll(FollowUpSpecification.dueBy(today, doctorScope),
                        Sort.by(Sort.Direction.ASC, "nextVisitDate"))
                .stream().limit(MAX_FOLLOW_UPS).map(VisitResponse::from).toList();
    }

    @Transactional
    public VisitResponse get(Long id, String actorEmail) {
        return VisitResponse.from(findVisible(id, actorEmail));
    }

    @Transactional
    public VisitResponse create(Long patientId, CreateVisitRequest request, String actorEmail, String ip) {
        Patient patient = patientService.requireVisible(patientId, actorEmail);
        if (!patient.isActive()) {
            throw new ConflictException("This patient is inactive. Restore them before recording a visit", null);
        }
        ensureFollowUpNotBeforeVisit(request.visitDate(), request.nextVisitDate());

        AppUser doctor = users.findByEmail(actorEmail)
                .orElseThrow(() -> new NotFoundException("Signed-in user not found"));
        Visit saved = visits.save(new Visit(patient, doctor, request.visitDate(), request.notes().trim(),
                request.nextVisitDate(), actorEmail));
        audit.record(AuditService.VISIT_CREATED, actorEmail, describe(saved), ip);
        return VisitResponse.from(saved);
    }

    @Transactional
    public VisitResponse update(Long id, UpdateVisitRequest request, String actorEmail, String ip) {
        Visit visit = findVisible(id, actorEmail);
        ensureFollowUpNotBeforeVisit(request.visitDate(), request.nextVisitDate());
        visit.update(request.visitDate(), request.notes().trim(), request.nextVisitDate());
        audit.record(AuditService.VISIT_UPDATED, actorEmail, describe(visit), ip);
        return VisitResponse.from(visit);
    }

    @Transactional
    public void delete(Long id, String actorEmail, String ip) {
        Visit visit = findVisible(id, actorEmail);
        visit.softDelete();
        audit.record(AuditService.VISIT_DELETED, actorEmail, describe(visit), ip);
    }

    @Transactional
    public VisitResponse restore(Long id, String actorEmail, String ip) {
        Visit visit = findVisible(id, actorEmail);
        visit.restore();
        audit.record(AuditService.VISIT_RESTORED, actorEmail, describe(visit), ip);
        return VisitResponse.from(visit);
    }

    /** Reuses the patient rule, so a doctor gets "not found" for another doctor's visit. */
    private Visit findVisible(Long id, String actorEmail) {
        Visit visit = visits.findById(id).orElseThrow(() -> new NotFoundException("Visit not found"));
        patientService.requireVisible(visit.getPatient().getId(), actorEmail);
        return visit;
    }

    private static void ensureFollowUpNotBeforeVisit(LocalDate visitDate, LocalDate nextVisitDate) {
        if (nextVisitDate != null && nextVisitDate.isBefore(visitDate)) {
            throw new ConflictException("The next visit cannot be before the visit date", "nextVisitDate");
        }
    }

    /** Ids only: notes and patient names must never reach a log (rules.md 5.8). */
    private String describe(Visit visit) {
        return "id=" + visit.getId() + " patient=" + visit.getPatient().getId();
    }
}
