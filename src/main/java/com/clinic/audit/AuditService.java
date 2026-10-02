package com.clinic.audit;

import com.clinic.common.PageResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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
    public PageResponse<AuditLogResponse> list(String eventType, int page, int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "id"));
        var result = eventType == null || eventType.isBlank()
                ? repository.findAll(pageable)
                : repository.findByEventType(eventType, pageable);
        return PageResponse.of(result.map(AuditLogResponse::from));
    }
}
