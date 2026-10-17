package com.clinic.visit;

import com.clinic.audit.AuditService;
import com.clinic.common.ConflictException;
import com.clinic.common.NotFoundException;
import com.clinic.common.PageResponse;
import com.clinic.patient.Patient;
import com.clinic.patient.PatientService;
import com.clinic.user.AppUser;
import com.clinic.user.AppUserRepository;
import com.clinic.user.Role;
import java.time.LocalDate;
import java.util.Objects;
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

    /**
     * Timeline for one patient. Access is decided by whether the caller may see that patient, and the
     * opening is audited because the timeline shows clinical notes (rules.md 5.7).
     */
    @Transactional(readOnly = true)
    public List<VisitResponse> listForPatient(Long patientId, boolean includeInactive, String actorEmail, String ip) {
        patientService.requireVisible(patientId, actorEmail);
        VisitFilter filter = new VisitFilter(null, null, null, includeInactive, null, patientId);
        List<VisitResponse> result = visits.findAll(filter.toSpecification(),
                        Sort.by(Sort.Direction.DESC, "visitDate").and(Sort.by(Sort.Direction.DESC, "id")))
                .stream().map(VisitResponse::from).toList();
        audit.record(AuditService.VISIT_HISTORY_VIEWED, actorEmail, "patient=" + patientId, ip);
        return result;
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
    public VisitResponse get(Long id, String actorEmail, String ip) {
        Visit visit = findVisible(id, actorEmail);
        audit.record(AuditService.VISIT_VIEWED, actorEmail, describe(visit), ip);
        return VisitResponse.from(visit);
    }

    @Transactional
    public VisitResponse create(Long patientId, CreateVisitRequest request, String actorEmail, String ip) {
        Patient patient = patientService.requireVisible(patientId, actorEmail);
        if (!patient.isActive()) {
            throw new ConflictException("This patient is inactive. Restore them before recording a visit", null);
        }
        ensureFollowUpAfterVisit(request.visitDate(), request.nextVisitDate());
        // Scoped to this patient: two different patients may of course be seen on the same day.
        if (visits.existsByPatientIdAndVisitDateAndDeletedAtIsNull(patientId, request.visitDate())) {
            throw new ConflictException("This patient already has a visit on that date", "visitDate");
        }

        AppUser doctor = resolveAuthor(patient, request.doctorId(), actorEmail);
        Visit saved = visits.save(new Visit(patient, doctor, request.visitDate(), request.notes().trim(),
                request.nextVisitDate(), actorEmail));
        // Every visit is aligned to the booking it follows, when there is one open. Excluding the
        // row just saved: it carries a booking of its own and would otherwise answer itself,
        // closing that follow-up the moment it was made.
        saved.linkTo(openFollowUp(patient.getId(), request.visitDate(), saved.getId()));
        audit.record(AuditService.VISIT_CREATED, actorEmail, describe(saved), ip);
        return VisitResponse.from(saved);
    }

    @Transactional
    public VisitResponse update(Long id, UpdateVisitRequest request, String actorEmail, String ip) {
        Visit visit = findVisible(id, actorEmail);
        ensureFollowUpAfterVisit(request.visitDate(), request.nextVisitDate());
        ensureDatesStillOpen(visit, request);
        ensureNotBeforeTheVisitItFollows(visit, request.visitDate());
        if (visits.existsByPatientIdAndVisitDateAndDeletedAtIsNullAndIdNot(
                visit.getPatient().getId(), request.visitDate(), id)) {
            throw new ConflictException("This patient already has a visit on that date", "visitDate");
        }
        visit.update(request.visitDate(), request.notes().trim(), request.nextVisitDate());

        // Only an admin may change whose visit this is, and only to another active doctor. A null
        // id leaves it alone, so no request can clear the author.
        AppUser actor = currentUser(actorEmail);
        if (actor.getRole() == Role.ADMIN && request.doctorId() != null
                && !request.doctorId().equals(visit.getDoctor().getId())) {
            visit.reassignTo(activeDoctor(request.doctorId()));
        }

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
        // Another visit may have answered that booking while this one was deleted. The follow-up is
        // already closed, so this visit comes back as a walk-in rather than fighting for the link.
        if (visit.getPreviousVisit() != null
                && visits.existsByPreviousVisitIdAndDeletedAtIsNullAndIdNot(visit.getPreviousVisit().getId(), id)) {
            visit.linkTo(null);
        }
        visit.restore();
        audit.record(AuditService.VISIT_RESTORED, actorEmail, describe(visit), ip);
        return VisitResponse.from(visit);
    }

    /**
     * A doctor always authors their own visits, so anything in the body is ignored for them. An
     * admin is not a doctor and must say whose visit this is; left null it falls back to the
     * patient's own doctor, which is the only sensible default.
     */
    private AppUser resolveAuthor(Patient patient, Long requestedDoctorId, String actorEmail) {
        AppUser actor = currentUser(actorEmail);
        if (actor.getRole() != Role.ADMIN) {
            return actor;
        }
        return requestedDoctorId == null ? patient.getDoctor() : activeDoctor(requestedDoctorId);
    }

    /** The booking this visit most likely answers: the latest one the patient has not kept. */
    private Visit openFollowUp(Long patientId, LocalDate visitDate, long excludeId) {
        return visits.findOpenFollowUps(patientId, visitDate, excludeId, PageRequest.of(0, 1))
                .stream().findFirst().orElse(null);
    }

    private AppUser currentUser(String email) {
        return users.findByEmail(email).filter(AppUser::isActive)
                .orElseThrow(() -> new NotFoundException("Signed-in user not found"));
    }

    private AppUser activeDoctor(Long doctorId) {
        return users.findById(doctorId)
                .filter(u -> u.getRole() == Role.DOCTOR && u.isActive())
                .orElseThrow(() -> new ConflictException("Choose an active doctor", "doctorId"));
    }

    /** Reuses the patient rule, so a doctor gets "not found" for another doctor's visit. */
    private Visit findVisible(Long id, String actorEmail) {
        Visit visit = visits.findById(id).orElseThrow(() -> new NotFoundException("Visit not found"));
        patientService.requireVisible(visit.getPatient().getId(), actorEmail);
        return visit;
    }

    /**
     * Once a later visit has answered this one, its dates are history: moving them would rewrite
     * how early or late that later visit was, and could reorder the chain. Notes stay editable,
     * because correcting what was written is not the same as changing what happened.
     */
    private void ensureDatesStillOpen(Visit visit, UpdateVisitRequest request) {
        if (!visits.existsByPreviousVisitIdAndDeletedAtIsNull(visit.getId())) {
            return;
        }
        if (!request.visitDate().equals(visit.getVisitDate())) {
            throw new ConflictException("A later visit already followed this one, so its date is fixed", "visitDate");
        }
        if (!Objects.equals(request.nextVisitDate(), visit.getNextVisitDate())) {
            throw new ConflictException("A later visit already answered this follow-up, so its date is fixed",
                    "nextVisitDate");
        }
    }

    /** The newest visit stays editable, but it cannot be dragged behind the visit it follows. */
    private static void ensureNotBeforeTheVisitItFollows(Visit visit, LocalDate visitDate) {
        Visit previous = visit.getPreviousVisit();
        if (previous != null && visitDate.isBefore(previous.getVisitDate())) {
            throw new ConflictException("This visit cannot be earlier than the visit it follows", "visitDate");
        }
    }

    /** A follow-up booked for the day of the visit is not a follow-up, so it must be strictly later. */
    private static void ensureFollowUpAfterVisit(LocalDate visitDate, LocalDate nextVisitDate) {
        if (nextVisitDate != null && !nextVisitDate.isAfter(visitDate)) {
            throw new ConflictException("The next visit must be after the visit date", "nextVisitDate");
        }
    }

    /** Ids only: notes and patient names must never reach a log (rules.md 5.8). */
    private String describe(Visit visit) {
        return "id=" + visit.getId() + " patient=" + visit.getPatient().getId();
    }
}
