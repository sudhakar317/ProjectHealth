package com.clinic.audit;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    List<AuditLog> findByEventTypeOrderByIdAsc(String eventType);

    Page<AuditLog> findByEventType(String eventType, Pageable pageable);
}
