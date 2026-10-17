package com.clinic.audit;

import com.clinic.common.PageResponse;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {

    public static final String LOGIN_SUCCESS = "LOGIN_SUCCESS";
    public static final String LOGIN_FAILURE = "LOGIN_FAILURE";
    public static final String LOGOUT = "LOGOUT";
    public static final String DOCTOR_CREATED = "DOCTOR_CREATED";
    public static final String DOCTOR_UPDATED = "DOCTOR_UPDATED";
    public static final String DOCTOR_DELETED = "DOCTOR_DELETED";
    public static final String SPECIALIZATION_CREATED = "SPECIALIZATION_CREATED";
    public static final String SPECIALIZATION_UPDATED = "SPECIALIZATION_UPDATED";
    public static final String SPECIALIZATION_DELETED = "SPECIALIZATION_DELETED";
    // Patient events record only the id: names, phones and notes must never reach a log (rules.md 5.8).
    public static final String PATIENT_CREATED = "PATIENT_CREATED";
    public static final String PATIENT_VIEWED = "PATIENT_VIEWED";
    public static final String PATIENT_UPDATED = "PATIENT_UPDATED";
    public static final String PATIENT_DELETED = "PATIENT_DELETED";
    public static final String PATIENT_RESTORED = "PATIENT_RESTORED";
    public static final String VISIT_CREATED = "VISIT_CREATED";
    public static final String VISIT_UPDATED = "VISIT_UPDATED";
    public static final String VISIT_DELETED = "VISIT_DELETED";
    public static final String VISIT_RESTORED = "VISIT_RESTORED";

    private static final int MAX_PAGE_SIZE = 100;

    private final AuditLogRepository repository;

    public AuditService(AuditLogRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String eventType, String actorEmail, String details, String ipAddress) {
        repository.save(new AuditLog(eventType, actorEmail, details, ipAddress));
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditLogResponse> list(String eventType, LocalDate from, LocalDate to, int page, int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "id"));
        return PageResponse.of(repository.findAll(filter(eventType, from, to), pageable).map(AuditLogResponse::from));
    }

    /** Both dates are inclusive and read in the server's zone, so "to" covers the whole of that day. */
    private Specification<AuditLog> filter(String eventType, LocalDate from, LocalDate to) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (eventType != null && !eventType.isBlank()) {
                predicates.add(cb.equal(root.get("eventType"), eventType));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), startOfDay(from)));
            }
            if (to != null) {
                predicates.add(cb.lessThan(root.get("createdAt"), startOfDay(to.plusDays(1))));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static Instant startOfDay(LocalDate date) {
        return date.atStartOfDay(ZoneId.systemDefault()).toInstant();
    }
}
