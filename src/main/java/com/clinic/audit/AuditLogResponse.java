package com.clinic.audit;

import java.time.Instant;

/**
 * The IP address is deliberately not exposed: it is still recorded on every entry for a
 * database-level security review, but it added a column of noise to the admin screen.
 */
public record AuditLogResponse(Long id, String eventType, String actorEmail, String details, Instant createdAt) {

    static AuditLogResponse from(AuditLog log) {
        return new AuditLogResponse(log.getId(), log.getEventType(), log.getActorEmail(), log.getDetails(),
                log.getCreatedAt());
    }
}
