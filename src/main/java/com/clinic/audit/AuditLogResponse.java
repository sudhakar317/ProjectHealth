package com.clinic.audit;

import java.time.Instant;

public record AuditLogResponse(Long id, String eventType, String actorEmail, String details, String ipAddress,
                               Instant createdAt) {

    static AuditLogResponse from(AuditLog log) {
        return new AuditLogResponse(log.getId(), log.getEventType(), log.getActorEmail(), log.getDetails(),
                log.getIpAddress(), log.getCreatedAt());
    }
}
